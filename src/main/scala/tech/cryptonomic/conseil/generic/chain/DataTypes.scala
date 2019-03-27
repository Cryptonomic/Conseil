package tech.cryptonomic.conseil.generic.chain

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import tech.cryptonomic.conseil.generic.chain.DataTypes.OperationType.OperationType
import tech.cryptonomic.conseil.generic.chain.DataTypes.OrderDirection.OrderDirection
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations

import scala.concurrent.{ExecutionContext, Future}


/**
  * Classes used for deserializing query.
  */
object DataTypes {
  import io.scalaland.chimney.dsl._

  /** Type representing Map[String, Any] */
  type AnyMap = Map[String, Any]

  /** Type representing Map[String, Option[Any]] for query response */
  type QueryResponse = Map[String, Option[Any]]

  /** Default value of limit parameter */
  val defaultLimitValue: Int = 10000

  /** Max value of limit parameter */
  val maxLimitValue: Int = 100000

  /** Trait representing query validation errors */
  sealed trait QueryValidationError extends Product with Serializable {
    val message: String
  }

  /** Class required for OperationType enum serialization */
  class OperationTypeRef extends TypeReference[OperationType.type]

  /** Class representing predicate */
  case class Predicate(
    field: String,
    @JsonScalaEnumeration(classOf[OperationTypeRef]) operation: OperationType,
    set: List[Any] = List.empty,
    inverse: Boolean = false,
    precision: Option[Int] = None
  )

  /** Class required for Ordering enum serialization */
  class QueryOrderingRef extends TypeReference[OrderDirection.type]

  case class QueryOrdering(field: String, @JsonScalaEnumeration(classOf[QueryOrderingRef]) direction: OrderDirection)

  /** Class representing invalid query field */
  case class InvalidQueryField(message: String) extends QueryValidationError

  /** Class representing invalid predicate field */
  case class InvalidPredicateField(message: String) extends QueryValidationError

  /** Class representing invalid order by field */
  case class InvalidOrderByField(message: String) extends QueryValidationError

  /** Class representing query */
  case class Query(
    fields: List[String] = List.empty,
    predicates: List[Predicate] = List.empty,
    orderBy: List[QueryOrdering] = List.empty,
    limit: Int = defaultLimitValue
  )

  /** Class representing query got through the REST API */
  case class ApiQuery(
    fields: Option[List[String]],
    predicates: Option[List[Predicate]],
    orderBy: Option[List[QueryOrdering]],
    limit: Option[Int]
  ) {
    /** Method which validates query fields, as jackson runs on top of runtime reflection so NPE can happen if fields are missing */
    def validate(entity: String, tezosPlatformDiscovery: TezosPlatformDiscoveryOperations)(implicit ec: ExecutionContext):
    Future[Either[List[QueryValidationError], Query]] = {
      import cats.implicits._

      val query = Query().patchWith(this)

      val invalidQueryFields = query
        .fields
        .map(field => tezosPlatformDiscovery.areFieldsValid(entity, Set(field)).map(_ -> field))
        .sequence
        .map(_.filterNot(_._1).map(fieldPair => InvalidQueryField(fieldPair._2)))

      val invalidPredicateFields = query
        .predicates
        .map(_.field)
        .map(field => tezosPlatformDiscovery.areFieldsValid(entity, Set(field)).map(_ -> field))
        .sequence
        .map(_.filterNot(_._1).map(fieldPair => InvalidPredicateField(fieldPair._2)))

      val invalidOrderByFields = query
        .orderBy
        .map(_.field)
        .map(field => tezosPlatformDiscovery.areFieldsValid(entity, Set(field)).map(_ -> field))
        .sequence
        .map(_.filterNot(_._1).map(fieldPair => InvalidPredicateField(fieldPair._2)))

      for {
        invQF <- invalidQueryFields
        invPF <- invalidPredicateFields
        invODBF <- invalidOrderByFields
      } yield {
        invQF ::: invPF ::: invODBF match {
          case Nil => Right(query)
          case wrongFields => Left(wrongFields)
        }
      }
    }
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

}
