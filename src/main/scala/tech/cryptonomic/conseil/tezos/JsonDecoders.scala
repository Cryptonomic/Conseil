package tech.cryptonomic.conseil.tezos

/** This expose decoders for json conversions */
object JsonDecoders {

  /** Circe-specific definitions as implicits */
  object Circe {

    import io.circe.Decoder
    import cats.syntax.functor._
    import io.circe.{Error, Errors, JsonObject}
    import io.circe.generic.extras._
    import io.circe.generic.extras.semiauto._
    import io.circe.generic.extras.Configuration
    import tech.cryptonomic.conseil.tezos.TezosTypes._

    type JsonDecoded[T] = Either[Error, T]

    /** Collects failures in json parsing, when circe is used to decode the objects out of strings
      *
      * @param jsonResults a list of generic input data that internally has some json string
      * @param decoding the operation that converts a single generic Encoded value to a possibly failed Decoded type
      * @tparam Encoded a json string, possibly with additional data, e.g. the original request correlation-id for our tezos-rpc
      * @tparam Decoded the type of the final result of correctly decoding the input json
      * @return an `Either` with all `io.circe.Errors` aggregated on the `Left` if there's any, or a `Right` with all the decoded results
      */
    def handleDecodingErrors[Encoded, Decoded](jsonResults: List[Encoded], decoding: Encoded => Either[Error, Decoded]): Either[Errors, List[Decoded]] = {
      import cats.data._
      import cats.syntax.either._

      val results = jsonResults.map(decoding)

      lazy val correctlyDecoded: List[Decoded] = results.collect { case Right(dec) => dec }
      val decodingErrors: Option[io.circe.Errors] =
      NonEmptyList.fromList(results.collect { case Left(decodingError) => decodingError })
      .map(io.circe.Errors)

      decodingErrors.fold(ifEmpty = correctlyDecoded.asRight[io.circe.Errors])(_.asLeft[List[Decoded]])
    }

    /* local definition of a base-58-check string wrapper, to allow parsing validation */
    private final case class Base58Check(content: String) extends AnyVal

    //lookup a key definition and, if found, swaps the key with a new field name, removing the old field altogether
    private def convertJsonFieldName(from: String, to: String) = (json: JsonObject) =>
      json(from).map(
        value => json.remove(from).add(to, value)
      ).getOrElse(json)

    /* use this to decode starting from string, adding format validation on the string to build another object based on valid results */
    private def decoderForValidString[T](validateString: String => Boolean, failedValidation: String, DecodedConstructor: String => T): Decoder[T] =
      Decoder.decodeString
        .map(_.trim)
        .ensure(validateString, failedValidation)
        .map(DecodedConstructor)

    /* decode only base58check-encoded strings */
    private implicit val base58CheckDecoder: Decoder[Base58Check] =
      decoderForValidString(
        validateString = isBase58Check,
        failedValidation = "The passed-in json string is not a proper Base58Check encoding",
        DecodedConstructor = Base58Check
      )

    /* decode only valid nonces */
    implicit val nonceDecoder: Decoder[Nonce] =
      decoderForValidString(
        validateString = _.forall(_.isLetterOrDigit),
        failedValidation = "The passed-in json string is not a valid nonce",
        DecodedConstructor = Nonce
      )

    /* decode only valid secrets */
    implicit val secretDecoder: Decoder[Secret] =
      decoderForValidString(
        validateString = _.forall(_.isLetterOrDigit),
        failedValidation = "The passed-in json string is not a valid secret",
        DecodedConstructor = Secret
      )

    /* decode any json value to its string representation wrapped in a Micheline*/
    implicit val michelineDecoder: Decoder[Micheline] =
      Decoder.decodeJson.map(json => Micheline(json.noSpaces))


    // The following are all b58check-encoded wrappers, that use the generic decoder to guarantee correct encoding of the internal string
    implicit val publicKeyDecoder: Decoder[PublicKey] = base58CheckDecoder.map(b58 => PublicKey(b58.content))
    implicit val pkhDecoder: Decoder[PublicKeyHash] = base58CheckDecoder.map(b58 => PublicKeyHash(b58.content))
    implicit val signatureDecoder: Decoder[Signature] = base58CheckDecoder.map(b58 => Signature(b58.content))
    implicit val blockHashDecoder: Decoder[BlockHash] = base58CheckDecoder.map(b58 => BlockHash(b58.content))
    implicit val opHashDecoder: Decoder[OperationHash] = base58CheckDecoder.map(b58 => OperationHash(b58.content))
    implicit val contractIdDecoder: Decoder[ContractId] = base58CheckDecoder.map(b58 => ContractId(b58.content))
    implicit val accountIdDecoder: Decoder[AccountId] = base58CheckDecoder.map(b58 => AccountId(b58.content))
    implicit val chainIdDecoder: Decoder[ChainId] = base58CheckDecoder.map(b58 => ChainId(b58.content))
    implicit val scriptIdDecoder: Decoder[ScriptId] = base58CheckDecoder.map(b58 => ScriptId(b58.content))

    /*
     * Collects definitions of decoders for the Operations hierarchy.
     * Import this in scope to be able to call `io.circe.parser.decode[T](json)` for a valid type of operation
     */
    object Operations {

      /* decode any json value to its string representation wrapped in a Error*/
      implicit val errorDecoder: Decoder[OperationResult.Error] =
        Decoder.decodeJson.map(json => OperationResult.Error(json.noSpaces))

      /* try decoding a number */
      private implicit val bignumDecoder: Decoder[Decimal] =
        Decoder.decodeString
          .emapTry(jsonString => scala.util.Try(BigDecimal(jsonString)))
          .map(Decimal)

      /* try decoding a positive number */
      private implicit val positiveBignumDecoder: Decoder[PositiveDecimal] =
        Decoder.decodeString
          .emapTry(jsonString => scala.util.Try(BigDecimal(jsonString)))
          .ensure(_ >= 0, "The passed-in json string is not a non-negative number")
          .map(PositiveDecimal)

      /* read any string and wrap it */
      private implicit val invalidBignumDecoder: Decoder[InvalidDecimal] =
        Decoder.decodeString
          .map(InvalidDecimal)

      /* read any string and wrap it */
      private implicit val invalidPositiveBignumDecoder: Decoder[InvalidPositiveDecimal] =
        Decoder.decodeString
          .map(InvalidPositiveDecimal)

      /* decodes in turn each subtype, failing that will fallthrough to the next one */
      implicit val bigPositiveDecoder: Decoder[PositiveBigNumber] =
        List[Decoder[PositiveBigNumber]](
          Decoder[PositiveDecimal].widen,
          Decoder[InvalidPositiveDecimal].widen
        ).reduceLeft(_ or _)

      /* decodes in turn each subtype, failing that will fallthrough to the next one */
      implicit val bigNumberDecoder: Decoder[BigNumber] =
        List[Decoder[BigNumber]](
          Decoder[Decimal].widen,
          Decoder[InvalidDecimal].widen
        ).reduceLeft(_ or _)

      //use the kind field to distinguish subtypes of the Operation ADT
      implicit val derivationConfig: Configuration =
        Configuration.default
          .withDiscriminator("kind")
          .withSnakeCaseConstructorNames

      //derive all the remaining decoders, sorted to preserve dependencies
      implicit val bigmapdiffDecoder: Decoder[Contract.BigMapDiff] = deriveDecoder
      implicit val scriptedContractsDecoder: Decoder[Scripted.Contracts] = deriveDecoder
      implicit val balanceUpdateDecoder: Decoder[OperationMetadata.BalanceUpdate] = deriveDecoder
      implicit val endorsementMetadataDecoder: Decoder[EndorsementMetadata] = deriveDecoder
      implicit val balanceUpdatesMetadataDecoder: Decoder[BalanceUpdatesMetadata] = deriveDecoder
      implicit val revealResultDecoder: Decoder[OperationResult.Reveal] = deriveDecoder
      implicit val transactionResultDecoder: Decoder[OperationResult.Transaction] = deriveDecoder
      implicit val originationResultDecoder: Decoder[OperationResult.Origination] = deriveDecoder
      implicit val delegationResultDecoder: Decoder[OperationResult.Delegation] = deriveDecoder
      implicit val revealMetadataDecoder: Decoder[ResultMetadata[OperationResult.Reveal]] = deriveDecoder
      implicit val transactionMetadataDecoder: Decoder[ResultMetadata[OperationResult.Transaction]] = deriveDecoder
      implicit val originationMetadataDecoder: Decoder[ResultMetadata[OperationResult.Origination]] = deriveDecoder
      implicit val delegationMetadataDecoder: Decoder[ResultMetadata[OperationResult.Delegation]] = deriveDecoder
      //the json needs pre-processing, to see if we need to adapt the schema version between different networks
      implicit val operationDecoder: Decoder[Operation] =
        deriveDecoder[Operation].prepare { cursor =>
          if (cursor.keys.exists(_.exists(_ == "managerPubkey"))) {
            //dig with the cursor to get the json object and update it
            cursor.withFocus(_.mapObject(
              convertJsonFieldName(from = "managerPubkey", to = "manager_pubkey")
            ))
          }
          else cursor
        }
      implicit val operationGroupDecoder: Decoder[OperationsGroup] = deriveDecoder

    }

  }


}