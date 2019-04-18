package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.MetadataOverridesConfiguration
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity, Network, Platform}
import tech.cryptonomic.conseil.metadata.Data.{AttributeData, EntityData, NetworkData, PlatformData}
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.OptionUtil.when
import tech.cryptonomic.conseil.util.ConfigUtil

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

class MetadataService(config: PlatformsConfiguration,
                      metadataOverrides: MetadataOverridesConfiguration,
                      tezosPlatformDiscoveryOperations: TezosPlatformDiscoveryOperations) {

  def getPlatforms: List[Platform] = {
    ConfigUtil
      .getPlatforms(config)
      .flatMap(platform => PlatformData(PlatformPath(platform.name), platform).apply(metadataOverrides))
  }

  def getNetworks(path: PlatformPath): Option[List[Network]] = {
    when(metadataOverrides.isVisible(path)) {
      ConfigUtil
        .getNetworks(config, path.platform)
        .flatMap(network => NetworkData(path.addLevel(network.name), network).apply(metadataOverrides))
    }
  }

  def getEntities(path: NetworkPath)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[Entity]]] = tezosPlatformDiscoveryOperations
    .getEntities
    .map(entities => {
      when(exists(path) && metadataOverrides.isVisible(path)) {
        entities
          .flatMap(entity => EntityData(path.addLevel(entity.name), entity).apply(metadataOverrides))
      }
    })

  def getTableAttributes(path: EntityPath)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[Attribute]]] = {
    tezosPlatformDiscoveryOperations
      .getTableAttributes(path.entity)
      .map {
        _.flatMap {
          attributes => when(exists(path.up) && metadataOverrides.isVisible(path)) {
            attributes.flatMap(attribute => AttributeData(path.addLevel(attribute.name), attribute).apply(metadataOverrides))
          }
        }
      }
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
