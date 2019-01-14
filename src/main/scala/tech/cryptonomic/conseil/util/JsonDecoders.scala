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

    /* local definition of a base-58-check string wrapper, to allow parsing validation */
    private final case class Base58Check(content: String) extends AnyVal

    /* use this to decode starting from string, adding format validation on the string to build another object based on valid results */
    private def deriveDecoderFromString[T](validateString: String => Boolean, failedValidation: String, DecodedConstructor: String => T): Decoder[T] =
      Decoder.decodeString
        .map(_.trim)
        .ensure(validateString, failedValidation)
        .map(DecodedConstructor)

    /* decode only base58check-encoded strings */
    private implicit val base58CheckDecoder: Decoder[Base58Check] =
      deriveDecoderFromString(
        validateString = isBase58Check,
        failedValidation = "The passed-in json string is not a proper Base58Check encoding",
        DecodedConstructor = Base58Check
      )

    /* decode only valid nonces */
    implicit val nonceDecoder: Decoder[Nonce] =
      deriveDecoderFromString(
        validateString = _.forall(_.isLetterOrDigit),
        failedValidation = "The passed-in json string is not a valid nonce",
        DecodedConstructor = Nonce
      )

    /* decode only valid secrets */
    implicit val secretDecoder: Decoder[Secret] =
      deriveDecoderFromString(
        validateString = _.forall(_.isLetterOrDigit),
        failedValidation = "The passed-in json string is not a valid secret",
        DecodedConstructor = Secret
      )

    // The following are all b58check-encoded wrappers, that use the generic decoder to guarantee correct encoding of the internal string
    implicit val pkhDecoder: Decoder[PublicKeyHash] = base58CheckDecoder.map(b58 => PublicKeyHash(b58.content))
    implicit val signatureDecoder: Decoder[Signature] = base58CheckDecoder.map(b58 => Signature(b58.content))
    implicit val blockHashDecoder: Decoder[BlockHash] = base58CheckDecoder.map(b58 => BlockHash(b58.content))
    implicit val opHashDecoder: Decoder[OperationHash] = base58CheckDecoder.map(b58 => OperationHash(b58.content))
    implicit val contractIdDecoder: Decoder[ContractId] = base58CheckDecoder.map(b58 => ContractId(b58.content))
    implicit val accountIdDecoder: Decoder[AccountId] = base58CheckDecoder.map(b58 => AccountId(b58.content))
    implicit val chainIdDecoder: Decoder[ChainId] = base58CheckDecoder.map(b58 => ChainId(b58.content))

    /*
     * Collects definitions of decoders for the Operations hierarchy.
     * Import this in scope to be able to call `io.circe.parser.decode[T](json)` for a valid type of operation
     */
    object Operations {

      //use the kind field to distinguish subtypes of the Operation ADT
      implicit val derivationConfig: Configuration =
        Configuration.default
          .withDiscriminator("kind")
          .withSnakeCaseConstructorNames

      //derive all needed decoders
      implicit val balanceUpdateDecoder: Decoder[TezosOperations.OperationMetadata.BalanceUpdate] = deriveDecoder
      implicit val endorsementMetadataDecoder: Decoder[TezosOperations.EndorsementMetadata] = deriveDecoder
      implicit val seedNonceRevelationMetadataDecoder: Decoder[TezosOperations.BalanceUpdatesMetadata] = deriveDecoder
      implicit val operationDecoder: Decoder[TezosOperations.Operation] = deriveDecoder[TezosOperations.Operation]
      implicit val operationGroupDecoder: Decoder[TezosOperations.Group] = deriveDecoder

    }

  }


}