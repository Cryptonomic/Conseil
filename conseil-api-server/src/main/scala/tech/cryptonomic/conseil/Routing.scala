package tech.cryptonomic.conseil

import cats.effect.{Async, IO}
import org.http4s.HttpApp
import sttp.model.StatusCode
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.ValuedEndpointOutput
import sttp.tapir.statusCode

object Routing {

  import tech.cryptonomic.conseil.info.model._

  import sttp.tapir.generic.auto._
  import io.circe.generic.semiauto._
  import sttp.tapir.json.circe._

  implicit val errorEncoder = deriveEncoder[GenericServerError]
  implicit val errorDecoder = deriveDecoder[GenericServerError]

  def instance[F[_]: Async]: HttpApp[IO] = {

    val endpoints =
      List(protocol.appInfo.serverLogicSuccess(_ => emptyInfo))

    Http4sServerInterpreter[IO](
      Http4sServerOptions
        .customInterceptors[IO, IO]
        .exceptionHandler { _ =>
          Some(
            ValuedEndpointOutput(
              jsonBody[GenericServerError].and(statusCode(StatusCode.InternalServerError)),
              GenericServerError("server failed")
            )
          )
        }
        .options
    ).toRoutes(endpoints).orNotFound

  }

}
