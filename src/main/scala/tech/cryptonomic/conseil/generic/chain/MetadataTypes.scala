package tech.cryptonomic.conseil.generic.chain

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import slick.ast.{OptionType, Type}
import tech.cryptonomic.conseil.generic.chain.MetadataTypes.DataType.DataType
import tech.cryptonomic.conseil.generic.chain.MetadataTypes.KeyType.KeyType

/**
  * Classes used for Platform routes
  */
object MetadataTypes {

  /** Class required for DataType enum serialization */
  class DataTypeRef extends TypeReference[DataType.type]

  /** Class required for KeyType enum serialization */
  class KeyTypeRef extends TypeReference[KeyType.type]

  /** Case class representing network */
  final case class Platform(name: String, displayName: String)

  /** Case class representing network */
  final case class Network(name: String, displayName: String, platform: String, network: String)

  /** Case class representing single entity of a given network */
  final case class Entity(name: String, displayName: String, count: Int, network: String)

  /** Case class representing single attribute of given entity from DB */
  final case class Attributes(
    name: String,
    displayName: String,
    @JsonScalaEnumeration(classOf[DataTypeRef]) dataType: DataType,
    cardinality: Int,
    @JsonScalaEnumeration(classOf[KeyTypeRef]) keyType: KeyType,
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

  /**
    * Function maps slick jdbc column type to DataType
    * @param tpe type from slick TableQuery
    * @return one of DataType enums
    */
  def mapType(tpe: Type): DataType = {
    import slick.jdbc.PostgresProfile.columnTypes._
    tpe match {
      case _: TimestampJdbcType => DataType.DateTime
      case _: StringJdbcType => DataType.String
      case _: IntJdbcType => DataType.Int
      case _: LongJdbcType => DataType.LargeInt
      case _: FloatJdbcType | _: DoubleJdbcType | _: BigDecimalJdbcType => DataType.Decimal
      case _: BooleanJdbcType => DataType.Boolean
      case optionType: OptionType => mapType(optionType.elementType)
      case _ => DataType.String
    }
  }
}
