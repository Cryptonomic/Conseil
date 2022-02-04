package tech.cryptonomic.conseil.api.platform.metadata

import tech.cryptonomic.conseil.common.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.common.generic.chain.{DataTypes, PlatformDiscoveryOperations}
import tech.cryptonomic.conseil.common.metadata._

import cats.effect.IO
import cats.syntax.all._

// service class for metadata
class MetadataService(
    config: PlatformsConfiguration,
    transformation: UnitTransformation,
    cacheConfiguration: AttributeValuesCacheConfiguration,
    platformDiscoveryOperations: PlatformDiscoveryOperations
) {
  private val platforms: List[Platform] = transformation.overridePlatforms(config.getPlatforms(), shouldLog = false)

  private val networks: Map[PlatformPath, List[Network]] = platforms.map { platform =>
    platform.path -> transformation.overrideNetworks(
      platform.path,
      config.getNetworks(platform.name),
      shouldLog = false
    )
  }.toMap

  private val entities: Map[NetworkPath, IO[List[Entity]]] = {
    def futureEntities(path: NetworkPath): IO[List[Entity]] =
      for {
        entities <- platformDiscoveryOperations.getEntities(path)
      } yield transformation.overrideEntities(path, entities, shouldLog = false)

    networks.values.flatten
      .map(_.path)
      .map(networkPath => networkPath -> futureEntities(networkPath))
      .toMap
  }

  private val attributes: IO[Map[EntityPath, List[Attribute]]] = {
    val entityPaths: Set[EntityPath] = entities.flatMap {
      case (networkPath: NetworkPath, entities: List[Entity]) =>
        entities.map(entity => networkPath.addLevel(entity.name))
    }.toSet

    entityPaths.toList.traverse { path =>
      platformDiscoveryOperations
        .getTableAttributes(path)
        .map(path -> transformation.overrideAttributes(path, _, shouldLog = false))
    }.map(_.toMap)
  }

  // fetches platforms
  def getPlatforms: IO[List[Platform]] = IO.pure(platforms)

  // fetches networks
  def getNetworks(path: PlatformPath): IO[List[Network]] = networks.getOrElse(path, List.empty).pure[IO]

  // fetches entities
  def getEntities(path: NetworkPath): IO[List[Entity]] = entities.getOrElse(path, List.empty.pure[IO])

  // gets current entities
  def getCurrentEntities(path: NetworkPath): IO[List[Entity]] =
    // TODO: ensure if if-else can't be simplified
    (for { bool <- exists(path) } yield
      if (bool)
        platformDiscoveryOperations.getEntities(path).map { allEntities =>
          transformation.overrideEntities(path, allEntities, shouldLog = false)
        } else List.empty.pure[IO]).flatten

  // fetches table attributes
  def getTableAttributes(path: EntityPath): IO[List[Attribute]] = attributes.map(_.getOrElse(path, List.empty))

  // fetches current attributes
  def getCurrentTableAttributes(path: EntityPath): IO[List[Attribute]] =
    (for { bool <- exists(path) } yield
      if (bool)
        platformDiscoveryOperations.getTableAttributes(path).map { attributes =>
          transformation.overrideAttributes(path, attributes, shouldLog = false)
        } else List.empty.pure[IO]).flatten

  // fetches table attributes without updating cache
  def getTableAttributesWithoutUpdatingCache(path: EntityPath): IO[List[Attribute]] =
    getAttributesHelper(path)(platformDiscoveryOperations.getTableAttributesWithoutUpdatingCache)

  // fetches attribute values
  def getAttributeValues(
      platform: String,
      network: String,
      entity: String,
      attribute: String,
      filter: Option[String] = None
  ): IO[Either[List[DataTypes.AttributesValidationError], List[String]]] = {
    val path = NetworkPath(network, PlatformPath(platform))

    for {
      bool <- exists(path)
      res <- {
        if (bool) {
          val attributePath = EntityPath(entity, path).addLevel(attribute)
          platformDiscoveryOperations
            .listAttributeValues(
              attributePath,
              filter,
              cacheConfiguration.getCacheConfiguration(attributePath)
            )
        } else Left(List.empty).pure[IO]
      }
    } yield res
  }

  // checks if path exists
  def exists(path: Path): IO[Boolean] = path match {
    case attributePath: AttributePath =>
      attributes.map(_.getOrElse(attributePath.up, List.empty).exists(_.name == attributePath.attribute))
    case entityPath: EntityPath =>
      entities.getOrElse(entityPath.up, List.empty.pure[IO]) map (_.exists(_.name == entityPath.entity))
    case networkPath: NetworkPath =>
      networks.getOrElse(networkPath.up, List.empty).exists(_.network == networkPath.network).pure[IO]
    case platformPath: PlatformPath =>
      platforms.exists(_.name == platformPath.platform).pure[IO]
    case _: EmptyPath => false.pure[IO]
  }

  // fetches attributes with given function
  private def getAttributesHelper(path: EntityPath)(
      getAttributes: EntityPath => IO[List[Attribute]]
  ): IO[List[Attribute]] =
    (for {
      bool <- exists(path)
    } yield
      if (bool)
        getAttributes(path).map(attributes => transformation.overrideAttributes(path, attributes, shouldLog = false))
      else List.empty.pure[IO]).flatten
}
