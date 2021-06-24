package tech.cryptonomic.conseil.api.metadata

import tech.cryptonomic.conseil.common.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.common.generic.chain.{DataTypes, PlatformDiscoveryOperations}
import tech.cryptonomic.conseil.common.metadata._

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

  private val platforms = transformation.overridePlatforms(config.getPlatforms(), shouldLog = false)

  private val networks = platforms.map { platform =>
    platform.path -> transformation.overrideNetworks(
      platform.path,
      config.getNetworks(platform.name),
      shouldLog = false
    )
  }.toMap

  private val entities = {
    def futureEntities(path: NetworkPath): List[Entity] =
      Await.result(platformDiscoveryOperations.getEntities(path), 10 second)

    networks.values.flatten
      .map(_.path)
      .map(
        networkPath =>
          networkPath -> transformation.overrideEntities(networkPath, futureEntities(networkPath), shouldLog = false)
      )
      .toMap
  }

  private val attributes: Map[EntityPath, List[Attribute]] = {
    val entityPaths = entities.flatMap {
      case (networkPath: NetworkPath, entities: List[Entity]) =>
        entities.map(entity => networkPath.addLevel(entity.name))
    }.toSet

    val result = Future.traverse(entityPaths) { path =>
      platformDiscoveryOperations
        .getTableAttributes(path)
        .map(
          attributes =>
            path -> transformation.overrideAttributes(path, attributes.getOrElse(List.empty), shouldLog = false)
        )
    }

    Await.result(result.map(_.toMap), 10 seconds)
  }

  // fetches platforms
  def getPlatforms: List[Platform] = platforms

  // fetches networks
  def getNetworks(path: PlatformPath): Option[List[Network]] = networks.get(path)

  // fetches entities
  def getEntities(path: NetworkPath): Option[List[Entity]] = entities.get(path)

  // gets current entities
  def getCurrentEntities(path: NetworkPath): Future[Option[List[Entity]]] =
    if (exists(path)) {
      platformDiscoveryOperations.getEntities(path).map { allEntities =>
        Some(transformation.overrideEntities(path, allEntities, shouldLog = false))
      }
    } else successful(None)

  // fetches table attributes
  def getTableAttributes(path: EntityPath): Option[List[Attribute]] = attributes.get(path)

  // fetches current attributes
  def getCurrentTableAttributes(path: EntityPath): Future[Option[List[Attribute]]] =
    if (exists(path)) {
      platformDiscoveryOperations.getTableAttributes(path).map { maybeAttributes =>
        maybeAttributes.map { attributes =>
          transformation.overrideAttributes(path, attributes, shouldLog = false)
        }
      }
    } else successful(None)

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
        .listAttributeValues(
          attributePath,
          filter,
          cacheConfiguration.getCacheConfiguration(attributePath)
        )
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
    case _: EmptyPath => false
  }

  // fetches attributes with given function
  private def getAttributesHelper(path: EntityPath)(
      getAttributes: EntityPath => Future[Option[List[Attribute]]]
  ): Future[Option[List[Attribute]]] =
    if (exists(path))
      getAttributes(path).map(
        _.map(attributes => transformation.overrideAttributes(path, attributes, shouldLog = false))
      )
    else
      Future.successful(None)

}
