package tech.cryptonomic.conseil.util

/** This expose decoders for json conversions */
object JsonDecoders {

  /** Circe-specific definitions as implicits */
  object Circe {

    import io.circe.Decoder
    import io.circe.generic.extras._
    import io.circe.generic.extras.semiauto._
    import io.circe.generic.extras.Configuration
    import tech.cryptonomic.conseil.tezos.TezosTypes._

    private final case class Base58Check(content: String) extends AnyVal

    private implicit val base58CheckDecoder: Decoder[Base58Check] =
      Decoder.decodeString
        .map(_.trim)
        .ensure(isBase58Check, "The passed-in json string is not a proper Base58Check encoding")
        .map(Base58Check)

    implicit val nonceDecoder: Decoder[Nonce] =
      Decoder.decodeString
        .map(_.trim)
        .ensure((_: String).forall(_.isLetterOrDigit), "The passed-in json string is not a valid nonce")
        .map(Nonce)

    implicit val pkhDecoder: Decoder[PublicKeyHash] = base58CheckDecoder.map(b58 => PublicKeyHash(b58.content))
    implicit val signatureDecoder: Decoder[Signature] = base58CheckDecoder.map(b58 => Signature(b58.content))
    implicit val blockHashDecoder: Decoder[BlockHash] = base58CheckDecoder.map(b58 => BlockHash(b58.content))
    implicit val opHashDecoder: Decoder[OperationHash] = base58CheckDecoder.map(b58 => OperationHash(b58.content))
    implicit val contractIdDecoder: Decoder[ContractId] = base58CheckDecoder.map(b58 => ContractId(b58.content))
    implicit val accountIdDecoder: Decoder[AccountId] = base58CheckDecoder.map(b58 => AccountId(b58.content))
    implicit val chainIdDecoder: Decoder[ChainId] = base58CheckDecoder.map(b58 => ChainId(b58.content))

    object Operations {
      implicit val derivationConfig: Configuration =
        Configuration.default
          .withDiscriminator("kind")
          .withSnakeCaseConstructorNames

      implicit val balanceUpdateDecoder: Decoder[TezosOperations.OperationMetadata.BalanceUpdate] = deriveDecoder
      implicit val endorsementMetadataDecoder: Decoder[TezosOperations.EndorsementMetadata] = deriveDecoder
      implicit val seedNonceRevelationMetadataDecoder: Decoder[TezosOperations.SeedNonceRevelationMetadata] = deriveDecoder
      implicit val operationDecoder: Decoder[TezosOperations.Operation] = deriveDecoder[TezosOperations.Operation]
      implicit val operationGroupDecoder: Decoder[TezosOperations.Group] = deriveDecoder

    }

  }


}