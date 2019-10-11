package tech.cryptonomic.conseil.generic.chain

import java.sql.Timestamp

import tech.cryptonomic.conseil.generic.chain.DataTypes.AggregationType.AggregationType
import tech.cryptonomic.conseil.generic.chain.DataTypes.FormatType.FormatType
import tech.cryptonomic.conseil.generic.chain.DataTypes.OperationType.OperationType
import tech.cryptonomic.conseil.generic.chain.DataTypes.OrderDirection.OrderDirection
import tech.cryptonomic.conseil.generic.chain.DataTypes.OutputType.OutputType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.metadata.{EntityPath, MetadataService}

import scala.concurrent.Future.successful
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
  lazy val canBeAggregated: DataType => AggregationType => Boolean = { dataType =>
    {
      case AggregationType.count => true
      case AggregationType.max | AggregationType.min =>
        Set(DataType.Decimal, DataType.Int, DataType.LargeInt, DataType.DateTime, DataType.Currency)(dataType)
      case AggregationType.avg | AggregationType.sum =>
        Set(DataType.Decimal, DataType.Int, DataType.LargeInt, DataType.Currency)(dataType)
    }
  }

  /** Default value of limit parameter */
  val defaultLimitValue: Int = 10000

  /** Replaces timestamp represented as Long in predicates with one understood by the SQL */
  private def replaceTimestampInPredicates(path: EntityPath, query: Query, metadataService: MetadataService)(
      implicit executionContext: ExecutionContext
  ): Future[Query] =
    query.predicates.map { predicate =>
      metadataService.getTableAttributesWithoutUpdatingCache(path).map { maybeAttributes =>
        maybeAttributes.flatMap { attributes =>
          attributes.find(_.name == dropAggregationPrefixes(predicate.field)).map {
            case attribute if attribute.dataType == DataType.DateTime =>
              predicate.copy(set = predicate.set.map(x => new Timestamp(x.toString.toLong).toString))
            case _ => predicate
          }
        }.toList
      }
    }.sequence.map(pred => query.copy(predicates = pred.flatten))

  /** Helper method for finding fields used in query that don't exist in the database */
  private def findNonExistingFields(
      query: Query,
      path: EntityPath,
      metadataService: MetadataService
  ): List[QueryValidationError] = {
    val fields = query.fields.map('query -> _.field) :::
          query.predicates.map(predicate => 'predicate -> dropAggregationPrefixes(predicate.field)) :::
          query.orderBy.map(orderBy => 'orderBy -> dropAggregationPrefixes(orderBy.field)) :::
          query.aggregation.map('aggregation -> _.field)

    fields.map {
      case (source, field) => (metadataService.exists(path.addLevel(field)), source, field)
    }.collect {
      case (false, 'query, field) => InvalidQueryField(field)
      case (false, 'predicate, field) => InvalidPredicateField(field)
      case (false, 'orderBy, field) => InvalidOrderByField(field)
      case (false, 'aggregation, field) => InvalidAggregationField(field)
    }
  }

  /** Drops aggregation prefixes from field name */
  private def dropAggregationPrefixes(fieldName: String): String =
    dropAnyOfPrefixes(fieldName, AggregationType.prefixes)

  /** Helper method for dropping prefixes from given string */
  private def dropAnyOfPrefixes(str: String, prefixes: List[String]): String =
    prefixes.collectFirst {
      case prefixToBeDropped if str.startsWith(prefixToBeDropped) => str.stripPrefix(prefixToBeDropped)
    }.getOrElse(str)

  /** Helper method for finding fields with invalid types in aggregation */
  private def findInvalidAggregationTypeFields(query: Query, path: EntityPath, metadataService: MetadataService)(
      implicit ec: ExecutionContext
  ): Future[List[InvalidAggregationFieldForType]] =
    query.aggregation.traverse { aggregation =>
      metadataService.getTableAttributesWithoutUpdatingCache(path).map { attributesOpt =>
        attributesOpt.flatMap { attributes =>
          attributes
            .find(_.name == aggregation.field)
            .map(attribute => canBeAggregated(attribute.dataType)(aggregation.function) -> aggregation.field)
        }
      }
    }.map(_.flatten.collect { case (false, fieldName) => InvalidAggregationFieldForType(fieldName) }.toList)

  /** Helper method for finding if queries does not contain filters on key fields or datetime fields */
  private def findInvalidPredicateFilteringFields(query: Query, path: EntityPath, metadataService: MetadataService)(
      implicit ec: ExecutionContext
  ): Future[List[InvalidPredicateFiltering]] = {
    val limitedQueryEntity = metadataService
      .getEntities(path.up)
      .toList
      .flatten
      .find(_.name == path.entity)
      .flatMap(_.limitedQuery)
      .getOrElse(false)
    if (limitedQueryEntity) {
      query.predicates.traverse { predicate =>
        metadataService.getTableAttributesWithoutUpdatingCache(path).map { attributesOpt =>
          attributesOpt.flatMap { attributes =>
            attributes.find(_.name == predicate.field)
          }.toList
        }
      }.map(attributes => attributes.flatten.flatMap(_.doesPredicateContainValidAttribute))
    } else {
      successful(List.empty)
    }
  }

  /** Helper method for query fields validation */
  private def findInvalidQueryFieldFormats(
      query: Query,
      path: EntityPath,
      metadataService: MetadataService
  ): List[InvalidQueryFieldFormatting] = {
    val columns = metadataService.getTableAttributes(path).toList.flatten
    query.fields.filterNot {
      case SimpleField(_) => true
      case FormattedField(field, FormatType.datePart, _) =>
        columns.exists(column => column.name == field && column.dataType == DataType.DateTime)
      case _ => false
    }.map(field => InvalidQueryFieldFormatting(s"Field ${field.field} does not have correct type to be formatted."))
  }

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
      set: List[Any] = List.empty,
      inverse: Boolean = false,
      precision: Option[Int] = None,
      group: Option[String] = None
  )

  /** Predicate which is received by the API */
  case class ApiPredicate(
      field: String,
      operation: OperationType,
      set: Option[List[Any]] = Some(List.empty),
      inverse: Option[Boolean] = Some(false),
      precision: Option[Int] = None
  ) {

    /** Method creating Predicate out of ApiPredicate which is received by the API */
    def toPredicate: Predicate =
      Predicate("tmp", OperationType.in).patchWith(this)
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

  /** Query field description */
  sealed trait Field {
    val field: String
  }

  /** Simple field without formatting */
  case class SimpleField(field: String) extends Field

  /** Formatted field with format description */
  case class FormattedField(field: String, function: FormatType, format: String) extends Field

  /** Class representing query */
  case class Query(
      fields: List[Field] = List.empty,
      predicates: List[Predicate] = List.empty,
      orderBy: List[QueryOrdering] = List.empty,
      limit: Int = defaultLimitValue,
      output: OutputType = OutputType.json,
      aggregation: List[Aggregation] = List.empty
  )

  /** Class representing predicate used in aggregation */
  case class AggregationPredicate(
      operation: OperationType,
      set: List[Any] = List.empty,
      inverse: Boolean = false,
      precision: Option[Int] = None
  )

  /** AggregationPredicate that is received by the API */
  case class ApiAggregationPredicate(
      operation: OperationType,
      set: Option[List[Any]] = Some(List.empty),
      inverse: Option[Boolean] = Some(false),
      precision: Option[Int] = None
  ) {

    /** Transforms Aggregation predicate received form API into AggregationPredicate */
    def toAggregationPredicate: AggregationPredicate =
      AggregationPredicate(operation = OperationType.in).patchWith(this)
  }

  /** Aggregation that is received by the API */
  case class ApiAggregation(
      field: String,
      function: AggregationType = AggregationType.sum,
      predicate: Option[ApiAggregationPredicate] = None
  ) {

    /** Transforms Aggregation received form API into Aggregation */
    def toAggregation: Aggregation =
      Aggregation(field, function, predicate.map(_.toAggregationPredicate))
  }

  /** Class representing aggregation */
  case class Aggregation(
      field: String,
      function: AggregationType = AggregationType.sum,
      predicate: Option[AggregationPredicate] = None
  ) {

    /** Method extracting predicate from aggregation */
    def getPredicate: Option[Predicate] =
      predicate.map(_.into[Predicate].withFieldConst(_.field, field).transform)
  }

  /** Class representing query got through the REST API */
  case class ApiQuery(
      fields: Option[List[Field]],
      predicates: Option[List[ApiPredicate]],
      orderBy: Option[List[QueryOrdering]],
      limit: Option[Int],
      output: Option[OutputType],
      aggregation: Option[List[ApiAggregation]]
  ) {

    /** Method which validates query fields */
    def validate(entity: EntityPath, metadataService: MetadataService)(
        implicit ec: ExecutionContext
    ): Future[Either[List[QueryValidationError], Query]] = {

      val patchedPredicates = predicates.getOrElse(List.empty).map(_.toPredicate)
      val query = this
        .into[Query]
        .withFieldConst(_.fields, fields.getOrElse(List.empty))
        .withFieldConst(_.predicates, patchedPredicates)
        .withFieldConst(_.orderBy, orderBy.getOrElse(List.empty))
        .withFieldConst(_.limit, limit.getOrElse(defaultLimitValue))
        .withFieldConst(_.output, output.getOrElse(OutputType.json))
        .withFieldConst(_.aggregation, aggregation.toList.flatten.map(_.toAggregation))
        .transform

      (
        successful(findNonExistingFields(query, entity, metadataService)),
        findInvalidAggregationTypeFields(query, entity, metadataService),
        findInvalidPredicateFilteringFields(query, entity, metadataService),
        successful(findInvalidQueryFieldFormats(query, entity, metadataService)),
        replaceTimestampInPredicates(entity, query, metadataService)
      ).mapN {
        (
            invalidNonExistingFields,
            invalidAggregationFieldForTypes,
            invalidPredicateFilteringFields,
            invalidQueryFieldFormats,
            updatedQuery
        ) =>
          invalidNonExistingFields ::: invalidAggregationFieldForTypes ::: invalidPredicateFilteringFields ::: invalidQueryFieldFormats match {
            case Nil => Right(updatedQuery)
            case wrongFields => Left(wrongFields)
          }
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
    val sum, count, max, min, avg = Value
  }

  /** Enumeration of aggregation functions */
  object FormatType extends Enumeration {

    /** Helper method for extracting prefixes needed for SQL */
    type FormatType = Value
    val datePart = Value
  }

}
