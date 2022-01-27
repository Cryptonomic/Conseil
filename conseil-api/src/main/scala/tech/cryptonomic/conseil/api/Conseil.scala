package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.api.config.ConseilAppConfig
import tech.cryptonomic.conseil.common.io.Logging
import tech.cryptonomic.conseil.api.util.syntax._

import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder

import cats.effect.{ExitCode, IO, IOApp}
import scala.concurrent.duration._

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
            IO("config loaded successfully\n\n").debug >>
              // IO.sleep(3.seconds).debug *>
              IO("running server\n\n").debug >>
              // IO.sleep(500.millis).debug *>
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

  def runServer(api: ConseilApi, config: CombinedConfiguration) =
    // IO.sleep(5.seconds) >>
    displayInfo(config.server) >>
        useServerBuilderResource(api) >>
        IO.sleep(5.seconds) >>
        IO(if (config.verbose.on) displayConfiguration(config.platforms) else ())

}
