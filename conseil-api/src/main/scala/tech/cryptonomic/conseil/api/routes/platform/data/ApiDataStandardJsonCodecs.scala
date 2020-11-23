package tech.cryptonomic.conseil.api.routes.platform.data

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{QueryResponse}
import endpoints.algebra.{Decoder, Encoder}
import endpoints.{Invalid, Valid}

/** Provides basic codecs from/to a specific json modeling (i.e. [[ujson]]) for specific types
  * exposed via the data api of conseil.
  */
object ApiDataStandardJsonCodecs {

  type Json = ujson.Value

  /* common types encoding */
  private def defaultAnyEncoder: PartialFunction[Any, Json] = {
    case x: java.lang.String => ujson.Str(x)
    case x: java.lang.Integer => ujson.Num(x.toDouble)
    case x: java.lang.Long => ujson.Num(x.toDouble)
    case x: java.sql.Timestamp => ujson.Num(x.getTime)
    case x: java.lang.Boolean => ujson.Bool(x)
    case x: scala.collection.immutable.Vector[Any] => ujson.Arr(x.map(anyEncoder.encode): _*)
    case x: java.math.BigDecimal =>
      ujson.Num(x.doubleValue) //<- we might be losing precision or even downright get a wrong conversion
  }

  /* Represents the function, that is going to return the fallback value (when any other encoder will fail) */
  private def fallbackAnyEncoderValue: PartialFunction[Any, Json] = { case x => ujson.Str(x.toString()) }

  /** Default implementation of the encoding for a totally arbitrary value */
  lazy val anyEncoder: Encoder[Any, Json] = customAnyEncoder(PartialFunction.empty)

  /** Adds a custom step to define encodings for specific types tailored to a custom domain */
  def customAnyEncoder(customisation: PartialFunction[Any, Json]): Encoder[Any, Json] =
    (x: Any) => (defaultAnyEncoder orElse customisation orElse fallbackAnyEncoderValue)(x)

  /** Default implementation of the decoding of a totally arbitrary value */
  lazy val anyDecoder: Decoder[Json, Any] =
    // verify if strings are correclty decoded with no surroundng quotes, as "String"...
    (json: Json) => Valid(json.value)

  /** Default implementation to encode the [[QueryResponse]] */
  lazy val queryResponseEncoder: Encoder[QueryResponse, Json] =
    (a: QueryResponse) =>
      ujson.Obj.from(
        a.mapValues {
          case Some(value) => anyEncoder.encode(value)
          case None => ujson.Null
        }
      )

  /** Default implementation to encode the [[QueryResponse]]
    * This shouldn't actually be used anywhere, in any case we can go as far as
    * assume that anything in the value is coming from json, but nothing more
    */
  lazy val queryResponseDecoder: Decoder[Json, QueryResponse] =
    (json: Json) =>
      json.objOpt match {
        case Some(jsonObject) =>
          Valid(
            jsonObject.mapValues(anyDecoder.decode(_).toEither.toOption).toMap
          )
        case None =>
          Invalid(Seq(s"I can only convert proper json objects to an api query response. The input was $json"))
      }
}
