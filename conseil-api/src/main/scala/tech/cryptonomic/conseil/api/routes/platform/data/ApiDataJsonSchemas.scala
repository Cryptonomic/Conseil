package tech.cryptonomic.conseil.api.routes.platform.data

import endpoints.generic
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataTypes.ApiQuery
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

trait ApiDataJsonSchemas extends generic.JsonSchemas {

  /** API field schema */
  implicit val fieldSchema: JsonSchema[Field]

  implicit lazy val formattedFieldSchema: JsonSchema[FormattedField] =
    genericJsonSchema[FormattedField]

  /** Format types schema */
  implicit lazy val formatTypesSchema: JsonSchema[FormatType.Value] =
    enumeration(FormatType.values.toSeq)(_.toString)

  /** API query schema */
  implicit lazy val queryRequestSchema: JsonSchema[ApiQuery] =
    genericJsonSchema[ApiQuery]

  /** Query predicate schema */
  implicit lazy val queryPredicateSchema: JsonSchema[Predicate] =
    genericJsonSchema[Predicate]

  /** Query ordering operation schema */
  implicit lazy val queryOrderingOperationSchema: JsonSchema[OperationType.Value] =
    enumeration(OperationType.values.toSeq)(_.toString)

  /** API predicate schema */
  implicit lazy val apiPredicateSchema: JsonSchema[ApiPredicate] =
    genericJsonSchema[ApiPredicate]

  /** API aggregation schema */
  implicit lazy val apiAggregationSchema: JsonSchema[ApiAggregation] =
    genericJsonSchema[ApiAggregation]

  /** API aggregation predicate schema */
  implicit lazy val apiAggregationPredicateSchema: JsonSchema[ApiAggregationPredicate] =
    genericJsonSchema[ApiAggregationPredicate]

  /** Query ordering schema */
  implicit lazy val queryOrderingSchema: JsonSchema[QueryOrdering] =
    genericJsonSchema[QueryOrdering]

  /** Query ordering direction schema */
  implicit lazy val queryOrderingDirectionSchema: JsonSchema[OrderDirection.Value] =
    enumeration(OrderDirection.values.toSeq)(_.toString)

  /** Query output schema */
  implicit lazy val queryOutputSchema: JsonSchema[OutputType.Value] =
    enumeration(OutputType.values.toSeq)(_.toString)

  /** Query aggregation schema */
  implicit lazy val queryAggregationSchema: JsonSchema[Aggregation] =
    genericJsonSchema[Aggregation]

  /** Query aggregation schema */
  implicit lazy val queryAggregationPredicateSchema: JsonSchema[AggregationPredicate] =
    genericJsonSchema[AggregationPredicate]

  /** Query aggregation type schema */
  implicit lazy val queryAggregationTypeSchema: JsonSchema[AggregationType.Value] =
    enumeration(AggregationType.values.toSeq)(_.toString)

  /** Timestamp schema */
  implicit lazy val timestampSchema: JsonSchema[java.sql.Timestamp] =
    xmapJsonSchema[Long, java.sql.Timestamp](
      implicitly[JsonSchema[Long]],
      millisFromEpoch => new java.sql.Timestamp(millisFromEpoch),
      ts => ts.getTime
    )

  /** Snapshot schema */
  implicit lazy val snapshotSchema: JsonSchema[Snapshot] =
    genericJsonSchema[Snapshot]

  /** Any schema */
  implicit def anySchema: JsonSchema[Any]

  /** Query response schema */
  implicit def queryResponseSchema: JsonSchema[QueryResponse]

  /** Query response schema with output type */
  implicit def queryResponseSchemaWithOutputType: JsonSchema[QueryResponseWithOutput]

}
