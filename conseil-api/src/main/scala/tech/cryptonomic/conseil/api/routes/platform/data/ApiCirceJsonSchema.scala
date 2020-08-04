package tech.cryptonomic.conseil.api.routes.platform.data

import cats.Functor
import endpoints.akkahttp.server.circe.{JsonSchemaEntities => AkkaCirceJsonSchema}
import io.circe._
import io.circe.syntax._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

/** Trait with methods for converting from data types to Json */
trait ApiCirceJsonSchema extends AkkaCirceJsonSchema with ApiDataJsonSchemas {

  private lazy val anyEncoder: Encoder[Any] =
    Encoder.instance(x => defaultAnyEncoder orElse customAnyEncoder applyOrElse (x, fallbackAnyEncoderValue))

  /** Represents the function, that is going to encode the most common types */
  private def defaultAnyEncoder: PartialFunction[Any, Json] = {
    case x: java.lang.String => Json.fromString(x)
    case x: java.lang.Integer => Json.fromInt(x)
    case x: java.lang.Long => Json.fromLong(x)
    case x: java.sql.Timestamp => Json.fromLong(x.getTime)
    case x: java.lang.Boolean => Json.fromBoolean(x)
    case x: scala.collection.immutable.Vector[Any] =>
      x.map(_.asJson(anyEncoder)).asJson //Due to type erasure, a recursive call is made here.
    case x: java.math.BigDecimal => Json.fromBigDecimal(x)
  }

  /** Represents the function, that is going to encode the blockchain specific data types */
  protected def customAnyEncoder: PartialFunction[Any, Json]

  /** Represents the function, that is going to return the fallback value (when any other encoder will fail) */
  private def fallbackAnyEncoderValue: Any => Json = x => Json.fromString(x.toString)

  /** JSON schema implementation for Any */
  implicit def anySchema: JsonSchema[Any] = new JsonSchema[Any] {
    override def encoder: Encoder[Any] = anyEncoder

    override def decoder: Decoder[Any] =
      (c: HCursor) => {
        // without this check strings are deserialized in double quotes for example "String" instead of String
        if (c.value.isString) {
          Right(c.value.asString.get)
        } else {
          Right(c.value)
        }
      }
  }

  /** Query response JSON schema implementation */
  implicit def queryResponseSchema: JsonSchema[QueryResponse] =
    new JsonSchema[QueryResponse] {
      override def encoder: Encoder[QueryResponse] =
        (a: QueryResponse) =>
          Json.obj(a.map {
            case (fieldName, fieldValue) => (fieldName, fieldValue.map(_.asJson(anyEncoder)).getOrElse(Json.Null))
          }.toList: _*)

      //This shouldn't actually be used anywhere, in any case we can go as far as
      //assume that anything in the value is coming from json, but nothing more
      override def decoder: Decoder[QueryResponse] =
        Decoder.decodeMap(
          KeyDecoder.decodeKeyString,
          Decoder.decodeOption(Decoder.decodeJson.map(_.asInstanceOf[Any]))
        )
    }

  /** Query response with output type JSON schema implementation */
  implicit def queryResponseSchemaWithOutputType: JsonSchema[QueryResponseWithOutput] =
    new JsonSchema[QueryResponseWithOutput] {
      override def encoder: Encoder[QueryResponseWithOutput] =
        (a: QueryResponseWithOutput) => a.queryResponse.asJson(Encoder.encodeList(queryResponseSchema.encoder))

      override def decoder: Decoder[QueryResponseWithOutput] =
        throw new UnsupportedOperationException("Decoder for 'QueryResponseWithOutput' should never be needed.")
    }

  def defaultQsFunctor: Functor[QueryString] = new Functor[QueryString] {
    override def map[From, To](f: QueryString[From])(mapping: From => To): QueryString[To] = new QueryString[To](
      f.directive.map(mapping)
    )
  }

  implicit val fieldSchema: JsonSchema[Field] = fieldJsonSchema(formattedFieldSchema)

  /** Creates 'JsonSchema' for 'Field' entity */
  private def fieldJsonSchema(formattedFieldSchema: JsonSchema[FormattedField]): JsonSchema[Field] =
    new JsonSchema[DataTypes.Field] {
      override def encoder: Encoder[Field] = {
        case SimpleField(field) => field.asJson
        case ff: FormattedField => ff.asJson(formattedFieldSchema.encoder)
      }

      override def decoder: Decoder[Field] =
        (c: HCursor) =>
          c.value match {
            case jsonString if jsonString.isString => Right(SimpleField(jsonString.asString.get))
            case formattedField => formattedField.as[FormattedField](formattedFieldSchema.decoder)
          }
    }

}
