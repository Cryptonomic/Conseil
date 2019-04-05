package tech.cryptonomic.conseil.generic.chain

import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.KeyType.KeyType

/**
  * Classes used for Platform routes
  */
object PlatformDiscoveryTypes {

  /** Case class representing network */
  final case class Platform(name: String, displayName: String)

  /** Case class representing network */
  final case class Network(name: String, displayName: String, platform: String, network: String)

  /** Case class representing single entity of a given network */
  final case class Entity(name: String, displayName: String, count: Int)

  /** Case class representing single attribute of given entity from DB */
  final case class Attribute(
    name: String,
    displayName: String,
    dataType: DataType,
    cardinality: Option[Int],
    keyType: KeyType,
    entity: String
  )

  /** Enumeration of data types */
  object DataType extends Enumeration {
    type DataType = Value
    val Enum, Hex, Binary, Date, DateTime, String, Int, LargeInt, Decimal, Boolean = Value
  }

  /** Enumeration of key types */
  object KeyType extends Enumeration {
    type KeyType = Value
    val NonKey, UniqueKey = Value
  }
}
