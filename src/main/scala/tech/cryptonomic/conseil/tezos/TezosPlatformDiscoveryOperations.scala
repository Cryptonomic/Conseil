package tech.cryptonomic.conseil.tezos

import slick.ast.FieldSymbol
import slick.dbio.{DBIO, DBIOAction}
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.meta.{MColumn, MIndexInfo, MPrimaryKey, MTable}
import tech.cryptonomic.conseil.generic.chain.{DataTypes, PlatformDiscoveryTypes}
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object TezosPlatformDiscoveryOperations {
  def apply(implicit executionContext: ExecutionContext): TezosPlatformDiscoveryOperations = new TezosPlatformDiscoveryOperations()
}

class TezosPlatformDiscoveryOperations(implicit executionContext: ExecutionContext) {

  private val tables = List(Tables.Blocks, Tables.Accounts, Tables.OperationGroups, Tables.Operations, Tables.Fees)
  private val tablesMap = tables.map(table => table.baseTableRow.tableName -> table)

  import cats.effect._
  import cats.effect.concurrent._
  import cats.implicits._

  implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
  private val attributesCache: MVar[IO, Map[String, (Long, List[Attribute])]] = MVar[IO].empty[Map[String, (Long, List[Attribute])]].unsafeRunSync()
  private val entitiesCache: MVar[IO, (Long, List[Entity])] = MVar[IO].empty[(Long, List[Entity])].unsafeRunSync()
  val timeout: FiniteDuration = 120 seconds

  def init(): Unit = {
    val start = now
    val result = for {
      ent <- IO.fromFuture(IO(ApiOperations.runQuery(preCacheEntities)))
      _ <- entitiesCache.put(ent)
      attr <- IO.fromFuture(IO(ApiOperations.runQuery(preCacheAttributes)))
      _ <- attributesCache.put(attr)
    } yield ()

    result.unsafeToFuture().foreach(_ => println(s"Done caching! ${(now - start) / 1000}"))
  }

  def preCacheEntities: DBIO[(Long, List[Entity])] = {
    for {
      tables <- MTable.getTables(Some(""), Some("public"), Some(""), Some(Seq("TABLE")))
      counts <-
        DBIOAction.sequence {
          tables.map { table =>
            TezosDatabaseOperations.countRows(table.name.name)
          }
        }
    } yield now -> (tables.map(_.name.name) zip counts).map {
      case (name, count) =>
        Entity(name, makeDisplayName(name), count)
    }.toList
  }

  def preCacheAttributes: DBIO[Map[String, (Long, List[Attribute])]] = {
    val res = for {
      tables <- MTable.getTables(Some(""), Some("public"), Some(""), Some(Seq("TABLE")))
      columns <-
        DBIOAction.sequence {
          tables.map { table =>
            table.getColumns
          }
        }
      indexes <-
        DBIOAction.sequence {
          tables.map { table =>
            table.getIndexInfo()
          }
        }
      primaryKeys <-
        DBIOAction.sequence {
          tables.map { table =>
            table.getPrimaryKeys
          }
        }
      card <-
        DBIOAction.sequence {
          columns.map { column =>
            DBIOAction.sequence {
              column.map { col =>
                if (canQueryType(mapType(col.typeName))) {
                  TezosDatabaseOperations.countDistinct(col.table.name, col.name).map(col -> Some(_))
                } else {
                  DBIOAction.successful(col -> None)
                }
              }
            }
          }
        }
    } yield {
      card.map { cols =>
        cols.head._1.table.name -> (now, cols.map { case (col, count) =>
          Attribute(
            name = col.name,
            displayName = makeDisplayName(col.name),
            dataType = mapType(col.typeName),
            cardinality = count,
            keyType = if (isIndex(col, indexes) || isKey(col, primaryKeys)) KeyType.UniqueKey else KeyType.NonKey,
            entity = col.table.name
          )
        }.toList)
      }
    }
    res.map(_.toMap)
  }


  def isKey(column: MColumn, keys: Vector[Vector[MPrimaryKey]]): Boolean = {
    keys
      .filter(_.forall(_.table == column.table))
      .flatten
      .exists(_.column == column.name)
  }

  def isIndex(column: MColumn, index: Vector[Vector[MIndexInfo]]): Boolean = {
    index
      .filter(_.forall(_.table == column.table))
      .flatten
      .exists(_.column.contains(column.name))
  }

  def mapType(tpe: String): DataType = {
    tpe match {
      case "timestamp" => DataType.DateTime
      case "varchar" => DataType.String
      case "int4" | "serial" => DataType.Int
      case "numeric" => DataType.Decimal
      case "bool" => DataType.Boolean
      case x =>
        println(s"Unknown type: $x")
        DataType.String
    }
  }

  def now: Long = System.currentTimeMillis()


//  def getEntities: Future[List[Entity]] = {
//    val result = for {
//      entities <- entitiesCache.read
//      res <- if (entities._1 + timeout.toMillis > now) {
//        IO.pure(entities._2)
//      } else {
//        for {
//          updatedEntities <- IO.fromFuture(IO(ApiOperations.runQuery(preCacheEntities)))
//          _ <- entitiesCache.take
//          _ <- entitiesCache.put(updatedEntities)
//        } yield updatedEntities._2
//      }
//    } yield res
//    result.unsafeToFuture()
//  }

  def getAttributes(tableName: String): Future[Option[List[Attribute]]] = {
    attributesCache.read.flatMap { entitiesMap =>
      entitiesMap.get(tableName).map { case (last, attributes) =>
        if (last + timeout.toMillis > now) {
          IO.pure(attributes)
        } else {
          IO.fromFuture(IO(getPartialAttributes(tableName, attributes))).flatMap { updatedAttributes =>
            attributesCache.take.flatMap { _ =>
              attributesCache.put(entitiesMap.updated(tableName, now -> updatedAttributes)).map(_ => updatedAttributes)
            }
          }
        }
      }.sequence
    }.unsafeToFuture()
  }

  def getPartialAttributes(tableName: String, columns: List[Attribute]): Future[List[Attribute]] = {
    ApiOperations.runQuery(getPartialAttributesQuery(tableName, columns))
  }

  private def getPartialAttributesQuery(tableName: String, columns: List[Attribute]): DBIO[List[Attribute]] = {
    DBIOAction.sequence {
      columns.map { column =>
        if (canQueryType(column.dataType)) {
          TezosDatabaseOperations.countDistinct(tableName, column.name)
            .map { count =>
              column.copy(cardinality = Some(count))
            }
        } else {
          DBIOAction.successful(column)
        }
      }
    }
  }




  /**
    * Extracts entities in the DB for the given network
    *
    * @return list of entities as a Future
    */
  def getEntities(): Future[List[Entity]] = {
    val result = for {
      entities <- entitiesCache.read
      res <- if (entities._1 + timeout.toMillis > now) {
        IO.pure(entities._2)
      } else {
        for {
          updatedEntities <- IO.fromFuture(IO(ApiOperations.runQuery(preCacheEntities)))
          _ <- entitiesCache.take
          _ <- entitiesCache.put(updatedEntities)
        } yield updatedEntities._2
      }
    } yield res
    result.unsafeToFuture()
  }

  /** Makes list of possible string values of the attributes
    *
    * @param  tableName  name of the table from which we extract attributes
    * @param  attribute  name of the attribute
    * @param  withFilter optional parameter which can filter attributes
    * @return list of attributes
    * */
  def listAttributeValues(tableName: String, attribute: String, withFilter: Option[String] = None): Future[List[String]] = {
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
  def verifyAttributesAndGetQueries(tableName: String, attribute: String, withFilter: Option[String]): DBIO[List[String]] = {
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
  private def makeAttributesQuery(tableName: String, column: FieldSymbol, withFilter: Option[String]): DBIO[List[String]] = {
    for {
      distinctCount <- TezosDb.countDistinct(tableName, column.name)
      if canQueryType(PlatformDiscoveryTypes.mapType(column.tpe)) && isLowCardinality(distinctCount)
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
  def getTableAttributes(tableName: String): Future[List[Attribute]] = {
    ApiOperations.runQuery(makeAttributesList(tableName))
  }

  /** Makes list of DB actions to be executed for extracting attributes
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of DBIO queries for attributes
    **/
  def makeAttributesList(tableName: String): DBIO[List[Attribute]] = {
    DBIO.sequence {
      for {
        (name, table) <- tablesMap
        if name == tableName
        col <- table.baseTableRow.create_*
      } yield {
        if(canQueryType(PlatformDiscoveryTypes.mapType(col.tpe))) {
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
      dataType = PlatformDiscoveryTypes.mapType(col.tpe),
      cardinality = distinctCount,
      keyType = if (distinctCount == overallCount) KeyType.UniqueKey else KeyType.NonKey,
      entity = tableName
    )

  /** Makes displayName out of name */
  private def makeDisplayName(name: String): String = {
    name.capitalize.replace("_", " ")
  }

}
