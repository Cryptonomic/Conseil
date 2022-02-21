package tech.cryptonomic.conseil.api.util.cors

import org.http4s.Method._
import org.http4s.server.middleware.CORS
import org.typelevel.ci.CIString

private[api] trait EnableCORS {

  private val allowedCorsVerbs = Set(CONNECT, DELETE, GET, HEAD, OPTIONS, PATCH, POST, PUT, TRACE)
  private val allowedCorsHeaders = Set("X-Requested-With", "content-type", "origin", "accept", "apiKey")
    .map(CIString.apply)

  protected val corsPolicy =
    CORS.policy.withAllowOriginAll
      .withAllowMethodsIn(allowedCorsVerbs)
      .withAllowHeadersIn(allowedCorsHeaders)
      .withAllowCredentials(true)

}
