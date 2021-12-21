package tech.cryptonomic.conseil.platform.data

import io.circe._
import io.circe.generic.semiauto._
import io.circe.syntax._

import tech.cryptonomic.conseil.common.tezos.Tables._
import tech.cryptonomic.conseil.platform.data.tezos.TezosDataOperations._

object converters {

  implicit val TimestampCodec: Encoder[java.sql.Timestamp] with Decoder[java.sql.Timestamp] =
    new Encoder[java.sql.Timestamp] with Decoder[java.sql.Timestamp] {
      override def apply(a: java.sql.Timestamp): Json = Encoder.encodeLong.apply(a.getTime)
      override def apply(c: HCursor): Decoder.Result[java.sql.Timestamp] =
        Decoder.decodeLong.map(s => new java.sql.Timestamp(s)).apply(c)
    }

  implicit val accountsRowCodec = deriveCodec[AccountsRow]
  implicit val blocksRowCodec = deriveCodec[BlocksRow]
  implicit val operationGroupsRowCodec = deriveCodec[OperationGroupsRow]
  implicit val operationsRowCodec = deriveCodec[OperationsRow]

  implicit val blockResultCodec = deriveCodec[BlockResult]
  implicit val operationGroupResultCodec = deriveCodec[OperationGroupResult]
  implicit val accountResultCodec = deriveCodec[AccountResult]

  /** Implementation of JSON encoder for Any */
  implicit val anyEncoder: Encoder[Any] =
    Encoder[Any].contramap {
      case x: java.lang.String => Json.fromString(x)
      case x: java.lang.Integer => Json.fromInt(x)
      case x: java.sql.Timestamp => Json.fromLong(x.getTime)
      case x: java.lang.Boolean => Json.fromBoolean(x)
      case x: scala.collection.immutable.Vector[Any] =>
        x.map(_.asJson(anyEncoder)).asJson // Due to type erasure, a recursive call is made here.
      case x: BlocksRow => x.asJson(blocksRowCodec)
      case x: AccountsRow => x.asJson(accountsRowCodec)
      case x: OperationGroupsRow => x.asJson(operationGroupsRowCodec)
      case x: OperationsRow => x.asJson(operationsRowCodec)
      case x: java.math.BigDecimal => Json.fromBigDecimal(x)
      case x => Json.fromString(x.toString)
    }

  implicit val anyDecoder: Decoder[Any] = ???

}
