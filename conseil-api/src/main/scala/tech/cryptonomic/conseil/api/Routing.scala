package tech.cryptonomic.conseil.api

import cats.effect.IO
import cats.syntax.option._
import org.http4s.HttpApp
import org.http4s.Method._
import org.http4s.server.middleware.CORS
import org.typelevel.ci.CIString
import sttp.model.StatusCode
import sttp.tapir.generic.auto._
import sttp.tapir.json.circe._
import sttp.tapir.server.http4s.{Http4sServerInterpreter, Http4sServerOptions}
import sttp.tapir.server.interceptor.ValuedEndpointOutput
import sttp.tapir.statusCode

object Routing {

  import tech.cryptonomic.conseil.api.info.model._
  import tech.cryptonomic.conseil.api.info.converters._

  private val allowedCorsVerbs = Set(CONNECT, DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT, TRACE)
  private val allowedCorsHeaders = Set("X-Requested-With", "content-type", "origin", "accept", "apiKey")
    .map(CIString.apply)

  val corsPolicy =
    CORS.policy.withAllowOriginAll
      .withAllowMethodsIn(allowedCorsVerbs)
      .withAllowHeadersIn(allowedCorsHeaders)
      .withAllowCredentials(true)

  def instance(api: ConseilApi): HttpApp[IO] =
    corsPolicy.apply(
      Http4sServerInterpreter[IO](
        Http4sServerOptions
          .customInterceptors[IO, IO]
          .exceptionHandler { _ =>
            ValuedEndpointOutput(
              jsonBody[GenericServerError].and(statusCode(StatusCode.InternalServerError)),
              GenericServerError("serverResource failed")
            ).some
          }
          .options
      ).toRoutes(api.route).orNotFound
    )

}
