package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import akka.http.scaladsl.server.Route
import cats.Functor
import endpoints.algebra.Documentation
import io.circe._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiValidation.defaultValidated
import tech.cryptonomic.conseil.api.routes.platform.data.{
  ApiCirceJsonSchema,
  ApiDataEndpoints,
  ApiDataJsonSchemas,
  ApiFilterQueryString
}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryValidationError

/** Represents helper for Data Endpoints that can be used to implement custom encoder for Bitcoin specific types */
private[ethereum] class EthereumDataHelpers
    extends ApiDataEndpoints
    with ApiDataJsonSchemas
    with ApiFilterQueryString
    with ApiCirceJsonSchema {

  /** Method for validating query request */
  override def validated[A](
      response: A => Route,
      invalidDocs: Documentation
  ): Either[List[QueryValidationError], A] => Route = defaultValidated(response, invalidDocs)

  /** Query string functor adding map operation */
  implicit override def qsFunctor: Functor[QueryString] = defaultQsFunctor

  /** Represents the function, that is going to encode the blockchain specific data types */
  override protected def customAnyEncoder: PartialFunction[Any, Json] = PartialFunction.empty

}
