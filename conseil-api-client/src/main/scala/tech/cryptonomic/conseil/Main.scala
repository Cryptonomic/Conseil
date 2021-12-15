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
  val localhost = Some(uri"http://localhost:8080")
  val clientInterpretter = Http4sClientInterpreter[IO]()

  def run: IO[Unit] =
    EmberClientBuilder
      .default[IO]
      .build
      .use { client =>
        val (req, handler) =
          clientInterpretter.toRequestUnsafe(protocol.appInfo, localhost).apply("api key")

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

  // import tech.cryptonomic.conseil.platform.data.tezos.TezosDataEndpoints._
  // val (r, h) =
  //   clientInterpretter.toRequestUnsafe(tezosQueryEndpoint, localhost).apply(()) //"api key")
  // // val (rr, hh) =
  // // clientInterpretter.toRequestUnsafe(tezosBlocksEndpoint, localhost).apply()
  // val (rrr, hhh) =
  //   clientInterpretter.toRequestUnsafe(tezosBlocksHeadEndpoint, localhost).apply(()) // None, "api key")
  // val (rrrr, hhhh) =
  //   clientInterpretter.toRequestUnsafe(tezosBlockByHashEndpoint, localhost).apply("") // , "api key")
  // // val (rrrrr, hhhhh) =
  // //   clientInterpretter.toRequestUnsafe(tezosAccountsEndpoint, localhost).apply(None, "api key")

}
