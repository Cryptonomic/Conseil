package tech.cryptonomic.conseil.tezos

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes.KeyType.KeyType

object PlatformDiscoveryTypes {

  class DataTypeRef extends TypeReference[DataType.type]

  class KeyTypeRef extends TypeReference[KeyType.type]

  final case class Network(name: String, displayName: String, platform: String, network: String)

  final case class Entity(name: String, displayName: String, count: Int, network: String)

  final case class Attributes(
    name: String,
    displayName: String,
    @JsonScalaEnumeration(classOf[DataTypeRef]) dataType: DataType,
    cardinality: Int,
    @JsonScalaEnumeration(classOf[KeyTypeRef]) keyType: KeyType,
    entity: String
  )

  object DataType extends Enumeration {
    type DataType = Value
    val Enum, Hex, Binary, Date, DateTime, String, Int, LargeInt, Decimal, Boolean = Value
  }

  object KeyType extends Enumeration {
    type KeyType = Value
    val NonKey, UniqueKey = Value
  }


//  Taking an attribute reference (described in #182) return a list of distinct values.
//
//    Error conditions:
//
//    query for high-cardinality field
//    query for date, or datetime field
//  query for any cardinality numeric field
//    Query for boolean fields should return a canned list: ["True", "False"]


}
