package tech.cryptonomic.conseil.routes.openapi

import endpoints.generic
import tech.cryptonomic.conseil.generic.chain.DataTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.tezos.ApiOperations.{BlockResult, OperationGroupResult, AccountResult}
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow, OperationsRow}

/** Trait containing Data endpoints JSON schemas */
trait DataJsonSchemas extends generic.JsonSchemas {

  /** API query schema */
  implicit lazy val queryRequestSchema: JsonSchema[ApiQuery] =
    genericJsonSchema[ApiQuery]

  /** Query predicate schema */
  implicit lazy val queryPredicateSchema: JsonSchema[Predicate] =
    genericJsonSchema[Predicate]

  /** Query ordering operation schema */
  implicit lazy val queryOrderingOperationSchema: JsonSchema[OperationType.Value] =
    enumeration(OperationType.values.toSeq)(_.toString)

  /** Query ordering schema */
  implicit lazy val queryOrderingSchema: JsonSchema[QueryOrdering] =
    genericJsonSchema[QueryOrdering]

  /** Query ordering direction schema */
  implicit lazy val queryOrderingDirectionSchema: JsonSchema[OrderDirection.Value] =
    enumeration(OrderDirection.values.toSeq)(_.toString)

  /** Query output schema */
  implicit lazy val queryOutputSchema: JsonSchema[OutputType.Value] =
    enumeration(OutputType.values.toSeq)(_.toString)

  /** Timestamp schema */
  implicit lazy val timestampSchema: JsonSchema[java.sql.Timestamp] = {
    xmapJsonSchema[Long, java.sql.Timestamp](
      implicitly[JsonSchema[Long]],
      millisFromEpoch => new java.sql.Timestamp(millisFromEpoch),
      ts => ts.getTime
    )
  }

  /** Blocks row schema */
  implicit lazy val blocksRowSchema: JsonSchema[BlocksRow] =
    genericJsonSchema[BlocksRow]

  /** Operation groups row schema */
  implicit lazy val operationGroupsRowSchema: JsonSchema[OperationGroupsRow] =
    genericJsonSchema[OperationGroupsRow]

  /** Average fees schema */
  implicit lazy val avgFeeSchema: JsonSchema[AverageFees] =
    genericJsonSchema[AverageFees]

  /** Any schema */
  implicit def anySchema: JsonSchema[Any]

  /** Query response schema */
  implicit def queryResponseSchema: JsonSchema[List[QueryResponse]]

  /** Query response schema with output type */
  implicit def queryResponseSchemaWithOutputType: JsonSchema[QueryResponseWithOutput]

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
