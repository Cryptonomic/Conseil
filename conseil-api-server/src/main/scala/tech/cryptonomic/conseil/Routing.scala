package tech.cryptonomic.conseil

import tech.cryptonomic.conseil.platform.data.tezos.TezosDataRoutes

import cats.effect.IO
import cats.syntax.option._
import org.http4s.HttpApp
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.Http4sServerInterpreter
import sttp.tapir.server.http4s.Http4sServerOptions
import sttp.tapir.server.interceptor.ValuedEndpointOutput
import sttp.tapir.statusCode

object Routing extends APIDocs {

  import tech.cryptonomic.conseil.info.model._
  import tech.cryptonomic.conseil.info.converters._

  val appInfo: ServerEndpoint[Any, IO] = protocol.appInfo.serverLogicSuccess(_ => currentInfo)

  private val tezosDataRoutes: TezosDataRoutes = ???

  // TODO: add routes for discoveryEndpoints
  val allPlatformRoutes =
    appInfo :: docsRoute ++ tezosDataRoutes.getRoute

  // def instance[F[_]: Async]: HttpApp[F] =
  def instance: HttpApp[IO] =
    Http4sServerInterpreter[IO](
      Http4sServerOptions
        .customInterceptors[IO, IO]
        .exceptionHandler { _ =>
          ValuedEndpointOutput(
            jsonBody[GenericServerError].and(statusCode(StatusCode.InternalServerError)),
            GenericServerError("server failed")
          ).some
        }
        .options
    ).toRoutes(allPlatformRoutes).orNotFound

}
