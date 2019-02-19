package tech.cryptonomic.conseil.routes.openapi

import endpoints.{algebra, generic}
import tech.cryptonomic.conseil.generic.chain.DataTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.tezos.Tables

trait DataJsonSchemas extends algebra.JsonSchemas with generic.JsonSchemas   {

  implicit def queryRequestSchema: JsonSchema[ApiQuery] =
    genericJsonSchema[ApiQuery]

  implicit def queryPredicateSchema: JsonSchema[Predicate] =
    genericJsonSchema[Predicate]

  implicit def queryOrderingOperationSchema: JsonSchema[OperationType.Value] =
    enumeration(OperationType.values.toSeq)(_.toString)

  implicit def queryOrderingSchema: JsonSchema[QueryOrdering] =
    genericJsonSchema[QueryOrdering]

  implicit def queryOrderingDirectionSchema: JsonSchema[OrderDirection.Value] =
    enumeration(OrderDirection.values.toSeq)(_.toString)

  implicit def timestampSchema: JsonSchema[java.sql.Timestamp]

  implicit def blocksRowSchema: JsonSchema[Tables.BlocksRow] =
    genericJsonSchema[Tables.BlocksRow]

  implicit def operationGroupsRowSchema: JsonSchema[Tables.OperationGroupsRow] =
    genericJsonSchema[Tables.OperationGroupsRow]

  implicit def avgFeeSchema: JsonSchema[AverageFees] =
    genericJsonSchema[AverageFees]

  implicit def anySchema: JsonSchema[Any]

  implicit def queryResponseSchema: JsonSchema[List[Map[String, Option[Any]]]]

  implicit def blocksByHashSchema: JsonSchema[AnyMap]

}


