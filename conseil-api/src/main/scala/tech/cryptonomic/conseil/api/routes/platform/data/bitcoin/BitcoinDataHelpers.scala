package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import akka.http.scaladsl.server.Route
import cats.Functor
import endpoints.algebra.Documentation
import io.circe._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataHelpers
import tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.generic.BitcoinDataEndpoints
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryValidationError

/** Represents helper for Data Endpoints that can be used to implement custom encoder for Bitcoin specific types */
private[bitcoin] class BitcoinDataHelpers extends BitcoinDataEndpoints with ApiDataHelpers {

  /** Method for validating query request */
  override def validated[A](
      response: A => Route,
      invalidDocs: Documentation
  ): Either[List[QueryValidationError], A] => Route = defaultValidated(response, invalidDocs)

  /** Query string functor adding map operation */
  implicit override def bitcoinQsFunctor: Functor[QueryString] = defaultQsFunctor

  /** Represents the function, that is going to encode the blockchain specific data types */
  override protected def customAnyEncoder: PartialFunction[Any, Json] = PartialFunction.empty

}
