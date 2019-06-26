package tech.cryptonomic.conseil

import java.net.InetSocketAddress

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.effect.{ContextShift, IO}
import akka.stream.scaladsl.Sink
import cats.effect.concurrent.MVar
import cats.effect.{ContextShift, IO}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import tech.cryptonomic.conseil.config.ConseilAppConfig
import tech.cryptonomic.conseil.directives.EnableCORSDirectives
import tech.cryptonomic.conseil.io.MainOutputs.ConseilOutput
import tech.cryptonomic.conseil.metadata.{AttributeValuesCacheConfiguration, MetadataService, UnitTransformation}
import tech.cryptonomic.conseil.io.MainOutputs.ConseilOutput
import tech.cryptonomic.conseil.metadata.{MetadataService, UnitTransformation}
import tech.cryptonomic.conseil.routes._
import tech.cryptonomic.conseil.tezos.{ApiOperations, MetadataCaching, TezosPlatformDiscoveryOperations}
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations.{AttributesCache, EntitiesCache}
import tech.cryptonomic.conseil.tezos.{ApiOperations, TezosPlatformDiscoveryOperations}
import tech.cryptonomic.conseil.util.RouteUtil._

import scala.concurrent.ExecutionContextExecutor
import scala.util.{Failure, Success}

object Conseil
    extends App
    with LazyLogging
    with EnableCORSDirectives
    with ConseilAppConfig
    with FailFastCirceSupport
    with ConseilOutput {

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

      // This part is a temporary middle ground between current implementation and moving code to use IO
      implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
      val metadataCaching = MetadataCaching.empty[IO].unsafeRunSync()

      lazy val transformation = new UnitTransformation(metadataOverrides)
      lazy val cacheOverrides = new AttributeValuesCacheConfiguration(metadataOverrides)

      lazy val tezosPlatformDiscoveryOperations =
        TezosPlatformDiscoveryOperations(ApiOperations, metadataCaching, cacheOverrides, server.cacheTTL)(
          executionContext,
          contextShift
        )

      tezosPlatformDiscoveryOperations.init().onComplete {
        case Failure(exception) => logger.error("Pre-caching metadata failed", exception)
        case Success(_) => logger.info("Pre-caching successful!")
      }

      tezosPlatformDiscoveryOperations.initAttributesCache.onComplete {
        case Failure(exception) => logger.error("Pre-caching attributes failed", exception)
        case Success(_) => logger.info("Pre-caching attributes successful!")
      }

      lazy val metadataService =
        new MetadataService(platforms, transformation, cacheOverrides, tezosPlatformDiscoveryOperations)
      lazy val platformDiscovery = PlatformDiscovery(metadataService)(tezosDispatcher)
      lazy val data = Data(platforms, metadataService, server)(tezosDispatcher)

      val route = cors() {
          enableCORS {
            recordResponseValues(inet)(materializer) {
              validateApiKey { _ =>
                logRequest("Conseil", Logging.DebugLevel) {
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
            Docs.route

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

    case Left(errors) =>
    //nothing to do
  }

}