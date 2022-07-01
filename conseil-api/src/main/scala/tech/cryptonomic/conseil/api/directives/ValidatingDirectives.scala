package tech.cryptonomic.conseil.api.directives

import akka.http.scaladsl.model.StatusCodes.Unauthorized
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives.{complete, onComplete, optionalHeaderValueByName, provide}
import tech.cryptonomic.conseil.api.security.Security.SecurityApi

import scala.util.Success

class ValidatingDirectives(securityApi: SecurityApi) {

  val validateApiKey: Directive[Tuple1[String]] = optionalHeaderValueByName("apikey").tflatMap[Tuple1[String]] {
    apiKeyTuple =>
      val apiKey = apiKeyTuple match {
        case Tuple1(key) => key
        case _ => None
      }

      onComplete(securityApi.validateApiKey(apiKey)).flatMap {
        case Success(true) => provide(apiKey.getOrElse(""))
        case _ =>
          complete(
            (
              Unauthorized,
              apiKey.fold("Missing API key") { _ =>
                "Incorrect API key"
              }
            )
          )
      }
  }

}
