package tech.cryptonomic.conseil.tezos

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import tech.cryptonomic.conseil.tezos.QueryProtocolTypes.OperationType.OperationType

import scala.util.Try

/**
  * Classes used for deserializing query.
  */
object QueryProtocolTypes {

  /** Class required for OperationType enum serialization */
  class OperationTypeRef extends TypeReference[OperationType.type]

  /** Class representing predicate */
  case class Predicate(
    field: String,
    @JsonScalaEnumeration(classOf[OperationTypeRef]) operation: OperationType,
    set: List[Any],
    inverse: Boolean = false,
    precision: Option[Int] = None
  )

  /** Class representing query */
  case class Query(
    fields: List[String],
    predicates: List[Predicate]
  ) {
    /** Method which validates query fields, as jackson runs on top of runtime reflection so NPE can happen if fields are missing */
    def validate: Option[Query] = {
      Try {
        predicates.foreach { pred =>
          fields.nonEmpty && pred.field.nonEmpty && OperationType.values.contains(pred.operation) && pred.set.nonEmpty
        }
        this
      }.toOption
    }
  }

  /** Enumeration of operation types */
  object OperationType extends Enumeration {
    type OperationType = Value
    val in, between, like, lt, gt, eq, startsWith, endsWith, before, after = Value
  }

}
