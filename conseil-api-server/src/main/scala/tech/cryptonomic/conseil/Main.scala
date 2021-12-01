package tech.cryptonomic.conseil

import cats.effect.IOApp
import cats.effect.IO
import com.comcast.ip4s._
import org.http4s.ember.server.EmberServerBuilder

object Main extends IOApp.Simple {
  def run: IO[Unit] =
    EmberServerBuilder
      .default[IO]
      .withHost(ipv4"0.0.0.0")
      .withPort(port"8080")
      .withHttpApp(Routing.instance[IO])
      .build
      .useForever
}
