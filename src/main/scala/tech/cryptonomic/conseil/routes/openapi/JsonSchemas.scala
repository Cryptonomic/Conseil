package tech.cryptonomic.conseil.routes.openapi

import endpoints.{algebra, generic}
import tech.cryptonomic.conseil.generic.chain.DataTypes._

trait JsonSchemas extends algebra.JsonSchemas with generic.JsonSchemas   {

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

  implicit def anySchema: JsonSchema[Any]

  implicit def queryResponseSchema: JsonSchema[List[Map[String, Option[Any]]]]

}
