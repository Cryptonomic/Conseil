package tech.cryptonomic.conseil.metadata

import scala.concurrent.duration._
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.generic.chain.DataTypes
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.CollectionOps.ExtendedOptionalList
import tech.cryptonomic.conseil.util.ConfigUtil
import tech.cryptonomic.conseil.util.OptionUtil.when

import scala.concurrent.Future.successful
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

// service class for metadata
class MetadataService(
    config: PlatformsConfiguration,
    transformation: UnitTransformation,
    cacheConfiguration: AttributeValuesCacheConfiguration,
    tezosPlatformDiscoveryOperations: TezosPlatformDiscoveryOperations
)(implicit apiExecutionContext: ExecutionContext) {

  private lazy val platforms = ConfigUtil
    .getPlatforms(config)
    .flatMap(platform => transformation.overridePlatform(platform, PlatformPath(platform.name)))

  private lazy val networks = platforms.flatMap { platform =>
    ConfigUtil
      .getNetworks(config, platform.name)
      .flatMap(network => transformation.overrideNetwork(network, platform.path.addLevel(network.name)))
  }

  private lazy val entities = {
    lazy val allEntities = Await.result(tezosPlatformDiscoveryOperations.getEntities, 1 second)

    networks
      .map(_.path)
      .map(
        networkPath =>
          networkPath -> allEntities
                .flatMap(entity => transformation.overrideEntity(entity, networkPath.addLevel(entity.name)))
      )
      .toMap
  }

  private lazy val tableAttributes = {
    val networkPaths = entities.flatMap {
      case (networkPath: NetworkPath, entities: List[Entity]) =>
        entities.map(entity => networkPath.addLevel(entity.name))
    }.toSet

    networkPaths.flatMap { path =>
      Await
        .result(
          getAttributesHelper(path)(tezosPlatformDiscoveryOperations.getTableAttributes),
          1 second
        )
        .map(path -> _)
    }.toMap
  }

  // fetches platforms
  def getPlatforms: List[Platform] = platforms

  // fetches networks
  def getNetworks(path: PlatformPath): Option[List[Network]] = when(exists(path)) {
    networks.filter(_.platform == path.platform)
  }

  // fetches entities
  def getEntities(path: NetworkPath): Option[List[Entity]] = entities.get(path)

  // fetches table attributes
  def getTableAttributes(path: EntityPath): Option[List[Attribute]] = tableAttributes.get(path)

  // fetches table attributes without updating cache
  def getTableAttributesWithoutUpdatingCache(path: EntityPath): Future[Option[List[Attribute]]] =
    getAttributesHelper(path)(tezosPlatformDiscoveryOperations.getTableAttributesWithoutUpdatingCache)

  // fetches attribute values
  def getAttributeValues(
      platform: String,
      network: String,
      entity: String,
      attribute: String,
      filter: Option[String] = None
  ): Future[Option[Either[List[DataTypes.AttributesValidationError], List[String]]]] = {

    val path = NetworkPath(network, PlatformPath(platform))
    if (exists(path)) {
      val attributePath = EntityPath(entity, path).addLevel(attribute)
      tezosPlatformDiscoveryOperations
        .listAttributeValues(entity, attribute, filter, cacheConfiguration.getCacheConfiguration(attributePath))
        .map(Some(_))
    } else
      successful(None)
  }

  // checks if attribute is valid
  def isAttributeValid(entity: String, attribute: String): Future[Boolean] =
    tezosPlatformDiscoveryOperations.isAttributeValid(entity, attribute)

  private def exists(path: NetworkPath): Boolean =
    getNetworks(path.up).getOrElse(List.empty).exists(_.network == path.network) && exists(path.up)

  private def exists(path: PlatformPath): Boolean = getPlatforms.exists(_.name == path.platform)

  private def exists(path: EntityPath): Boolean =
    getEntities(path.up).toList.flatten.exists(_.name == path.entity) && exists(path.up)

  // fetches attributes with given function
  private def getAttributesHelper(path: EntityPath)(
      getAttributes: String => Future[Option[List[Attribute]]]
  ): Future[Option[List[Attribute]]] =
    if (exists(path))
      getAttributes(path.entity).map(
        _.mapNested(attribute => transformation.overrideAttribute(attribute, path.addLevel(attribute.name)))
      )
    else
      Future.successful(None)
}
