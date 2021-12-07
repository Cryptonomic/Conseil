package tech.cryptonomic.conseil.platform.data

import io.circe.generic.semiauto._
import sttp.tapir._
import sttp.tapir.json.circe._
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import io.circe.{Decoder, Encoder, Json}
// import tech.cryptonomic.conseil.common.tezos.Tables

trait ApiDataEndpoints {

  protected def commonPath(platform: String) =
    infallibleEndpoint.in("v2" / "data" / platform / "network" / "entity")

  // implicit val outputTypeCodec = deriveCodec[OutputType]
  // implicit val queryResponseCodec = deriveCodec[QueryResponse]

  implicit val keyTypeCodec = deriveCodec[KeyType]
  implicit val dataTypeCodec = deriveCodec[DataType]

  implicit val attributeCacheCodec = deriveCodec[AttributeCacheConfiguration]
  implicit val attributesCodec = deriveCodec[Attribute]

  // implicit val anything = deriveCodec[Option[Any]]
  /** Implementation of JSON encoder for Any */
  implicit val anyEncoder: Encoder[Any] = // deriveEncoder[Typ]
    Encoder[Any].contramap {
      case x: java.lang.String => Json.fromString(x)
      case x: java.lang.Integer => Json.fromInt(x)
      case x: java.sql.Timestamp => Json.fromLong(x.getTime)
      case x: java.lang.Boolean => Json.fromBoolean(x)
    }

  implicit val anyDecoder: Decoder[Any] = ???

  implicit val otCodec = deriveCodec[OutputType]
  implicit val qrCodec = deriveCodec[QR]
  implicit val typCodec = deriveCodec[QueryResponseWithOutput]
  // implicit val typEncoder = deriveEncoder[QueryResponseWithOutput]
  // implicit val typDecoder = deriveDecoder[QueryResponseWithOutput]
  // implicit lazy val anything: Schema[Any] = Schema.derived
  // implicit lazy val xd: Schema[QueryResponse] = Schema.derived
  // implicit lazy val st: Schema[QR] = Schema.derived
  // implicit lazy val sth: Schema[QueryResponseWithOutput] = Schema.derived

  // implicit val anyEncoder: Encoder[Any] =
  //   (a: Any) =>
  //     a match {
  //       case x: java.lang.String => Json.fromString(x)
  //       case x: java.lang.Integer => Json.fromInt(x)
  //       case x: java.sql.Timestamp => Json.fromLong(x.getTime)
  //       case x: java.lang.Boolean => Json.fromBoolean(x)
  //       // case x: scala.collection.immutable.Vector[Any] =>
  //       //   x.map(_.asJson(anyEncoder)).asJson // Due to type erasure, a recursive call is made here.
  //       // case x: Tables.BlocksRow => x.asJson(blocksRowSchema.encoder)
  //       // case x: Tables.AccountsRow => x.asJson(accountsRowSchema.encoder)
  //       // case x: Tables.OperationGroupsRow => x.asJson(operationGroupsRowSchema.encoder)
  //       // case x: Tables.OperationsRow => x.asJson(operationsRowSchema.encoder)
  //       case x: java.math.BigDecimal => Json.fromBigDecimal(x)
  //       case x => Json.fromString(x.toString)
  //     }

  /** V2 Query endpoint definition */
  // def queryEndpoint(platform: String): Endpoint[Unit, Unit, Nothing, QueryResponseWithOutput, Any] =
  // import sttp.tapir._
  // import sttp.tapir.json.circe._
  // import sttp.tapir.generic.auto._
  def queryEndpoint(platform: String) =
    commonPath(platform).post
  // .out(jsonBodyQueryResponseWithOutput])

  /** Common method for compatibility queries */
  def compatibilityQuery[A: Encoder: Decoder: Schema](endpointName: String) = jsonBody[A]

}
