package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.MetadataOverridesConfiguration
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity, Network, Platform}
import tech.cryptonomic.conseil.util.OptionUtil.when

object Data {

  case class PlatformData(path: PlatformPath, platform: Platform) {
    def apply(overrides: MetadataOverridesConfiguration): Option[Platform] = when(overrides.isVisible(path)) {
      platform.copy(displayName = overrides
        .platform(path)
        .flatMap(_.displayName)
        .getOrElse(platform.displayName))
    }
  }

  case class NetworkData(path: NetworkPath, network: Network) {
    def apply(overrides: MetadataOverridesConfiguration): Option[Network] = when(overrides.isVisible(path)) {
      network.copy(displayName = overrides
        .network(path)
        .flatMap(_.displayName)
        .getOrElse(network.displayName))
    }
  }

  case class EntityData(path: EntityPath, entity: Entity) {
    def apply(overrides: MetadataOverridesConfiguration): Option[Entity] = when(overrides.isVisible(path)) {
      entity.copy(displayName = overrides
        .entity(path)
        .flatMap(_.displayName)
        .getOrElse(entity.displayName))
    }
  }

  case class AttributeData(path: AttributePath, attribute: Attribute) {
    def apply(overrides: MetadataOverridesConfiguration): Option[Attribute] = when(overrides.isVisible(path)) {
      attribute.copy(displayName = overrides
        .attribute(path)
        .flatMap(_.displayName)
        .getOrElse(attribute.displayName))
    }
  }
}
