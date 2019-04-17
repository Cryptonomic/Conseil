package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.config.Types.{AttributeName, EntityName, NetworkName, PlatformName}
import tech.cryptonomic.conseil.metadata.{AttributePath, EntityPath, NetworkPath, Path, PlatformPath}

object Types {
  type PlatformName = String
  type NetworkName = String
  type EntityName = String
  type AttributeName = String
}

case class MetadataOverridesConfiguration(metadataOverrides: Map[PlatformName, PlatformConfiguration]) {

  def isVisible(path: Path): Boolean = {
    def isPlatformVisible = platform(path).flatMap(_.visible).getOrElse(true)
    def isNetworkVisible = network(path).flatMap(_.visible).getOrElse(true)
    def isEntityVisible = entity(path).flatMap(_.visible).getOrElse(true)
    def isAttributeVisible = attribute(path).flatMap(_.visible).getOrElse(true)

    isPlatformVisible && isNetworkVisible && isEntityVisible && isAttributeVisible
  }

  def platform(path: Path): Option[PlatformConfiguration] = path match {
    case platformPath: PlatformPath => metadataOverrides.get(platformPath.platform)
  }

  def network(path: Path): Option[NetworkConfiguration] = path match {
    case networkPath: NetworkPath =>
      for {
        platform <- platform(path)
        network <- platform.networks.get(networkPath.network)
      } yield network
  }

  def entity(path: Path): Option[EntityConfiguration] = path match {
    case entityPath: EntityPath =>
      for {
        network <- network(path)
        entity <- network.entities.get(entityPath.entity)
      } yield entity
  }

  def attribute(path: Path): Option[AttributeConfiguration] = path match {
    case attributePath: AttributePath =>
      for {
        entity <- entity(path)
        attribute <- entity.attributes.get(attributePath.attribute)
      } yield attribute
  }
}

case class PlatformConfiguration(displayName: Option[String], visible: Option[Boolean], networks: Map[NetworkName, NetworkConfiguration])

case class NetworkConfiguration(displayName: Option[String], visible: Option[Boolean], entities: Map[EntityName, EntityConfiguration])

case class EntityConfiguration(displayName: Option[String], visible: Option[Boolean], attributes: Map[AttributeName, AttributeConfiguration])

case class AttributeConfiguration(displayName: Option[String], visible: Option[Boolean])
