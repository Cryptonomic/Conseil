package tech.cryptonomic.conseil

import cats.effect.{Async, IO}
import org.http4s.HttpApp
import sttp.model.StatusCode
import sttp.tapir.docs.openapi.OpenAPIDocsInterpreter
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.openapi.OpenAPI
import sttp.tapir.openapi.circe.yaml._
import sttp.tapir.server.ServerEndpoint
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.ValuedEndpointOutput
import sttp.tapir.statusCode
import sttp.tapir.swagger.SwaggerUI

trait APIDocs {
  val docs: OpenAPI = OpenAPIDocsInterpreter().toOpenAPI(protocol.appInfo, "My App", "1.0")
  val docsRoute: List[ServerEndpoint[Any, IO]] = SwaggerUI[IO](docs.toYaml)
}

object Routing extends APIDocs {

  import tech.cryptonomic.conseil.info.model._
  import tech.cryptonomic.conseil.info.converters._

  val pai: ServerEndpoint[Any, IO] = protocol.appInfo.serverLogicSuccess(_ => emptyInfo)

  val endpoints = pai :: docsRoute

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
