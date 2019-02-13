package tech.cryptonomic.conseil.routes.openapi

import endpoints.{algebra, generic}
import tech.cryptonomic.conseil.generic.chain.DataTypes._
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.DataType
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

  implicit def blocksByHashSchema: JsonSchema[Map[String, Any]]

}

trait PlatformDiscoveryJsonSchemas extends algebra.JsonSchemas with generic.JsonSchemas {
  implicit def platformsSchema: JsonSchema[List[Platform]] =
    genericJsonSchema[List[Platform]]

  implicit def networksSchema: JsonSchema[List[Network]] =
    genericJsonSchema[List[Network]]

  implicit def entitiesSchema: JsonSchema[List[Entity]] =
    genericJsonSchema[List[Entity]]

  implicit def attributesSchema: JsonSchema[List[Attribute]] =
    genericJsonSchema[List[Attribute]]

  implicit def attributeValuesSchema: JsonSchema[List[String]] =
    genericJsonSchema[List[String]]

  implicit def dataTypeSchema: JsonSchema[List[DataType.Value]] =
    enumeration(DataType.values.toSeq)(_.toString)

  implicit def keyTypeSchema: JsonSchema[List[KeyType.Value]] =
    enumeration(KeyType.values.toSeq)(_.toString)
}
