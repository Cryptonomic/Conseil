package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.config.Types.{AttributeName, EntityName, NetworkName, PlatformName}
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{AttributeCacheConfiguration, Network}
import tech.cryptonomic.conseil.metadata._

object Types {
  type PlatformName = String
  type NetworkName = String
  type EntityName = String
  type AttributeName = String
}

// metadata configuration
case class MetadataConfiguration(metadataConfiguration: Map[PlatformName, PlatformConfiguration]) {

  // determines if a given path is visible
  def isVisible(path: Path): Boolean = path match {
    case EmptyPath() => true
    case p: PlatformPath => platform(p).flatMap(_.visible).getOrElse(false)
    case p: NetworkPath => network(p).flatMap(_.visible).getOrElse(false) && isVisible(p.up)
    case p: EntityPath => entity(p).flatMap(_.visible).getOrElse(false) && isVisible(p.up)
    case p: AttributePath => attribute(p).flatMap(_.visible).getOrElse(false) && isVisible(p.up)
  }

  // fetches platform based on a given path
  def platform(path: PlatformPath): Option[PlatformConfiguration] = metadataConfiguration.get(path.platform)

  // fetches network based on a given path
  def network(path: NetworkPath): Option[NetworkConfiguration] = platform(path.up).flatMap(_.networks.get(path.network))

  // fetches entity based on a given path
  def entity(path: EntityPath): Option[EntityConfiguration] = network(path.up).flatMap(_.entities.get(path.entity))

  // fetches attribute based on a given path
  def attribute(path: AttributePath): Option[AttributeConfiguration] =
    entity(path.up).flatMap(_.attributes.get(path.attribute))

  // fetches all platforms
  def allPlatforms: Map[PlatformPath, PlatformConfiguration] = metadataConfiguration.map {
    case (platformName, platformConfiguration) => PlatformPath(platformName) -> platformConfiguration
  }

  // fetches networks based on a given path
  def networks(path: PlatformPath): Map[NetworkPath, NetworkConfiguration] = allNetworks.collect {
    case (networkPath @ NetworkPath(_, `path`), networkConfiguration) => networkPath -> networkConfiguration
  }

  // fetches all networks
  def allNetworks: Map[NetworkPath, NetworkConfiguration] = allPlatforms.flatMap {
    case (platformPath, platformConfiguration) =>
      platformConfiguration.networks.map {
        case (networkName, networkConfiguration) => (platformPath.addLevel(networkName), networkConfiguration)
      }
  }

  def entities(networkPath: NetworkPath): Map[EntityPath, EntityConfiguration] = allEntities.collect {
    case (entityPath @ EntityPath(_, `networkPath`), entityConfiguration) => entityPath -> entityConfiguration
  }

  // fetches all entities
  def allEntities: Map[EntityPath, EntityConfiguration] = allNetworks.flatMap {
    case (networkPath, networkConfiguration) =>
      networkConfiguration.entities.map {
        case (entityName, entityConfiguration) => (networkPath.addLevel(entityName), entityConfiguration)
      }
  }

  def attributes(path: EntityPath): Map[AttributePath, AttributeConfiguration] = allAttributes.collect {
    case (attributePath @ AttributePath(_, `path`), attributeConfiguration) => attributePath -> attributeConfiguration
  }

  // fetches all attributes
  def allAttributes: Map[AttributePath, AttributeConfiguration] = allEntities.flatMap {
    case (entityPath, entityConfiguration) =>
      entityConfiguration.attributes.map {
        case (attributeName, attributeConfiguration) => (entityPath.addLevel(attributeName), attributeConfiguration)
      }
  }

}

// configuration for platform
case class PlatformConfiguration(
    displayName: Option[String],
    visible: Option[Boolean],
    description: Option[String] = None,
    networks: Map[NetworkName, NetworkConfiguration] = Map.empty
)

// configuration for network
case class NetworkConfiguration(
    displayName: Option[String],
    visible: Option[Boolean],
    description: Option[String] = None,
    entities: Map[EntityName, EntityConfiguration] = Map.empty
)

// configuration for entity
case class EntityConfiguration(
    displayName: Option[String],
    displayNamePlural: Option[String],
    visible: Option[Boolean],
    description: Option[String] = None,
    attributes: Map[AttributeName, AttributeConfiguration] = Map.empty
)

// configuration for attribute
case class AttributeConfiguration(
    displayName: Option[String],
    visible: Option[Boolean],
    description: Option[String] = None,
    placeholder: Option[String] = None,
    scale: Option[Int] = None,
    dataType: Option[String] = None,
    dataFormat: Option[String] = None,
    valueMap: Option[Map[String, String]] = None,
    reference: Option[Map[String, String]] = None,
    cacheConfig: Option[AttributeCacheConfiguration] = None,
    displayPriority: Option[Int] = None,
    displayOrder: Option[Int] = None,
    currencySymbol: Option[String] = None,
    currencySymbolCode: Option[Int] = None,
    cardinalityHint: Option[Int] = None
)
