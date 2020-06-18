package tech.cryptonomic.conseil.api.routes.platform.discovery

import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity}
import tech.cryptonomic.conseil.common.generic.chain.{DataTypes, PlatformDiscoveryOperations, PlatformDiscoveryTypes}
import tech.cryptonomic.conseil.common.metadata.{AttributePath, EntityPath, NetworkPath}

import scala.concurrent.Future

/*
 * Implementation of PlatformDiscoveryOperations for tests purposes. It's fully functional and keeps entities and
 * attributes in memory
 **/
class TestPlatformDiscoveryOperations extends PlatformDiscoveryOperations {

  private var entities: Map[NetworkPath, List[Entity]] = Map.empty.withDefaultValue(List.empty[Entity])
  private var attributes: Map[EntityPath, List[Attribute]] = Map.empty.withDefaultValue(List.empty[Attribute])

  def addEntity(path: NetworkPath, entity: Entity): Unit = this.synchronized {
    entities = entities.updated(path, entities(path) :+ entity)
  }

  override def getEntities(path: NetworkPath): Future[List[Entity]] = this.synchronized {
    Future.successful(entities(path))
  }

  def addAttribute(path: EntityPath, attribute: Attribute): Unit = this.synchronized {
    attributes = attributes.updated(path, attributes(path) :+ attribute)
  }

  override def getTableAttributes(path: EntityPath): Future[Option[List[Attribute]]] = this.synchronized {
    Future.successful(attributes.get(path))
  }

  override def getTableAttributesWithoutUpdatingCache(entityPath: EntityPath): Future[Option[List[Attribute]]] =
    getTableAttributes(entityPath)

  override def listAttributeValues(
      attributePath: AttributePath,
      withFilter: Option[String],
      attributesCacheConfig: Option[PlatformDiscoveryTypes.AttributeCacheConfiguration]
  ): Future[Either[List[DataTypes.AttributesValidationError], List[String]]] = ???

  def clear(): Unit = this.synchronized {
    entities = Map.empty
    attributes = Map.empty
  }

}
