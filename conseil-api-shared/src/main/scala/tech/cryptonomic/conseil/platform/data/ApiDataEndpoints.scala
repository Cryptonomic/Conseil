package tech.cryptonomic.conseil.platform.data

import sttp.tapir._
import sttp.tapir.json.circe._
import io.circe.{Decoder, Encoder, Json}

trait ApiDataEndpoints {

  protected def commonPath(platform: String) =
    infallibleEndpoint.in("v2" / "data" / platform / "network" / "entity")

  /** Implementation of JSON encoder for Any */
  implicit val anyEncoder: Encoder[Any] =
    Encoder[Any].contramap {
      case x: java.lang.String => Json.fromString(x)
      case x: java.lang.Integer => Json.fromInt(x)
      case x: java.sql.Timestamp => Json.fromLong(x.getTime)
      case x: java.lang.Boolean => Json.fromBoolean(x)
      // case x: scala.collection.immutable.Vector[Any] =>
      //   x.map(_.asJson(anyEncoder)).asJson // Due to type erasure, a recursive call is made here.
      // case x: Tables.BlocksRow => x.asJson(blocksRowSchema.encoder)
      // case x: Tables.AccountsRow => x.asJson(accountsRowSchema.encoder)
      // case x: Tables.OperationGroupsRow => x.asJson(operationGroupsRowSchema.encoder)
      // case x: Tables.OperationsRow => x.asJson(operationsRowSchema.encoder)
      case x: java.math.BigDecimal => Json.fromBigDecimal(x)
      case x => Json.fromString(x.toString)
    }

  implicit val anyDecoder: Decoder[Any] = ???

  /** V2 Query endpoint definition */
  def queryEndpoint(platform: String) =
    commonPath(platform).post
  // .out(jsonBodyQueryResponseWithOutput])

  /** Common method for compatibility queries */
  def compatibilityQuery[A: Encoder: Decoder: Schema](endpointName: String) = jsonBody[A]

}
