package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.api.config.ConseilAppConfig
import tech.cryptonomic.conseil.common.io.Logging

import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder

import cats.effect.{ExitCode, IO, IOApp}

object Conseil extends IOApp with ConseilAppConfig with APIDocs with ConseilMainOutput {

  import ConseilAppConfig.CombinedConfiguration

  /* Sadly, we're currently forced to do this to actually configure the loggers */
  Logging.init()

  def run(args: List[String]) =
    runConseil(args).as(ExitCode.Success)

  /* Starts the web serverResource */
  def runConseil(args: List[String]) =
    loadApplicationConfiguration(args)
      .fold(
        _ => IO.raiseError(new RuntimeException("There is an error in the provided configuration")),
        // TODO: implement [[cats.effect]] retry for [[Right]] case
        config => ConseilApi.create(config).flatMap(runServer(_, config))
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
    IO(displayInfo(config.server)) *>
        useServerBuilderResource(api) *>
        IO(if (config.verbose.on) displayConfiguration(config.platforms) else ())

}
