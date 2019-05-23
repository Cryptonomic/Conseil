package tech.cryptonomic.conseil.tezos

import cats.Monad
import cats.effect.concurrent.MVar
import com.rklaehn.radixtree.RadixTree
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity}
import tech.cryptonomic.conseil.tezos.MetadataCaching._


object MetadataCaching {
  type LastUpdated = Long

  type MetadataCache[A] = Map[String, (LastUpdated, A)]
  type AttributesCache = MetadataCache[List[Attribute]]
  type AttributeValuesCache = MetadataCache[RadixTree[String, String]]
  type EntitiesCache = MetadataCache[List[Entity]]


  sealed trait CachingStatus

  case object NotStarted extends CachingStatus

  case object InProgress extends CachingStatus

  case object Finished extends CachingStatus

}

class MetadataCaching[F[_]](
  cachingStatus: MVar[F, CachingStatus],
  attributesCache: MVar[F, AttributesCache],
  entitiesCache: MVar[F, EntitiesCache],
  attributeValuesCache: MVar[F, AttributeValuesCache]
)(implicit monad: Monad[F]) {

  import cats.implicits._

  /** Reads current caching status */
  def getCachingStatus: F[CachingStatus] =
    cachingStatus.read

  /** Updates caching status */
  def updateCachingStatus(status: CachingStatus): F[Unit] = {
    for {
      _ <- cachingStatus.take
      _ <- cachingStatus.put(status)
    } yield ()
  }

  /** Reads entities from cache */
  def getEntities(network: String): F[Option[(LastUpdated, List[Entity])]] =
    getFromCache(network)(entitiesCache)

  /** Reads attributes from cache for given entity */
  def getAttributes(entity: String): F[Option[(LastUpdated, List[Attribute])]] =
    getFromCache(entity)(attributesCache)

  /** Generic method for getting value from cache */
  private def getFromCache[A](key: String)(cache: MVar[F, MetadataCache[A]]): F[Option[(LastUpdated, A)]] = {
    cache.read.map(_.get(key))
  }

  /** Reads all attributes from cache */
  def getAllAttributes: F[MetadataCache[List[Attribute]]] =
    getAllFromCache(attributesCache)

  /** Generic method which reads reads all values from cache */
  private def getAllFromCache[A](cache: MVar[F, MetadataCache[A]]): F[MetadataCache[A]] =
    cache.read

  /** Reads attribute values from cache */
  def getAttributeValues(entity: String, attribute: String): F[Option[(LastUpdated, RadixTree[String, String])]] =
    getFromCache(makeKey(entity, attribute))(attributeValuesCache)

  /** Inserts entities into cache */
  def putEntities(network: String, entities: List[Entity]): F[Unit] =
    putIntoCache(network, entities)(entitiesCache)

  /** Inserts all entities into cache */
  def putAllEntities(entities: EntitiesCache): F[Unit] =
    putAllIntoCache(entities)(entitiesCache)

  /** Generic method inserting all values into cache */
  private def putAllIntoCache[A](values: MetadataCache[A])(cache: MVar[F, MetadataCache[A]]): F[Unit] = {
    for {
      _ <- cache.take
      _ <- cache.put(values)
    } yield ()
  }

  /** Inserts all entities into empty cache */
  def putAllEntitiesIntoEmptyCache(entities: EntitiesCache): F[Unit] =
    putAllIntoEmptyCache(entities)(entitiesCache)

  /** Inserts attributes into cache */
  def putAttributes(entity: String, attributes: List[Attribute]): F[Unit] =
    putIntoCache(entity, attributes)(attributesCache)

  /** Inserts all attributes into cache */
  def putAllAttributes(attributes: AttributesCache): F[Unit] =
    putAllIntoCache(attributes)(attributesCache)

  /** Inserts all attributes into empty cache */
  def putAllAttributesIntoEmptyCache(attributes: AttributesCache): F[Unit] =
    putAllIntoEmptyCache(attributes)(attributesCache)

  /** Inserts attribute values into cache */
  def putAttributeValues(entity: String, attribute: String, radixTree: RadixTree[String, String]): F[Unit] =
    putIntoCache(makeKey(entity, attribute), radixTree)(attributeValuesCache)

  /** Makes key out of table and column names */
  private def makeKey(table: String, column: String): String = s"$table.$column"

  /** Generic method for putting value into cache */
  private def putIntoCache[A](key: String, value: A)(cache: MVar[F, MetadataCache[A]]): F[Unit] = {
    for {
      ca <- cache.take
      _ <- cache.put(ca.updated(key, (now, value)))
    } yield ()
  }

  /** Returns current time in milliseconds */
  private def now: Long = System.currentTimeMillis()

  /** Inserts all attribute values into cache */
  def putAllAttributeValues(attributeValues: AttributeValuesCache): F[Unit] =
    putAllIntoCache(attributeValues)(attributeValuesCache)

  /** Inserts all attribute values into empty cache */
  def putAllAttributeValuesIntoEmptyCache(attributeValues: AttributeValuesCache): F[Unit] =
    putAllIntoEmptyCache(attributeValues)(attributeValuesCache)

  /** Generic method inserting all values into cache */
  private def putAllIntoEmptyCache[A](values: MetadataCache[A])(cache: MVar[F, MetadataCache[A]]): F[Unit] =
    cache.put(values)
}

