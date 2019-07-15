package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.MetadataConfiguration
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes

class TransparentUnitTransformation(overrides: MetadataConfiguration = MetadataConfiguration(Map.empty))
    extends UnitTransformation(overrides) {
  override def overridePlatforms(
      platforms: List[PlatformDiscoveryTypes.Platform]
  ): List[PlatformDiscoveryTypes.Platform] = platforms

  override def overrideNetworks(
      platformPath: PlatformPath,
      networks: List[PlatformDiscoveryTypes.Network]
  ): List[PlatformDiscoveryTypes.Network] = networks

  override def overrideEntities(
      networkPath: NetworkPath,
      entities: List[PlatformDiscoveryTypes.Entity]
  ): List[PlatformDiscoveryTypes.Entity] = entities

  override def overrideAttributes(
      path: EntityPath,
      attributes: List[PlatformDiscoveryTypes.Attribute]
  ): List[PlatformDiscoveryTypes.Attribute] = attributes
}
