package tech.cryptonomic.conseil.tezos

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import tech.cryptonomic.conseil.tezos.QueryProtocolTypes.OperationType.OperationType

object QueryProtocolTypes {
  def mapOperationToSQL(operation: OperationType, inverse: Boolean): String = {
    val op = operation match {
      case OperationType.in => "IN"
    }
    if (inverse) s"NOT $op"
    else op
  }

  /** Class required for KeyType enum serialization */
  class OperationTypeRef extends TypeReference[OperationType.type]

  case class Predicates(
    field: String,
    @JsonScalaEnumeration(classOf[OperationTypeRef]) operation: OperationType,
    set: List[Any],
    inverse: Boolean
  )

  case class JsonQuery(
    fields: List[String],
    predicates: List[Predicates]
  )

  /** Enumeration of operation types */
  object OperationType extends Enumeration {
    type OperationType = Value
    val in = Value
  }
}
