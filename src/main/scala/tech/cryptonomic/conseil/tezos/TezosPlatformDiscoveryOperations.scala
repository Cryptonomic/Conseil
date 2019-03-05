package tech.cryptonomic.conseil.tezos

import slick.ast.FieldSymbol
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}

import scala.concurrent.{ExecutionContext, Future}


object TezosPlatformDiscoveryOperations {

  private val tables = List(Tables.Blocks, Tables.Accounts, Tables.OperationGroups, Tables.Operations, Tables.Fees)
  private val tablesMap = tables.map(table => table.baseTableRow.tableName -> table)

  /**
    * Extracts entities in the DB for the given network
    *
    * @return list of entities as a Future
    */
  def getEntities(implicit ec: ExecutionContext): Future[List[Entity]] = {
    ApiOperations.countAll.map(createEntities)
  }

  /** creates entities out of provided data */
  private def createEntities(counts: Map[String, Int]): List[Entity] = {
    for {
      (tableName, _) <- tablesMap
      tableCount <- counts.get(tableName)
    } yield Entity(tableName, makeDisplayName(tableName), tableCount)
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

  /** Sanitizes string to be viable to paste into plain SQL */
  def sanitizeForSql(str: String): String = {
    val supportedCharacters = Set('_', '.', '+', ':', '-')
    str.filter(c => c.isLetterOrDigit || supportedCharacters.contains(c))
  }

  /** Checks if columns exist for the given table */
  def areFieldsValid(tableName: String, fields: Set[String]): Boolean = {
    tablesMap.exists {
      case (name, table) =>
        val cols = table.baseTableRow.create_*.map(_.name).toSet
        name == tableName && fields.subsetOf(cols)
    }
  }


  /** Checks if entity is valid
    *
    * @param tableName name of the table(entity) which needs to be checked
    * @return boolean which tells us if entity is valid
    */
  def isEntityValid(tableName: String): Boolean = {
    tablesMap.map(_._1).contains(tableName)
  }

  /** Checks if attribute is valid for given entity
    *
    * @param tableName  name of the table(entity) which needs to be checked
    * @param columnName name of the column(attribute) which needs to be checked
    * @return boolean which tells us if attribute is valid for given entity
    */
  def isAttributeValid(tableName: String, columnName: String): Boolean = {
    {
      for {
        (_, table) <- tablesMap.find(_._1 == tableName)
        column <- table.baseTableRow.create_*.find(_.name == columnName)
      } yield column
    }.isDefined
  }

  /**
    * Extracts attributes in the DB for the given table name
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of attributes as a Future
    */
  def getTableAttributes(tableName: String)(implicit ec: ExecutionContext): Future[List[Attribute]] = {
    ApiOperations.runQuery(makeAttributesList(tableName))
  }

  /** Makes list of DB actions to be executed for extracting attributes
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of DBIO queries for attributes
    **/
  def makeAttributesList(tableName: String)(implicit ec: ExecutionContext): DBIO[List[Attribute]] = {
    DBIO.sequence {
      for {
        (name, table) <- tablesMap
        if name == tableName
        col <- table.baseTableRow.create_*
      } yield {
        if(canQueryType(mapType(col.tpe))) {
          for {
            overallCnt <- TezosDb.countRows(table)
            distinctCnt <- TezosDb.countDistinct(table.baseTableRow.tableName, col.name)
          } yield makeAttributes(col, Some(distinctCnt), Some(overallCnt), tableName)
        } else {
          DBIO.successful(makeAttributes(col, None, None, tableName))
        }
      }
    }
  }

  /** Makes attributes out of parameters */
  private def makeAttributes(col: FieldSymbol, distinctCount: Option[Int], overallCount: Option[Int], tableName: String): Attribute =
    Attribute(
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

}
