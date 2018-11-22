package tech.cryptonomic.conseil.tezos

import com.typesafe.config.Config
import slick.ast.FieldSymbol
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object PlatformDiscoveryOperations {

  private val tables = List(Tables.Blocks, Tables.Accounts, Tables.OperationGroups, Tables.Operations, Tables.Fees)
  private val tablesMap = tables.map(table => table.baseTableRow.tableName -> table)

  /**
    * Extracts networks from config file
    * @param  config configuration object
    * @return list of networks from configuration
    */
  def getNetworks(config: Config): List[Network] = {
    for {
      (platform, strippedConf) <- config.getObject("platforms").asScala
      (network, _) <- strippedConf.atKey(platform).getObject(platform).asScala
    } yield Network(network, network.capitalize, platform, network)
  }.toList


  /**
    * Extracts entities in the DB for the given network
    * @param  network name of the network
    * @return list of entities as a Future
    */
  def getEntities(network: String)(implicit ec: ExecutionContext): Future[List[Entity]] = {
    ApiOperations.countAll.map { counts =>
      createEntities(network, counts)
    }
  }

  /** creates entities out of provided data */
  private def createEntities(network: String, counts: Map[String, Int])(implicit ec: ExecutionContext): List[Entity] = {
    for {
      (tableName, _) <- tablesMap
      tableCount <- counts.get(tableName)
    } yield Entity(tableName, makeDisplayName(tableName), tableCount, network)
  }

  /**
    * Extracts attributes in the DB for the given table name
    * @param  tableName name of the table from which we extract attributes
    * @return list of attributes as a Future
    */
  def getTableAttributes(tableName: String)(implicit ec: ExecutionContext): Future[List[Attributes]] = {
    ApiOperations.prepareTableAttributes(makeAttributesList(tableName))
  }

  /** Makes list of DB actions to be executed for extracting attributes
    * @param  tableName name of the table from which we extract attributes
    * @return list of DBIO queries for attributes
    * */
  def makeAttributesList(tableName: String)(implicit ec: ExecutionContext): List[DBIO[Attributes]] = {
    for {
      (name, table) <- tablesMap
      if name == tableName
      col <- table.baseTableRow.create_*
    } yield {
      for {
        overallCnt <- TezosDb.countRows(table)
        distinctCnt <- TezosDb.countDistinct(table.baseTableRow.tableName, col.name)
      } yield makeAttributes(col, distinctCnt, overallCnt, tableName)
    }
  }

  def listAttributeValues(entity: String, attribute: String, withFilter: Option[String] = None)(implicit ec: ExecutionContext): Future[Option[List[String]]] = {
    val res = tablesMap.collectFirst {
      case (name, table) if name == entity =>
        table.baseTableRow.create_*.collectFirst {
          case col if col.name == attribute =>
            for {
              cd <- TezosDb.countDistinct(table.baseTableRow.tableName, col.name)
              if canQueryType(mapType(col.tpe.toString)) && !isHighCardinality(cd)
              sd <- withFilter match {
                case Some(filter) =>
                  TezosDatabaseOperations.selectDistinctLike(entity, attribute, filter)
                case None =>
                  TezosDatabaseOperations.selectDistinct(entity, attribute)
              }
            } yield sd
        }
    }.flatten
    ???
    //futOpt(res.map(dbHandle.run))
  }


  private def futOpt[A](x: Option[Future[A]])(implicit ec: ExecutionContext): Future[Option[A]] =
    x match {
      case Some(f) => f.map(Some(_))
      case None => Future.successful(None)
    }

  /** Makes attributes out of parameters */
  private def makeAttributes(col: FieldSymbol, distinctCount: Int, overallCount: Int, tableName: String): Attributes =
    Attributes(
      name = col.name,
      displayName = makeDisplayName(col.name),
      dataType = mapType(col.tpe.toString),
      cardinality = distinctCount,
      keyType = if (distinctCount == overallCount) KeyType.UniqueKey else KeyType.NonKey,
      entity = tableName
    )

  /** Makes displayName out of name */
  private def makeDisplayName(name: String): String = {
    name.capitalize.replace("_", " ")
  }

  private def canQueryType(dt: DataType): Boolean = {
    // values described in the ticket #183
    val cantQuery = List(DataType.Date, DataType.DateTime, DataType.Int, DataType.LargeInt, DataType.Decimal)
    !cantQuery.contains(dt)
  }

  private def isHighCardinality(distinctCount: Int): Boolean = {
    // arbitrary value which will be defined in the config
    val maxCount = 1000
    distinctCount > maxCount
  }
}
