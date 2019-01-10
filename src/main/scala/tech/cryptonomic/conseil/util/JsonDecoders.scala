package tech.cryptonomic.conseil.util

/** This expose decoders for json conversions */
object JsonDecoders {

  /** Circe-specific definitions as implicits */
  object Circe {

    import io.circe.Decoder
    import tech.cryptonomic.conseil.tezos.TezosTypes.{isBase58Check, PublicKeyHash, Signature, BlockHash, OperationHash, AccountId}

    private final case class Base58Check(content: String) extends AnyVal

    private implicit lazy val base58CheckDecoder: Decoder[Base58Check] =
      Decoder.decodeString
        .map(_.trim)
        .ensure(isBase58Check, "The passed-in json string is not a proper Base58Check encoding")
        .map(Base58Check)

    implicit lazy val pkhDecoder: Decoder[PublicKeyHash] = base58CheckDecoder.map(b58 => PublicKeyHash(b58.content))
    implicit lazy val signatureDecoder: Decoder[Signature] = base58CheckDecoder.map(b58 => Signature(b58.content))
    implicit lazy val blockHashDecoder: Decoder[BlockHash] = base58CheckDecoder.map(b58 => BlockHash(b58.content))
    implicit lazy val opHashDecoder: Decoder[OperationHash] = base58CheckDecoder.map(b58 => OperationHash(b58.content))
    implicit lazy val accountIdDecoder: Decoder[AccountId] = base58CheckDecoder.map(b58 => AccountId(b58.content))

  }


}