package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.{BasicDirectives, ExecutionDirectives}
import akka.stream.ActorMaterializer
import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.MVar
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.slf4j.MDC
import tech.cryptonomic.conseil.io.MainOutputs.ConseilOutput
import tech.cryptonomic.conseil.config.ConseilAppConfig
import tech.cryptonomic.conseil.directives.EnableCORSDirectives
import tech.cryptonomic.conseil.routes._
import tech.cryptonomic.conseil.routes.openapi.OpenApiDoc
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations.{AttributesCache, EntitiesCache}
import tech.cryptonomic.conseil.tezos.{ApiOperations, TezosPlatformDiscoveryOperations}

import scala.concurrent.{ExecutionContextExecutor, duration}
import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.util.{Failure, Success}

object Conseil extends App with LazyLogging with EnableCORSDirectives with ConseilAppConfig with FailFastCirceSupport with ConseilOutput {

  loadApplicationConfiguration(args) match {
    case Right((server, platforms, securityApi, verbose)) =>

      val validateApiKey = headerValueByName("apikey").tflatMap[Tuple1[String]] {
        case Tuple1(apiKey) if securityApi.validateApiKey(apiKey) =>
          provide(apiKey)
        case _ =>
          complete((Unauthorized, "Incorrect API key"))
      }

      implicit val system: ActorSystem = ActorSystem("conseil-system")
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val tezosDispatcher = system.dispatchers.lookup("akka.tezos-dispatcher")
      lazy val tezos = Tezos(tezosDispatcher)

      // This part is a temporary middle ground between current implementation and moving code to use IO
      implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
      val attributesCache = MVar[IO].empty[AttributesCache].unsafeRunSync()
      val entitiesCache = MVar[IO].empty[EntitiesCache].unsafeRunSync()
      lazy val tezosPlatformDiscoveryOperations =
        TezosPlatformDiscoveryOperations(ApiOperations, attributesCache, entitiesCache, server.cacheTTL)(executionContext)

      tezosPlatformDiscoveryOperations.init().onComplete {
        case Failure(exception) => logger.error("Pre-caching metadata failed", exception)
        case Success(_) => logger.info("Pre-caching successful!")
      }
      lazy val platformDiscovery = PlatformDiscovery(platforms, tezosPlatformDiscoveryOperations)(tezosDispatcher)
      lazy val data = Data(platforms, tezosPlatformDiscoveryOperations)(tezosDispatcher)

//      def xxx: Directive0 = {
//        extract{ r =>
//
//        }
//      }

      def recordResponseValues = BasicDirectives.extractRequestContext.flatMap { ctx =>
        val requestStartTime = System.nanoTime()
        BasicDirectives.mapResponse { resp =>
          val result = record(requestStartTime)

          MDC.put("responseTime", result.toMillis.toString)
          MDC.put("path", ctx.request.uri.path.toString())
          MDC.put("apiKey", ctx.request.headers.find(_.is("apiKey")).map(_.toString()).getOrElse(""))
          MDC.put("responseCode",  resp.status.value)
          logger.debug("HTTP request")
          MDC.clear()
          resp
        }
      }

//      private def responseTimeRecordingExceptionHandler(endpoint: String, requestStartTime: Long) = ExceptionHandler {
//  case NonFatal(e) =>
//  record(endpoint, requestStartTime)
//
//    // Rethrow the exception to allow proper handling
//    // from handlers higher ip in the hierarchy
//  throw e
//  }
      def record(requestStartTime: Long): Duration = {
        val requestEndTime = System.nanoTime()
        val total = new FiniteDuration(requestEndTime - requestStartTime, duration.NANOSECONDS)

        total
      }


      val route = cors() {
        enableCORS {
          recordResponseValues {
            validateApiKey { _ =>
              logRequest("Conseil", Logging.DebugLevel) {
                tezos.route ~
                  AppInfo.route
              } ~
                logRequest("Metadata Route", Logging.DebugLevel) {
                  platformDiscovery.route
                } ~
                logRequest("Data Route", Logging.DebugLevel) {
                  data.getRoute ~ data.postRoute
                }
            } ~
              options {
                // Support for CORS pre-flight checks.
                complete("Supported methods : GET and POST.")
              }
          }
        }
      } ~
      pathPrefix("docs") {
        pathEndOrSingleSlash {
          getFromResource("web/index.html")
        }
      } ~
      pathPrefix("swagger-ui") {
        getFromResourceDirectory("web/swagger-ui/")
      } ~
      path("openapi.json") {
        complete(OpenApiDoc.openapiJson)
      }

      val bindingFuture = Http().bindAndHandle(route, server.hostname, server.port)
      displayInfo(server)
      if (verbose.on) displayConfiguration(platforms)

      sys.addShutdownHook {
        bindingFuture
          .flatMap(_.unbind().andThen { case _ => logger.info("Server stopped...") })
          .flatMap(_ => system.terminate())
          .onComplete(_ => logger.info("We're done here, nothing else to see"))
      }

    case Left(errors)
    =>
    //nothing to do
  }

}
