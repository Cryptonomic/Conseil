package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.api.config.ConseilAppConfig
import tech.cryptonomic.conseil.common.io.Logging
import tech.cryptonomic.conseil.common.util.syntax._
import tech.cryptonomic.conseil.api.util.syntax._

import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder

import cats.effect.{ExitCode, IO, IOApp}
import scala.concurrent.duration._
import cats.effect.kernel.Outcome

object Conseil extends IOApp with ConseilAppConfig with APIDocs with ConseilMainOutput {

  import ConseilAppConfig.CombinedConfiguration

  /* Sadly, we're currently forced to do this to actually configure the loggers */
  Logging.init()

  def run(args: List[String]) = runConseil(args).as(ExitCode.Success)

  /* Starts the web serverResource */
  def runConseil(args: List[String]) =
    loadApplicationConfiguration(args)
      .fold(
        _ => IO.raiseError(new RuntimeException("Cannot load provided config")),
        config =>
          ConseilApi.create(config).flatMap {
            IO("config loaded successfully\n").debug >>
              IO.sleep(500.millis).debug >>
              IO("running server\n").debug >>
              IO.sleep(500.millis).debug >>
              runServer(_, config)
                .retry(4, 1.hour)
          }
      )

  def useServerBuilderResource(api: ConseilApi) =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(Routing.instance(api))
      .build
      .useForever
      .guaranteeCase {
        case Outcome.Succeeded(x) => x
        case _ => IO.raiseError(new RuntimeException("ember server builder failed"))
      }
  // .handleErrorWith(_ => IO.raiseError(new RuntimeException("ember server builder failed")))

  def runServer(api: ConseilApi, config: CombinedConfiguration) =
    displayInfo(config.server) >>
        IO.sleep(1500.millis) >>
        useServerBuilderResource(api) >>
        IO.sleep(1500.millis) >>
        IO(if (config.verbose.on) displayConfiguration(config.platforms)).debug.void

}
