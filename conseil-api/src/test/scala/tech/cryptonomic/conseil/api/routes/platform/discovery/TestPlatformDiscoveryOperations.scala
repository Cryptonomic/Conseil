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

  private var entities: Map[String, List[Entity]] = Map.empty.withDefaultValue(List.empty[Entity])
  private var attributes: Map[String, List[Attribute]] = Map.empty.withDefaultValue(List.empty[Attribute])

  def addEntity(path: NetworkPath, entity: Entity): Unit = this.synchronized {
    entities = entities.updated(asKey(path), entities(asKey(path)) :+ entity)
  }

  override def getEntities(path: NetworkPath): Future[List[Entity]] = this.synchronized {
    Future.successful(entities(asKey(path)))
  }

  def addAttribute(path: EntityPath, attribute: Attribute): Unit = this.synchronized {
    attributes = attributes.updated(asKey(path), attributes(asKey(path)) :+ attribute)
  }

  override def getTableAttributes(path: EntityPath): Future[Option[List[Attribute]]] = this.synchronized {
    Future.successful(attributes.get(asKey(path)))
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

  private def asKey(path: NetworkPath): String = s"${path.up.platform}.${path.network}"
  private def asKey(path: EntityPath): String = s"${path.up.up.platform}.${path.up.network}.${path.entity}"

}
