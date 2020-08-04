package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import akka.http.scaladsl.server.Route
import cats.Functor
import endpoints.algebra.Documentation
import io.circe._
import io.circe.syntax._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiCirceJsonSchema
import tech.cryptonomic.conseil.api.routes.platform.data.ApiValidation.defaultValidated
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryValidationError
import tech.cryptonomic.conseil.common.tezos.Tables

/** Trait with helpers needed for data routes */
private[tezos] class TezosDataHelpers extends TezosDataEndpoints with ApiCirceJsonSchema {

  /** Method for validating query request */
  override def validated[A](
      response: A => Route,
      invalidDocs: Documentation
  ): Either[List[QueryValidationError], A] => Route =
    defaultValidated(response, invalidDocs)

  /** Query string functor adding map operation */
  implicit override def qsFunctor: Functor[QueryString] = defaultQsFunctor

  /** Represents the function, that is going to encode the blockchain specific data types */
  override def customAnyEncoder: PartialFunction[Any, Json] = {
    case x: Tables.BlocksRow => x.asJson(blocksRowSchema.encoder)
    case x: Tables.AccountsRow => x.asJson(accountsRowSchema.encoder)
    case x: Tables.OperationGroupsRow => x.asJson(operationGroupsRowSchema.encoder)
    case x: Tables.OperationsRow => x.asJson(operationsRowSchema.encoder)
  }

}
