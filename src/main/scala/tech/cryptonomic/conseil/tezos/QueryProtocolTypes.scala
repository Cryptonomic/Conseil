package tech.cryptonomic.conseil.tezos

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import tech.cryptonomic.conseil.tezos.QueryProtocolTypes.OperationType.OperationType

import scala.util.Try

object QueryProtocolTypes {

  /** Class required for KeyType enum serialization */
  class OperationTypeRef extends TypeReference[OperationType.type]

  case class Predicates(
    field: String,
    @JsonScalaEnumeration(classOf[OperationTypeRef]) operation: OperationType,
    set: List[Any],
    inverse: Boolean = false
  )

  case class FieldQuery(
    fields: List[String],
    predicates: List[Predicates]
  ) {
    def validate: Option[FieldQuery] = {
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
    val in = Value
  }

}
