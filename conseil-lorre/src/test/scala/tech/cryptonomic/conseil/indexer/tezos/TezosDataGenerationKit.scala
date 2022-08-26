package tech.cryptonomic.conseil.indexer.tezos

import io.circe.Json
import tech.cryptonomic.conseil.common.testkit.util.{DBSafe, RandomGenerationKit}
import tech.cryptonomic.conseil.common.tezos.Fork
import tech.cryptonomic.conseil.common.tezos.Tables.{AccountsHistoryRow, AccountsRow, BakersHistoryRow, BakersRow, BakingRightsRow, BlocksRow, EndorsingRightsRow, FeesRow, GovernanceRow, OperationGroupsRow, OperationsRow, ProcessedChainEventsRow, TokenBalancesRow}
import tech.cryptonomic.conseil.common.tezos.TezosTypes
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{Block, BlockData, BlockHeaderMetadata, OperationHash, OperationsGroup, PositiveDecimal, PublicKeyHash, RightsFetchKey, TezosBlockHash, Voting, VotingPeriod}
import org.scalacheck._
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.ScalacheckShapeless._
import io.circe.syntax._

import java.time._
import java.time.format.DateTimeFormatter

/** A facility to get random generators of tezos entities for unit and property tests. */
object TezosDataGenerationKit extends RandomGenerationKit with TezosDatabaseCompatibilityVerification {

  /** A typed wrapper that will testify that data which could be
    * fork-invalidated is actually not.
    *
    * So a ForkValid[BlocksRow] is a BlocksRow whose invalidation fields are
    * not marked as such
    *
    * We use this typed "marker" to use randomly generate arbitrary instances from an underlying
    * "totally generic" generator for `T` without recursively have the same
    * Arbitrary[T] implicit in scope, which will loop during compilation.
    * Here we can then provide an Arbitrary[ForkValid[T]] that is different
    * from an implicitly available Arbitrary[T].
    *
    * @param data the non-invalidated data content
    */
  case class ForkValid[T](data: T) extends AnyVal

  def arbitraryBase58CheckString =
    boundedAlphabetStringGenerator(
      size = 50,
      alphabet = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz"
    )

  /** This module contributes generators for the tezos domain model,
    * i.e. any typed representation of the tezos entities, especially
    * those defined in the [[TezosTypes]] object.
    */
  object DomainModelGeneration {
    /* Locally provide simpler data generators which more complex generators depends upon */
    implicit val zdtInstance = Arbitrary(utcZoneDateTimeGen)

    implicit val blockHashGenerator = Arbitrary(arbitraryBase58CheckString.map(TezosBlockHash))

    implicit lazy val arbitraryJSON: Arbitrary[Json] = Arbitrary(Gen.oneOf("{}".asJson, "{[]}".asJson))

    private val blockDataGenerator: Gen[BlockData] =
      for {
        data <- arbitrary[BlockData].retryUntil(canBeWrittenToDb)
        hash <- blockHashGenerator.arbitrary
        metadata <- arbitrary[BlockHeaderMetadata].retryUntil(canBeWrittenToDb)
        gasConsumption <- databaseFriendlyBigDecimalGenerator.map { case DBSafe(num) => PositiveDecimal(num) }
      } yield data.copy(
        hash = hash,
        metadata = metadata.copy(
          consumed_gas = gasConsumption
        )
      )

    private val emptyOperationsGroupGenerator =
      for {
        group <- arbitrary[OperationsGroup]
        hash <- arbitraryBase58CheckString.map(OperationHash)
        blockHash <- blockHashGenerator.arbitrary
      } yield group.copy(
        hash = hash,
        branch = blockHash,
        contents = List.empty
      )

    /** This instance in scope allows to obtain random [[VotingPeriod.Kind]] */
    implicit val votingPeriodInstance = Arbitrary(Gen.oneOf(VotingPeriod.values.toSet))

    /** This instance in scope allows to obtain random ballots wrapped in a [[Voting.Vote]] */
    implicit val ballotVoteInstance = Arbitrary(Gen.oneOf("yay", "nay", "pass").map(Voting.Vote))

    /** This instance in scope allows to obtain random operation kinds */
    def operationKindGenerator = Gen.oneOf(TezosTypes.knownOperationKinds)

    /** This instance in scope allows to obtain random [[Voting.BakerRolls]] */
    implicit val bakerRollsInstance = Arbitrary(
      for {
        pkh <- arbitraryBase58CheckString
        rolls <- arbitrary[Long]
      } yield Voting.BakerRolls(pkh = PublicKeyHash(pkh), rolls = Some(rolls), None)
    )

    /** This instance in scope allows to obtain [wildly] random [[Block]] */
    implicit val validBlockInstance: Arbitrary[DBSafe[Block]] = Arbitrary(
      /* we modify the completely random instance provided by scalacheck shapeless
       * to provide our customized version
       */
      for {
        totallyArbitrary <- arbitrary[Block]
        data <- blockDataGenerator
        group <- emptyOperationsGroupGenerator
      } yield DBSafe(totallyArbitrary.copy(data = data, operationGroups = List(group)))
    )

    /** This instance in scope allows to obtain a random [[RightsFetchKey]] */
    implicit val validBakingRights: Arbitrary[DBSafe[RightsFetchKey]] = Arbitrary(
      /* we modify the completely random instance provided by scalacheck shapeless
       * to provide our customized version
       */
      for {
        totallyArbitrary <- arbitrary[RightsFetchKey]
        blockHash <- arbitrary[TezosBlockHash]
      } yield DBSafe(totallyArbitrary.copy(blockHash = blockHash))
    )
  }

  /** This module contributes generators for the tezos persistent data
    * model, i.e. the slick types generated to interoperate with the
    * database, currently available in the [[Tables]] object/class.
    *
    * In general, importing the contents of this object, allows you to obtain
    * a random database entity type (e.g. block row, operation row, operation group row),
    * based on the implicit generators defined herein, in a one to one correspondence, of course.
    * These can be used in two main ways
    * 1. Explicitly importing and "summoning" the implicit `Arbitrary` instance
    * {{{
    *   import DataModelGeneration._
    *
    *   val generated = arbitrary[BlocksRow].sample
    * }}}
    * Which will produce an "optional" sample of the given type
    *
    * 2. Via the `property` test descriptor with Scalacheck to have automatically
    * generated instances on which to verify a property
    * {{{
    *   import DataModelGeneration._
    *
    *   property("the property under test to verify, described for test results"){
    *     forAll { (row: BlocksRow) =>
    *        //verify any property that should stand true for all rows
    *     }
    *   }
    * }}}
    */
  object DataModelGeneration {

    /** Exposes the timestamp generation as an [[Arbitrary]] implicit */
    implicit val timestampInstance = Arbitrary(timestampGenerator)

    /** This instance in scope allows to obtain random [[BlocksRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validBlocksRowGenerator: Arbitrary[ForkValid[BlocksRow]] = Arbitrary(
      /* we modify the completely random instance provided by scalacheck shapeless
       * to provide our customized version
       */
      for {
        totallyArbitrary <- arbitrary[BlocksRow]
        arbitraryB58C <- Gen.infiniteStream(arbitraryBase58CheckString)
        arbitraryTimestamp <- timestampGenerator
        arbitraryFit <- databaseFriendlyStringGenerator
        arbitraryCtx <- Gen.option(databaseFriendlyStringGenerator)
        arbitrarySig <- Gen.option(databaseFriendlyStringGenerator)
        arbitraryProposal <- Gen.option(arbitraryBase58CheckString)
        arbitraryGas <- Gen.option(databaseFriendlyBigDecimalGenerator.map(_.value))
        arbitraryDatetime = Instant.ofEpochMilli(arbitraryTimestamp.getTime).atOffset(ZoneOffset.UTC)
      } yield ForkValid(
        totallyArbitrary.copy(
          predecessor = arbitraryB58C(0),
          protocol = arbitraryB58C(1),
          hash = arbitraryB58C(2),
          activeProposal = arbitraryProposal,
          fitness = arbitraryFit.value,
          context = arbitraryCtx.map(_.value),
          signature = arbitrarySig.map(_.value),
          consumedGas = arbitraryGas,
          timestamp = arbitraryTimestamp,
          utcYear = arbitraryDatetime.getYear(),
          utcMonth = arbitraryDatetime.getMonth().getValue(),
          utcDay = arbitraryDatetime.getDayOfMonth(),
          utcTime = arbitraryDatetime.format(DateTimeFormatter.ISO_TIME),
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[OperationsRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validOperationsRowGenerator: Arbitrary[ForkValid[OperationsRow]] = Arbitrary(
      /* we modify the completely random instance provided by scalacheck shapeless
       * to provide our customized version
       */
      for {
        totallyArbitrary <- arbitrary[OperationsRow].retryUntil(canBeWrittenToDb)
        arbitraryScript <- Gen.option(Gen.alphaNumStr)
        arbitraryStorage <- Gen.option(Gen.alphaNumStr)
        arbitraryBigDecimals <- Gen.infiniteStream(Gen.option(databaseFriendlyBigDecimalGenerator.map(_.value)))
        arbitraryTimestamp <- timestampGenerator
        arbitraryDatetime = Instant.ofEpochMilli(arbitraryTimestamp.getTime).atOffset(ZoneOffset.UTC)
        arbitraryProposalHashes <- Gen.option(Gen.listOf(arbitraryBase58CheckString))
        arbitraryBallot <- Gen.option(DomainModelGeneration.ballotVoteInstance.arbitrary)
        arbitraryKind <- DomainModelGeneration.operationKindGenerator
        arbitraryTag <- Gen.option(Gen.alphaNumStr)
      } yield ForkValid(
        /* we currently generate totally random strings for hash fields, as long as it's ok */
        totallyArbitrary.copy(
          fee = arbitraryBigDecimals(0),
          counter = arbitraryBigDecimals(1),
          gasLimit = arbitraryBigDecimals(2),
          storageLimit = arbitraryBigDecimals(3),
          amount = arbitraryBigDecimals(4),
          balance = arbitraryBigDecimals(5),
          consumedGas = arbitraryBigDecimals(6),
          storageSize = arbitraryBigDecimals(7),
          paidStorageSizeDiff = arbitraryBigDecimals(8),
          ballot = arbitraryBallot.map(_.value),
          kind = arbitraryKind,
          script = arbitraryScript,
          storage = arbitraryStorage,
          timestamp = arbitraryTimestamp,
          utcYear = arbitraryDatetime.getYear(),
          utcMonth = arbitraryDatetime.getMonth().getValue(),
          utcDay = arbitraryDatetime.getDayOfMonth(),
          utcTime = arbitraryDatetime.format(DateTimeFormatter.ISO_TIME),
          proposal = arbitraryProposalHashes.map(_.mkString(",")),
          invalidatedAsof = None,
          forkId = Fork.mainForkId,
          eventtype = arbitraryScript,
          tag = arbitraryTag,
          payload = arbitraryScript
        )
      )
    )

    /** This instance in scope allows to obtain random [[AccountsRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validAccountsRowGenerator: Arbitrary[ForkValid[AccountsRow]] = Arbitrary(
      /* we modify the completely random instance provided by scalacheck shapeless
       * to provide our customized version
       */
      for {
        totallyArbitrary <- arbitrary[AccountsRow]
        arbitraryBase58Check <- arbitraryBase58CheckString
        arbitraryB58COption <- Gen.infiniteStream(Gen.option(arbitraryBase58CheckString))
        DBSafe(arbitraryBalance) <- databaseFriendlyBigDecimalGenerator
        arbitraryScript <- Gen.option(databaseFriendlyStringGenerator)
        arbitraryStorage <- Gen.option(databaseFriendlyStringGenerator)
        arbitraryScriptHash <- Gen.option(databaseFriendlyStringGenerator)
      } yield ForkValid(
        totallyArbitrary.copy(
          accountId = arbitraryBase58Check,
          balance = arbitraryBalance,
          script = arbitraryScript.map(_.value),
          storage = arbitraryStorage.map(_.value),
          scriptHash = arbitraryScriptHash.map(_.value),
          manager = arbitraryB58COption(0),
          delegateValue = arbitraryB58COption(1),
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[AccountsHistoryRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validAccountsHistoryRowGenerator: Arbitrary[ForkValid[AccountsHistoryRow]] = Arbitrary(
      /* we modify the completely random instance provided by scalacheck shapeless
       * to provide our customized version
       */
      for {
        totallyArbitrary <- arbitrary[AccountsHistoryRow]
        arbitraryBase58Check <- arbitraryBase58CheckString
        DBSafe(arbitraryBalance) <- databaseFriendlyBigDecimalGenerator
      } yield ForkValid(
        totallyArbitrary.copy(
          accountId = arbitraryBase58Check,
          balance = arbitraryBalance,
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[BakersRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validBakersRowGenerator: Arbitrary[ForkValid[BakersRow]] = Arbitrary(
      /* we modify the completely random instance provided by scalacheck shapeless
       * to provide our customized version
       */
      for {
        totallyArbitrary <- arbitrary[BakersRow]
        arbitraryBase58Check <- arbitraryBase58CheckString
        arbitraryBalances <- Gen.infiniteStream(Gen.option(databaseFriendlyBigDecimalGenerator.map(_.value)))
      } yield ForkValid(
        totallyArbitrary.copy(
          pkh = arbitraryBase58Check,
          balance = arbitraryBalances(0),
          frozenBalance = arbitraryBalances(1),
          stakingBalance = arbitraryBalances(2),
          delegatedBalance = arbitraryBalances(3),
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[BakersHistoryRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validBakersHistoryRowGenerator: Arbitrary[ForkValid[BakersHistoryRow]] = Arbitrary(
      /* we modify the completely random instance provided by scalacheck shapeless
       * to provide our customized version
       */
      for {
        totallyArbitrary <- arbitrary[BakersHistoryRow]
        arbitraryBase58Check <- arbitraryBase58CheckString
        arbitraryBalances <- Gen.infiniteStream(Gen.option(databaseFriendlyBigDecimalGenerator.map(_.value)))
      } yield ForkValid(
        totallyArbitrary.copy(
          pkh = arbitraryBase58Check,
          balance = arbitraryBalances(0),
          frozenBalance = arbitraryBalances(1),
          stakingBalance = arbitraryBalances(2),
          delegatedBalance = arbitraryBalances(3),
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[OperationGroupsRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validOperationGroupsRowGenerator = Arbitrary(
      /* We build a new instance from individual random values.
       * No value added here from generating the base object completely randomly,
       * 'cause all fields might fail to save on db.
       */
      for {
        arbitraryHash <- arbitraryBase58CheckString
        arbitraryBlockHash <- arbitraryBase58CheckString
        arbitraryStrings <- Gen.infiniteStream(databaseFriendlyStringGenerator)
        arbitraryOptionalStrings <- Gen.infiniteStream(Gen.option(databaseFriendlyStringGenerator))
        arbitraryLevel <- Gen.posNum[Long]
      } yield ForkValid(
        OperationGroupsRow(
          hash = arbitraryHash,
          protocol = arbitraryStrings(0).value,
          branch = arbitraryStrings(1).value,
          chainId = arbitraryOptionalStrings(0).map(_.value),
          signature = arbitraryOptionalStrings(1).map(_.value),
          blockId = arbitraryBlockHash,
          blockLevel = arbitraryLevel,
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[BakingRightsRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validBakinRightsRowGenerator: Arbitrary[ForkValid[BakingRightsRow]] = Arbitrary(
      /* we modify the completely random instance provided by scalacheck shapeless
       * to provide our customized version
       */
      for {
        totallyArbitrary <- arbitrary[BakingRightsRow]
        arbitraryHash <- Gen.option(arbitraryBase58CheckString)
        arbitraryDelegate <- arbitraryBase58CheckString
      } yield ForkValid(
        totallyArbitrary.copy(
          blockHash = arbitraryHash,
          delegate = arbitraryDelegate,
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[EndorsingRightsRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validEndorsingRightsRowGenerator: Arbitrary[ForkValid[EndorsingRightsRow]] = Arbitrary(
      for {
        totallyArbitrary <- arbitrary[EndorsingRightsRow]
        arbitraryHash <- Gen.option(arbitraryBase58CheckString)
        arbitraryDelegate <- arbitraryBase58CheckString
      } yield ForkValid(
        totallyArbitrary.copy(
          blockHash = arbitraryHash,
          delegate = arbitraryDelegate,
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[GovernanceRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validGovernanceRowGenerator: Arbitrary[ForkValid[GovernanceRow]] = Arbitrary(
      for {
        totallyArbitrary <- arbitrary[GovernanceRow]
        arbitraryHash <- arbitraryBase58CheckString
        arbitraryProposal <- arbitraryBase58CheckString
        arbitraryRolls <- Gen.infiniteStream(Gen.option(databaseFriendlyLongGenerator.map(_.value)))
        arbitraryPeriodKind <- DomainModelGeneration.votingPeriodInstance.arbitrary
      } yield ForkValid(
        totallyArbitrary.copy(
          blockHash = arbitraryHash,
          proposalHash = arbitraryProposal,
          votingPeriodKind = arbitraryPeriodKind.toString,
          yayRolls = arbitraryRolls(0),
          nayRolls = arbitraryRolls(1),
          passRolls = arbitraryRolls(2),
          totalRolls = arbitraryRolls(3),
          blockYayRolls = arbitraryRolls(4),
          blockNayRolls = arbitraryRolls(5),
          blockPassRolls = arbitraryRolls(6),
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[TokenBalancesRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validTokenBalancesRowGenerator: Arbitrary[ForkValid[TokenBalancesRow]] = Arbitrary(
      for {
        totallyArbitrary <- arbitrary[TokenBalancesRow]
        arbitraryHash <- arbitraryBase58CheckString
        arbitraryAddress <- arbitraryBase58CheckString
        arbitraryTimestamp <- timestampGenerator
        DBSafe(arbitraryBalance) <- databaseFriendlyBigDecimalGenerator
      } yield ForkValid(
        totallyArbitrary.copy(
          blockId = arbitraryHash,
          address = arbitraryAddress,
          asof = arbitraryTimestamp,
          balance = arbitraryBalance,
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[FeesRow]]
      *
      * **Notice** that the generated rows are **not invalidated**
      */
    implicit val validFeesRowGenerator: Arbitrary[ForkValid[FeesRow]] = Arbitrary(
      for {
        totallyArbitrary <- arbitrary[FeesRow]
        arbitraryTimestamp <- timestampGenerator
        arbitraryKind <- DomainModelGeneration.operationKindGenerator
      } yield ForkValid(
        totallyArbitrary.copy(
          kind = arbitraryKind,
          timestamp = arbitraryTimestamp,
          invalidatedAsof = None,
          forkId = Fork.mainForkId
        )
      )
    )

    /** This instance in scope allows to obtain random [[ProcessedChainEventsRow]] */
    implicit val processedEventsRowGenerator: Arbitrary[DBSafe[ProcessedChainEventsRow]] = Arbitrary(
      for {
        totallyArbitrary <- arbitrary[ProcessedChainEventsRow]
        DBSafe(arbitraryEventType) <- databaseFriendlyStringGenerator
      } yield DBSafe(
        totallyArbitrary.copy(eventType = arbitraryEventType)
      )
    )
  }

}
