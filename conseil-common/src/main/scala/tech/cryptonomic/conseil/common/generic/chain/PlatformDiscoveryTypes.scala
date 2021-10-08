package tech.cryptonomic.conseil.common.generic.chain

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.InvalidPredicateFiltering
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.KeyType.KeyType
import tech.cryptonomic.conseil.common.metadata.{NetworkPath, PlatformPath}

/**
  * Classes used for Platform routes
  */
object PlatformDiscoveryTypes {

  /** Case class representing network */
  case class Platform(name: String, displayName: String, description: Option[String]) {
    val path = PlatformPath(name)
  }

  /** Case class representing network */
  case class Network(
      name: String,
      displayName: String,
      platform: String,
      network: String,
      description: Option[String]
  ) {
    lazy val path = NetworkPath(network, PlatformPath(platform))
  }

  /** Case class representing single entity of a given network */
  case class Entity(
      name: String,
      displayName: String,
      count: Int,
      displayNamePlural: Option[String],
      description: Option[String],
      limitedQuery: Option[Boolean]
  )

  /** Case class representing single attribute of given entity from DB */
  case class Attribute(
      name: String,
      displayName: String,
      dataType: DataType,
      cardinality: Option[Int],
      keyType: KeyType,
      entity: String,
      description: Option[String],
      placeholder: Option[String],
      dataFormat: Option[String],
      valueMap: Option[Map[String, String]],
      scale: Option[Int],
      reference: Option[Map[String, String]],
      cacheConfig: Option[AttributeCacheConfiguration],
      displayPriority: Option[Int],
      displayOrder: Option[Int],
      sufficientForQuery: Option[Boolean],
      currencySymbol: Option[String],
      currencySymbolCode: Option[Int]
  ) {

    /** Checks if attribute is valid for predicate */
    def doesPredicateContainValidAttribute: List[InvalidPredicateFiltering] =
      if (keyType == KeyType.UniqueKey || dataType == DataType.DateTime || sufficientForQuery.getOrElse(false)) {
        List.empty
      } else {
        List(InvalidPredicateFiltering(s"Query needs to contain a predicate on UniqueKey or DateTime attribute"))
      }
  }

  /** Enumeration of data types */
  object DataType extends Enumeration {
    type DataType = Value
    val Enum, Hex, Binary, Date, DateTime, String, Hash, AccountAddress, Int, LargeInt, Decimal, Currency, Boolean =
      Value
  }

  /** Maps type from DB to type used in query */
  def mapDataType(tpe: String): DataType =
    tpe match {
      case "timestamp" => DataType.DateTime
      case "varchar" => DataType.String
      case "int4" | "int" | "serial" => DataType.Int
      case "bigint" | "int8" => DataType.LargeInt
      case "numeric" => DataType.Decimal
      case "bool" => DataType.Boolean
      case "hash" => DataType.Hash
      case "accountAddress" => DataType.AccountAddress
      case "currency" => DataType.Currency
      case _ => DataType.String
    }

  /** Enumeration of key types */
  object KeyType extends Enumeration {
    type KeyType = Value
    val NonKey, UniqueKey = Value
  }

  /** Attribute cache configuration */
  final case class AttributeCacheConfiguration(
      cached: Boolean = false,
      minMatchLength: Int = 0,
      maxResultSize: Int = Int.MaxValue
  )
}
