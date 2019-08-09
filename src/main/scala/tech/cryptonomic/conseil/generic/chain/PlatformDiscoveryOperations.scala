package tech.cryptonomic.conseil.generic.chain

import tech.cryptonomic.conseil.generic.chain.DataTypes.AttributesValidationError
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, AttributeCacheConfiguration, Entity}

import scala.concurrent.Future

trait PlatformDiscoveryOperations {
  def getEntities: Future[List[Entity]]
  def getTableAttributes(tableName: String): Future[Option[List[Attribute]]]
  def getTableAttributesWithoutUpdatingCache(tableName: String): Future[Option[List[Attribute]]]
  def listAttributeValues(
      tableName: String,
      column: String,
      withFilter: Option[String] = None,
      attributesCacheConfig: Option[AttributeCacheConfiguration] = None
  ): Future[Either[List[AttributesValidationError], List[String]]]
}
