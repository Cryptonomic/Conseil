package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.config.Types.{AttributeName, EntityName, NetworkName, PlatformName}
import tech.cryptonomic.conseil.metadata.{AttributePath, EmptyPath, EntityPath, NetworkPath, Path, PlatformPath}

object Types {
  type PlatformName = String
  type NetworkName = String
  type EntityName = String
  type AttributeName = String
}

// metadata overrides configuration
case class MetadataOverridesConfiguration(metadataOverrides: Map[PlatformName, PlatformConfiguration]) {

  // determines if a given path is visible
  def isVisible(path: Path): Boolean = path match {
    case EmptyPath() => true
    case p: PlatformPath => platform(p).flatMap(_.visible).getOrElse(false)
    case p: NetworkPath => network(p).flatMap(_.visible).getOrElse(false) && isVisible(p.up)
    case p: EntityPath => entity(p).flatMap(_.visible).getOrElse(false) && isVisible(p.up)
    case p: AttributePath => attribute(p).flatMap(_.visible).getOrElse(false) && isVisible(p.up)
  }

  // fetches platform based on a given path
  def platform(path: PlatformPath): Option[PlatformConfiguration] = metadataOverrides.get(path.platform)

  // fetches network based on a given path
  def network(path: NetworkPath): Option[NetworkConfiguration] = platform(path.up).flatMap(_.networks.get(path.network))

  // fetches entity based on a given path
  def entity(path: EntityPath): Option[EntityConfiguration] = network(path.up).flatMap(_.entities.get(path.entity))

  // fetches attribute based on a given path
  def attribute(path: AttributePath): Option[AttributeConfiguration] = entity(path.up).flatMap(_.attributes.get(path.attribute))
}

// configuration for platform
case class PlatformConfiguration(displayName: Option[String], visible: Option[Boolean], description: Option[String] = None, networks: Map[NetworkName, NetworkConfiguration] = Map.empty)

// configuration for network
case class NetworkConfiguration(displayName: Option[String], visible: Option[Boolean], description: Option[String] = None, entities: Map[EntityName, EntityConfiguration] = Map.empty)

// configuration for entity
case class EntityConfiguration(displayName: Option[String], visible: Option[Boolean], description: Option[String] = None, attributes: Map[AttributeName, AttributeConfiguration] = Map.empty)

// configuration for attribute
case class AttributeConfiguration(displayName: Option[String], visible: Option[Boolean], description: Option[String] = None, placeholder: Option[String] = None, dataformat: Option[String] = None)
