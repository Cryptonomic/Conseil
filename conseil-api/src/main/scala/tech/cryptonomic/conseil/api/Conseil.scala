package tech.cryptonomic.conseil.api

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.{ActorMaterializer, Materializer}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import tech.cryptonomic.conseil.api.config.{ConseilAppConfig, ConseilConfiguration}
import tech.cryptonomic.conseil.api.util.Retry.retry
import tech.cryptonomic.conseil.api.util.RetryStrategy.retryGiveUpStrategy
import tech.cryptonomic.conseil.common.io.Logging
import tech.cryptonomic.conseil.common.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.common.config._

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.language.postfixOps
import scala.util.Failure

object Conseil extends App with ConseilAppConfig with FailFastCirceSupport with ConseilMainOutput {

  /* Sadly, we're currently forced to do this to actually configure the loggers */
  Logging.init()

  loadApplicationConfiguration(args) match {
    case Left(errors) =>
      //nothing to do, take note that the errors were already logged in the previous call
      logger.error("There is an error in the provided configuration")
    case Right(config) =>
      implicit val system: ActorSystem = ActorSystem("conseil-system")
      implicit val materializer: Materializer = ActorMaterializer()
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val retries = if (config.failFast.on) Some(0) else None

      val serverBinding =
        retry(
          maxRetry = retries,
          deadline = Some(config.server.startupDeadline fromNow),
          giveUpOnThrowable = retryGiveUpStrategy
        )(ConseilApi.create(config)).andThen {
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
  )(implicit system: ActorSystem) = {
    val bindingFuture = Http().newServerAt(server.hostname, server.port).bindFlow(api.route)
    displayInfo(server)
    if (verbose.on) displayConfiguration(platforms)
    bindingFuture

  }
}
