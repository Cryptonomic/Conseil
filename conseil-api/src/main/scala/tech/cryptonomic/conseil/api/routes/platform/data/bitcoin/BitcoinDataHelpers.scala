package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import akka.http.scaladsl.server.Route
import endpoints.akkahttp.server.Endpoints
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.api.routes.platform.data.ApiServerJsonSchema
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryValidationError
import tech.cryptonomic.conseil.api.routes.platform.data.ApiValidation.defaultValidated

/** Represents helper for Data Endpoints that can be used to implement custom encoder for Bitcoin specific types */
private[bitcoin] class BitcoinDataHelpers extends Endpoints with BitcoinDataEndpoints with ApiServerJsonSchema {

  /** Method for validating query request */
  override def validated[A](
      response: A => Route,
      invalidDocs: Documentation
  ): Either[List[QueryValidationError], A] => Route = defaultValidated(response, invalidDocs)

  /** Represents the function, that is going to encode the blockchain specific data types */
  override protected def customAnyEncoder = PartialFunction.empty

}
