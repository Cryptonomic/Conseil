package tech.cryptonomic.conseil.tezos

import cats.Monad
import cats.effect.IO
import cats.effect.concurrent.MVar
import com.rklaehn.radixtree.RadixTree
import slick.dbio.{DBIO, DBIOAction}
import slick.jdbc.meta.{MColumn, MIndexInfo, MPrimaryKey, MTable}
import tech.cryptonomic.conseil.generic.chain.DataTypes.{AttributesValidationError, HighCardinalityAttribute, InvalidAttributeDataType, InvalidAttributeFilterLength}
import tech.cryptonomic.conseil.generic.chain.MetadataOperations
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.metadata.AttributeValuesCacheConfiguration
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/** Companion object providing apply method implementation */
object TezosPlatformDiscoveryOperations {

  type CacheTTL = Long

  type MetadataCache[A] = Map[String, (CacheTTL, A)]
  type AttributesCache = MetadataCache[List[Attribute]]
  //Map[String, (CacheTTL, List[Attribute])]
  type AttributeValuesCache = MetadataCache[RadixTree[String, String]]
  //Map[String, (CacheTTL, RadixTree[String, String])]
  type EntitiesCache = MetadataCache[List[Entity]] //Map[String, (CacheTTL, List[Entity])]


  def apply(metadataOperations: MetadataOperations,
    attributesCache: MVar[IO, AttributesCache],
    entitiesCache: MVar[IO, EntitiesCache],
    attributeValuesCache: MVar[IO, AttributeValuesCache],
    cacheOverrides: AttributeValuesCacheConfiguration,
    cacheTTL: FiniteDuration)
    (implicit executionContext: ExecutionContext): TezosPlatformDiscoveryOperations =
    new TezosPlatformDiscoveryOperations(metadataOperations: MetadataOperations, attributesCache, entitiesCache, attributeValuesCache, cacheOverrides, cacheTTL)
}

class Caching[F[_]](
  attributesCache: MVar[F, AttributesCache],
  entitiesCache: MVar[F, EntitiesCache],
  attributeValuesCache: MVar[F, AttributeValuesCache]
)(implicit monad: Monad[F]) {

  import cats.implicits._

  def getEntitiesFromCache(network: String): F[Option[(CacheTTL, List[Entity])]] =
    getFromCache(network)(entitiesCache)

  def getAttributesFromCache(entity: String): F[Option[(CacheTTL, List[Attribute])]] =
    getFromCache(entity)(attributesCache)

  def getAttributeValuesFromCache(entity:String, attribute: String): F[Option[(CacheTTL, RadixTree[String, String])]] =
    getFromCache(makeKey(entity, attribute))(attributeValuesCache)

  def putEntitiesIntoCache(network: String, entities: List[Entity]): F[Unit] =
    putIntoCache(network, entities)(entitiesCache)

  def putAllEntitiesIntoCache(entities: EntitiesCache): F[Unit] =
    putAllIntoCache(entities)(entitiesCache)

  def putAttributesIntoCache(entity: String, attributes: List[Attribute]): F[Unit] =
    putIntoCache(entity, attributes)(attributesCache)

  def putAllAttributesIntoCache(attributes: AttributesCache): F[Unit] =
    putAllIntoCache(attributes)(attributesCache)

  def putAttributeValuesIntoCache(entity: String, attribute: String, radixTree: RadixTree[String, String]): F[Unit] =
    putIntoCache(makeKey(entity, attribute), radixTree)(attributeValuesCache)

  def putAllAttributeValuesIntoCache(attributeValues: AttributeValuesCache): F[Unit] =
    putAllIntoCache(attributeValues)(attributeValuesCache)

  private def getFromCache[A](key: String)(cache: MVar[F, MetadataCache[A]]): F[Option[(CacheTTL, A)]] = {
    cache.read.map(_.get(key))
  }

  private def putAllIntoCache[A](values: MetadataCache[A])(cache: MVar[F, MetadataCache[A]]): F[Unit] = {
    for {
      _ <- cache.take
      _ <- cache.put(values)
    } yield ()
  }

  private def putIntoCache[A](key: String, value: A)(cache: MVar[F, MetadataCache[A]]): F[Unit] = {
    for {
      ca <- cache.take
      _ <- cache.put(ca.updated(key, (now, value)))
    } yield ()
  }

  /** Makes key out of table and column names */
  private def makeKey(table: String, column: String): String = s"$table.$column"
  /** Returns current time in milliseconds */
  private def now: Long = System.currentTimeMillis()
}

/** Class providing the implementation of the metadata calls with caching */
class TezosPlatformDiscoveryOperations(
  networkName: String,
  metadataOperations: MetadataOperations,
  caching: Caching[IO],
  cacheOverrides: AttributeValuesCacheConfiguration,
  cacheTTL: FiniteDuration)
  (implicit executionContext: ExecutionContext) {

  import cats.effect._
  import cats.implicits._


  var isCachingComplete: Boolean = false

  /** Method for initializing values of the cache */
  def init(): Future[Unit] = {
    val result = for {
      ent <- IO.fromFuture(IO(metadataOperations.runQuery(preCacheEntities)))
      _ <- caching.putAllEntitiesIntoCache(ent)
      attr <- IO.fromFuture(IO(metadataOperations.runQuery(preCacheAttributes)))
      _ <- caching.putAllAttributesIntoCache(attr)
      attrValues <- IO.fromFuture(IO(metadataOperations.runQuery(preCacheAttributeValues)))
      _ <- caching.putAllAttributeValuesIntoCache(attrValues)
    } yield ()

    result.unsafeToFuture()
  }

  /** Pre-caching attributes without cardinality */
  private def preCacheAttributes: DBIO[AttributesCache] = {
    val result = for {
      tables <- MTable.getTables(Some(""), Some("public"), Some(""), Some(Seq("TABLE")))
      columns <- getColumns(tables)
      indexes <- getIndexes(tables)
      primaryKeys <- getPrimaryKeys(tables)
    } yield {
      columns.map { cols =>
        cols.head.table.name -> (0L, cols.map { col =>
          makeAttributes(col, 0, primaryKeys, indexes)
        }.toList)
      }
    }
    result.map(_.toMap)
  }

  /** MTable query for getting columns from the DB */
  private def getColumns(tables: Vector[MTable]): DBIO[Vector[Vector[MColumn]]] = {
    DBIOAction.sequence {
      tables.map { table =>
        table.getColumns
      }
    }
  }

  /** MTable query for getting indexes from the DB */
  private def getIndexes(tables: Vector[MTable]): DBIO[Vector[Vector[MIndexInfo]]] = {
    DBIOAction.sequence {
      tables.map { table =>
        table.getIndexInfo()
      }
    }
  }

  /** MTable query for getting primary keys from the DB */
  private def getPrimaryKeys(tables: Vector[MTable]): DBIO[Vector[Vector[MPrimaryKey]]] = {
    DBIOAction.sequence {
      tables.map { table =>
        table.getPrimaryKeys
      }
    }
  }

  /** Makes attributes out of parameters */
  private def makeAttributes(col: MColumn, count: Int, primaryKeys: Vector[Vector[MPrimaryKey]], indexes: Vector[Vector[MIndexInfo]]): Attribute =
    Attribute(
      name = col.name,
      displayName = makeDisplayName(col.name),
      dataType = mapType(col.typeName),
      cardinality = if (canQueryType(mapType(col.typeName))) Some(count) else None,
      keyType = if (isIndex(col, indexes) || isKey(col, primaryKeys)) KeyType.UniqueKey else KeyType.NonKey,
      entity = col.table.name
    )

  /** Maps type from DB to type used in query */
  private def mapType(tpe: String): DataType = {
    tpe match {
      case "timestamp" => DataType.DateTime
      case "varchar" => DataType.String
      case "int4" | "serial" => DataType.Int
      case "numeric" => DataType.Decimal
      case "bool" => DataType.Boolean
      case _ => DataType.String
    }
  }

  /** Checks if given MColumn has primary key */
  private def isKey(column: MColumn, keys: Vector[Vector[MPrimaryKey]]): Boolean = {
    keys
      .filter(_.forall(_.table == column.table))
      .flatten
      .exists(_.column == column.name)
  }

  /** Checks if given MColumn has index */
  private def isIndex(column: MColumn, index: Vector[Vector[MIndexInfo]]): Boolean = {
    index
      .filter(_.forall(_.table == column.table))
      .flatten
      .exists(_.column.contains(column.name))
  }

  /** Makes displayName out of name */
  private def makeDisplayName(name: String): String = {
    name.capitalize.replace("_", " ")
  }

  private def preCacheAttributeValues: DBIO[AttributeValuesCache] = {
    DBIO.sequence {
      cacheOverrides.getAttributesToCache.map {
        case (table, column) =>
          TezosDatabaseOperations.selectDistinct(table, column).map { values =>
            val radixTree = RadixTree(values.map(x => x.toLowerCase -> x): _*)
            makeKey(table, column) -> (now -> radixTree)
          }
      }
    }.map(_.toMap)
  }

  /** Method querying slick metadata tables for entities */
  private def preCacheEntities: DBIO[EntitiesCache] = {
    val result = for {
      tables <- MTable.getTables(Some(""), Some("public"), Some(""), Some(Seq("TABLE")))
      counts <- getTablesCount(tables)
    } yield now -> (tables.map(_.name.name) zip counts).map {
      case (name, count) =>
        Entity(name, makeDisplayName(name), count)
    }.toList
    result.map(value => Map(networkName -> value))
  }

  /** Query for counting rows in the table */
  private def getTablesCount(tables: Vector[MTable]): DBIO[Vector[Int]] = {
    DBIOAction.sequence {
      tables.map { table =>
        TezosDatabaseOperations.countRows(table.name.name)
      }
    }
  }

  /** Checks if attribute is valid for given entity
    *
    * @param tableName  name of the table(entity) which needs to be checked
    * @param columnName name of the column(attribute) which needs to be checked
    * @return boolean which tells us if attribute is valid for given entity
    */
  def isAttributeValid(tableName: String, columnName: String): Future[Boolean] = {
    caching.getAttributesFromCache(tableName).map { attributesOpt =>
      attributesOpt.exists {
        case (_, attributes) =>
          attributes.exists(_.name == columnName)
      }
    }.unsafeToFuture()
  }

  /**
    * Extracts entities in the DB for the given network
    *
    * @return list of entities as a Future
    */
    def getEntities: Future[List[Entity]] = {
      val result = for {
        entities <- caching.getEntitiesFromCache(networkName)
        res <- if (entities.get._1 + cacheTTL.toMillis > now) {
          IO.pure(entities.get._2)
        } else {
          for {
            updatedEntities <- IO.fromFuture(IO(metadataOperations.runQuery(preCacheEntities)))
            _ <- caching.putAllEntitiesIntoCache(updatedEntities)
          } yield updatedEntities(networkName)._2
        }
      } yield res
      result.unsafeToFuture()
    }


  /** Makes list of possible string values of the attributes
    *
    * @param  tableName             name of the table from which we extract attributes
    * @param  column                name of the attribute
    * @param  withFilter            optional parameter which can filter attributes
    * @param  attributesCacheConfig optional parameter available when attribute needs to be cached
    * @return Either list of attributes or list of errors
    * */
  def listAttributeValues(tableName: String, column: String, withFilter: Option[String] = None, attributesCacheConfig: Option[AttributeCacheConfiguration] = None): Future[Either[List[AttributesValidationError], List[String]]] = {
    getTableAttributesWithoutUpdatingCache(tableName) map (_.flatMap(_.find(_.name == column))) flatMap { attrOpt =>
      val res = (attributesCacheConfig, withFilter) match {
        case (Some(AttributeCacheConfiguration(cached, minMatchLength, maxResultLength)), Some(attributeFilter)) if cached =>
          Either.cond(
            test = attributeFilter.length >= minMatchLength,
            right = getAttributeValuesFromCache(tableName, column, attributeFilter, maxResultLength),
            left = Future.successful(List(InvalidAttributeFilterLength(column, minMatchLength)))
          )

        case _ =>
          val invalidDataTypeValidationResult = if (!attrOpt.exists(attr => canQueryType(attr.dataType))) Some(InvalidAttributeDataType(column)) else None
          val highCardinalityValidationResult = if (!isLowCardinality(attrOpt.flatMap(_.cardinality))) Some(HighCardinalityAttribute(column)) else None
          val validationErrors = List(invalidDataTypeValidationResult, highCardinalityValidationResult).flatten
          Either.cond(
            test = validationErrors.isEmpty,
            right = attrOpt.map(attr => makeAttributesQuery(tableName, attr.name, withFilter)).toList.sequence.map(_.flatten),
            left = Future.successful(validationErrors)
          )
      }
      res.bisequence
    }
  }

  /** Checks the data types if cannot be queried by */
  private def canQueryType(dt: DataType): Boolean = {
    // values described in the ticket #183
    val cantQuery = Set(DataType.Date, DataType.DateTime, DataType.Int, DataType.LargeInt, DataType.Decimal)
    !cantQuery(dt)
  }

  /** Gets attribute values from cache and updates them if necessary */
  private def getAttributeValuesFromCache(tableName: String, columnName: String, attributeFilter: String, maxResultLength: Int): Future[List[String]] = {
    caching.getAttributeValuesFromCache(tableName, columnName).flatMap {
        case Some((last, radixTree)) if last + cacheTTL.toMillis > now =>
          IO.pure(radixTree.filterPrefix(attributeFilter.toLowerCase).values.take(maxResultLength).toList)
        case Some((_, oldRadixTree)) =>
          for {
            _ <- caching.putAttributeValuesIntoCache(tableName, columnName, oldRadixTree)
            attributeValues <- IO.fromFuture(IO(makeAttributesQuery(tableName, columnName, None)))
            radixTree = RadixTree(attributeValues.map(x => x.toLowerCase -> x): _*)
            _ <- caching.putAttributeValuesIntoCache(tableName, columnName, radixTree)
          } yield radixTree.filterPrefix(attributeFilter).values.take(maxResultLength).toList
        case None =>
          IO.pure(List.empty)
    }.unsafeToFuture()
  }

  /** Returns current time in milliseconds */
  private def now: Long = System.currentTimeMillis()

  /** Makes key out of table and column names */
  private def makeKey(table: String, column: String): String = s"$table.$column"

  /** Makes list of possible string values of the attributes
    *
    * @param  tableName  name of the table from which we extract attributes
    * @param  column     name of the attribute
    * @param  withFilter optional parameter which can filter attributes
    * @return list of attributes
    * */
  private def makeAttributesQuery(tableName: String, column: String, withFilter: Option[String]): Future[List[String]] = {
    withFilter match {
      case Some(filter) =>
        metadataOperations.runQuery(TezosDatabaseOperations.selectDistinctLike(tableName, column, ApiOperations.sanitizeForSql(filter)))
      case None =>
        metadataOperations.runQuery(TezosDatabaseOperations.selectDistinct(tableName, column))
    }
  }

  /**
    * Extracts attributes in the DB for the given table name without updating counts
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of attributes as a Future
    */
  def getTableAttributesWithoutUpdatingCache(tableName: String): Future[Option[List[Attribute]]] = {
    caching.getAttributesFromCache(tableName).map { attrOpt =>
      attrOpt.map {
        case (_, attr) => attr
      }
    }.unsafeToFuture()
  }

  /** Checks if cardinality of the column is not too high so it should not be queried */
  private def isLowCardinality(distinctCount: Option[Int]): Boolean = {
    // reasonable value which I thought of for now
    val maxCount = 1000
    distinctCount.getOrElse(maxCount) < maxCount
  }

  /**
    * Extracts attributes in the DB for the given table name
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of attributes as a Future
    */
  def getTableAttributes(tableName: String): Future[Option[List[Attribute]]] = {
    if (isCachingComplete) {
      attributesCache.read.flatMap { attributesMap =>
        attributesMap.get(tableName).map { case (last, attributes) =>
          if (last + cacheTTL.toMillis > now) {
            println("1st")
            IO.pure(attributes)
          } else {
            println("2nd")
            for {
              updatedAttributes <- IO.fromFuture(IO(getUpdatedAttributes(tableName, attributes)))
              _ <- attributesCache.take
              _ <- attributesCache.put(attributesMap.updated(tableName, now -> updatedAttributes))
            } yield updatedAttributes
          }
        }.sequence
      }.unsafeToFuture()
    } else {
      println("3rd")
      getTableAttributesWithoutUpdatingCache(tableName)
    }
  }

  /** Runs query and attributes with updated counts */
  private def getUpdatedAttributes(tableName: String, columns: List[Attribute]): Future[List[Attribute]] = {
    metadataOperations.runQuery(getUpdatedAttributesQuery(tableName, columns))
  }

  /** Query for returning partial attributes with updated counts */
  private def getUpdatedAttributesQuery(tableName: String, columns: List[Attribute]): DBIO[List[Attribute]] = {
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

  def initAttributesCount(): Future[List[Unit]] = {
    val result = for {
      entCache <- entitiesCache.read
      attributes <- attributesCache.read
      updatedAttributes <- IO.fromFuture {
        IO {
          metadataOperations.runQuery {
            DBIOAction.sequence {
              val (_, entities) = entCache
              for {
                entity <- entities
                (_, (_, attr)) <- attributes.filter(_._1 == entity.name)
              } yield getUpdatedAttributesQuery(entity.name, attr).map(entity.name -> _)
            }
          }
        }
      }
    } yield
      updatedAttributes.map {
        case (tableName, attr) =>
          attributesCache.take.flatMap { _ =>
            attributesCache.put(attributes.updated(tableName, now -> attr))
          }
      }.sequence

    result.flatten.unsafeToFuture()
  }

}
