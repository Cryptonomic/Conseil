package tech.cryptonomic.conseil.common.cache

import cats.Monad
import cats.effect.Concurrent
import cats.effect.concurrent.{MVar, Ref}
import com.rklaehn.radixtree.RadixTree
import tech.cryptonomic.conseil.common.cache.MetadataCaching._
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity}

/** Companion object providing useful types related with MetadataCaching */
object MetadataCaching {
  type LastUpdated = Long

  /** Class representing entry in cache */
  final case class CacheEntry[A](lastUpdated: LastUpdated, value: A)

  /** Class representing key in cache */
  sealed trait CacheKey {
    def key: String
  }
  case class EntitiesCacheKey(platform: String, network: String) extends CacheKey {
    val key: String = s"$platform.$network"
  }
  case class AttributesCacheKey(platform: String, network: String, table: String) extends CacheKey {
    val key: String = s"$platform.$network.$table"
  }
  case class AttributeValuesCacheKey(platform: String, network: String, table: String, column: String)
      extends CacheKey {
    val key: String = s"$platform.$network.$table.$column"
  }

  type Cache[K <: CacheKey, A] = Map[K, CacheEntry[A]]
  type AttributesCache = Cache[AttributesCacheKey, List[Attribute]]
  type AttributeValuesCache = Cache[AttributeValuesCacheKey, RadixTree[String, String]]
  type EntitiesCache = Cache[EntitiesCacheKey, List[Entity]]

  /** Cache initialization statuses */
  sealed trait CachingStatus extends Product with Serializable

  case object NotStarted extends CachingStatus

  case object InProgress extends CachingStatus

  case object Finished extends CachingStatus

  import cats.implicits._

  /** Initializes metadata caching */
  def empty[F[_]](implicit concurrent: Concurrent[F]): F[MetadataCaching[F]] =
    for {
      cachingStatus <- Ref[F].of[CachingStatus](NotStarted)
      attributesCache <- MVar[F].empty[AttributesCache]
      entitiesCache <- MVar[F].empty[EntitiesCache]
      attributeValuesCache <- MVar[F].empty[AttributeValuesCache]
    } yield new MetadataCaching[F](cachingStatus, attributesCache, entitiesCache, attributeValuesCache)

  /** Returns current time in nanoseconds */
  def now: Long = System.nanoTime()
}

/** Class providing caching for metadata */
class MetadataCaching[F[_]](
    cachingStatus: Ref[F, CachingStatus],
    attributesCache: MVar[F, AttributesCache],
    entitiesCache: MVar[F, EntitiesCache],
    attributeValuesCache: MVar[F, AttributeValuesCache]
)(implicit monad: Monad[F]) {

  import cats.implicits._

  /** Reads current caching status */
  def getCachingStatus: F[CachingStatus] =
    cachingStatus.get

  /** Updates caching status */
  def updateCachingStatus(status: CachingStatus): F[Unit] =
    cachingStatus.update(_ => status)

  /** Reads entities from cache */
  def getEntities: EntitiesCacheKey => F[Option[CacheEntry[List[Entity]]]] =
    getFromCache(entitiesCache)

  /** Reads attributes from cache for given entity */
  def getAttributes: AttributesCacheKey => F[Option[CacheEntry[List[Attribute]]]] =
    getFromCache(attributesCache)

  /** Generic method for getting value from cache */
  private def getFromCache[K <: CacheKey, A](cache: MVar[F, Cache[K, A]])(key: K): F[Option[CacheEntry[A]]] =
    cache.read.map(_.get(key))

  /** Reads all attributes from cache */
  def getAllAttributes: F[AttributesCache] =
    attributesCache.read

  /** Reads all attributes from cache for given seq of keys */
  def getAllAttributesByKeys: Set[AttributesCacheKey] => F[AttributesCache] =
    keys => attributesCache.read.map(_.filterKeys(keys))

  /** Reads all entities from cache */
  def getAllEntities: F[EntitiesCache] =
    entitiesCache.read

  /** Reads attribute values from cache */
  def getAttributeValues(key: AttributeValuesCacheKey): F[Option[CacheEntry[RadixTree[String, String]]]] =
    getFromCache(attributeValuesCache)(key)

  /** Inserts entities into cache */
  def putEntities(key: EntitiesCacheKey, entities: List[Entity]): F[Unit] =
    putIntoCache(key, entities)(entitiesCache)

  /** Inserts all entities into cache */
  def putAllEntities: EntitiesCache => F[Unit] = updateVar(entitiesCache)

  /** Inserts all entities into empty cache */
  def fillEntitiesCache(entities: EntitiesCache): F[Boolean] =
    fillCache(entities)(entitiesCache)

  /** Generic method inserting all values into cache */
  private def fillCache[K <: CacheKey, A](values: Cache[K, A])(cache: MVar[F, Cache[K, A]]): F[Boolean] =
    cache.tryPut(values)

  /** Inserts attributes into cache */
  def putAttributes(key: AttributesCacheKey, attributes: List[Attribute]): F[Unit] =
    putIntoCache(key, attributes)(attributesCache)

  /** Inserts all attributes into cache */
  def putAllAttributes: AttributesCache => F[Unit] = updateVar(attributesCache)

  /** Inserts all attributes into empty cache */
  def fillAttributesCache(attributes: AttributesCache): F[Boolean] =
    fillCache(attributes)(attributesCache)

  /** Inserts attribute values into cache */
  def putAttributeValues(key: AttributeValuesCacheKey, radixTree: RadixTree[String, String]): F[Unit] =
    putIntoCache(key, radixTree)(attributeValuesCache)

  /** Generic method for putting value into cache */
  private def putIntoCache[K <: CacheKey, A](key: K, value: A)(cache: MVar[F, Cache[K, A]]): F[Unit] =
    for {
      ca <- cache.take
      _ <- cache.put(ca.updated(key, CacheEntry(now, value)))
    } yield ()

  /** Helper method for updating MVars */
  private def updateVar[T](mvar: MVar[F, T])(value: T): F[Unit] = mvar.take >> mvar.put(value)

  /** Inserts all attribute values into cache */
  def putAllAttributeValues: AttributeValuesCache => F[Unit] = updateVar(attributeValuesCache)

  /** Inserts all attribute values into empty cache */
  def fillAttributeValuesCache(attributeValues: AttributeValuesCache): F[Boolean] =
    fillCache(attributeValues)(attributeValuesCache)
}
