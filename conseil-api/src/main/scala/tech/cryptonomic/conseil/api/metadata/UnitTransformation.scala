package tech.cryptonomic.conseil.api.metadata

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.common.config.{MetadataConfiguration, PlatformConfiguration}
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.common.metadata._
import tech.cryptonomic.conseil.common.util.OptionUtil.when

// class for applying overrides configurations
class UnitTransformation(overrides: MetadataConfiguration) extends LazyLogging {

  // overrides platforms
  def overridePlatforms(platforms: List[Platform]): List[Platform] = {
    logDifferences(platforms.map(_.path), overrides.allPlatforms.keys.toList)
    platforms.flatMap(platform => overridePlatform(platform, PlatformPath(platform.name)))
  }

  // overrides networks
  def overrideNetworks(platformPath: PlatformPath, networks: List[Network]): List[Network] = {
    logDifferences(networks.map(_.path), overrides.networks(platformPath).keys.toList)
    networks.flatMap(network => overrideNetwork(network, network.path))
  }

  // overrides entities
  def overrideEntities(networkPath: NetworkPath, entities: List[Entity], shouldLog: Boolean = true): List[Entity] = {
    if (shouldLog)
      logDifferences(
        entities.map(entity => networkPath.addLevel(entity.name)),
        overrides.entities(networkPath).keys.toList
      )
    entities.flatMap(entity => overrideEntity(entity, networkPath.addLevel(entity.name)))
  }

  // overrides attributes
  def overrideAttributes(path: EntityPath, attributes: List[Attribute], shouldLog: Boolean = true): List[Attribute] = {
    if (shouldLog)
      logDifferences(attributes.map(attribute => path.addLevel(attribute.name)), overrides.attributes(path).keys.toList)
    attributes.flatMap(attribute => overrideAttribute(attribute, path.addLevel(attribute.name)))
  }

  // overrides platform
  private def overridePlatform(platform: Platform, path: PlatformPath): Option[Platform] =
    when(overrides.isVisible(path)) {
      val overridePlatform: Option[PlatformConfiguration] = overrides.platform(path)

      platform.copy(
        displayName = overridePlatform
          .flatMap(_.displayName)
          .getOrElse(platform.displayName),
        description = overridePlatform
          .flatMap(_.description)
      )
    }

  // overrides network
  private def overrideNetwork(network: Network, path: NetworkPath): Option[Network] = when(overrides.isVisible(path)) {
    val overrideNetwork = overrides.network(path)

    network.copy(
      displayName = overrideNetwork
        .flatMap(_.displayName)
        .getOrElse(network.displayName),
      description = overrideNetwork
        .flatMap(_.description)
    )
  }

  // overrides entity
  private def overrideEntity(entity: Entity, path: EntityPath): Option[Entity] = when(overrides.isVisible(path)) {
    val overrideEntity = overrides.entity(path)

    entity.copy(
      displayName = overrideEntity
        .flatMap(_.displayName)
        .getOrElse(entity.displayName),
      displayNamePlural = overrideEntity
        .flatMap(_.displayNamePlural),
      description = overrideEntity
        .flatMap(_.description)
    )
  }

  // overrides attribute
  private def overrideAttribute(attribute: Attribute, path: AttributePath): Option[Attribute] =
    when(overrides.isVisible(path)) {
      val overrideAttribute = overrides.attribute(path)

      attribute.copy(
        displayName = overrideAttribute.flatMap(_.displayName).getOrElse(attribute.displayName),
        description = overrideAttribute.flatMap(_.description),
        placeholder = overrideAttribute.flatMap(_.placeholder),
        dataFormat = overrideAttribute.flatMap(_.dataFormat),
        scale = overrideAttribute.flatMap(_.scale),
        dataType = overrideAttribute.flatMap(_.dataType).map(mapDataType).getOrElse(attribute.dataType),
        valueMap = overrideAttribute.flatMap(_.valueMap).filter(_.nonEmpty),
        reference = overrideAttribute.flatMap(_.reference).filter(_.nonEmpty),
        cacheConfig = overrideAttribute.flatMap(_.cacheConfig),
        displayPriority = overrideAttribute.flatMap(_.displayPriority),
        displayOrder = overrideAttribute.flatMap(_.displayOrder),
        currencySymbol = overrideAttribute.flatMap(_.currencySymbol),
        currencySymbolCode = overrideAttribute.flatMap(_.currencySymbolCode)
      )
    }

  private def logDifferences(paths: List[Path], overriddenPaths: List[Path]): Unit = {
    (paths.toSet diff overriddenPaths.toSet)
      .foreach(logger.warn("""There're missing metadata overrides for "{}"""", _))

    (overriddenPaths.toSet diff paths.toSet)
      .foreach(logger.error("""Metadata overrides "{}" override nothing""", _))
  }
}
