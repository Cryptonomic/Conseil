package tech.cryptonomic.conseil.generic.chain

import java.time.format.DateTimeFormatter.ISO_INSTANT
import java.time.{Instant, ZoneOffset}

import tech.cryptonomic.conseil.generic.chain.DataTypes.AggregationType.AggregationType
import tech.cryptonomic.conseil.generic.chain.DataTypes.OperationType.OperationType
import tech.cryptonomic.conseil.generic.chain.DataTypes.OrderDirection.OrderDirection
import tech.cryptonomic.conseil.generic.chain.DataTypes.OutputType.OutputType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations

import scala.concurrent.{ExecutionContext, Future}


/**
  * Classes used for deserializing query.
  */
object DataTypes {

  import cats.implicits._
  import io.scalaland.chimney.dsl._


  /** Type representing Map[String, Option[Any]] for query response */
  type QueryResponse = Map[String, Option[Any]]
  /** Method checks if type can be aggregated */
  lazy val canBeAggregated: DataType => AggregationType => Boolean = {
    dataType => {
      case AggregationType.count => true
      case AggregationType.max | AggregationType.min => Set(DataType.Decimal, DataType.Int, DataType.LargeInt, DataType.DateTime)(dataType)
      case AggregationType.avg | AggregationType.sum => Set(DataType.Decimal, DataType.Int, DataType.LargeInt)(dataType)
    }
  }
  /** Default value of limit parameter */
  val defaultLimitValue: Int = 10000
  /** Max value of limit parameter */
  val maxLimitValue: Int = 100000

  /** Replaces timestamp represented as Long in predicates with one understood by the SQL */
  private def replaceTimestampInPredicates(entity: String, query: Query, tezosPlatformDiscoveryOperations: TezosPlatformDiscoveryOperations)
    (implicit executionContext: ExecutionContext): Future[Query] = {
    query.predicates.map { predicate =>
      tezosPlatformDiscoveryOperations.getTableAttributesWithoutUpdatingCache(entity).map { maybeAttributes =>
        maybeAttributes.flatMap { attributes =>
          attributes.find(_.name == predicate.field).map {
            case attribute if attribute.dataType == DataType.DateTime =>
              predicate.copy(set = predicate.set.map(x => formatToIso(x.toString.toLong)))
            case _ => predicate
          }
        }.toList
      }
    }.sequence.map(pred => query.copy(predicates = pred.flatten))
  }

  /** Method formatting millis to ISO format */
  def formatToIso(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
      .atZone(ZoneOffset.UTC)
      .format(ISO_INSTANT)

  /** Helper method for finding fields used in query that don't exist in the database */
  private def findNonExistingFields(query: Query, entity: String, tezosPlatformDiscovery: TezosPlatformDiscoveryOperations)
    (implicit ec: ExecutionContext): Future[List[QueryValidationError]] = {
    val fields = query.fields.map('query -> _) :::
      query.predicates.map('predicate -> _.field) :::
      query.orderBy.map('orderBy -> _.field) :::
      query.aggregation.map('aggregation -> _.field).toList

    fields.traverse {
      case (source, field) => tezosPlatformDiscovery.isAttributeValid(entity, field).map((_, source, field))
    }.map {
      _.collect {
        case (false, 'query, field) => InvalidQueryField(field)
        case (false, 'predicate, field) => InvalidPredicateField(field)
        case (false, 'orderBy, field) => InvalidOrderByField(field)
        case (false, 'aggregation, field) => InvalidAggregationField(field)
      }
    }
  }

  /** Helper method for finding fields with invalid types in aggregation */
  private def findInvalidAggregationTypeFields(query: Query, entity: String, tezosPlatformDiscovery: TezosPlatformDiscoveryOperations)
    (implicit ec: ExecutionContext): Future[List[InvalidAggregationFieldForType]] = {
    query
      .aggregation
      .traverse { aggregation =>
        tezosPlatformDiscovery.getTableAttributesWithoutUpdatingCache(entity).map { attributesOpt =>
          attributesOpt.flatMap { attributes =>
            attributes
              .find(_.name == aggregation.field)
              .map(attribute => canBeAggregated(attribute.dataType)(aggregation.function) -> aggregation.field)
          }
        }
      }
      .map(_.flatten.collect { case (false, fieldName) => InvalidAggregationFieldForType(fieldName) }.toList)
  }

  /** Trait representing attribute validation errors */
  sealed trait AttributesValidationError extends Product with Serializable {
    val message: String
  }

  /** Attribute shouldn't be queried because it is a high cardinality field */
  case class HighCardinalityAttribute(message: String) extends AttributesValidationError

  /** Attribute shouldn't be queried because it is of a type that we forbid to query */
  case class InvalidAttributeDataType(message: String) extends AttributesValidationError

  /** Trait representing query validation errors */
  sealed trait QueryValidationError extends Product with Serializable {
    val message: String
  }

  /** Class which contains output type with the response */
  case class QueryResponseWithOutput(queryResponse: List[QueryResponse], output: OutputType)

  /** Class representing predicate */
  case class Predicate(
    field: String,
    operation: OperationType,
    set: List[Any] = List.empty,
    inverse: Boolean = false,
    precision: Option[Int] = None
  )

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

  /** Class representing query */
  case class Query(
    fields: List[String] = List.empty,
    predicates: List[Predicate] = List.empty,
    orderBy: List[QueryOrdering] = List.empty,
    limit: Int = defaultLimitValue,
    output: OutputType = OutputType.json,
    aggregation: Option[Aggregation] = None
  )

  /** Class representing predicate used in aggregation */
  case class AggregationPredicate(
    operation: OperationType,
    set: List[Any] = List.empty,
    inverse: Boolean = false,
    precision: Option[Int] = None
  )

  /** Class representing aggregation */
  case class Aggregation(field: String, function: AggregationType = AggregationType.sum, predicate: Option[AggregationPredicate] = None) {
    /** Method extracting predicate from aggregation */
    def getPredicate: Option[Predicate] = {
      predicate.map(_.into[Predicate].withFieldConst(_.field, field).transform)
    }
  }

  /** Class representing query got through the REST API */
  case class ApiQuery(
    fields: Option[List[String]],
    predicates: Option[List[Predicate]],
    orderBy: Option[List[QueryOrdering]],
    limit: Option[Int],
    output: Option[OutputType],
    aggregation: Option[Aggregation] = None
  ) {
    /** Method which validates query fields */
    def validate(entity: String, tezosPlatformDiscovery: TezosPlatformDiscoveryOperations)(implicit ec: ExecutionContext):
    Future[Either[List[QueryValidationError], Query]] = {

      val query = Query().patchWith(this)

      val nonExistingFields = findNonExistingFields(query, entity, tezosPlatformDiscovery)
      val invalidTypeAggregationField = findInvalidAggregationTypeFields(query, entity, tezosPlatformDiscovery)

      for {
        invalidNonExistingFields <- nonExistingFields
        invalidAggregationFieldForTypes <- invalidTypeAggregationField
        updatedQuery <- replaceTimestampInPredicates(entity, query, tezosPlatformDiscovery)
      } yield {
        invalidNonExistingFields ::: invalidAggregationFieldForTypes match {
          case Nil => Right(updatedQuery)
          case wrongFields => Left(wrongFields)
        }
      }

    }
  }

  /** Enumeration for output types */
  object OutputType extends Enumeration {
    type OutputType = Value
    val json, csv = Value
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
    type AggregationType = Value
    val sum, count, max, min, avg = Value
  }

}
