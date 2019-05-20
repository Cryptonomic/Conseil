package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.MetadataConfiguration
import tech.cryptonomic.conseil.config.Types.{AttributeName, EntityName}
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.AttributeCacheConfiguration
import tech.cryptonomic.conseil.util.OptionUtil.when

/** Class for extracting attribute cache configurations */
class AttributeValuesCacheOverrides(metadataConfiguration: MetadataConfiguration) {

  /** extracts cache configuration for given attribute path */
  def getCacheConfiguration(path: AttributePath): Option[AttributeCacheConfiguration] = when(metadataConfiguration.isVisible(path)) {
    metadataConfiguration
      .attribute(path)
      .map(attribute =>
        AttributeCacheConfiguration(attribute.cached.getOrElse(false),
          attribute.minMatchLength.getOrElse(0),
          attribute.maxResults.getOrElse(Int.MaxValue)))
  }.flatten

  /** extracts pair (entity, attribute) which needs to be cached */
  def getAttributesToCache: List[(EntityName, AttributeName)] = {
    metadataConfiguration.allAttributes
      .filter {
        case (_, conf) => conf.cached.getOrElse(false)
      }
      .keys
      .filter(metadataConfiguration.isVisible)
      .map(path => (path.up.entity, path.attribute))
      .toList
  }
}
