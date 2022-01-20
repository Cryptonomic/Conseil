package tech.cryptonomic.conseil.platform.metadata

import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.config.Types.{AttributeName, EntityName, NetworkName, PlatformName}
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.AttributeCacheConfiguration
import tech.cryptonomic.conseil.common.metadata.{AttributePath, Path}
import tech.cryptonomic.conseil.common.util.OptionUtil.when

/** Class for extracting attribute cache configurations */
class AttributeValuesCacheConfiguration(metadataConfiguration: MetadataConfiguration) {

  /** extracts cache configuration for given attribute path */
  def getCacheConfiguration(path: AttributePath): Option[AttributeCacheConfiguration] =
    whenVisible(path) {
      metadataConfiguration
        .attribute(path)
        .flatMap(_.cacheConfig)
    }

  /** extracts cache configuration for given attribute path */
  def getCardinalityHint(path: AttributePath): Option[Int] =
    whenVisible(path) {
      metadataConfiguration
        .attribute(path)
        .flatMap(_.cardinalityHint)
    }

  /** helper method for getting information from the path */
  private def whenVisible[T](path: Path)(value: => Option[T]): Option[T] =
    when(metadataConfiguration.isVisible(path))(value).flatten

  /** extracts tuple (platform, entity, attribute) which needs to be cached */
  def getAttributesToCache: List[(PlatformName, NetworkName, EntityName, AttributeName)] =
    metadataConfiguration.allAttributes.filter {
      case (_, conf) => conf.cacheConfig.exists(_.cached)
    }.keys
      .filter(metadataConfiguration.isVisible)
      .map(path => (path.up.up.up.platform, path.up.up.network, path.up.entity, path.attribute))
      .toList
}
