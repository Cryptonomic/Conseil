package tech.cryptonomic.conseil

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, StatusCode}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.{Directive, Directive0, ExceptionHandler, RouteResult}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import cats.effect.concurrent.MVar
import cats.effect.{ContextShift, IO}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.slf4j.{LoggerFactory, MDC}
import tech.cryptonomic.conseil.config.ConseilAppConfig
import tech.cryptonomic.conseil.directives.EnableCORSDirectives
import tech.cryptonomic.conseil.io.MainOutputs.ConseilOutput
import tech.cryptonomic.conseil.routes._
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations.{AttributesCache, EntitiesCache}
import tech.cryptonomic.conseil.tezos.{ApiOperations, TezosPlatformDiscoveryOperations}

import scala.concurrent.{ExecutionContextExecutor, duration}
import scala.util.{Failure, Success, Try}

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

      val asyncLogger = LoggerFactory.getLogger("ASYNC_LOGGER")

      def recordResponseValues(inet: InetSocketAddress): Directive[Unit] =
        BasicDirectives.extractRequestContext.flatMap { ctx =>
          import scala.concurrent.duration._
          val apiVersion = if (ctx.request.uri.path.toString().startsWith("/v2")) "v2" else "v1"
          MDC.put("httpMethod", ctx.request.method.value)
          MDC.put("requestBody", ctx.request.entity.toStrict(1000.millis).value.get.get.data.utf8String)
          MDC.put("clientIp", inet.getAddress.getHostAddress)
          MDC.put("path", ctx.request.uri.path.toString())
          MDC.put("apiVersion", apiVersion)
          MDC.put("apiKey", ctx.request.headers.find(_.is("apikey")).map(_.value()).getOrElse(""))
          val requestStartTime = System.currentTimeMillis()
          MDC.put("tmpStartTime", requestStartTime.toString)

          val response = BasicDirectives.mapResponse { resp =>
            val requestEndTime = System.currentTimeMillis()
            val responseTime = requestEndTime - requestStartTime
            MDC.remove("tmpStartTime")
            MDC.put("responseTime", responseTime.toString)
            MDC.put("responseCode", resp.status.value)
            asyncLogger.info("HTTP request")
            MDC.clear()
            resp
          }
          response
        }

      def myExceptionHandler: ExceptionHandler =
        ExceptionHandler {
          case e: Throwable =>
            val responseTime = Try(System.currentTimeMillis() - MDC.get("tmpStartTime").toLong).getOrElse(0)
            MDC.remove("tmpStartTime")
            MDC.put("responseTime", responseTime.toString)
            MDC.put("responseCode", "500")
            asyncLogger.info("HTTP request")
            MDC.clear()
            e.printStackTrace()
            complete(HttpResponse(InternalServerError))
        }

      def route(inet: InetSocketAddress) = handleExceptions(myExceptionHandler) {
        cors() {
          enableCORS {
            recordResponseValues(inet) {
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
      }

      displayInfo(server)
      if (verbose.on) displayConfiguration(platforms)

      // https://stackoverflow.com/questions/40132262/obtaining-the-client-ip-in-akka-http
      Http().bind(server.hostname, server.port).runWith(Sink.foreach { conn =>
        val address = conn.remoteAddress

        conn.handleWith(route(address))
      })


      sys.addShutdownHook {

        Http()
          .shutdownAllConnectionPools()
          .map(_ => logger.info("Server stopped..."))
          .flatMap(_ => system.terminate())
          .onComplete(_ => logger.info("We're done here, nothing else to see"))
      }

    case Left(errors)
    =>
    //nothing to do
  }

}