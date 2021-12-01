package tech.cryptonomic.conseil

import cats.effect.{IO, IOApp}
import cats.implicits._
import org.http4s.ember.client.EmberClientBuilder
import org.http4s.implicits._
import org.http4s.Status
import sttp.tapir.client.http4s.Http4sClientInterpreter

import tech.cryptonomic.conseil.info.model.GenericServerError
import tech.cryptonomic.conseil.info.converters.genericServerErrorDecoder

object Main extends IOApp.Simple {
  def run: IO[Unit] =
    EmberClientBuilder
      .default[IO]
      .build
      .use { client =>
        val (req, handler) =
          Http4sClientInterpreter[IO]()
            .toRequestUnsafe(protocol.appInfo, Some(uri"http://localhost:8080"))
            .apply(None, "api key")

        client
          .run(req)
          .use {
            case r if r.status == Status.InternalServerError =>
              r.bodyText.compile.string
                .map(io.circe.parser.decode[GenericServerError])

            case r => handler(r).rethrow
          }
          .void
      }
}
