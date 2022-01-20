package tech.cryptonomic.conseil.api.platform.data

import cats.syntax.functor._
import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.common.tezos.Tables._
import tech.cryptonomic.conseil.api.platform.data.tezos.TezosDataOperations._

private[data] object converters {

  implicit val timestampEncoder = Encoder.encodeLong.contramap[java.sql.Timestamp](_.getTime)
  implicit val timestampDecoder = Decoder.decodeLong.map(new java.sql.Timestamp(_))

  implicit val accountsRowCodec = deriveCodec[AccountsRow]
  implicit val blocksRowCodec = deriveCodec[BlocksRow]
  implicit val operationGroupsRowCodec = deriveCodec[OperationGroupsRow]
  implicit val operationsRowCodec = deriveCodec[OperationsRow]

  implicit val blockResultCodec = deriveCodec[BlockResult]
  implicit val operationGroupResultCodec = deriveCodec[OperationGroupResult]
  implicit val accountResultCodec = deriveCodec[AccountResult]

  // Evaluations of both `Any` encoder & decoder must be deferred
  implicit def anyEncoder: Encoder[Any] =
    Encoder[Any].contramap {
      case x: java.lang.Integer => Json.fromInt(x)
      case x: java.lang.Boolean => Json.fromBoolean(x)
      case x: java.math.BigDecimal => Json.fromBigDecimal(x)
      case x: java.sql.Timestamp => Json.fromLong(x.getTime)
      case x: BlocksRow => x.asJson
      case x: AccountsRow => x.asJson
      case x: OperationGroupsRow => x.asJson
      case x: OperationsRow => x.asJson
      case x: Vector[Any] => x.map(_.asJson(anyEncoder)).asJson // Due to type erasure, a recursive call is made here.
      case x: java.lang.String => Json.fromString(x)
      case x => Json.fromString(x.toString)
    }

  implicit def anyDecoder: Decoder[Any] =
    List[Decoder[Any]](
      Decoder.decodeJavaInteger.widen,
      Decoder.decodeJavaBoolean.widen,
      Decoder.decodeJavaBigDecimal.widen,
      Decoder[java.sql.Timestamp].widen,
      Decoder[BlocksRow].widen,
      Decoder[AccountsRow].widen,
      Decoder[OperationGroupsRow].widen,
      Decoder[OperationsRow].widen,
      Decoder[Vector[Any]].widen,
      Decoder.decodeString.widen,
      Decoder[Any].widen
    ).reduceLeft(_ or _) // FIXME: is the order correct for `or`?

  implicit val qrCodec = deriveCodec[QR]
  implicit val outputTypeCodec = deriveCodec[OutputType]
  implicit val queryResultWithOutputCodec = deriveCodec[QueryResponseWithOutput]

}
