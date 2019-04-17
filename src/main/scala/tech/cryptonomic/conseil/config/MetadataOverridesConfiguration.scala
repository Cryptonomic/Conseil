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

  def isVisible(path: Path): Boolean = {
    path match {
      case EmptyPath() => true
      case p: PlatformPath => platform(p).flatMap(_.visible).getOrElse(true)
      case p: NetworkPath => network(p).flatMap(_.visible).getOrElse(true) && isVisible(p.up)
      case p: EntityPath => entity(p).flatMap(_.visible).getOrElse(true) && isVisible(p.up)
      case p: AttributePath => attribute(p).flatMap(_.visible).getOrElse(true) && isVisible(p.up)
    }
  }

  def platform(path: PlatformPath): Option[PlatformConfiguration] = metadataOverrides.get(path.platform)

  def network(path: NetworkPath): Option[NetworkConfiguration] = for {
    platform <- platform(path.up)
    network <- platform.networks.get(path.network)
  } yield network

  def entity(path: EntityPath): Option[EntityConfiguration] =
    for {
      network <- network(path.up)
      entity <- network.entities.get(path.entity)
    } yield entity

  def attribute(path: AttributePath): Option[AttributeConfiguration] =
    for {
      entity <- entity(path.up)
      attribute <- entity.attributes.get(path.attribute)
    } yield attribute
}

case class PlatformConfiguration(displayName: Option[String], visible: Option[Boolean], networks: Map[NetworkName, NetworkConfiguration])

case class NetworkConfiguration(displayName: Option[String], visible: Option[Boolean], entities: Map[EntityName, EntityConfiguration])

case class EntityConfiguration(displayName: Option[String], visible: Option[Boolean], attributes: Map[AttributeName, AttributeConfiguration])

case class AttributeConfiguration(displayName: Option[String], visible: Option[Boolean])