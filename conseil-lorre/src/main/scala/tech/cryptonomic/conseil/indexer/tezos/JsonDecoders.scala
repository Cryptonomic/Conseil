package tech.cryptonomic.conseil.indexer.tezos

import com.github.ghik.silencer.silent
import tech.cryptonomic.conseil.common.tezos.TezosTypes
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.util.JsonUtil.CirceCommonDecoders.decodeUntaggedEither

import scala.util.Try

/** This expose decoders for json conversions */
private[tezos] object JsonDecoders {

  @silent("private val conf in object ")
  /** Circe-specific definitions as implicits */
  object Circe {

    import cats.ApplicativeError
    import cats.syntax.functor._
    import io.circe.Decoder
    import io.circe.generic.extras.Configuration
    import io.circe.generic.extras.semiauto._
    import tech.cryptonomic.conseil.common.util.JsonUtil.CirceCommonDecoders

    type JsonDecoded[T] = Either[Error, T]

    /** Helper to decode json and convert to any effectful result that can
      * raise errors, as implied with the type class contraint
      * This is not necessarily running any async operation
      */
    def decodeLiftingTo[Eff[_], A: io.circe.Decoder](
        json: String
    )(implicit app: ApplicativeError[Eff, Throwable]): Eff[A] = {
      import cats.instances.either._
      import cats.syntax.bifunctor._
      import cats.syntax.either._
      import io.circe.parser.decode

      decode[A](json).leftWiden[Throwable].raiseOrPure[Eff]
    }

    /* use this to decode starting from string, adding format validation on the string to build another object based on valid results */
    private def deriveDecoderFromString[T](
        validateString: String => Boolean,
        failedValidation: String,
        decodedConstructor: String => T
    ): Decoder[T] =
      Decoder.decodeString
        .map(_.trim)
        .ensure(validateString, failedValidation)
        .map(decodedConstructor)

    /* local definition of a base-58-check string wrapper, to allow parsing validation */
    final private case class Base58Check(content: String) extends AnyVal

    /* decode only base58check-encoded strings */
    implicit private val base58CheckDecoder: Decoder[Base58Check] =
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

    // holds a template for customization of derivation rules, or to use as-is, by importing it in scope as an implicit
    object Derivation {
      val tezosDerivationConfig: Configuration = Configuration.default.withSnakeCaseConstructorNames
    }

    object Scripts {
      implicit private val conf = Derivation.tezosDerivationConfig

      implicit val scriptedContractsDecoder: Decoder[Scripted.Contracts] = deriveDecoder
    }

    /* Collects definitions to decode delegates and their contracts */
    object Delegates {
      //reusing much of the values used in operations
      import Numbers._
      import Scripts._
      implicit private val conf = Derivation.tezosDerivationConfig

      implicit val contractDelegateDecoder: Decoder[ContractDelegate] = deriveDecoder
      implicit val delegateDecoder: Decoder[Delegate] = deriveDecoder
      implicit val cycleBalanceDecoder: Decoder[CycleBalance] = deriveDecoder
      implicit val contractDecoder: Decoder[Contract] = deriveDecoder
    }

    /* Collects definitions to decode voting data and their components */
    object Votes {

      import Voting._

      implicit private val conf = Derivation.tezosDerivationConfig

      private val admittedVotes = Set("yay", "nay", "pass")

      implicit val ballotVoteDecoder: Decoder[Vote] =
        deriveDecoderFromString(
          validateString = admittedVotes,
          failedValidation = "The passed-in json string is not allowed as a ballot vote",
          decodedConstructor = Vote
        )

      implicit val bakerDecoder: Decoder[BakerRolls] = deriveDecoder
      implicit val ballotDecoder: Decoder[Ballot] = deriveDecoder
      implicit val ballotCountsDecoder: Decoder[BallotCounts] = deriveDecoder
      implicit val bakersDecoder: Decoder[List[BakerRolls]] =
        Decoder.decodeList[BakerRolls]
      implicit val ballotsDecoder: Decoder[List[Ballot]] =
        Decoder.decodeList[Ballot]
      implicit val proposalsDecoder: Decoder[List[(ProtocolId, ProposalSupporters)]] =
        Decoder.decodeList[(ProtocolId, ProposalSupporters)]
    }

    /* Collects definitions to decode blocks and their components */
    object Blocks {
      // we need to decode BalanceUpdates
      import Numbers._
      import Operations._
      implicit private val conf = Derivation.tezosDerivationConfig

      val genesisMetadataDecoder: Decoder[GenesisMetadata.type] = deriveDecoder
      implicit val metadataLevelDecoder: Decoder[BlockHeaderMetadataLevel] = deriveDecoder
      val blockMetadataDecoder: Decoder[BlockHeaderMetadata] = deriveDecoder
      implicit val metadataDecoder: Decoder[BlockMetadata] = blockMetadataDecoder.widen or genesisMetadataDecoder.widen
      implicit val headerDecoder: Decoder[BlockHeader] = deriveDecoder
      implicit val mainDecoder: Decoder[BlockData] = deriveDecoder //remember to add ISO-control filtering
    }

    /* Collects alternatives for numbers with different constraints */
    object Numbers {

      /* try decoding a number */
      implicit private val bignumDecoder: Decoder[Decimal] =
        Decoder.decodeString
          .emapTry(jsonString => scala.util.Try(BigDecimal(jsonString)))
          .map(Decimal)

      /* try decoding a positive number */
      implicit private val positiveBignumDecoder: Decoder[PositiveDecimal] =
        Decoder.decodeString
          .emapTry(jsonString => scala.util.Try(BigDecimal(jsonString)))
          .ensure(_ >= 0, "The passed-in json string is not a non-negative number")
          .map(PositiveDecimal)

      /* read any string and wrap it */
      implicit private val invalidBignumDecoder: Decoder[InvalidDecimal] =
        Decoder.decodeString
          .map(InvalidDecimal)

      /* read any string and wrap it */
      implicit private val invalidPositiveBignumDecoder: Decoder[InvalidPositiveDecimal] =
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
    }

    /* decodes the big-map-diffs, both for pre-babylon and later */
    object BigMapDiff {
      import Numbers._
      import Contract.{
        BigMapAlloc,
        BigMapCopy,
        BigMapDiff,
        BigMapRemove,
        BigMapUpdate,
        CompatBigMapDiff,
        Protocol4BigMapDiff
      }
      //use the action field to distinguish subtypes of the protocol-5+ ADT
      implicit private val conf = Derivation.tezosDerivationConfig.withDiscriminator("action")
      import CirceCommonDecoders._

      implicit private val protocol4Decoder: Decoder[Protocol4BigMapDiff] = deriveDecoder
      implicit private val bigmapdiffDecoder: Decoder[BigMapDiff] = List[Decoder[BigMapDiff]](
        deriveDecoder[BigMapUpdate].widen,
        deriveDecoder[BigMapCopy].widen,
        deriveDecoder[BigMapAlloc].widen,
        deriveDecoder[BigMapRemove].widen
      ).reduceLeft(_ or _)
      implicit val compatDecoder: Decoder[CompatBigMapDiff] = decodeUntaggedEither
    }

    /*
     * Collects definitions of decoders for the Operations hierarchy.
     * Import this in scope to be able to call `io.circe.parser.decode[T](json)` for a valid type of operation
     */
    object Operations {
      import Scripts._
      import Numbers._
      import Votes._
      import BigMapDiff._

      /* decode any json value to its string representation wrapped in a Error*/
      implicit val errorDecoder: Decoder[OperationResult.Error] =
        Decoder.decodeJson.map(json => OperationResult.Error(json.noSpaces))

      //use the kind field to distinguish subtypes of the Operation ADT
      implicit private val conf = Derivation.tezosDerivationConfig.withDiscriminator("kind")

      //derive all the remaining decoders, sorted to preserve dependencies
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
      implicit val internalOperationResultDecoder: Decoder[InternalOperationResults.InternalOperationResult] =
        deriveDecoder
      implicit val parametersDecoder: Decoder[InternalOperationResults.Parameters] = deriveDecoder
      implicit val internalRevealResultDecoder: Decoder[InternalOperationResults.Reveal] = deriveDecoder
      implicit val internalTransactionResultDecoder: Decoder[InternalOperationResults.Transaction] =
        deriveDecoder
      implicit val internalOriginationResultDecoder: Decoder[InternalOperationResults.Origination] =
        deriveDecoder
      implicit val internalDelegationResultDecoder: Decoder[InternalOperationResults.Delegation] =
        deriveDecoder
      implicit val tezosTypesParametersDecoder: Decoder[TezosTypes.Parameters] = deriveDecoder
      implicit val operationDecoder: Decoder[Operation] = deriveDecoder
      implicit val operationGroupDecoder: Decoder[OperationsGroup] = deriveDecoder
      implicit val parametersCompatDecoder: Decoder[ParametersCompatibility] = decodeUntaggedEither

    }

    /* Collects definitions to decode accounts and their components */
    object Accounts {
      import Scripts._
      import CirceCommonDecoders._
      implicit private val conf = Derivation.tezosDerivationConfig

      implicit val delegateProtocol4Decoder: Decoder[Protocol4Delegate] = deriveDecoder
      implicit val accountDecoder: Decoder[Account] = deriveDecoder
    }

    object Rights {
      implicit private val conf = Derivation.tezosDerivationConfig

      implicit val endorsingRightsDecoder: Decoder[EndorsingRights] = deriveDecoder
      implicit val bakingRightsDecoder: Decoder[BakingRights] = deriveDecoder
    }

  }

}
