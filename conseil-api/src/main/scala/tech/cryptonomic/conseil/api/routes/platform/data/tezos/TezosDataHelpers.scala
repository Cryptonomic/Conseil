package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import io.circe._
import io.circe.syntax._
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataHelpers
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.generic.TezosDataEndpoints
import tech.cryptonomic.conseil.common.tezos.Tables

/** Trait with helpers needed for data routes */
private[tezos] class TezosDataHelpers extends TezosDataEndpoints with ApiDataHelpers {

  /** Represents the function, that is going to encode the blockchain specific data types */
  override def customAnyEncoder: PartialFunction[Any, Json] = {
    case x: Tables.BlocksRow => x.asJson(blocksRowSchema.encoder)
    case x: Tables.AccountsRow => x.asJson(accountsRowSchema.encoder)
    case x: Tables.OperationGroupsRow => x.asJson(operationGroupsRowSchema.encoder)
    case x: Tables.OperationsRow => x.asJson(operationsRowSchema.encoder)
  }

}
