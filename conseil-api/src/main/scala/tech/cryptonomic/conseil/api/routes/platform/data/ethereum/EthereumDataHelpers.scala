package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import akka.http.scaladsl.server.Route
import endpoints4s.akkahttp.server.Endpoints
import endpoints4s.algebra.Documentation

import tech.cryptonomic.conseil.api.routes.validation.Validation.QueryValidating
import tech.cryptonomic.conseil.api.routes.platform.data.ApiValidation.defaultValidated
import tech.cryptonomic.conseil.api.routes.platform.data.{
  ApiDataEndpoints,
  ApiDataJsonSchemas,
  ApiFilterQueryString,
  ApiServerJsonSchema
}

/** Represents helper for Data Endpoints that can be used to implement custom encoder for Ethereum specific types */
private[ethereum] class EthereumDataHelpers
    extends Endpoints
    with ApiDataEndpoints
    with ApiDataJsonSchemas
    with ApiFilterQueryString
    with ApiServerJsonSchema {

  /** Method for validating query request */
  override def validated[A](
      response: A => Route,
      invalidDocs: Documentation
  ): QueryValidating[A] => Route = defaultValidated(response, invalidDocs)

  /** Represents the function, that is going to encode the blockchain specific data types */
  override protected def customAnyEncoder = PartialFunction.empty

}
