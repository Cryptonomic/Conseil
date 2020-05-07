package tech.cryptonomic.conseil.api.directives

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.headers.{
  `Access-Control-Allow-Credentials`,
  `Access-Control-Allow-Headers`,
  `Access-Control-Allow-Methods`,
  `Access-Control-Allow-Origin`
}
import akka.http.scaladsl.server.directives.RespondWithDirectives

trait EnableCORSDirectives extends RespondWithDirectives {

  private val allowedCorsVerbs = List(
    CONNECT,
    DELETE,
    GET,
    HEAD,
    OPTIONS,
    PATCH,
    POST,
    PUT,
    TRACE
  )

  private val allowedCorsHeaders = List(
    "X-Requested-With",
    "content-type",
    "origin",
    "accept",
    "apiKey"
  )

  lazy val enableCORS =
    respondWithHeader(`Access-Control-Allow-Origin`.`*`) &
        respondWithHeader(`Access-Control-Allow-Methods`(allowedCorsVerbs)) &
        respondWithHeader(`Access-Control-Allow-Headers`(allowedCorsHeaders)) &
        respondWithHeader(`Access-Control-Allow-Credentials`(true))

}
