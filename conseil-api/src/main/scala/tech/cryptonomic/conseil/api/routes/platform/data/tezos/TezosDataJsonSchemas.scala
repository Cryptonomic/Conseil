package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataJsonSchemas
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.TezosDataOperations.{AccountResult, BlockResult, OperationGroupResult}
import tech.cryptonomic.conseil.common.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow, OperationsRow}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Fee.AverageFees

/** Trait containing Data endpoints JSON schemas */
private[tezos] trait TezosDataJsonSchemas extends ApiDataJsonSchemas {

  /** Blocks row schema */
  implicit lazy val blocksRowSchema: JsonSchema[BlocksRow] =
    genericJsonSchema[BlocksRow]

  /** Operation groups row schema */
  implicit lazy val operationGroupsRowSchema: JsonSchema[OperationGroupsRow] =
    genericJsonSchema[OperationGroupsRow]

  /** Average fees schema */
  implicit lazy val avgFeeSchema: JsonSchema[AverageFees] =
    genericJsonSchema[AverageFees]

  /** Operation row schema */
  implicit lazy val operationsRowSchema: JsonSchema[OperationsRow] =
    genericJsonSchema[OperationsRow]

  /** Accounts row schema */
  implicit lazy val accountsRowSchema: JsonSchema[AccountsRow] =
    genericJsonSchema[AccountsRow]

  /** api operation results schema for blocks */
  implicit lazy val blockResultSchema: JsonSchema[BlockResult] =
    genericJsonSchema[BlockResult]

  /** api operation results schema for operation groups */
  implicit lazy val operationGroupResultSchema: JsonSchema[OperationGroupResult] =
    genericJsonSchema[OperationGroupResult]

  /** api operation results schema for accounts */
  implicit lazy val accountResultSchema: JsonSchema[AccountResult] =
    genericJsonSchema[AccountResult]

}
