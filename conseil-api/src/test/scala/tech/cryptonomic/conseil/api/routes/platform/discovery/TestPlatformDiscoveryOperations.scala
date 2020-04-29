package tech.cryptonomic.conseil.api.routes.platform.discovery

import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity}
import tech.cryptonomic.conseil.common.generic.chain.{DataTypes, PlatformDiscoveryOperations, PlatformDiscoveryTypes}
import tech.cryptonomic.conseil.common.metadata.{AttributePath, EntityPath}

import scala.concurrent.Future

//TODO This class is a duplicate from conseil-common,
// which will be updated once entire project will be split properly

/*
 * Implementation of PlatformDiscoveryOperations for tests purposes. It's fully functional and keeps entities and
 * attributes in memory
 **/
class TestPlatformDiscoveryOperations extends PlatformDiscoveryOperations {

  var entities: List[Entity] = List.empty
  var attributes: List[Attribute] = List.empty

  def addEntity(entity: Entity): Unit = this.synchronized {
    entities = entities :+ entity
  }

  override def getEntities: Future[List[Entity]] = this.synchronized {
    Future.successful(entities)
  }

  def addAttribute(attribute: Attribute): Unit = this.synchronized {
    attributes = attributes :+ attribute
  }

  override def getTableAttributes(entityPath: EntityPath): Future[Option[List[Attribute]]] = this.synchronized {
    Future.successful(Some(attributes.filter(_.entity == entityPath.entity)))
  }

  override def getTableAttributesWithoutUpdatingCache(entityPath: EntityPath): Future[Option[List[Attribute]]] =
    getTableAttributes(entityPath)

  override def listAttributeValues(
      attributePath: AttributePath,
      withFilter: Option[String],
      attributesCacheConfig: Option[PlatformDiscoveryTypes.AttributeCacheConfiguration]
  ): Future[Either[List[DataTypes.AttributesValidationError], List[String]]] = ???

  def clear(): Unit = this.synchronized {
    entities = List.empty
    attributes = List.empty
  }
}
