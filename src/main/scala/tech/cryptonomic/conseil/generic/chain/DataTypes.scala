package tech.cryptonomic.conseil.generic.chain

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

  /** Type representing Map[String, Any] */
  type AnyMap = Map[String, Any]

  /** Type representing Map[String, Option[Any]] for query response */
  type QueryResponse = Map[String, Option[Any]]
  /** Default value of limit parameter */
  val defaultLimitValue: Int = 10000
  /** Max value of limit parameter */
  val maxLimitValue: Int = 100000

  /** Helper method for finding invalid fields in query */
  private def findInvalidQueryFields(query: Query, entity: String, tezosPlatformDiscovery: TezosPlatformDiscoveryOperations)
    (implicit ec: ExecutionContext): Future[List[InvalidQueryField]] = {
    query
      .fields
      .traverse(field => tezosPlatformDiscovery.areFieldsValid(entity, Set(field)).map(_ -> field))
      .map(_.collect { case (false, fieldName) => InvalidQueryField(fieldName) })

  }

  /** Helper method for finding invalid fields in predicate */
  private def findInvalidPredicateFields(query: Query, entity: String, tezosPlatformDiscovery: TezosPlatformDiscoveryOperations)
    (implicit ec: ExecutionContext): Future[List[InvalidPredicateField]] = {
    query
      .predicates
      .traverse(predicate => tezosPlatformDiscovery.areFieldsValid(entity, Set(predicate.field)).map(_ -> predicate.field))
      .map(_.collect { case (false, fieldName) => InvalidPredicateField(fieldName) })

  }

  /** Helper method for finding invalid fields in orderBy */
  private def findInvalidOrderByFields(query: Query, entity: String, tezosPlatformDiscovery: TezosPlatformDiscoveryOperations)
    (implicit ec: ExecutionContext): Future[List[InvalidOrderByField]] = {
    query
      .orderBy
      .traverse(ordering => tezosPlatformDiscovery.areFieldsValid(entity, Set(ordering.field)).map(_ -> ordering.field))
      .map(_.collect { case (false, fieldName) => InvalidOrderByField(fieldName) })

  }

  /** Helper method for finding invalid fields in aggregation */
  private def findInvalidAggregationFields(query: Query, entity: String, tezosPlatformDiscovery: TezosPlatformDiscoveryOperations)
    (implicit ec: ExecutionContext): Future[List[InvalidAggregationField]] = {
    query
      .aggregation
      .toList
      .traverse(aggregation => tezosPlatformDiscovery.areFieldsValid(entity, Set(aggregation.field)).map(_ -> aggregation.field))
      .map(_.collect { case (false, fieldName) => InvalidAggregationField(fieldName) })
  }

  /** Helper method for finding fields with invalid types in aggregation */
  private def findInvalidAggregationTypeFields(query: Query, entity: String, tezosPlatformDiscovery: TezosPlatformDiscoveryOperations)
    (implicit ec: ExecutionContext): Future[List[InvalidAggregationFieldForType]] = {
    query
      .aggregation
      .traverse { aggregation =>
        tezosPlatformDiscovery.getTableAttributesWithoutCounts(entity).map { attributesOpt =>
          attributesOpt.flatMap { attributes =>
            attributes
              .find(_.name == aggregation.field)
              .map(attribute => canBeAggregated(attribute.dataType) -> aggregation.field)
          }
        }
      }
      .map(_.flatten.collect { case (false, fieldName) => InvalidAggregationFieldForType(fieldName) }.toList)
  }

  /** Method checks if type can be aggregated */
  private def canBeAggregated(tpe: DataType): Boolean = {
    val typesToAggregate = Set(DataType.Decimal, DataType.Int, DataType.LargeInt, DataType.DateTime)
    typesToAggregate.contains(tpe)
  }

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
  case class Aggregation(field: String, function: AggregationType, predicate: Option[AggregationPredicate]) {
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

      val invalidQueryFields = findInvalidQueryFields(query, entity, tezosPlatformDiscovery)
      val invalidPredicateFields = findInvalidPredicateFields(query, entity, tezosPlatformDiscovery)
      val invalidOrderByFields = findInvalidOrderByFields(query, entity, tezosPlatformDiscovery)
      val invalidAggregationFields = findInvalidAggregationFields(query, entity, tezosPlatformDiscovery)
      val invalidTypeAggregationField = findInvalidAggregationTypeFields(query, entity, tezosPlatformDiscovery)

      for {
        invQF <- invalidQueryFields
        invPF <- invalidPredicateFields
        invODBF <- invalidOrderByFields
        invAF <- invalidAggregationFields
        invTAF <- invalidTypeAggregationField
      } yield {
        invQF ::: invPF ::: invODBF ::: invAF ::: invTAF match {
          case Nil => Right(query)
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
