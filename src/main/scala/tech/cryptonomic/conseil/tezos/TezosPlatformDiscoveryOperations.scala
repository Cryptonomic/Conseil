package tech.cryptonomic.conseil.tezos

import slick.dbio.{DBIO, DBIOAction}
import slick.jdbc.meta.{MColumn, MIndexInfo, MPrimaryKey, MTable}
import tech.cryptonomic.conseil.generic.chain.MetadataOperations
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._

import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{ExecutionContext, Future}

object TezosPlatformDiscoveryOperations {
  def apply(metadataOperations: MetadataOperations)(implicit executionContext: ExecutionContext): TezosPlatformDiscoveryOperations =
    new TezosPlatformDiscoveryOperations(metadataOperations: MetadataOperations)
}

class TezosPlatformDiscoveryOperations(metadataOperations: MetadataOperations)(implicit executionContext: ExecutionContext) {

  import cats.effect._
  import cats.effect.concurrent._
  import cats.implicits._

  implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
  private val attributesCache: MVar[IO, Map[String, (Long, List[Attribute])]] = MVar[IO].empty[Map[String, (Long, List[Attribute])]].unsafeRunSync()
  private val entitiesCache: MVar[IO, (Long, List[Entity])] = MVar[IO].empty[(Long, List[Entity])].unsafeRunSync()
  val timeout: FiniteDuration = 12 seconds

  def init(): Unit = {
    val start = now
    val result = for {
      ent <- IO.fromFuture(IO(metadataOperations.runQuery(preCacheEntities)))
      _ <- entitiesCache.put(ent)
      attr <- IO.fromFuture(IO(metadataOperations.runQuery(preCacheAttributes)))
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


  def getPartialAttributes(tableName: String, columns: List[Attribute]): Future[List[Attribute]] = {
    metadataOperations.runQuery(getPartialAttributesQuery(tableName, columns))
  }

  private def getPartialAttributesQuery(tableName: String, columns: List[Attribute]): DBIO[List[Attribute]] = {
    DBIOAction.sequence {
      columns.map { column =>
        if (canQueryType(column.dataType) && isLowCardinality(column.cardinality)) {
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
  def getEntities: Future[List[Entity]] = {
    val result = for {
      entities <- entitiesCache.read
      res <- if (entities._1 + timeout.toMillis > now) {
        IO.pure(entities._2)
      } else {
        for {
          updatedEntities <- IO.fromFuture(IO(metadataOperations.runQuery(preCacheEntities)))
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
    for {
      attrOpt <- attributesCache.read.map(_.get(tableName)
        .flatMap(_._2.find(_.name == attribute))).unsafeToFuture()
      result <- attrOpt.map(attr => makeAttributesQuery(tableName, attr.name, withFilter)).toList.sequence
    } yield result.flatten
  }

  /** Makes list of possible string values of the attributes
    *
    * @param  tableName  name of the table from which we extract attributes
    * @param  column     name of the attribute
    * @param  withFilter optional parameter which can filter attributes
    * @return list of attributes
    * */
  private def makeAttributesQuery(tableName: String, column: String, withFilter: Option[String]): Future[List[String]] = {
    for {
      attribute <- attributesCache.read
        .map(_.get(tableName)
          .flatMap(_._2.find(_.name == column))).unsafeToFuture()
      if attribute.exists(attr => canQueryType(attr.dataType)) && isLowCardinality(attribute.flatMap(_.cardinality))
      distinctSelect <- withFilter match {
        case Some(filter) =>
          metadataOperations.runQuery(TezosDatabaseOperations.selectDistinctLike(tableName, column, ApiOperations.sanitizeForSql(filter)))
        case None =>
          metadataOperations.runQuery(TezosDatabaseOperations.selectDistinct(tableName, column))
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
  private def isLowCardinality(distinctCount: Option[Int]): Boolean = {
    // reasonable value which I thought of for now
    val maxCount = 1000
    distinctCount.getOrElse(maxCount) < maxCount
  }

  /** Checks if columns exist for the given table */
  def areFieldsValid(tableName: String, fields: Set[String]): Future[Boolean] = {
    attributesCache.read.map { cache =>
      cache.get(tableName).exists { case (_, attributes) =>
        fields.subsetOf(attributes.map(_.name).toSet)
      }
    }.unsafeToFuture()
  }

  /**
    * Extracts attributes in the DB for the given table name
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of attributes as a Future
    */
  def getTableAttributes(tableName: String): Future[Option[List[Attribute]]] = {
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

  /** Makes displayName out of name */
  private def makeDisplayName(name: String): String = {
    name.capitalize.replace("_", " ")
  }

}
