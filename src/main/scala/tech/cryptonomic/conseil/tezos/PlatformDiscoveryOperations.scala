package tech.cryptonomic.conseil.tezos

import com.fasterxml.jackson.core.`type`.TypeReference
import com.fasterxml.jackson.module.scala.JsonScalaEnumeration
import com.typesafe.config.Config
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryOperations.DataType.DataType
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryOperations.KeyType.KeyType
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object PlatformDiscoveryOperations {

  lazy val dbHandle: PostgresProfile.backend.Database = DatabaseUtil.db

  private val tables = List(Tables.Blocks, Tables.Accounts, Tables.OperationGroups, Tables.Operations, Tables.Fees)

  def getNetworks(config: Config): List[Network] = {
    config.getObject("platforms").asScala.flatMap {
      case (platform, strippedConf) =>
        strippedConf.atKey(platform).getObject(platform).asScala.map {
          case (network, _) =>
            Network(network, network.capitalize, platform, network)
        }.toList
    }.toList
  }

  def getEntities(network: String)(implicit ec: ExecutionContext): Future[List[Entity]] = {
    ApiOperations.countAll.map { counts =>
      tables.map(_.baseTableRow.tableName).flatMap { tableName =>
        counts.get(tableName).map { tableCount =>
          Entity(
            name = tableName,
            displayName = makeDisplayName(tableName),
            count = tableCount,
            network = network
          )
        }.toList
      }
    }
  }

  private def makeDisplayName(name: String): String = {
    name.capitalize.replace("_", " ")
  }

  def tableAttributes(tableName: String)(implicit ec: ExecutionContext): Future[List[Attributes]] = {
    val tablesMap = tables.map(table => table.baseTableRow.tableName -> table)

    val res = tablesMap.find(_._1 == tableName).map {
      case (_, table) =>
        dbHandle.run {
          val overallCount = TezosDb.count(table)
          DBIO.sequence(
            table.baseTableRow.create_*.map { col =>
              for {
                overallCnt <- overallCount
                distinctCnt <-  TezosDb.countDistinct(table.baseTableRow.tableName, col.name)
              } yield  (col, distinctCnt, overallCnt)
            }
          )
        }.map { distinctCountList =>
          distinctCountList.map {
            case (col, distinctCount, overallCount) =>
              Attributes(
                name = col.name,
                displayName = makeDisplayName(col.name),
                dataType = mapType(col.tpe.toString),
                cardinality = distinctCount,
                keyType = if(distinctCount == overallCount) KeyType.UniqueKey else KeyType.NonKey,
                entity = table.baseTableRow.tableName
              )
          }
        }
    }
    futOpt(res).map(_.toList.flatten)
  }

  private def mapType(tpe: String): DataType = {
    tpe match {
      case "java.sql.Timestamp'" => DataType.DateTime
      case "String'" => DataType.String
      case "Int'" => DataType.Int
      case "Long'" => DataType.LargeInt
      case "Float'" | "Double'" | "scala.math.BigDecimal'" => DataType.Decimal
      case "Boolean" => DataType.Boolean
      case x if x.startsWith("Option[") => mapType(x.drop(7).dropRight(1))
      case _ => DataType.String
    }
  }

  private def futOpt[A](x: Option[Future[A]])(implicit ec: ExecutionContext): Future[Option[A]] =
    x match {
      case Some(f) => f.map(Some(_))
      case None => Future.successful(None)
    }

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

}
