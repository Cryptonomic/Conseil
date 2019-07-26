package tech.cryptonomic.conseil.metadata

import cats.implicits._
import cats.syntax.all._
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.generic.chain.{DataTypes, PlatformDiscoveryOperations}
import tech.cryptonomic.conseil.util.ConfigUtil

import scala.concurrent.Future.successful
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps

// service class for metadata
class MetadataService(
    config: PlatformsConfiguration,
    transformation: UnitTransformation,
    cacheConfiguration: AttributeValuesCacheConfiguration,
    platformDiscoveryOperations: PlatformDiscoveryOperations
)(implicit apiExecutionContext: ExecutionContext) {

  private val platforms = transformation.overridePlatforms(ConfigUtil.getPlatforms(config))

  private val networks = platforms.map { platform =>
    platform.path -> transformation.overrideNetworks(platform.path, ConfigUtil.getNetworks(config, platform.name))
  }.toMap

  private val entities = {
    val allEntities = Await.result(platformDiscoveryOperations.getEntities, 2 second)

    networks.values.flatten
      .map(_.path)
      .map(
        networkPath => networkPath -> transformation.overrideEntities(networkPath, allEntities)
      )
      .toMap
  }

  private val attributes: Map[EntityPath, List[Attribute]] = {
    val networkPaths = entities.flatMap {
      case (networkPath: NetworkPath, entities: List[Entity]) =>
        entities.map(entity => networkPath.addLevel(entity.name))
    }.toSet

    val result = networkPaths.map { path =>
      platformDiscoveryOperations
        .getTableAttributes(path.entity)
        .map(attributes => path -> transformation.overrideAttributes(path, attributes.getOrElse(List.empty)))
    }

    Await.result(Future.sequence(result).map(_.toMap), 10 seconds)
  }

  // fetches platforms
  def getPlatforms: List[Platform] = platforms

  // fetches networks
  def getNetworks(path: PlatformPath): Option[List[Network]] = networks.get(path)

  // fetches entities
  def getEntities(path: NetworkPath): Option[List[Entity]] = entities.get(path)

  // fetches table attributes
  def getTableAttributes(path: EntityPath): Option[List[Attribute]] = attributes.get(path)

  // fetches table attributes without updating cache
  def getTableAttributesWithoutUpdatingCache(path: EntityPath): Future[Option[List[Attribute]]] =
    getAttributesHelper(path)(platformDiscoveryOperations.getTableAttributesWithoutUpdatingCache)

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
      platformDiscoveryOperations
        .listAttributeValues(entity, attribute, filter, cacheConfiguration.getCacheConfiguration(attributePath))
        .map(Some(_))
    } else
      successful(None)
  }

  // checks if path exists
  def exists(path: Path): Boolean = path match {
    case attributePath: AttributePath =>
      attributes.getOrElse(attributePath.up, List.empty).exists(_.name == attributePath.attribute)
    case entityPath: EntityPath =>
      entities.getOrElse(entityPath.up, List.empty).exists(_.name == entityPath.entity)
    case networkPath: NetworkPath =>
      networks.getOrElse(networkPath.up, List.empty).exists(_.network == networkPath.network)
    case platformPath: PlatformPath =>
      platforms.exists(_.name == platformPath.platform)
  }

  // fetches attributes with given function
  private def getAttributesHelper(path: EntityPath)(
      getAttributes: String => Future[Option[List[Attribute]]]
  ): Future[Option[List[Attribute]]] =
    if (exists(path))
      getAttributes(path.entity).map(
        _.map(attributes => transformation.overrideAttributes(path, attributes))
      )
    else
      Future.successful(None)
}
