package tech.cryptonomic.conseil.platform.data

import sttp.tapir._

import tech.cryptonomic.conseil.common.tezos.Tables._
import tech.cryptonomic.conseil.platform.data.tezos.TezosDataOperations._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

object schemas {

  implicit val timestampSchema: Schema[java.sql.Timestamp] = Schema(SchemaType.SString())
  implicit val blocksSchema: Schema[BlocksRow] = Schema.derived

  implicit val accountsRowSchema: Schema[AccountsRow] = Schema.derived
  implicit val accountResultSchema: Schema[AccountResult] = Schema.derived

  implicit val queryResponseSchema: Schema[QueryResponse] = ??? // Schema(SchemaType.SString) // FIXME: probably needs to be more specific
  implicit val qrSchema: Schema[QR] = Schema.derived
  implicit val outputTypeSchema: Schema[OutputType] = Schema.derived
  implicit val queryResultWithoutOutputSchema: Schema[QueryResponseWithOutput] = Schema.derived

  implicit val operationGroupsRowSchema: Schema[OperationGroupsRow] = Schema.derived
  implicit val operationsRowSchema: Schema[OperationsRow] = Schema.derived
  implicit val operationGroupResultSchema: Schema[OperationGroupResult] = Schema.derived

}
