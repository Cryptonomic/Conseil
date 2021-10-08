package tech.cryptonomic.conseil.api.routes.platform.discovery

import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._

import endpoints4s.generic.JsonSchemas

/** Trait containing metadata endpoints JSON schemas */
private[discovery] trait PlatformDiscoveryJsonSchemas extends JsonSchemas {

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
    stringEnumeration(DataType.values.toSeq)(_.toString)

  /** Key type JSON schema */
  implicit lazy val keyTypeSchema: JsonSchema[KeyType.Value] =
    stringEnumeration(KeyType.values.toSeq)(_.toString)
}
