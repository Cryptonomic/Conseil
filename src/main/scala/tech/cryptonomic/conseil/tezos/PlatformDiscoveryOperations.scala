package tech.cryptonomic.conseil.tezos

import slick.ast.FieldSymbol
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.config.Platforms._
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.tezos.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}


object PlatformDiscoveryOperations {

  private val tables = List(Tables.Blocks, Tables.Accounts, Tables.OperationGroups, Tables.Operations, Tables.Fees)
  private val tablesMap = tables.map(table => table.baseTableRow.tableName -> table)

  /**
    * Extracts networks from config file
    *
    * @param  config a mapping from platform to all available networks configurations
    * @return list of networks from configuration
    */
  def getNetworks(config: PlatformsConfiguration): List[Network] =
    for {
      (platform, configs) <- config.platforms.toList
      networkConfiguration <- configs
    } yield {
      val network = networkConfiguration.network
      Network(network, network.capitalize, platform.name, network)
    }

  /**
    * Extracts entities in the DB for the given network
    *
    * @param  network name of the network
    * @return list of entities as a Future
    */
  def getEntities(network: String)(implicit ec: ExecutionContext): Future[List[Entity]] = {
    ApiOperations.countAll.map { counts =>
      createEntities(network, counts)
    }
  }

  /** creates entities out of provided data */
  private def createEntities(network: String, counts: Map[String, Int]): List[Entity] = {
    for {
      (tableName, _) <- tablesMap
      tableCount <- counts.get(tableName)
    } yield Entity(tableName, makeDisplayName(tableName), tableCount, network)
  }

  /**
    * Extracts attributes in the DB for the given table name
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of attributes as a Future
    */
  def getTableAttributes(tableName: String)(implicit ec: ExecutionContext): Future[List[Attributes]] = {
    ApiOperations.runQuery(makeAttributesList(tableName))
  }

  /** Makes list of DB actions to be executed for extracting attributes
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of DBIO queries for attributes
    * */
  def makeAttributesList(tableName: String)(implicit ec: ExecutionContext): DBIO[List[Attributes]] = {
    DBIO.sequence {
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
  }

  /** Makes attributes out of parameters */
  private def makeAttributes(col: FieldSymbol, distinctCount: Int, overallCount: Int, tableName: String): Attributes =
    Attributes(
      name = col.name,
      displayName = makeDisplayName(col.name),
      dataType = mapType(col.tpe),
      cardinality = distinctCount,
      keyType = if (distinctCount == overallCount) KeyType.UniqueKey else KeyType.NonKey,
      entity = tableName
    )

  /** Makes displayName out of name */
  private def makeDisplayName(name: String): String = {
    name.capitalize.replace("_", " ")
  }


  /** Makes list of possible string values of the attributes
    *
    * @param  tableName  name of the table from which we extract attributes
    * @param  attribute  name of the attribute
    * @param  withFilter optional parameter which can filter attributes
    * @return list of attributes
    * */
  def listAttributeValues(tableName: String, attribute: String, withFilter: Option[String] = None)
    (implicit ec: ExecutionContext): Future[List[String]] = {
    val res = verifyAttributesAndGetQueries(tableName, attribute, withFilter)
    ApiOperations.runQuery(res)
  }

  /** Makes list of DBIO actions to get possible string values of the attributes
    *
    * @param  tableName  name of the table from which we extract attributes
    * @param  attribute  name of the attribute
    * @param  withFilter optional parameter which can filter attributes
    * @return list of DBIO actions to get matching attributes
    * */
  def verifyAttributesAndGetQueries(tableName: String, attribute: String, withFilter: Option[String])
    (implicit ec: ExecutionContext): DBIO[List[String]] = {
    DBIO.sequence {
      for {
        (name, table) <- tablesMap
        if name == tableName
        col <- table.baseTableRow.create_*
        if col.name == attribute
      } yield makeAttributesQuery(name, col, withFilter)
    }.map(_.flatten)
  }

  /** Makes list of possible string values of the attributes
    *
    * @param  tableName  name of the table from which we extract attributes
    * @param  column     name of the attribute
    * @param  withFilter optional parameter which can filter attributes
    * @return list of attributes
    * */
  private def makeAttributesQuery(tableName: String, column: FieldSymbol, withFilter: Option[String])
    (implicit ec: ExecutionContext): DBIO[List[String]] = {
    for {
      distinctCount <- TezosDb.countDistinct(tableName, column.name)
      if canQueryType(mapType(column.tpe)) && isLowCardinality(distinctCount)
      distinctSelect <- withFilter match {
        case Some(filter) =>
          TezosDatabaseOperations.selectDistinctLike(tableName, column.name, sanitizeForSql(filter))
        case None =>
          TezosDatabaseOperations.selectDistinct(tableName, column.name)
      }
    } yield distinctSelect
  }

  /** Checks the data types if cannot be queried by */
  private def canQueryType(dt: DataType): Boolean = {
    // values described in the ticket #183
    val cantQuery = Set(DataType.Date, DataType.DateTime, DataType.Int, DataType.LargeInt, DataType.Decimal)
    !cantQuery(dt)
  }

  /** Checks if cardinality of the column is not too high so it should not be queried */
  private def isLowCardinality(distinctCount: Int): Boolean = {
    // reasonable value which I thought of for now
    val maxCount = 1000
    distinctCount < maxCount
  }

  /** Leaves only letters and digits in the SQL string */
  private def sanitizeForSql(str: String): String = {
    str.filter(_.isLetterOrDigit)
  }
}
