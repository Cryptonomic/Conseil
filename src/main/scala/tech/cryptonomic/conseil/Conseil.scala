package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import cats.effect.{ContextShift, IO}
import cats.effect.concurrent.MVar
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import tech.cryptonomic.conseil.io.MainOutputs.ConseilOutput
import tech.cryptonomic.conseil.config.ConseilAppConfig
import tech.cryptonomic.conseil.directives.EnableCORSDirectives
import tech.cryptonomic.conseil.metadata.{AttributeValuesCacheOverrides, MetadataService, UnitTransformation}
import tech.cryptonomic.conseil.routes._
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations.{AttributeValuesCache, AttributesCache, EntitiesCache}
import tech.cryptonomic.conseil.tezos.{ApiOperations, TezosPlatformDiscoveryOperations}

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object Conseil extends App with LazyLogging with EnableCORSDirectives with ConseilAppConfig with FailFastCirceSupport with ConseilOutput {

  loadApplicationConfiguration(args) match {
    case Right((server, platforms, securityApi, verbose, metadataOverrides)) =>

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
      val attributeValuesCache = MVar[IO].empty[AttributeValuesCache].unsafeRunSync()
      lazy val transformation = new UnitTransformation(metadataOverrides)
      lazy val cacheOverrides = new AttributeValuesCacheOverrides(metadataOverrides)

      lazy val tezosPlatformDiscoveryOperations =
        TezosPlatformDiscoveryOperations(ApiOperations, attributesCache, entitiesCache, attributeValuesCache, cacheOverrides, server.cacheTTL)(executionContext)

//      tezosPlatformDiscoveryOperations.init().onComplete {
//        case Failure(exception) => logger.error("Pre-caching metadata failed", exception)
//        case Success(_) =>
//          tezosPlatformDiscoveryOperations.initAttributesCount().onComplete {
//            case Failure(exception) => logger.error("Pre-caching attributes counts failed", exception)
//            case Success(_) =>
//              tezosPlatformDiscoveryOperations.isCachingComplete = true
//              logger.info("Pre-caching attributes counts successful!")
//          }
//          logger.info("Pre-caching successful!")
//      }

      for {
        _ <- tezosPlatformDiscoveryOperations.init()
        _ = logger.info("Pre-caching successful!")
        _ <- tezosPlatformDiscoveryOperations.initAttributesCount()
      } {
        tezosPlatformDiscoveryOperations.isCachingComplete = true
        logger.info("Pre-caching attributes counts successful!")
      }

      lazy val metadataService = new MetadataService(platforms, transformation, cacheOverrides, tezosPlatformDiscoveryOperations)
      lazy val platformDiscovery = PlatformDiscovery(metadataService)(tezosDispatcher)
      lazy val data = Data(platforms, tezosPlatformDiscoveryOperations)(tezosDispatcher)

      val route = cors() {
        enableCORS {
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
      } ~
      pathPrefix("docs") {
        pathEndOrSingleSlash {
          getFromResource("web/index.html")
        }
      } ~
      pathPrefix("swagger-ui") {
        getFromResourceDirectory("web/swagger-ui/")
      } ~
      Docs.route

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
