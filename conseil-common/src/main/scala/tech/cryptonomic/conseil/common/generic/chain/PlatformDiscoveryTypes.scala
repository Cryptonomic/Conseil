package tech.cryptonomic.conseil.common.generic.chain

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.InvalidPredicateFiltering
import tech.cryptonomic.conseil.common.metadata.{NetworkPath, PlatformPath}

/**
  * Classes used for Platform routes
  */
object PlatformDiscoveryTypes {

  /** Case class representing network */
  final case class Platform(name: String, displayName: String, description: Option[String] = None) {
    val path = PlatformPath(name)
  }

  /** Case class representing network */
  final case class Network(
      name: String,
      displayName: String,
      platform: String,
      network: String,
      description: Option[String] = None
  ) {
    lazy val path = NetworkPath(network, PlatformPath(platform))
  }

  /** Case class representing single entity of a given network */
  final case class Entity(
      name: String,
      displayName: String,
      count: Int,
      displayNamePlural: Option[String] = None,
      description: Option[String] = None,
      limitedQuery: Option[Boolean] = None
  )

  /** Case class representing single attribute of given entity from DB */
  final case class Attribute(
      name: String,
      displayName: String,
      dataType: DataType,
      cardinality: Option[Int],
      keyType: KeyType,
      entity: String,
      description: Option[String] = None,
      placeholder: Option[String] = None,
      dataFormat: Option[String] = None,
      valueMap: Option[Map[String, String]] = None,
      scale: Option[Int] = None,
      reference: Option[Map[String, String]] = None,
      cacheConfig: Option[AttributeCacheConfiguration] = None,
      displayPriority: Option[Int] = None,
      displayOrder: Option[Int] = None,
      sufficientForQuery: Option[Boolean] = None,
      currencySymbol: Option[String] = None,
      currencySymbolCode: Option[Int] = None
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
  sealed trait DataType extends Product with Serializable
  object DataType {
    case object Date extends DataType
    case object DateTime extends DataType
    case object String extends DataType
    case object Int extends DataType
    case object LargeInt extends DataType
    case object Decimal extends DataType
    case object Boolean extends DataType
    case object Hash extends DataType
    case object AccountAddress extends DataType
    case object Currency extends DataType
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

  def imapDataType(tpe: DataType): String =
    tpe match {
      case DataType.DateTime => "timestamp"
      case DataType.String => "varchar"
      case DataType.Int => "int"
      case DataType.LargeInt => "bigint"
      case DataType.Decimal => "numeric"
      case DataType.Boolean => "bool"
      case DataType.Hash => "hash"
      case DataType.AccountAddress => "accountAddress"
      case DataType.Currency => "currency"
      case _ => ""
    }

  /** Enumeration of key types */
  sealed trait KeyType extends Product with Serializable
  object KeyType {
    case object NonKey extends KeyType
    case object UniqueKey extends KeyType
  }

  /** Attribute cache configuration */
  final case class AttributeCacheConfiguration(
      cached: Boolean = false,
      minMatchLength: Int = 0,
      maxResultSize: Int = Int.MaxValue
  )
}
