package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.MetadataOverridesConfiguration
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.AttributeCacheConfiguration

/** Class for extracting attribute cache configurations */
class AttributeValuesCacheOverrides(overrides: MetadataOverridesConfiguration) {

  /** extracts cache configuration for given attribute path */
  def getCacheConfiguration(path: AttributePath): Option[AttributeCacheConfiguration] = {
    val result = for {
      (platformName, platform) <- overrides.metadataOverrides.toList
      if path.up.up.up.platform == platformName
      (networkName, network) <- platform.networks.toList
      if path.up.up.network == networkName
      (entityName, entity) <- network.entities.toList
      if path.up.entity == entityName
      (attributeName, attribute) <- entity.attributes.toList
      if path.attribute == attributeName
    } yield AttributeCacheConfiguration(attribute.cached.getOrElse(false), attribute.minMatchLength.getOrElse(0), attribute.maxResults.getOrElse(Int.MaxValue))

    result.headOption
  }

  /** extracts pair (entity, attribute) which needs to be cached */
  def getAttributesToCache: List[(String, String)] = {
    val result = for {
      platform <- overrides.metadataOverrides.values
      network <- platform.networks.values
      (entityName, entity) <- network.entities.toList
      (attributeName, attribute) <- entity.attributes.toList
      if attribute.cached.getOrElse(false) && attribute.visible.getOrElse(false)
    } yield entityName -> attributeName

    result.toList
  }
}
