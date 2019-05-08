package tech.cryptonomic.conseil.tezos

import scala.util.Try
import tech.cryptonomic.conseil.tezos.TezosTypes._

/** This expose decoders for json conversions */
object JsonDecoders {

  /** Circe-specific definitions as implicits */
  object Circe {

    import cats.ApplicativeError
    import cats.syntax.functor._
    import io.circe.Decoder
    import io.circe.generic.extras._
    import io.circe.generic.extras.semiauto._
    import io.circe.generic.extras.Configuration

    type JsonDecoded[T] = Either[Error, T]

    /** Helper to decode json and convert to any effectful result that can
     *  raise errors, as implied with the type class contraint
      * This is not necessarily running any async operation
      */
    def decodeLiftingTo[Eff[_], A: io.circe.Decoder](json: String)(implicit app: ApplicativeError[Eff, Throwable]): Eff[A] = {
      import io.circe.parser.decode
      import cats.instances.either._
      import cats.syntax.either._
      import cats.syntax.bifunctor._

      decode[A](json).leftWiden[Throwable].raiseOrPure[Eff]
    }

    /* local definition of a base-58-check string wrapper, to allow parsing validation */
    private final case class Base58Check(content: String) extends AnyVal

    /* use this to decode starting from string, adding format validation on the string to build another object based on valid results */
    private def deriveDecoderFromString[T](validateString: String => Boolean, failedValidation: String, decodedConstructor: String => T): Decoder[T] =
      Decoder.decodeString
        .map(_.trim)
        .ensure(validateString, failedValidation)
        .map(decodedConstructor)

    /* decode only base58check-encoded strings */
    private implicit val base58CheckDecoder: Decoder[Base58Check] =
      deriveDecoderFromString(
        validateString = isBase58Check,
        failedValidation = "The passed-in json string is not a proper Base58Check encoding",
        decodedConstructor = Base58Check
      )

    /* decode only valid nonces */
    implicit val nonceDecoder: Decoder[Nonce] =
      deriveDecoderFromString(
        validateString = _.forall(_.isLetterOrDigit),
        failedValidation = "The passed-in json string is not a valid nonce",
        decodedConstructor = Nonce
      )

    /* decode only valid secrets */
    implicit val secretDecoder: Decoder[Secret] =
      deriveDecoderFromString(
        validateString = _.forall(_.isLetterOrDigit),
        failedValidation = "The passed-in json string is not a valid secret",
        decodedConstructor = Secret
      )

    /* decode any json value to its string representation wrapped in a Micheline*/
    implicit val michelineDecoder: Decoder[Micheline] =
      Decoder.decodeJson.map(json => Micheline(json.noSpaces))

    /* decode an enumerated string to a valid VotingPeriod Kind */
    implicit val votingPeriodKindDecoder: Decoder[VotingPeriod.Kind] =
      Decoder.decodeString.emapTry(kind => Try(VotingPeriod.withName(kind)))

    // The following are all b58check-encoded wrappers, that use the generic decoder to guarantee correct encoding of the internal string
    implicit val publicKeyDecoder: Decoder[PublicKey] = base58CheckDecoder.map(b58 => PublicKey(b58.content))
    implicit val pkhDecoder: Decoder[PublicKeyHash] = base58CheckDecoder.map(b58 => PublicKeyHash(b58.content))
    implicit val signatureDecoder: Decoder[Signature] = base58CheckDecoder.map(b58 => Signature(b58.content))
    implicit val blockHashDecoder: Decoder[BlockHash] = base58CheckDecoder.map(b58 => BlockHash(b58.content))
    implicit val opHashDecoder: Decoder[OperationHash] = base58CheckDecoder.map(b58 => OperationHash(b58.content))
    implicit val contractIdDecoder: Decoder[ContractId] = base58CheckDecoder.map(b58 => ContractId(b58.content))
    implicit val accountIdDecoder: Decoder[AccountId] = base58CheckDecoder.map(b58 => AccountId(b58.content))
    implicit val chainIdDecoder: Decoder[ChainId] = base58CheckDecoder.map(b58 => ChainId(b58.content))
    implicit val protocolIdDecoder: Decoder[ProtocolId] = base58CheckDecoder.map(b58 => ProtocolId(b58.content))
    implicit val scriptIdDecoder: Decoder[ScriptId] = base58CheckDecoder.map(b58 => ScriptId(b58.content))
    implicit val nonceHashDecoder: Decoder[NonceHash] = base58CheckDecoder.map(b58 => NonceHash(b58.content))

    val tezosDerivationConfig: Configuration =
    Configuration.default.withSnakeCaseConstructorNames

    /* Collects definitions to decode voting data and their components */
    object Votes {
      import Voting._
      private implicit val conf = tezosDerivationConfig

      private val admittedVotes = Set("yay", "nay", "pass")

      implicit val ballotVoteDecoder: Decoder[Vote] =
        deriveDecoderFromString(
          validateString = admittedVotes,
          failedValidation = "The passed-in json string is not allowed as a ballot vote",
          decodedConstructor = Vote
        )

      implicit val bakerDecoder: Decoder[BakerRolls] = deriveDecoder
      implicit val ballotDecoder: Decoder[Ballot] = deriveDecoder
      implicit val bakersDecoder: Decoder[List[BakerRolls]] =
        Decoder.decodeList[BakerRolls]
      implicit val ballotsDecoder: Decoder[List[Ballot]] =
        Decoder.decodeList[Ballot]
      implicit val protocolIdsDecoder: Decoder[List[ProtocolId]] =
        Decoder.decodeList[ProtocolId]
    }

    /* Collects definitions to decode blocks and their components */
    object Blocks {
      // we need to decode BalanceUpdates
      import Operations._
      private implicit val conf = tezosDerivationConfig

      val genesisMetadataDecoder: Decoder[GenesisMetadata.type] = deriveDecoder
      implicit val metadataLevelDecoder: Decoder[BlockHeaderMetadataLevel] = deriveDecoder
      val blockMetadataDecoder: Decoder[BlockHeaderMetadata] = deriveDecoder
      implicit val metadataDecoder: Decoder[BlockMetadata] = blockMetadataDecoder.widen or genesisMetadataDecoder.widen
      implicit val headerDecoder: Decoder[BlockHeader] = deriveDecoder
      implicit val mainDecoder: Decoder[BlockData] = deriveDecoder //remember to add ISO-control filtering
    }

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
      private implicit val conf = tezosDerivationConfig.withDiscriminator("kind")

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
      implicit val operationDecoder: Decoder[Operation] = deriveDecoder
      implicit val operationGroupDecoder: Decoder[OperationsGroup] = deriveDecoder

    }

    /* Collects definitions to decode accounts and their components */
    object Accounts {
      private implicit val conf = tezosDerivationConfig

      import JsonDecoders.Circe.Operations.scriptedContractsDecoder

      implicit val delegateDecoder: Decoder[AccountDelegate] = deriveDecoder
      implicit val accountDecoder: Decoder[Account] = deriveDecoder
    }

  }
}