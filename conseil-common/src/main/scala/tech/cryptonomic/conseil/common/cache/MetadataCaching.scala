package tech.cryptonomic.conseil.common.cache

import cats.Monad
import cats.effect.{Concurrent, Ref}
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
  def empty[F[_]: Concurrent]: F[MetadataCaching[F]] =
    for {
      cachingStatus <- Ref[F].of[CachingStatus](NotStarted)
      attributesCache <- Ref[F]
        .of[AttributesCache](Map.empty[AttributesCacheKey, List[Attribute]].asInstanceOf[AttributesCache])
      entitiesCache <- Ref[F].of[EntitiesCache](Map.empty[EntitiesCacheKey, List[Entity]].asInstanceOf[EntitiesCache])
      attributeValuesCache <- Ref[F].of[AttributeValuesCache](
        Map.empty[AttributeValuesCacheKey, RadixTree[String, String]].asInstanceOf[AttributeValuesCache]
      )
    } yield new MetadataCaching[F](cachingStatus, attributesCache, entitiesCache, attributeValuesCache)

  /** Returns current time in nanoseconds */
  def now: Long = System.nanoTime()
}

/** Class providing caching for metadata */
class MetadataCaching[F[_]: Monad](
    cachingStatus: Ref[F, CachingStatus],
    attributesCache: Ref[F, AttributesCache],
    entitiesCache: Ref[F, EntitiesCache],
    attributeValuesCache: Ref[F, AttributeValuesCache]
) {

  import cats.implicits._

  /** Reads current caching status */
  def getCachingStatus: F[CachingStatus] = cachingStatus.get

  /** Updates caching status */
  def updateCachingStatus(status: CachingStatus): F[Unit] = cachingStatus.update(_ => status)

  /** Reads entities from cache */
  def getEntities: EntitiesCacheKey => F[Option[CacheEntry[List[Entity]]]] = getFromCache(entitiesCache)

  /** Reads attributes from cache for given entity */
  def getAttributes: AttributesCacheKey => F[Option[CacheEntry[List[Attribute]]]] = getFromCache(attributesCache)

  /** Generic method for getting value from cache */
  private def getFromCache[K <: CacheKey, A](cache: Ref[F, Cache[K, A]])(key: K): F[Option[CacheEntry[A]]] =
    cache.get.map(_.get(key))

  /** Reads all attributes from cache */
  def getAllAttributes: F[AttributesCache] = attributesCache.get

  /** Reads all attributes from cache for given seq of keys */
  def getAllAttributesByKeys: Set[AttributesCacheKey] => F[AttributesCache] =
    keys => attributesCache.get.map(_.filterKeys(keys))

  /** Reads all entities from cache */
  def getAllEntities: F[EntitiesCache] = entitiesCache.get

  /** Reads attribute values from cache */
  def getAttributeValues(key: AttributeValuesCacheKey): F[Option[CacheEntry[RadixTree[String, String]]]] =
    getFromCache(attributeValuesCache)(key)

  /** Inserts entities into cache */
  def putEntities(key: EntitiesCacheKey, entities: List[Entity]): F[Boolean] =
    putIntoCache(key, entities)(entitiesCache)

  /** Inserts all entities into cache */
  def putAllEntities: EntitiesCache => F[Unit] = updateVar(entitiesCache)

  /** Inserts all entities into empty cache */
  def fillEntitiesCache(entities: EntitiesCache): F[Boolean] = fillCache(entities)(entitiesCache)

  /** Generic method inserting all values into cache */
  private def fillCache[K <: CacheKey, A](values: Cache[K, A])(cache: Ref[F, Cache[K, A]]): F[Boolean] =
    cache.tryUpdate(currCache => currCache ++ values) // .void

  /** Inserts attributes into cache */
  def putAttributes(key: AttributesCacheKey, attributes: List[Attribute]): F[Boolean] =
    putIntoCache(key, attributes)(attributesCache)

  /** Inserts all attributes into cache */
  def putAllAttributes: AttributesCache => F[Unit] = updateVar(attributesCache)

  /** Inserts all attributes into empty cache */
  def fillAttributesCache(attributes: AttributesCache): F[Boolean] = fillCache(attributes)(attributesCache)

  /** Inserts attribute values into cache */
  def putAttributeValues(key: AttributeValuesCacheKey, radixTree: RadixTree[String, String]): F[Boolean] =
    putIntoCache(key, radixTree)(attributeValuesCache)

  /** Generic method for putting value into cache */
  private def putIntoCache[K <: CacheKey, A](key: K, value: A)(cache: Ref[F, Cache[K, A]]): F[Boolean] =
    cache.tryUpdate(_.updated(key, CacheEntry(now, value)))

  /** Helper method for updating Refs */
  private def updateVar[T](ref: Ref[F, T])(value: T): F[Unit] = ref.get >> ref.set(value)

  /** Inserts all attribute values into cache */
  def putAllAttributeValues: AttributeValuesCache => F[Unit] = updateVar(attributeValuesCache)

  /** Inserts all attribute values into empty cache */
  def fillAttributeValuesCache(attributeValues: AttributeValuesCache): F[Boolean] =
    fillCache(attributeValues)(attributeValuesCache)
}
