package tech.cryptonomic.conseil.platform.discovery

import io.circe.generic.semiauto._

import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._

object converters {

  implicit val platformsCodec = deriveCodec[Platform]

  implicit val networksCodec = deriveCodec[Network]

  implicit val entitiesCodec = deriveCodec[Entity]

  implicit val keyTypeCodec = deriveCodec[KeyType]
  implicit val dataTypeCodec = deriveCodec[DataType]
  implicit val attributeCacheEncoder = deriveEncoder[AttributeCacheConfiguration]
  implicit val attributeCacheDecoder = deriveDecoder[AttributeCacheConfiguration]
  implicit val attributesCodec = deriveCodec[Attribute]

}
