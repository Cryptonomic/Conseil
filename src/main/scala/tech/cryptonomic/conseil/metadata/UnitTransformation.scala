package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.MetadataConfiguration
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity, Network, Platform}
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations.mapType
import tech.cryptonomic.conseil.util.OptionUtil.when

// class for applying overrides configurations
class UnitTransformation(overrides: MetadataConfiguration) {

  // overrides platform
  def overridePlatform(platform: Platform, path: PlatformPath): Option[Platform] = when(overrides.isVisible(path)) {
    val overridePlatform = overrides.platform(path)

    platform.copy(
      displayName = overridePlatform
        .flatMap(_.displayName)
        .getOrElse(platform.displayName),
      description = overridePlatform
        .flatMap(_.description)
    )
  }

  // overrides network
  def overrideNetwork(network: Network, path: NetworkPath): Option[Network] = when(overrides.isVisible(path)) {
    val overrideNetwork = overrides.network(path)

    network.copy(
      displayName = overrideNetwork
        .flatMap(_.displayName)
        .getOrElse(network.displayName),
      description = overrideNetwork
        .flatMap(_.description)
    )
  }

  // overrides entity
  def overrideEntity(entity: Entity, path: EntityPath): Option[Entity] = when(overrides.isVisible(path)) {
    val overrideEntity = overrides.entity(path)

    entity.copy(
      displayName = overrideEntity
        .flatMap(_.displayName)
        .getOrElse(entity.displayName),
      displayNamePlural = overrideEntity
        .flatMap(_.displayNamePlural),
      description = overrideEntity
        .flatMap(_.description)
    )
  }

  // overrides attribute
  def overrideAttribute(attribute: Attribute, path: AttributePath): Option[Attribute] =
    when(overrides.isVisible(path)) {
      val overrideAttribute = overrides.attribute(path)

      attribute.copy(
        displayName = overrideAttribute.flatMap(_.displayName).getOrElse(attribute.displayName),
        description = overrideAttribute.flatMap(_.description),
        placeholder = overrideAttribute.flatMap(_.placeholder),
        dataFormat = overrideAttribute.flatMap(_.dataFormat),
        scale = overrideAttribute.flatMap(_.scale),
        dataType = overrideAttribute.flatMap(_.dataType).map(mapType).getOrElse(attribute.dataType),
        valueMap = overrideAttribute.flatMap(_.valueMap).filter(_.nonEmpty),
        reference = overrideAttribute.flatMap(_.reference).filter(_.nonEmpty),
        displayPriority = overrideAttribute.flatMap(_.displayPriority),
        displayOrder = overrideAttribute.flatMap(_.displayOrder)
      )
    }
}
