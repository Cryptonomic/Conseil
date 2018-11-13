package tech.cryptonomic.conseil.tezos

import com.typesafe.config.Config
import slick.ast.FieldSymbol
import slick.jdbc.PostgresProfile
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object PlatformDiscoveryOperations {

  lazy val dbHandle: PostgresProfile.backend.Database = DatabaseUtil.db

  private val tables = List(Tables.Blocks, Tables.Accounts, Tables.OperationGroups, Tables.Operations, Tables.Fees)
  private val tablesMap = tables.map(table => table.baseTableRow.tableName -> table)

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
    val res: DBIO[List[Attributes]] =
      DBIO.sequence(
        tablesMap.collect {
          case (name, table) if name == tableName =>
            val overallCount = TezosDb.countRows(table)
            table.baseTableRow.create_*.map { col =>
              for {
                overallCnt <- overallCount
                distinctCnt <- TezosDb.countDistinct(table.baseTableRow.tableName, col.name)
              } yield makeAttributes(col, distinctCnt, overallCnt, tableName)
            }
        }.flatMap(_.toList)
      )
    dbHandle.run(res)
  }

  private def makeAttributes(col: FieldSymbol, distinctCount: Int, overallCount: Int, tableName: String): Attributes =
    Attributes(
      name = col.name,
      displayName = makeDisplayName(col.name),
      dataType = mapType(col.tpe.toString),
      cardinality = distinctCount,
      keyType = if (distinctCount == overallCount) KeyType.UniqueKey else KeyType.NonKey,
      entity = tableName
    )


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

}
