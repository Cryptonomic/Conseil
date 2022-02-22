package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.api.config.ConseilAppConfig
import tech.cryptonomic.conseil.common.util.syntax._
import tech.cryptonomic.conseil.api.util.syntax._

import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder

import cats.effect.{ExitCode, IO, IOApp}

import scala.concurrent.duration._

object Conseil extends IOApp with ConseilAppConfig with APIDocs with ConseilMainOutput {

  import ConseilAppConfig.CombinedConfiguration

  def run(args: List[String]) = runConseil(args)

  /* Starts the web server resource and retries running if needed */
  def runConseil(args: List[String]) =
    loadApplicationConfiguration(args)
      .fold(
        _ => IO.raiseError(new RuntimeException("Cannot load provided config")),
        config =>
          ConseilApi.create(config).flatMap {
            IO("config loaded successfully\n").debug >>
              IO.sleep(500.millis) >>
              IO("running server\n").debug >>
              IO.sleep(500.millis) >>
              runServer(_, config)
          }
      )
      .retry(4, 1.hour)
      .as(ExitCode.Success)

  def useServerBuilderResource(api: ConseilApi) =
    EmberServerBuilder
      .default[IO]
      // .withHost(host"conseil-api")
      .withHost(ipv4"0.0.0.0")
      .withPort(port"1337")
      .withHttpApp(Routing.instance(api))
      .build
      .useForever

  def runServer(api: ConseilApi, config: CombinedConfiguration) =
    displayInfo(config.server) >>
        IO.sleep(1500.millis) >>
        useServerBuilderResource(api).handleErrorWith {
          case e: StackOverflowError => IO("stack overflow").debug >> IO.raiseError(e)
        } >>
        IO.sleep(1500.millis) >>
        IO(if (config.verbose.on) displayConfiguration(config.platforms)).debug.void

}
