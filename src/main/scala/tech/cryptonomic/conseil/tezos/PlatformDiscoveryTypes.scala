package tech.cryptonomic.conseil.tezos

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes.KeyType.KeyType

/**
  * Classes used for Platform routes
  */
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

  def mapType(tpe: String): DataType = {
    val optionRegex = "Option\\[([A-Za-z0-9']+)\\]".r
    tpe match {
      case "java.sql.Timestamp'" => DataType.DateTime
      case "String'" => DataType.String
      case "Int'" => DataType.Int
      case "Long'" => DataType.LargeInt
      case "Float'" | "Double'" | "scala.math.BigDecimal'" => DataType.Decimal
      case "Boolean'" => DataType.Boolean
      case optionRegex(t) => mapType(t)
      case _ => DataType.String
    }
  }
}
