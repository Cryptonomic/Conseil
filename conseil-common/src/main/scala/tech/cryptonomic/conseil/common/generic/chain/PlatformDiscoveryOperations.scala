package tech.cryptonomic.conseil.common.generic.chain

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.AttributesValidationError
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{
  Attribute,
  AttributeCacheConfiguration,
  Entity
}
import tech.cryptonomic.conseil.common.metadata.{AttributePath, EntityPath, NetworkPath}

import cats.effect.IO

trait PlatformDiscoveryOperations {
  def getEntities(networkPath: NetworkPath): IO[List[Entity]]
  def getTableAttributes(entityPath: EntityPath): IO[List[Attribute]]
  def getTableAttributesWithoutUpdatingCache(entityPath: EntityPath): IO[List[Attribute]]
  def listAttributeValues(
      attributePath: AttributePath,
      withFilter: Option[String] = None,
      attributesCacheConfig: Option[AttributeCacheConfiguration] = None
  ): IO[Either[List[AttributesValidationError], List[String]]]
}
