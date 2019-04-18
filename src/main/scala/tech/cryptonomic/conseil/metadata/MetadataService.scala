package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.MetadataOverridesConfiguration
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity, Network, Platform}
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.CollectionOps.{ExtendedFuture, ExtendedFutureWithOption}
import tech.cryptonomic.conseil.util.ConfigUtil
import tech.cryptonomic.conseil.util.OptionUtil.when

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

class MetadataService(config: PlatformsConfiguration,
                      metadataOverrides: MetadataOverridesConfiguration,
                      tezosPlatformDiscoveryOperations: TezosPlatformDiscoveryOperations) {

  val overridePlatform = UnitTransformation.platform(metadataOverrides)
  val overrideNetwork = UnitTransformation.network(metadataOverrides)
  val overrideEntity = UnitTransformation.entity(metadataOverrides)
  val overrideAttribute = UnitTransformation.attribute(metadataOverrides)

  def getPlatforms: List[Platform] = ConfigUtil
    .getPlatforms(config)
    .flatMap(platform => overridePlatform(platform, PlatformPath(platform.name)))

  def getNetworks(path: PlatformPath): Option[List[Network]] = when(metadataOverrides.isVisible(path)) {
    ConfigUtil
      .getNetworks(config, path.platform)
      .flatMap(network => overrideNetwork(network, path.addLevel(network.name)))
  }

  def getEntities(path: NetworkPath)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[Entity]]] = {
    if (exists(path) && metadataOverrides.isVisible(path))
      tezosPlatformDiscoveryOperations
        .getEntities
        .embeddedMap(entity => overrideEntity(entity, path.addLevel(entity.name)))
        .map(Some(_))
    else
      successful(None)
  }

  def getTableAttributes(path: EntityPath)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[Attribute]]] = {
    if (exists(path.up) && metadataOverrides.isVisible(path))
      tezosPlatformDiscoveryOperations
        .getTableAttributes(path.entity)
        .embeddedMap(attribute => overrideAttribute(attribute, path.addLevel(attribute.name)))
    else
      successful(None)
  }

  def getAttributeValues(platform: String, network: String, entity: String, attribute: String, filter: Option[String] = None)
                        (implicit apiExecutionContext: ExecutionContext): Future[Option[List[String]]] = {
    if (exists(NetworkPath(network, PlatformPath(platform))))
      tezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute, filter).map(Some(_))
    else
      successful(None)
  }

  private def exists(path: NetworkPath): Boolean = {
    getNetworks(path.up).getOrElse(List()).exists(_.network == path.network)
  }
}
