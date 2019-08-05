package tech.cryptonomic.conseil.metadata

import tech.cryptonomic.conseil.generic.chain.{DataTypes, PlatformDiscoveryOperations, PlatformDiscoveryTypes}
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, Entity}

import scala.concurrent.Future

/* Implementation of PlatformDiscoveryOperations for tests purposes. It's fully functional and keeps entities and
 * attributes in memory
 *
 * */
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

  override def getTableAttributes(tableName: String): Future[Option[List[Attribute]]] = this.synchronized {
    Future.successful(Some(attributes.filter(_.entity == tableName)))
  }

  override def getTableAttributesWithoutUpdatingCache(tableName: String): Future[Option[List[Attribute]]] =
    getTableAttributes(tableName)

  override def listAttributeValues(
      tableName: String,
      column: String,
      withFilter: Option[String],
      attributesCacheConfig: Option[PlatformDiscoveryTypes.AttributeCacheConfiguration]
  ): Future[Either[List[DataTypes.AttributesValidationError], List[String]]] = ???

  def clear(): Unit = this.synchronized {
    entities = List.empty
    attributes = List.empty
  }
}
