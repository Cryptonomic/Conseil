package tech.cryptonomic.conseil.api.platform.data

import sttp.tapir._

import tech.cryptonomic.conseil.common.ethereum.{Tables => EthereumTables}
import tech.cryptonomic.conseil.common.tezos.{Tables => TezosTables}
import tech.cryptonomic.conseil.api.platform.data.tezos.TezosDataOperations._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

private[data] object schemas {

  implicit val timestampSchema: Schema[java.sql.Timestamp] = Schema(SchemaType.SString())
  implicit val blocksSchemaTezos: Schema[TezosTables.BlocksRow] = Schema.derived
  implicit val blocksSchemaEthereum: Schema[EthereumTables.BlocksRow] = Schema.derived

  implicit val accountsRowSchemaTezos: Schema[TezosTables.AccountsRow] = Schema.derived
  implicit val accountsRowSchemaEthereum: Schema[EthereumTables.AccountsRow] = Schema.derived
  implicit val accountResultSchema: Schema[AccountResult] = Schema.derived

  implicit val queryResponseSchema: Schema[QueryResponse] = Schema.string // FIXME: is String enough?
  implicit val qrSchema: Schema[QR] = Schema.derived
  implicit val outputTypeSchema: Schema[OutputType] = Schema.derived
  implicit val queryResultWithOutputSchema: Schema[QueryResponseWithOutput] = Schema.derived

  implicit val operationGroupsRowSchema: Schema[TezosTables.OperationGroupsRow] = Schema.derived
  implicit val operationsRowSchema: Schema[TezosTables.OperationsRow] = Schema.derived
  implicit val operationGroupResultSchema: Schema[OperationGroupResult] = Schema.derived

  implicit val blockResultSchema: Schema[BlockResult] = Schema.derived

}
