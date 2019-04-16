package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.config.Types.{AttributeName, EntityName, NetworkName, PlatformName}

object Types {
  type PlatformName = String
  type NetworkName = String
  type EntityName = String
  type AttributeName = String
}

case class OverridesPath(platform: Option[PlatformName],
                         network: Option[NetworkName] = None,
                         entity: Option[EntityName] = None,
                         attribute: Option[AttributeName] = None)

object OverridesPath {
  def apply(platform: String) = new OverridesPath(platform = Some(platform))
  def apply(platform: String, network: String) = new OverridesPath(platform = Some(platform), network = Some(network))
  def apply(platform: String, network: String, entity: String) = new OverridesPath(platform = Some(platform), network = Some(network), entity = Some(entity))
  def apply(platform: String, network: String, entity: String, attribute: String) = new OverridesPath(platform = Some(platform), network = Some(network), entity = Some(entity), attribute = Some(attribute))
}

case class MetadataOverridesConfiguration(metadataOverrides: Map[PlatformName, PlatformConfiguration]) {

  def isVisible(path: OverridesPath): Boolean = {
    def isPlatformVisible = platform(path).flatMap(_.visible).getOrElse(true)
    def isNetworkVisible = network(path).flatMap(_.visible).getOrElse(true)
    def isEntityVisible = entity(path).flatMap(_.visible).getOrElse(true)
    def isAttributeVisible = attribute(path).flatMap(_.visible).getOrElse(true)

    isPlatformVisible && isNetworkVisible && isEntityVisible && isAttributeVisible
  }

  def platform(path: OverridesPath): Option[PlatformConfiguration] = {
    for {
      platformName <- path.platform
      platform <- metadataOverrides.get(platformName)
    } yield platform
  }

  def network(path: OverridesPath): Option[NetworkConfiguration] = {
    for {
      platform <- platform(path)
      networkName <- path.network
      network <- platform.networks.get(networkName)
    } yield network
  }

  def entity(path: OverridesPath): Option[EntityConfiguration] = {
    for {
      network <- network(path)
      entityName <- path.entity
      entity <- network.entities.get(entityName)
    } yield entity
  }

  def attribute(path: OverridesPath): Option[AttributeConfiguration] = {
    for {
      entity <- entity(path)
      attributeName <- path.attribute
      attribute <- entity.attributes.get(attributeName)
    } yield attribute
  }
}

case class PlatformConfiguration(displayName: Option[String], visible: Option[Boolean] = Some(true), networks: Map[NetworkName, NetworkConfiguration])

case class NetworkConfiguration(displayName: Option[String], visible: Option[Boolean] = Some(true), entities: Map[EntityName, EntityConfiguration])

case class EntityConfiguration(displayName: Option[String], visible: Option[Boolean] = Some(true), attributes: Map[AttributeName, AttributeConfiguration])

case class AttributeConfiguration(displayName: Option[String], visible: Option[Boolean] = Some(true))