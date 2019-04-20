package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.MetadataOverridesConfiguration
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity, Network, Platform}
import tech.cryptonomic.conseil.util.OptionUtil.when

class UnitTransformation(overrides: MetadataOverridesConfiguration) {

  def overridePlatform(platform: Platform, path: PlatformPath): Option[Platform] = when(overrides.isVisible(path)) {
    platform.copy(displayName = overrides
      .platform(path)
      .flatMap(_.displayName)
      .getOrElse(platform.displayName))
  }

  def overrideNetwork(network: Network, path: NetworkPath): Option[Network] = when(overrides.isVisible(path)) {
    network.copy(displayName = overrides
      .network(path)
      .flatMap(_.displayName)
      .getOrElse(network.displayName))
  }

  def overrideEntity(entity: Entity, path: EntityPath): Option[Entity] = when(overrides.isVisible(path)) {
    entity.copy(displayName = overrides
      .entity(path)
      .flatMap(_.displayName)
      .getOrElse(entity.displayName))
  }

  def overrideAttribute(attribute: Attribute, path: AttributePath): Option[Attribute] = when(overrides.isVisible(path)) {
    attribute.copy(displayName = overrides
      .attribute(path)
      .flatMap(_.displayName)
      .getOrElse(attribute.displayName))
  }
}
