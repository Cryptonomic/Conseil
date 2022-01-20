package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.api.config.{ConseilAppConfig, ConseilConfiguration}
import tech.cryptonomic.conseil.common.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.common.config._
import tech.cryptonomic.conseil.common.io.Logging

import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder

import cats.syntax.option._
import org.http4s.HttpApp
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
// import sttp.tapir.serverResource.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.ValuedEndpointOutput
import sttp.tapir.statusCode

import cats.effect.{ExitCode, IO, IOApp}

object Conseil extends IOApp with ConseilAppConfig /*with FailFastCirceSupport*/ with ConseilMainOutput {

  /* Sadly, we're currently forced to do this to actually configure the loggers */
  Logging.init()

  def run(args: List[String]): IO[ExitCode] =
    loadApplicationConfiguration(args) match {
      // nothing to do, take note that the errors were already logged in the previous call
      case Left(_) =>
        val msg = "There is an error in the provided configuration"
        IO.println(msg) *> IO.raiseError(new RuntimeException(msg))
      case Right(config: ConseilAppConfig.CombinedConfiguration) =>
        // FIXME: retry using [[cats.effect]] should be simple and straighforward
        ConseilApi
          .create(config)
          .flatMap(runServer(_, config.server, config.platforms, config.verbose))
          .as(ExitCode.Success)

      // sys.addShutdownHook {
      //   serverBinding
      //     .flatMap(_.unbind().andThen { case _ => logger.info("Server stopped...") })
      //     // .andThen {
      //     //   case _ => system.terminate()
      //     // }
      //     .onComplete(_ => logger.info("We're done here, nothing else to see"))
      // }
    }

  /** Starts the web serverResource
    * @param serverResource configuration needed for the http serverResource
    * @param platforms configuration regarding the exposed blockchains available
    * @param verbose flag to state if the serverResource should log a more detailed configuration setup upon startup
    */
  import tech.cryptonomic.conseil.info.model._
  import tech.cryptonomic.conseil.info.converters._

  def instance(api: ConseilApi): HttpApp[IO] =
    Http4sServerInterpreter[IO](
      Http4sServerOptions
        .customInterceptors[IO, IO]
        .exceptionHandler { _ =>
          ValuedEndpointOutput(
            jsonBody[GenericServerError].and(statusCode(StatusCode.InternalServerError)),
            GenericServerError("serverResource failed")
          ).some
        }
        .options
    ).toRoutes(api.route).orNotFound

  def runServer(
      api: ConseilApi,
      server: ConseilConfiguration,
      platforms: PlatformsConfiguration,
      verbose: VerboseOutput
  ) =
    for {
      _ <- IO(displayInfo(server))
      _ <- EmberServerBuilder
        .default[IO]
        .withHost(ipv4"0.0.0.0")
        .withPort(port"8080")
        .withHttpApp(instance(api))
        .build
        .useForever
        .void
      _ <- IO(if (verbose.on) displayConfiguration(platforms) else ())
    } yield ()
}
