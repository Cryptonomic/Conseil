package tech.cryptonomic.conseil.api.routes.platform.data

import endpoints4s.akkahttp.server.JsonEntitiesFromSchemas
import endpoints4s.{Decoder, Encoder}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponse
import ApiDataStandardJsonCodecs.{
  anyDecoder,
  customAnyEncoder => anyEncoder,
  queryResponseDecoder,
  queryResponseEncoder,
  fieldDecoder,
  fieldEncoder,
  Json
}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import ujson.Value

/** Trait with methods for converting from data types to Json */
trait ApiServerJsonSchema extends JsonEntitiesFromSchemas with ApiDataJsonSchemas {

  /** Represents the function, that is going to encode the blockchain specific data types */
  protected def customAnyEncoder: PartialFunction[Any, Json]

  /** JSON schema implementation for Any */
  implicit override lazy val anySchema: JsonSchema[Any] = new JsonSchema[Any] {
    override def encoder: Encoder[Any, Json] = anyEncoder(customAnyEncoder)

    override def decoder: Decoder[Json, Any] = anyDecoder

  }

  /** Query response JSON schema implementation */
  implicit override lazy val queryResponseSchema: JsonSchema[QueryResponse] =
    new JsonSchema[QueryResponse] {
      override def encoder: Encoder[QueryResponse, Json] = queryResponseEncoder

      override def decoder: Decoder[Json, QueryResponse] = queryResponseDecoder

    }

  /** Fields JSON schema implementation */
  implicit override lazy val fieldSchema: JsonSchema[DataTypes.Field] =
    new JsonSchema[DataTypes.Field] {

      override def encoder: Encoder[DataTypes.Field, Value] = fieldEncoder(formattedFieldSchema.encoder)

      override def decoder: Decoder[Value, DataTypes.Field] = fieldDecoder(formattedFieldSchema.decoder)

    }

}
