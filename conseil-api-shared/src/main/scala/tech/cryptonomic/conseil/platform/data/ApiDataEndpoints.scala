package tech.cryptonomic.conseil.platform.data

import io.circe.generic.semiauto._
import sttp.tapir._
import sttp.tapir.json.circe._
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import io.circe.{Decoder, Encoder}

trait ApiDataEndpoints {

  protected val commonPath = (platform: String) =>
    infallibleEndpoint.in("v2" / "data" / platform / "network" / "entity")

  implicit val outputTypeCodec = deriveCodec[OutputType]
  // implicit val queryResponseCodec = deriveCodec[QueryResponse]

  implicit val keyTypeCodec = deriveCodec[KeyType]
  implicit val dataTypeCodec = deriveCodec[DataType]

  implicit val attributeCacheCodec = deriveCodec[AttributeCacheConfiguration]
  implicit val attributesCodec = deriveCodec[Attribute]

  implicit val queryResponseWithOutputCodec = ??? // deriveCodec[QueryResponseWithOutput]

  /** V2 Query endpoint definition */
  def queryEndpoint(platform: String) =
    commonPath(platform).post
      .out(jsonBody[QueryResponseWithOutput])

  /** Common method for compatibility queries */
  def compatibilityQuery[A: Encoder: Decoder: Schema](endpointName: String) = jsonBody[A]

}
