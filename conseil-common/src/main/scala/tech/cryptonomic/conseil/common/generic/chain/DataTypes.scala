package tech.cryptonomic.conseil.common.generic.chain

import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.AggregationType.AggregationType
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.FormatType.FormatType
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.OperationType.OperationType
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.OrderDirection.OrderDirection
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.OutputType.OutputType
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.DataType
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.common.metadata.EntityPath

/**
  * Classes used for deserializing query.
  */
object DataTypes {

  import cats.implicits._
  import io.scalaland.chimney.dsl._

  /** Type representing `Map[String, Option[Any]]` for query response */
  type QueryResponse = Map[String, Option[Any]]

  /** Method checks if type can be aggregated */
  lazy val canBeAggregated: DataType => AggregationType => Boolean = { dataType =>
    {
      case AggregationType.count | AggregationType.countDistinct => true
      case AggregationType.max | AggregationType.min =>
        Set(DataType.Decimal, DataType.Int, DataType.LargeInt, DataType.DateTime, DataType.Currency)(dataType)
      case AggregationType.avg | AggregationType.sum =>
        Set(DataType.Decimal, DataType.Int, DataType.LargeInt, DataType.Currency)(dataType)
    }
  }

  /** Default value of limit parameter */
  val defaultLimitValue: Int = 10000

  /** Trait representing attribute validation errors */
  sealed trait AttributesValidationError extends Product with Serializable {
    def message: String
  }

  /** Trait representing query validation errors */
  sealed trait QueryValidationError extends Product with Serializable {
    def message: String
  }

  /** Attribute shouldn't be queried because it is a high cardinality field */
  case class HighCardinalityAttribute(message: String) extends AttributesValidationError

  /** Attribute shouldn't be queried because it is of a type that we forbid to query */
  case class InvalidAttributeDataType(message: String) extends AttributesValidationError

  /** Attribute values should not be listed because minimal length is greater than filter length for given column  */
  case class InvalidAttributeFilterLength(message: String, minMatchLength: Int) extends AttributesValidationError

  /** Class which contains output type with the response */
  case class QueryResponseWithOutput(queryResponse: List[QueryResponse], output: OutputType)

  /** Class representing predicate */
  case class Predicate(
      field: String,
      operation: OperationType,
      set: List[Any],
      inverse: Boolean,
      precision: Option[Int],
      group: Option[String]
  )

  /** Predicate which is received by the API */
  case class ApiPredicate(
      field: String,
      operation: OperationType,
      set: Option[List[Any]],
      inverse: Option[Boolean],
      precision: Option[Int],
      group: Option[String]
  ) {

    /** Method creating Predicate out of ApiPredicate which is received by the API */
    def toPredicate: Predicate =
      Predicate("tmp", OperationType.in, List.empty, false, None, None).patchWith(this)
  }

  /** Class representing query ordering */
  case class QueryOrdering(field: String, direction: OrderDirection)

  /** Class representing invalid query field */
  case class InvalidQueryField(message: String) extends QueryValidationError

  /** Class representing invalid predicate field */
  case class InvalidPredicateField(message: String) extends QueryValidationError

  /** Class representing invalid order by field */
  case class InvalidOrderByField(message: String) extends QueryValidationError

  /** Class representing invalid aggregation field */
  case class InvalidAggregationField(message: String) extends QueryValidationError

  /** Class representing invalid field type used in aggregation */
  case class InvalidAggregationFieldForType(message: String) extends QueryValidationError

  /** Class representing lack of key/datetime field usage in predicates */
  case class InvalidPredicateFiltering(message: String) extends QueryValidationError

  /** Class representing invalid field formatting */
  case class InvalidQueryFieldFormatting(message: String) extends QueryValidationError

  /** Class representing invalid field in snapshot */
  case class InvalidSnapshotField(message: String) extends QueryValidationError

  /** Query field description */
  sealed trait Field {
    val field: String
  }

  /** Simple field without formatting */
  case class SimpleField(field: String) extends Field

  /** Formatted field with format description */
  case class FormattedField(field: String, function: FormatType, format: String) extends Field

  object Query {
    val empty: Query = Query(
      List.empty[Field],
      List.empty[Predicate],
      List.empty[QueryOrdering],
      defaultLimitValue,
      OutputType.json,
      List.empty[Aggregation],
      None,
      None
    )
  }

  /** Class representing query */
  case class Query(
      fields: List[Field],
      predicates: List[Predicate],
      orderBy: List[QueryOrdering],
      limit: Int,
      output: OutputType,
      aggregation: List[Aggregation],
      temporalPartition: Option[String],
      snapshot: Option[Snapshot]
  ) {
    def withLimitCap(value: Int): Query = copy(limit = Math.min(limit, value))
  }

  /** Class representing predicate used in aggregation */
  case class AggregationPredicate(
      operation: OperationType,
      set: List[Any],
      inverse: Boolean,
      precision: Option[Int]
  )

  /** AggregationPredicate that is received by the API */
  case class ApiAggregationPredicate(
      operation: OperationType,
      set: Option[List[Any]],
      inverse: Option[Boolean],
      precision: Option[Int]
  ) {

    /** Transforms Aggregation predicate received form API into AggregationPredicate */
    def toAggregationPredicate: AggregationPredicate =
      AggregationPredicate(operation = OperationType.in, set = List.empty, inverse = false, precision = None)
        .patchWith(this)
  }

  /** Aggregation that is received by the API */
  case class ApiAggregation(
      field: String,
      function: AggregationType,
      predicate: Option[ApiAggregationPredicate]
  ) {

    /** Transforms Aggregation received form API into Aggregation */
    def toAggregation: Aggregation =
      Aggregation(field, function, predicate.map(_.toAggregationPredicate))
  }

  /** Class representing aggregation */
  case class Aggregation(
      field: String,
      function: AggregationType,
      predicate: Option[AggregationPredicate]
  ) {

    /** Method extracting predicate from aggregation */
    def getPredicate: Option[Predicate] =
      predicate.map(
        _.into[Predicate]
          .withFieldConst(_.field, field)
          .withFieldConst(_.group, None)
          .transform
      )
    // predicate.map(_.into[Predicate].withFieldConst(_.field, field).transform)
  }

  /** Class representing snapshot */
  case class Snapshot(field: String, value: Any) {

    /** Transforms snapshot to predicate */
    def toPredicate: Predicate =
      Predicate(
        field = field,
        operation = OperationType.after,
        set = List(value),
        inverse = true,
        precision = None,
        group = None
      )

    /** Transforms snapshot to ordering */
    def toOrdering: QueryOrdering =
      QueryOrdering(
        field = field,
        direction = OrderDirection.desc
      )
  }

  /** Finds invalid snapshot fields*/
  def findInvalidSnapshotFields(
      query: Query,
      entity: EntityPath,
      metadataConfiguration: MetadataConfiguration
  ): List[InvalidSnapshotField] = {
    import cats.instances.list._
    import cats.instances.option._

    query.snapshot.foldMap { snapshot =>
      metadataConfiguration.entity(entity).flatMap { entityCfg =>
        entityCfg.attributes.find {
          case (k, v) =>
            k == snapshot.field && v.temporalColumn.getOrElse(false)
        }
      } match {
        case Some(_) => List.empty
        case None => List(InvalidSnapshotField(snapshot.field))
      }
    }
  }

  /** Enumeration for output types */
  object OutputType extends Enumeration {
    type OutputType = Value
    val json, csv, sql = Value
  }

  /** Enumeration for order direction */
  object OrderDirection extends Enumeration {
    type OrderDirection = Value
    val asc, desc = Value
  }

  /** Enumeration of operation types */
  object OperationType extends Enumeration {
    type OperationType = Value
    val in, between, like, lt, gt, eq, startsWith, endsWith, before, after, isnull = Value
  }

  /** Enumeration of aggregation functions */
  object AggregationType extends Enumeration {

    /** Helper method for extracting prefixes needed for SQL */
    def prefixes: List[String] = values.toList.map(_.toString + "_")
    type AggregationType = Value
    val sum, count, max, min, avg, countDistinct = Value
  }

  /** Enumeration of aggregation functions */
  object FormatType extends Enumeration {

    /** Helper method for extracting prefixes needed for SQL */
    type FormatType = Value
    val datePart = Value
  }

}
