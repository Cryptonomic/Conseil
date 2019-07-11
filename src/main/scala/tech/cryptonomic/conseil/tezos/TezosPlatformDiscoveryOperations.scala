package tech.cryptonomic.conseil.tezos

import cats.effect.{ContextShift, IO}
import com.rklaehn.radixtree.RadixTree
import slick.dbio.{DBIO, DBIOAction}
import slick.jdbc.meta.{MColumn, MIndexInfo, MPrimaryKey, MTable}
import tech.cryptonomic.conseil.generic.chain.DataTypes.{
  AttributesValidationError,
  HighCardinalityAttribute,
  InvalidAttributeDataType,
  InvalidAttributeFilterLength
}
import tech.cryptonomic.conseil.generic.chain.MetadataOperations
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.DataType.DataType
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.metadata.AttributeValuesCacheConfiguration
import tech.cryptonomic.conseil.metadata.repositories.MetadataRepository

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}

/** Companion object providing apply method implementation */
object TezosPlatformDiscoveryOperations {

  def apply(
      metadataOperations: MetadataOperations,
      metadataRepository: MetadataRepository[DBIO, String, String, String],
      caching: MetadataCaching[IO],
      cacheOverrides: AttributeValuesCacheConfiguration,
      cacheTTL: FiniteDuration
  )(implicit executionContext: ExecutionContext, contextShift: ContextShift[IO]): TezosPlatformDiscoveryOperations =
    new TezosPlatformDiscoveryOperations(metadataOperations, metadataRepository, caching, cacheOverrides, cacheTTL)

  /** Maps type from DB to type used in query */
  def mapType(tpe: String): DataType =
    tpe match {
      case "timestamp" => DataType.DateTime
      case "varchar" => DataType.String
      case "int4" | "int" | "serial" => DataType.Int
      case "numeric" => DataType.Decimal
      case "bool" => DataType.Boolean
      case "hash" => DataType.Hash
      case "accountAddress" => DataType.AccountAddress
      case "currency" => DataType.Currency
      case _ => DataType.String
    }
}

/** Class providing the implementation of the metadata calls with caching */
class TezosPlatformDiscoveryOperations(
    metadataOperations: MetadataOperations,
    metadataRepository: MetadataRepository[DBIO, String, String, String],
    caching: MetadataCaching[IO],
    cacheOverrides: AttributeValuesCacheConfiguration,
    cacheTTL: FiniteDuration,
    networkName: String = "notUsed"
)(implicit executionContext: ExecutionContext, contextShift: ContextShift[IO]) {

  import MetadataCaching._
  import cats.effect._
  import cats.implicits._

  /** Method for initializing values of the cache */
  def init(): Future[Unit] = {
    val entities = IO.fromFuture(IO(metadataOperations.runQuery(preCacheEntities)))
    val attributes = IO.fromFuture(IO(metadataOperations.runQuery(preCacheAttributes)))
    val attributeValues = IO.fromFuture(IO(metadataOperations.runQuery(preCacheAttributeValues)))

    (
      entities flatMap caching.fillEntitiesCache,
      attributes flatMap caching.fillAttributesCache,
      attributeValues flatMap caching.fillAttributeValuesCache
    ).tupled.void.unsafeToFuture
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
        CacheKey(cols.head.table.name) -> CacheEntry(0L, cols.map { col =>
          makeAttributes(col, 0, primaryKeys, indexes)
        }.toList)
      }
    }
    result.map(_.toMap)
  }

  /** MTable query for getting columns from the DB */
  private def getColumns(tables: Vector[MTable]): DBIO[Vector[Vector[MColumn]]] =
    DBIOAction.sequence {
      tables.map { table =>
        table.getColumns
      }
    }

  /** MTable query for getting indexes from the DB */
  private def getIndexes(tables: Vector[MTable]): DBIO[Vector[Vector[MIndexInfo]]] =
    DBIOAction.sequence {
      tables.map { table =>
        table.getIndexInfo()
      }
    }

  /** MTable query for getting primary keys from the DB */
  private def getPrimaryKeys(tables: Vector[MTable]): DBIO[Vector[Vector[MPrimaryKey]]] =
    DBIOAction.sequence {
      tables.map { table =>
        table.getPrimaryKeys
      }
    }

  /** Makes attributes out of parameters */
  private def makeAttributes(
      col: MColumn,
      count: Int,
      primaryKeys: Vector[Vector[MPrimaryKey]],
      indexes: Vector[Vector[MIndexInfo]]
  ): Attribute =
    Attribute(
      name = col.name,
      displayName = makeDisplayName(col.name),
      dataType = TezosPlatformDiscoveryOperations.mapType(col.typeName),
      cardinality = if (canQueryType(TezosPlatformDiscoveryOperations.mapType(col.typeName))) Some(count) else None,
      keyType = if (isIndex(col, indexes) || isKey(col, primaryKeys)) KeyType.UniqueKey else KeyType.NonKey,
      entity = col.table.name
    )

  /** Checks if given MColumn has primary key */
  private def isKey(column: MColumn, keys: Vector[Vector[MPrimaryKey]]): Boolean =
    keys
      .filter(_.forall(_.table == column.table))
      .flatten
      .exists(_.column == column.name)

  /** Checks if given MColumn has index */
  private def isIndex(column: MColumn, index: Vector[Vector[MIndexInfo]]): Boolean =
    index
      .filter(_.forall(_.table == column.table))
      .flatten
      .exists(_.column.contains(column.name))

  private def preCacheAttributeValues: DBIO[AttributeValuesCache] =
    DBIO.sequence {
      cacheOverrides.getAttributesToCache.map {
        case (table, column) =>
          metadataRepository.selectDistinct(table, column).map { values =>
            val radixTree = RadixTree(values.map(x => x.toLowerCase -> x): _*)
            CacheKey(makeKey(table, column)) -> CacheEntry(now, radixTree)
          }
      }
    }.map(_.toMap)

  /** Checks if attribute is valid for given entity
    *
    * @param tableName  name of the table(entity) which needs to be checked
    * @param columnName name of the column(attribute) which needs to be checked
    * @return boolean which tells us if attribute is valid for given entity
    */
  def isAttributeValid(tableName: String, columnName: String): Future[Boolean] =
    caching
      .getAttributes(tableName)
      .map { attributesOpt =>
        attributesOpt.exists {
          case CacheEntry(_, attributes) =>
            attributes.exists(_.name == columnName)
        }
      }
      .unsafeToFuture()

  /**
    * Extracts entities in the DB for the given network
    *
    * @return list of entities as a Future
    */
  def getEntities: Future[List[Entity]] = {
    val result = for {
      entities <- caching.getEntities(networkName)
      res <- entities.traverse {
        case CacheEntry(last, ent) =>
          if (!cacheExpired(last)) {
            IO.pure(ent)
          } else {
            (for {
              _ <- caching.putEntities(networkName, ent)
              _ <- contextShift.shift
              updatedEntities <- IO.fromFuture(IO(metadataOperations.runQuery(preCacheEntities)))
              _ <- caching.putAllEntities(updatedEntities)
            } yield ()).unsafeRunAsyncAndForget()
            IO.pure(ent)
          }
      }
    } yield res.toList.flatten
    result.unsafeToFuture()
  }

  /** Method querying slick metadata tables for entities */
  private def preCacheEntities: DBIO[EntitiesCache] = {
    val result = for {
      tables <- MTable.getTables(Some(""), Some("public"), Some(""), Some(Seq("TABLE")))
      counts <- getTablesCount(tables)
    } yield
      now -> (tables.map(_.name.name) zip counts).map {
            case (name, count) =>
              Entity(name, makeDisplayName(name), count)
          }.toList
    result.map(value => Map(CacheKey(networkName) -> CacheEntry(value._1, value._2)))
  }

  /** Makes displayName out of name */
  private def makeDisplayName(name: String): String =
    name.capitalize.replace("_", " ")

  /** Query for counting rows in the table */
  private def getTablesCount(tables: Vector[MTable]): DBIO[Vector[Int]] =
    DBIOAction.sequence {
      tables.map { table =>
        metadataRepository.countRows(table.name.name)
      }
    }

  /** Makes list of possible string values of the attributes
    *
    * @param  tableName             name of the table from which we extract attributes
    * @param  column                name of the attribute
    * @param  withFilter            optional parameter which can filter attributes
    * @param  attributesCacheConfig optional parameter available when attribute needs to be cached
    * @return Either list of attributes or list of errors
    * */
  def listAttributeValues(
      tableName: String,
      column: String,
      withFilter: Option[String] = None,
      attributesCacheConfig: Option[AttributeCacheConfiguration] = None
  ): Future[Either[List[AttributesValidationError], List[String]]] =
    getTableAttributesWithoutUpdatingCache(tableName) map (_.flatMap(_.find(_.name == column))) flatMap { attrOpt =>
        val res = (attributesCacheConfig, withFilter) match {
          case (Some(AttributeCacheConfiguration(cached, minMatchLength, maxResultLength)), Some(attributeFilter))
              if cached =>
            Either.cond(
              test = attributeFilter.length >= minMatchLength,
              right = getAttributeValuesFromCache(tableName, column, attributeFilter, maxResultLength),
              left = Future.successful(List(InvalidAttributeFilterLength(column, minMatchLength)))
            )

          case _ =>
            val invalidDataTypeValidationResult =
              if (!attrOpt.exists(attr => canQueryType(attr.dataType))) Some(InvalidAttributeDataType(column)) else None
            val highCardinalityValidationResult =
              if (!isLowCardinality(attrOpt.flatMap(_.cardinality))) Some(HighCardinalityAttribute(column)) else None
            val validationErrors = List(invalidDataTypeValidationResult, highCardinalityValidationResult).flatten
            Either.cond(
              test = validationErrors.isEmpty,
              right = attrOpt
                .map(attr => makeAttributesQuery(tableName, attr.name, withFilter))
                .toList
                .sequence
                .map(_.flatten),
              left = Future.successful(validationErrors)
            )
        }
        res.bisequence
      }

  /** Gets attribute values from cache and updates them if necessary */
  private def getAttributeValuesFromCache(
      tableName: String,
      columnName: String,
      attributeFilter: String,
      maxResultLength: Int
  ): Future[List[String]] =
    caching
      .getAttributeValues(tableName, columnName)
      .flatMap {
        case Some(CacheEntry(last, radixTree)) if !cacheExpired(last) =>
          IO.pure(radixTree.filterPrefix(attributeFilter.toLowerCase).values.take(maxResultLength).toList)
        case Some(CacheEntry(_, oldRadixTree)) =>
          (for {
            _ <- caching.putAttributeValues(tableName, columnName, oldRadixTree)
            _ <- contextShift.shift
            attributeValues <- IO.fromFuture(IO(makeAttributesQuery(tableName, columnName, None)))
            radixTree = RadixTree(attributeValues.map(x => x.toLowerCase -> x): _*)
            _ <- caching.putAttributeValues(tableName, columnName, radixTree)
          } yield ()).unsafeRunAsyncAndForget()
          IO.pure(oldRadixTree.filterPrefix(attributeFilter).values.take(maxResultLength).toList)
        case None =>
          IO.pure(List.empty)
      }
      .unsafeToFuture()

  /** Makes list of possible string values of the attributes
    *
    * @param  tableName  name of the table from which we extract attributes
    * @param  column     name of the attribute
    * @param  withFilter optional parameter which can filter attributes
    * @return list of attributes
    * */
  private def makeAttributesQuery(tableName: String, column: String, withFilter: Option[String]): Future[List[String]] =
    withFilter match {
      case Some(filter) =>
        metadataOperations.runQuery(
          metadataRepository.selectDistinctLike(tableName, column, filter)
        )
      case None =>
        metadataOperations.runQuery(metadataRepository.selectDistinct(tableName, column))
    }

  /**
    * Extracts attributes in the DB for the given table name
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of attributes as a Future
    */
  def getTableAttributes(tableName: String): Future[Option[List[Attribute]]] =
    caching.getCachingStatus.map { status =>
      if (status == Finished) {
        caching
          .getAttributes(tableName)
          .flatMap { attributesOpt =>
            attributesOpt.map {
              case CacheEntry(last, attributes) =>
                if (!cacheExpired(last)) {
                  IO.pure(attributes)
                } else {
                  (for {
                    _ <- caching.putAttributes(tableName, attributes)
                    _ <- contextShift.shift
                    updatedAttributes <- IO.fromFuture(IO(getUpdatedAttributes(tableName, attributes)))
                    _ <- caching.putAttributes(tableName, updatedAttributes)
                  } yield ()).unsafeRunAsyncAndForget()
                  IO.pure(attributes)
                }
            }.sequence
          }
          .unsafeToFuture()
      } else {
        // if caching is not finished cardinality should be set to None
        getTableAttributesWithoutUpdatingCache(tableName).map(_.map(_.map(_.copy(cardinality = None))))
      }
    }.unsafeToFuture().flatten

  /** Checks if cache expired */
  private def cacheExpired(lastUpdated: LastUpdated): Boolean =
    lastUpdated + cacheTTL.toNanos < now

  /**
    * Extracts attributes in the DB for the given table name without updating counts
    *
    * @param  tableName name of the table from which we extract attributes
    * @return list of attributes as a Future
    */
  def getTableAttributesWithoutUpdatingCache(tableName: String): Future[Option[List[Attribute]]] =
    caching
      .getAttributes(tableName)
      .map { attrOpt =>
        attrOpt.map(_.value)
      }
      .unsafeToFuture()

  /** Runs query and attributes with updated counts */
  private def getUpdatedAttributes(tableName: String, columns: List[Attribute]): Future[List[Attribute]] =
    metadataOperations.runQuery(getUpdatedAttributesQuery(tableName, columns))

  /** Query for returning partial attributes with updated counts */
  private def getUpdatedAttributesQuery(tableName: String, columns: List[Attribute]): DBIO[List[Attribute]] =
    DBIOAction.sequence {
      columns.map { column =>
        if (canQueryType(column.dataType) && isLowCardinality(column.cardinality)) {
          metadataRepository.countDistinct(tableName, column.name).map { count =>
            column.copy(cardinality = Some(count))
          }
        } else {
          DBIOAction.successful(column)
        }
      }
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

  /** Starts initialization of attributes count cache */
  def initAttributesCache: Future[Unit] = {
    val result = for {
      _ <- caching.updateCachingStatus(InProgress)
      entCache <- caching.getEntities(networkName)
      attributes <- caching.getAllAttributes
      _ <- contextShift.shift
      updatedAttributes <- entCache.fold(IO(Map.empty[String, List[Attribute]]))(
        entC => getAllUpdatedAttributes(entC.value, attributes)
      )
    } yield
      updatedAttributes.map {
        case (tableName, attr) =>
          caching.putAttributes(tableName, attr)
      }

    result.flatMap(_.toList.sequence) >> caching.updateCachingStatus(Finished)
  }.unsafeToFuture()

  /** Helper method for updating */
  private def getAllUpdatedAttributes(
      entities: List[Entity],
      attributes: Cache[List[Attribute]]
  ): IO[Map[String, List[Attribute]]] = {
    val queries = attributes.filterKeys {
      case CacheKey(key) => entities.map(_.name).toSet(key)
    }.mapValues {
      case CacheEntry(_, attrs) => attrs
    }.map {
      case (entityName, attrs) => getUpdatedAttributesQuery(entityName.key, attrs).map(entityName.key -> _)
    }
    val action = DBIO.sequence(queries)
    IO.fromFuture(IO(metadataOperations.runQuery(action))).map(_.toMap)
  }

}
