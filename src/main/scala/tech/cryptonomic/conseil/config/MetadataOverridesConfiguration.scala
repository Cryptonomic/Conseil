package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.config.Types.{AttributeName, EntityName, NetworkName, PlatformName}
import tech.cryptonomic.conseil.metadata.{AttributePath, EmptyPath, EntityPath, NetworkPath, Path, PlatformPath}

object Types {
  type PlatformName = String
  type NetworkName = String
  type EntityName = String
  type AttributeName = String
}

case class MetadataOverridesConfiguration(metadataOverrides: Map[PlatformName, PlatformConfiguration]) {

  def isVisible(path: Path): Boolean = path match {
    case EmptyPath() => true
    case p: PlatformPath => platform(p).flatMap(_.visible).getOrElse(true)
    case p: NetworkPath => network(p).flatMap(_.visible).getOrElse(true) && isVisible(p.up)
    case p: EntityPath => entity(p).flatMap(_.visible).getOrElse(true) && isVisible(p.up)
    case p: AttributePath => attribute(p).flatMap(_.visible).getOrElse(true) && isVisible(p.up)
  }

  def platform(path: PlatformPath): Option[PlatformConfiguration] = metadataOverrides.get(path.platform)

  def network(path: NetworkPath): Option[NetworkConfiguration] = platform(path.up).flatMap(_.networks.get(path.network))

  def entity(path: EntityPath): Option[EntityConfiguration] = network(path.up).flatMap(_.entities.get(path.entity))

  def attribute(path: AttributePath): Option[AttributeConfiguration] = entity(path.up).flatMap(_.attributes.get(path.attribute))
}

case class PlatformConfiguration(displayName: Option[String], visible: Option[Boolean], networks: Map[NetworkName, NetworkConfiguration] = Map.empty)

case class NetworkConfiguration(displayName: Option[String], visible: Option[Boolean], entities: Map[EntityName, EntityConfiguration] = Map.empty)

case class EntityConfiguration(displayName: Option[String], visible: Option[Boolean], attributes: Map[AttributeName, AttributeConfiguration] = Map.empty)

case class AttributeConfiguration(displayName: Option[String], visible: Option[Boolean])