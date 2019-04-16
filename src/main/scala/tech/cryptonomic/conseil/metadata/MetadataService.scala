package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.{MetadataOverridesConfiguration, NetworkConfiguration, OverridesPath}
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.config.Types.PlatformName
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity, Network, Platform}
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.ConfigUtil

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

class MetadataService(config: PlatformsConfiguration,
                      tezosPlatformDiscoveryOperations: TezosPlatformDiscoveryOperations,
                      metadataOverrides: MetadataOverridesConfiguration) {

  def getPlatforms: List[Platform] = ConfigUtil
    .getPlatforms(config)
    .flatMap(processPlatform)

  private def processPlatform(platform: Platform): Option[Platform] = {
    val path = OverridesPath(platform.name)

    if (metadataOverrides.isVisible(path)) {
      val newDisplayName = metadataOverrides.platform(path).flatMap(_.displayName)
      Some(platform.copy(displayName = newDisplayName.getOrElse(platform.displayName)))
    } else {
      None
    }
  }

  def getNetworks(platform: String): List[Network] = {
    ConfigUtil
      .getNetworks(config, platform)
      .flatMap(network => processNetwork(platform, network))
  }

  private def processNetwork(platformName: PlatformName, network: Network): Option[Network] = {
    val path = OverridesPath(platformName, network.name)

    if (metadataOverrides.isVisible(path)) {
      val newDisplayName = metadataOverrides.network(path).flatMap(_.displayName)
      Some(network.copy(displayName = newDisplayName.getOrElse(network.displayName)))
    } else {
      None
    }
  }

  def getEntities(platform: String, network: String)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[Entity]]] = {
    if (getNetworks(platform).exists(_.network == network))
      tezosPlatformDiscoveryOperations
        .getEntities
        .map(entities => Some(entities
          .flatMap(entity => processEntity(platform, network, entity))))
    else
      successful(None)
  }

  private def processEntity(platform: String, network: String, entity: Entity): Option[Entity] = {
    val path = OverridesPath(platform, network, entity.name)

    if (metadataOverrides.isVisible(path)) {
      val newDisplayName = metadataOverrides.entity(path).flatMap(_.displayName)
      Some(entity.copy(displayName = newDisplayName.getOrElse(entity.displayName)))
    } else {
      None
    }
  }

  def getTableAttributes(platform: String, network: String, tableName: String)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[Attribute]]] = {
    if (getNetworks(platform).exists(_.network == network))
      tezosPlatformDiscoveryOperations
        .getTableAttributes(tableName)
        .map(attributes => attributes.map(_
          .flatMap(attribute => processAttribute(platform, network, tableName, attribute))))
    else
      successful(None)
  }

  private def processAttribute(platform: String, network: String, tableName: String, attribute: Attribute): Option[Attribute] = {
    val path = OverridesPath(platform, network, tableName, attribute.name)

    if (metadataOverrides.isVisible(path)) {
      val newDisplayName = metadataOverrides.attribute(path).flatMap(_.displayName)
      Some(attribute.copy(displayName = newDisplayName.getOrElse(attribute.displayName)))
    } else {
      None
    }
  }

  def getAttributeValues(platform: String, network: String , entity: String, attribute: String)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[String]]] = {
    if (getNetworks(platform).exists(_.network == network))
      tezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute).map(Some(_))
    else
      successful(None)
  }


  def getAttributesValuesWithFilterRoute(platform: String, network: String, entity: String, attribute: String, filter: String)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[String]]] = {
    if (getNetworks(platform).exists(_.network == network))
      tezosPlatformDiscoveryOperations.listAttributeValues(entity, attribute, Some(filter)).map(Some(_))
    else
      successful(None)
  }
}
