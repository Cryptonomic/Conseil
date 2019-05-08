package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.generic.chain.DataTypes
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity, Network, Platform}
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.CollectionOps.{ExtendedFuture, ExtendedOptionalList}
import tech.cryptonomic.conseil.util.ConfigUtil
import tech.cryptonomic.conseil.util.OptionUtil.when

import scala.concurrent.Future.successful
import scala.concurrent.{ExecutionContext, Future}

// service class for metadata
class MetadataService(config: PlatformsConfiguration,
                      transformation: UnitTransformation,
                      tezosPlatformDiscoveryOperations: TezosPlatformDiscoveryOperations) {

  // fetches platforms
  def getPlatforms: List[Platform] = ConfigUtil
    .getPlatforms(config)
    .flatMap(platform => transformation.overridePlatform(platform, PlatformPath(platform.name)))

  // fetches networks
  def getNetworks(path: PlatformPath): Option[List[Network]] = when(exists(path)) {
    ConfigUtil
      .getNetworks(config, path.platform)
      .flatMap(network => transformation.overrideNetwork(network, path.addLevel(network.name)))
  }

  // fetches entities
  def getEntities(path: NetworkPath)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[Entity]]] = {
    if (exists(path))
      tezosPlatformDiscoveryOperations
        .getEntities
        .mapNested(entity => transformation.overrideEntity(entity, path.addLevel(entity.name)))
        .map(Some(_))
    else
      successful(None)
  }

  // fetches table attributes
  def getTableAttributes(path: EntityPath)(implicit apiExecutionContext: ExecutionContext): Future[Option[List[Attribute]]] = for {
    exists <- exists(path)
    attributes <- tezosPlatformDiscoveryOperations.getTableAttributes(path.entity)
  } yield attributes
    .filter { _ => exists }
    .mapNested(attribute => transformation.overrideAttribute(attribute, path.addLevel(attribute.name)))

  // fetches attribute values
  def getAttributeValues(platform: String, network: String, entity: String, attribute: String, filter: Option[String] = None)
                        (implicit apiExecutionContext: ExecutionContext): Future[Option[Either[List[DataTypes.AttributesValidationError], List[String]]]] = {
    if (exists(NetworkPath(network, PlatformPath(platform))))
      tezosPlatformDiscoveryOperations
        .listAttributeValues(entity, attribute, filter)
        .map(Some(_))
    else
      successful(None)
  }

  private def exists(path: EntityPath)(implicit apiExecutionContext: ExecutionContext): Future[Boolean] =
    getEntities(path.up).map(_.getOrElse(List.empty).exists(_.name == path.entity) && exists(path.up))

  private def exists(path: NetworkPath): Boolean = getNetworks(path.up).getOrElse(List.empty).exists(_.network == path.network) && exists(path.up)

  private def exists(path: PlatformPath): Boolean = getPlatforms.exists(_.name == path.platform)
}
