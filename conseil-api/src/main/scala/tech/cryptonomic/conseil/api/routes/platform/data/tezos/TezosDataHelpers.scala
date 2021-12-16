package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import akka.http.scaladsl.server.Route
import endpoints4s.akkahttp.server.Endpoints
import endpoints4s.algebra.Documentation
import tech.cryptonomic.conseil.api.routes.validation.Validation.QueryValidating
import tech.cryptonomic.conseil.api.routes.platform.data.ApiServerJsonSchema
import tech.cryptonomic.conseil.api.routes.platform.data.ApiValidation.defaultValidated
import tech.cryptonomic.conseil.common.tezos.Tables

/** Trait with helpers needed for data routes */
private[tezos] class TezosDataHelpers extends Endpoints with TezosDataEndpoints with ApiServerJsonSchema {

  /** Method for validating query request */
  override def validated[A](
      response: A => Route,
      invalidDocs: Documentation
  ): QueryValidating[A] => Route =
    defaultValidated(response, invalidDocs)

  /** Represents the function, that is going to encode the blockchain specific data types */
  override protected def customAnyEncoder = {
    case x: Tables.BlocksRow => blocksRowSchema.encoder.encode(x)
    case x: Tables.AccountsRow => accountsRowSchema.encoder.encode(x)
    case x: Tables.OperationGroupsRow => operationGroupsRowSchema.encoder.encode(x)
    case x: Tables.OperationsRow => operationsRowSchema.encoder.encode(x)
  }

}
