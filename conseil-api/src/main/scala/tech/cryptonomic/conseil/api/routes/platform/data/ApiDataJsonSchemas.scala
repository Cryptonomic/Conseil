package tech.cryptonomic.conseil.api.routes.platform.data

import endpoints4s.generic
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataTypes.ApiQuery
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

/** Default json schemas for API handling */
trait ApiDataJsonSchemas extends generic.JsonSchemas {

  /** Any schema */
  implicit def anySchema: JsonSchema[Any]

  /** Query response schema */
  implicit def queryResponseSchema: JsonSchema[QueryResponse]

  /** API field schema */
  implicit def fieldSchema: JsonSchema[Field]

  /* needed to generically derive the formatted field schema */
  implicit lazy val formatTypesSchema: JsonSchema[FormatType.Value] =
    stringEnumeration(FormatType.values.toSeq)(_.toString)

  /* derives an implementation for the formatted case class */
  lazy val formattedFieldSchema: JsonSchema[FormattedField] =
    genericJsonSchema[FormattedField]

  /** API query schema */
  implicit lazy val queryRequestSchema: JsonSchema[ApiQuery] =
    genericJsonSchema[ApiQuery]

  /** Query predicate schema */
  implicit lazy val queryPredicateSchema: JsonSchema[Predicate] =
    genericJsonSchema[Predicate]

  /** Query ordering operation schema */
  implicit lazy val queryOrderingOperationSchema: JsonSchema[OperationType.Value] =
    stringEnumeration(OperationType.values.toSeq)(_.toString)

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
    stringEnumeration(OrderDirection.values.toSeq)(_.toString)

  /** Query output schema */
  implicit lazy val queryOutputSchema: JsonSchema[OutputType.Value] =
    stringEnumeration(OutputType.values.toSeq)(_.toString)

  /** Query aggregation schema */
  implicit lazy val queryAggregationSchema: JsonSchema[Aggregation] =
    genericJsonSchema[Aggregation]

  /** Query aggregation schema */
  implicit lazy val queryAggregationPredicateSchema: JsonSchema[AggregationPredicate] =
    genericJsonSchema[AggregationPredicate]

  /** Query aggregation type schema */
  implicit lazy val queryAggregationTypeSchema: JsonSchema[AggregationType.Value] =
    stringEnumeration(AggregationType.values.toSeq)(_.toString)

  /** Timestamp schema */
  implicit lazy val timestampSchema: JsonSchema[java.sql.Timestamp] =
    implicitly[JsonSchema[Long]].xmap(millisFromEpoch => new java.sql.Timestamp(millisFromEpoch))(ts => ts.getTime)

  /** Snapshot schema */
  implicit lazy val snapshotSchema: JsonSchema[Snapshot] =
    genericJsonSchema[Snapshot]

  /** Query response schema with output type */
  implicit def queryResponseSchemaWithOutputType: JsonSchema[QueryResponseWithOutput] =
    implicitly[JsonSchema[List[QueryResponse]]]
      .xmap(QueryResponseWithOutput(_, OutputType.json))(_.queryResponse)

}
