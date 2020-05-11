package tech.cryptonomic.conseil.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import tech.cryptonomic.conseil.api.config.{ConseilAppConfig, ConseilConfiguration}
import tech.cryptonomic.conseil.api.util.Retry.retry
import tech.cryptonomic.conseil.common.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.common.config._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor}
import scala.language.postfixOps
import scala.util.Failure

object Conseil extends App with LazyLogging with ConseilAppConfig with FailFastCirceSupport with ConseilMainOutput {

  loadApplicationConfiguration(args) match {
    case Left(errors) =>
    //nothing to do
    case Right(config) =>
      implicit val system: ActorSystem = ActorSystem("conseil-system")
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val retries = if (config.failFast.on) Some(0) else None

      val serverBinding =
        retry(maxRetry = retries, deadline = Some(config.server.startupDeadline fromNow))(ConseilApi.create(config)).andThen {
          case Failure(error) =>
            logger.error(
              "The server was not started correctly, I failed to create the required Metadata service",
              error
            )
            Await.ready(system.terminate(), 10.seconds)
        }.flatMap(
          runServer(_, config.server, config.platforms, config.verbose)
        )

      sys.addShutdownHook {
        serverBinding
          .flatMap(_.unbind().andThen { case _ => logger.info("Server stopped...") })
          .andThen {
            case _ => system.terminate()
          }
          .onComplete(_ => logger.info("We're done here, nothing else to see"))
      }

  }

  /** Starts the web server
    * @param server configuration needed for the http server
    * @param platforms configuration regarding the exposed blockchains available
    * @param verbose flag to state if the server should log a more detailed configuration setup upon startup
    */
  def runServer(
      api: ConseilApi,
      server: ConseilConfiguration,
      platforms: PlatformsConfiguration,
      verbose: VerboseOutput
  )(implicit executionContext: ExecutionContext, system: ActorSystem, mat: ActorMaterializer) = {
    val bindingFuture = Http().bindAndHandle(api.route, server.hostname, server.port)
    displayInfo(server)
    if (verbose.on) displayConfiguration(platforms)
    bindingFuture

  }
}
