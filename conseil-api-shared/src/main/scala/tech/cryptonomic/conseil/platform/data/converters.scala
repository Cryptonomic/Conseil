package tech.cryptonomic.conseil.platform.data

import io.circe._
import io.circe.generic.semiauto._

import tech.cryptonomic.conseil.common.tezos.Tables._
import tech.cryptonomic.conseil.platform.data.tezos.TezosDataOperations._

object converters {

  implicit val TimestampCodec: Encoder[java.sql.Timestamp] with Decoder[java.sql.Timestamp] =
    new Encoder[java.sql.Timestamp] with Decoder[java.sql.Timestamp] {
      override def apply(a: java.sql.Timestamp): Json = Encoder.encodeLong.apply(a.getTime)
      override def apply(c: HCursor): Decoder.Result[java.sql.Timestamp] =
        Decoder.decodeLong.map(s => new java.sql.Timestamp(s)).apply(c)
    }

  implicit val accountRowCodec = deriveCodec[AccountsRow]
  implicit val blocksRowCodec = deriveCodec[BlocksRow]
  implicit val operationGroupsRowCodec = deriveCodec[OperationGroupsRow]
  implicit val operationsRowCodec = deriveCodec[OperationsRow]

  implicit val blockResultCodec = deriveCodec[BlockResult]
  implicit val operationGroupResultCodec = deriveCodec[OperationGroupResult]
  implicit val accountResultCodec = deriveCodec[AccountResult]

}
