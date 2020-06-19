package tech.cryptonomic.conseil.common.generic.chain

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.AttributesValidationError
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{
  Attribute,
  AttributeCacheConfiguration,
  Entity
}
import tech.cryptonomic.conseil.common.metadata.{AttributePath, EntityPath, NetworkPath}

import scala.concurrent.Future

trait PlatformDiscoveryOperations {
  def getEntities(networkPath: NetworkPath): Future[List[Entity]]
  def getTableAttributes(entityPath: EntityPath): Future[Option[List[Attribute]]]
  def getTableAttributesWithoutUpdatingCache(entityPath: EntityPath): Future[Option[List[Attribute]]]
  def listAttributeValues(
      attributePath: AttributePath,
      withFilter: Option[String] = None,
      attributesCacheConfig: Option[AttributeCacheConfiguration] = None
  ): Future[Either[List[AttributesValidationError], List[String]]]
}
