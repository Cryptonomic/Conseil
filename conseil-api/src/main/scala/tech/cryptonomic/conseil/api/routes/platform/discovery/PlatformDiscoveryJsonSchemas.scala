package tech.cryptonomic.conseil.api.routes.platform.discovery

import endpoints.generic
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._

/** Trait containing metadata endpoints JSON schemas */
private[discovery] trait PlatformDiscoveryJsonSchemas extends generic.JsonSchemas {

  /** Platform JSON schema */
  implicit lazy val platformSchema: JsonSchema[Platform] =
    genericJsonSchema[Platform]

  /** Network JSON schema */
  implicit lazy val networkSchema: JsonSchema[Network] =
    genericJsonSchema[Network]

  /** Entity JSON schema */
  implicit lazy val entitySchema: JsonSchema[Entity] =
    genericJsonSchema[Entity]

  /** Attribute JSON schema */
  implicit lazy val attributeSchema: JsonSchema[Attribute] =
    genericJsonSchema[Attribute]

  /** Attribute JSON schema */
  implicit lazy val attributeCacheConfigSchema: JsonSchema[AttributeCacheConfiguration] =
    genericJsonSchema[AttributeCacheConfiguration]

  /** Data type JSON schema */
  implicit lazy val dataTypeSchema: JsonSchema[DataType.Value] =
    enumeration(DataType.values.toSeq)(_.toString)

  /** Key type JSON schema */
  implicit lazy val keyTypeSchema: JsonSchema[KeyType.Value] =
    enumeration(KeyType.values.toSeq)(_.toString)
}
