package tech.cryptonomic.conseil.routes.openapi

import endpoints.{algebra, generic}
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes._

trait PlatformDiscoveryJsonSchemas extends algebra.JsonSchemas with generic.JsonSchemas {

  implicit def platformSchema: JsonSchema[Platform] =
    genericJsonSchema[Platform]

  implicit def networkSchema: JsonSchema[Network] =
    genericJsonSchema[Network]

  implicit def entitieSchema: JsonSchema[Entity] =
    genericJsonSchema[Entity]

  implicit def attributeSchema: JsonSchema[Attribute] =
    genericJsonSchema[Attribute]

  implicit def dataTypeSchema: JsonSchema[DataType.Value] =
    enumeration(DataType.values.toSeq)(_.toString)

  implicit def keyTypeSchema: JsonSchema[KeyType.Value] =
    enumeration(KeyType.values.toSeq)(_.toString)
}