package tech.cryptonomic.conseil

import cats.effect.IOApp
import cats.effect.IO
import org.http4s.ember.client.EmberClientBuilder
import sttp.tapir.client.http4s.Http4sClientInterpreter
import org.http4s.Status
import org.http4s.implicits._
import tech.cryptonomic.conseil.info.model.GenericServerError

import io.circe.generic.semiauto._

object Main extends IOApp.Simple {

  def run: IO[Unit] =
    EmberClientBuilder.default[IO].build.use { client =>
      implicit val errorDecoder = deriveDecoder[GenericServerError]

      val (req, handler) =
        Http4sClientInterpreter[IO]()
          .toRequestUnsafe(protocol.appInfo, Some(uri"http://localhost:8080"))
          .apply(Some("napis"), "napis")

      client
        .run(req)
        .use {
          case r if r.status == Status.InternalServerError =>
            r.bodyText.compile.string
              .map(io.circe.parser.decode[GenericServerError]) // .flatMap(_.raiseError)
              .void

          case r => handler(r).map(_.merge).void // FIXME: useless
          // handler(r).rethrow
        }

    }

}
