package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.directives.EnableCORSDirectives
import tech.cryptonomic.conseil.config.ConseilAppConfig
import tech.cryptonomic.conseil.routes.{PlatformDiscovery, Tezos}

import scala.concurrent.ExecutionContextExecutor

object Conseil extends App with LazyLogging with EnableCORSDirectives with ConseilAppConfig {

  applicationConfiguration match {
    case Right((server, platforms, securityApi)) =>

      val validateApiKey = headerValueByName("apikey").tflatMap[Tuple1[String]] {
        case Tuple1(apiKey) if securityApi.validateApiKey(apiKey) =>
            provide(apiKey)
        case _ =>
            complete((Unauthorized, "Incorrect API key"))
      }

      implicit val system: ActorSystem = ActorSystem("conseil-system")
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val route = cors() {
        enableCORS {
          validateApiKey { _ =>
            logRequest("Conseil", Logging.DebugLevel) {
              pathPrefix("tezos") {
                Tezos(system.dispatchers.lookup("akka.tezos-dispatcher")).route
              }
            }
          } ~ options {
            // Support for CORS pre-flight checks.
            complete("Supported methods : GET and POST.")
          }
        } ~ logRequest("Service route", Logging.DebugLevel) {
          pathPrefix("metadata") {
            PlatformDiscovery(platforms)(system.dispatchers.lookup("akka.tezos-dispatcher")).route
          }
        }
      }

      val bindingFuture = Http().bindAndHandle(route, server.hostname, server.port)
      logger.info("Bonjour...")

      sys.addShutdownHook {
        bindingFuture
        .flatMap(_.unbind().andThen{ case _ => logger.info("Server stopped...")} )
        .flatMap( _ => system.terminate())
        .onComplete(_ => logger.info("We're done here, nothing else to see"))
      }

    case Left(errors) =>
      //nothing to do
  }
}
