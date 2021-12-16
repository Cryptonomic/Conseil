package tech.cryptonomic.conseil

import cats.effect.{Async, IO}
import org.http4s.HttpApp
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.ValuedEndpointOutput
import sttp.tapir.statusCode

object Routing extends APIDocs {

  import tech.cryptonomic.conseil.info.model._
  import tech.cryptonomic.conseil.info.converters._

  val appInfo: ServerEndpoint[Any, IO] = protocol.appInfo.serverLogicSuccess(_ => currentInfo)

  val endpoints = appInfo :: docsRoute

  def instance[F[_]: Async]: HttpApp[IO] =
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
