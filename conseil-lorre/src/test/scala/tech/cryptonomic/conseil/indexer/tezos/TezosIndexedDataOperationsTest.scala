package tech.cryptonomic.conseil.indexer.tezos

import cats.implicits._
import org.scalatest.concurrent.IntegrationPatience
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.ScalacheckShapeless._
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.testkit.util.{DBSafe, RandomSeed}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{
  makeAccountId,
  BakingRights,
  Block,
  BlockReference,
  EndorsingRights,
  RightsFetchKey,
  TezosBlockHash,
  Voting
}
import tech.cryptonomic.conseil.common.tezos.{Fork, TezosOptics}
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.Tables.{
  AccountsHistoryRow,
  AccountsRow,
  BakersHistoryRow,
  BakersRow,
  BakingRightsRow,
  BlocksRow,
  EndorsingRightsRow,
  GovernanceRow,
  OperationGroupsRow,
  OperationsRow,
  TokenBalancesRow
}
import tech.cryptonomic.conseil.indexer.tezos.TezosDataGenerationKit.ForkValid
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.{TNSContract, TokenContracts}

import scala.concurrent.Await
import scala.concurrent.duration._
import java.{util => ju}
import java.time.Instant
import java.sql.Timestamp
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class TezosIndexedDataOperationsTest
    extends ConseilSpec
    with InMemoryDatabase
    with TezosInMemoryDatabaseSetup
    with IntegrationPatience
    with TezosDatabaseOperationsTestFixtures {

  import scala.concurrent.ExecutionContext.Implicits.global
  import TezosDataGenerationKit.DomainModelGeneration._
  import TezosDataGenerationKit.DataModelGeneration._
  import TezosDataGenerationKit.arbitraryBase58CheckString
  import LocalGenerationUtils._

  "TezosIndexedDataOperations" should {
      implicit val noTokenContracts: TokenContracts = TokenContracts.fromConfig(List.empty)
      implicit val noTNSContracts: TNSContract = TNSContract.noContract

      val sut = new TezosIndexedDataOperations(dbHandler)

      "fetchBlockAtLevel for missing entry" in {
        //when
        val result = sut.fetchBlockAtLevel(1).futureValue

        //then
        result shouldBe None
      }

      "fetchBlockAtLevel for a matching entry" in {
        // given

        /* group by key and count the resulting buckets' size to detect duplicates */
        def noDuplicateKeys(safeBlocks: List[DBSafe[Block]]): Boolean =
          !safeBlocks.groupBy(_.value.data.hash).exists { case (key, rows) => rows.size > 1 }

        val generator = for {
          arbitraryBlocks <- Gen.nonEmptyListOf(arbitrary[DBSafe[Block]]).retryUntil(noDuplicateKeys)
          level <- Gen.choose(0L, arbitraryBlocks.size - 1)
        } yield {
          val orderedBlocks = arbitraryBlocks.zipWithIndex.map {
            case (DBSafe(block), lvl) =>
              TezosOptics.Blocks.onLevel
                .set(lvl)(block)
                .copy(operationGroups = List.empty)
          }
          (orderedBlocks, level)
        }

        val (generatedBlocks, pickLevel) = generator.sample.value

        info(s"verifying with ${generatedBlocks.size} blocks for level $pickLevel")

        Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks, noTokenContracts)), 5.seconds)

        // when
        val result = sut.fetchBlockAtLevel(pickLevel).futureValue.value

        // then
        result.level shouldBe pickLevel
      }

      "read latest account ids from checkpoint" in {
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        //generate data
        val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

        //store required blocks for FK
        val accountIds = Array("a0", "a1", "a2", "a3", "a4", "a5", "a6")
        val blockIds = blocks.map(_.hash)

        //create test data:
        val checkpointRows = Array(
          Tables.AccountsCheckpointRow(accountIds(1), blockIds(1), blockLevel = 1, testReferenceTimestamp),
          Tables.AccountsCheckpointRow(accountIds(2), blockIds(1), blockLevel = 1, testReferenceTimestamp),
          Tables.AccountsCheckpointRow(accountIds(3), blockIds(1), blockLevel = 1, testReferenceTimestamp),
          Tables.AccountsCheckpointRow(accountIds(4), blockIds(2), blockLevel = 2, testReferenceTimestamp),
          Tables.AccountsCheckpointRow(accountIds(5), blockIds(2), blockLevel = 2, testReferenceTimestamp),
          Tables.AccountsCheckpointRow(accountIds(2), blockIds(3), blockLevel = 3, testReferenceTimestamp),
          Tables.AccountsCheckpointRow(accountIds(3), blockIds(4), blockLevel = 4, testReferenceTimestamp),
          Tables.AccountsCheckpointRow(accountIds(5), blockIds(4), blockLevel = 4, testReferenceTimestamp),
          Tables.AccountsCheckpointRow(accountIds(6), blockIds(5), blockLevel = 5, testReferenceTimestamp)
        )

        def entry(accountAtIndex: Int, atLevel: Int, time: Timestamp) =
          makeAccountId(accountIds(accountAtIndex)) ->
              BlockReference(TezosBlockHash(blockIds(atLevel)), atLevel, Some(time.toInstant), None, None)

        //expecting only the following to remain
        val expected =
          Map(
            entry(accountAtIndex = 1, atLevel = 1, time = testReferenceTimestamp),
            entry(accountAtIndex = 2, atLevel = 3, time = testReferenceTimestamp),
            entry(accountAtIndex = 3, atLevel = 4, time = testReferenceTimestamp),
            entry(accountAtIndex = 4, atLevel = 2, time = testReferenceTimestamp),
            entry(accountAtIndex = 5, atLevel = 4, time = testReferenceTimestamp),
            entry(accountAtIndex = 6, atLevel = 5, time = testReferenceTimestamp)
          )

        val populate = for {
          blockCounts <- Tables.Blocks ++= blocks
          checkpoint <- Tables.AccountsCheckpoint ++= checkpointRows
        } yield (blockCounts, checkpoint)

        val (storedBlocks, initialCount) = dbHandler.run(populate.transactionally).futureValue
        val latest = sut.getLatestAccountsFromCheckpoint.futureValue

        storedBlocks.value shouldBe blocks.size
        initialCount.value shouldBe checkpointRows.size

        latest.toSeq should contain theSameElementsAs expected.toSeq

      }

      "ignore forked data on fetchBlockAtLevel" in {
        // given

        val (validBlock, invalidation, fork) =
          arbitrary[(ForkValid[BlocksRow], Timestamp, ju.UUID)].sample.value
        val invalidBlock = validBlock.data.copy(
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        // when
        val populateAndFetch = for {
          stored <- dbHandler.run(Tables.Blocks += invalidBlock)
          result <- sut.fetchBlockAtLevel(invalidBlock.level)
        } yield (stored, result)

        val (stored, result) = populateAndFetch.futureValue

        //then
        stored shouldBe >(0)

        result shouldBe empty

      }

      "fetchMaxLevel when DB is empty" in {
        // when
        val result = sut.fetchMaxLevel().futureValue

        // then
        result shouldBe -1
      }

      "fetchMaxLevel" in {
        // given

        /* group by key and count the resulting buckets' size to detect duplicates */
        def noDuplicateKeys(safeBlocks: List[DBSafe[Block]]): Boolean =
          !safeBlocks.groupBy(_.value.data.hash).exists { case (key, rows) => rows.size > 1 }

        val generator = Gen
          .nonEmptyListOf(arbitrary[DBSafe[Block]])
          .retryUntil(noDuplicateKeys)
          .map { arbitraryBlocks =>
            arbitraryBlocks.zipWithIndex.map {
              case (DBSafe(block), lvl) =>
                TezosOptics.Blocks.onLevel
                  .set(lvl)(block)
                  .copy(operationGroups = List.empty)
            }
          }
        val generatedBlocks = generator.sample.value

        info(s"verifying with ${generatedBlocks.size} blocks")

        Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks, noTokenContracts)), 5.seconds)

        // when
        val result = sut.fetchMaxLevel().futureValue

        // then
        result shouldBe generatedBlocks.map(_.data.header.level).max
      }

      "ignore fork-invalidated data when fetchMaxLevel" in {
        // given

        val (validBlock, invalidation, fork) =
          arbitrary[(ForkValid[BlocksRow], Timestamp, ju.UUID)].sample.value
        val invalidBlock = validBlock.data.copy(
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        // when
        val populateAndFetch = for {
          stored <- dbHandler.run(Tables.Blocks += invalidBlock)
          result <- sut.fetchMaxLevel()
        } yield (stored, result)

        val (stored, result) = populateAndFetch.futureValue

        // then
        stored shouldBe >(0)

        result shouldBe -1

      }

      "fetchMaxBakingRightsLevel when DB is empty" in {
        // when
        val result = sut.fetchMaxBakingRightsLevel().futureValue

        // then
        result shouldBe -1
      }

      "fetchMaxEndorsingRightsLevel when DB is empty" in {
        // when
        val result = sut.fetchMaxEndorsingRightsLevel().futureValue

        // then
        result shouldBe -1
      }

      "fetchMaxBakingRightsLevel" in {
        // when
        val (fetchKey, bakingRights, block) = {
          val generator = for {
            DBSafe(referenceBlock) <- arbitrary[DBSafe[Block]]
            DBSafe(fetchKey) <- arbitrary[DBSafe[RightsFetchKey]]
            bakeRights <- Gen.nonEmptyListOf(arbitrary[BakingRights])
            delegates <- Gen.infiniteStream(arbitrary[DBSafe[String]])
            dbSafeRights = fetchKey.copy(blockHash = referenceBlock.data.hash)
            dbSafeBakingRights = bakeRights.zip(delegates).map {
              case (rights, DBSafe(delegate)) => rights.copy(delegate = delegate)
            }
          } yield (dbSafeRights, dbSafeBakingRights, referenceBlock)

          generator.sample.value
        }

        info(s"verifying with ${bakingRights.size} rights")

        val populate = for {
          _ <- TezosDatabaseOperations.writeBlocks(List(block), noTokenContracts)
          Some(stored) <- TezosDatabaseOperations.upsertBakingRights(Map(fetchKey -> bakingRights))
        } yield stored

        val stored = dbHandler.run(populate).futureValue

        stored shouldBe >(0)

        val result = sut.fetchMaxBakingRightsLevel().futureValue

        // then
        result shouldBe bakingRights.map(_.level).max
      }

      "fetchMaxEndorsingRightsLevel" in {
        // when
        val (fetchKey, endorsingRights, block) = {
          val generator = for {
            DBSafe(referenceBlock) <- arbitrary[DBSafe[Block]]
            DBSafe(fetchKey) <- arbitrary[DBSafe[RightsFetchKey]]
            endorseRights <- Gen.nonEmptyListOf(arbitrary[EndorsingRights]).retryUntil(_.exists(_.slots.nonEmpty))
            delegates <- Gen.infiniteStream(arbitraryBase58CheckString)
            dbSafeRights = fetchKey.copy(blockHash = referenceBlock.data.hash)
            dbSafeEndorsingRights = endorseRights.zip(delegates).map {
              case (rights, delegate) => rights.copy(delegate = delegate)
            }
          } yield (dbSafeRights, dbSafeEndorsingRights, referenceBlock)

          generator.sample.value
        }

        info(s"verifying with ${endorsingRights.size} rights")

        val populate = for {
          _ <- TezosDatabaseOperations.writeBlocks(List(block), noTokenContracts)
          Some(stored) <- TezosDatabaseOperations.upsertEndorsingRights(Map(fetchKey -> endorsingRights))
        } yield stored

        val stored = dbHandler.run(populate).futureValue

        stored shouldBe >(0)

        val result = sut.fetchMaxEndorsingRightsLevel().futureValue

        // then
        result shouldBe endorsingRights.map(_.level).max
      }

      "ignore fork-invalidated data when fetchMaxBakingRightsLevel" in {
        //given
        val (validRow, validReferencedBlock, invalidation, fork) =
          arbitrary[(ForkValid[BakingRightsRow], ForkValid[BlocksRow], Timestamp, ju.UUID)].sample.value
        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRow = validRow.data.copy(
          blockLevel = invalidBlock.level,
          blockHash = Some(invalidBlock.hash),
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        // when
        val populate = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.BakingRights += invalidRow
        } yield stored

        val stored = dbHandler.run(populate).futureValue

        val loaded = sut.fetchMaxBakingRightsLevel().futureValue

        // then
        stored shouldBe >(0)

        loaded shouldBe -1

      }

      "ignore fork-invalidated data when fetchMaxEndorsingRightsLevel" in {
        //given
        val (validRow, validReferencedBlock, invalidation, fork) =
          arbitrary[(ForkValid[EndorsingRightsRow], ForkValid[BlocksRow], Timestamp, ju.UUID)].sample.value
        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRow = validRow.data.copy(
          blockLevel = invalidBlock.level,
          blockHash = Some(invalidBlock.hash),
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        // when
        val populate = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.EndorsingRights += invalidRow
        } yield stored

        val stored = dbHandler.run(populate).futureValue

        val loaded = sut.fetchMaxEndorsingRightsLevel().futureValue

        // then
        stored shouldBe >(0)

        loaded shouldBe -1

      }

      "ignore fork-invalidated data for latest operation hashes by kind" in {
        /* Generate the data random sample */
        val (validRows, validReferencedGroup, validReferencedBlock, invalidation, fork) =
          Gen
            .zip(
              nonConflictingArbitrary[ForkValid[OperationsRow]](
                //add a bit more realism
                satisfying = (row: ForkValid[OperationsRow]) => row.data.blockLevel > 0
              ),
              arbitrary[ForkValid[OperationGroupsRow]],
              arbitrary[ForkValid[BlocksRow]],
              arbitrary[Timestamp],
              arbitrary[ju.UUID]
            )
            .sample
            .value

        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidGroup = validReferencedGroup.data.copy(
          blockId = invalidBlock.hash,
          blockLevel = invalidBlock.level,
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )
        /* take heed that we keep the levels random */
        val invalidRows = validRows.map(
          _.data.copy(
            blockHash = invalidBlock.hash,
            operationGroupHash = invalidGroup.hash,
            invalidatedAsof = Some(invalidation),
            forkId = fork.toString
          )
        )

        /* take note of relevant params */
        val minRecordedLevel = invalidRows.map(_.blockLevel).min
        val operationKinds = invalidRows.map(_.kind).toSet
        //double-check
        minRecordedLevel shouldBe >(0L)
        operationKinds should not be empty

        /* Store everything on db */
        val populate = for {
          _ <- Tables.Blocks += invalidBlock
          _ <- Tables.OperationGroups += invalidGroup
          Some(stored) <- Tables.Operations ++= invalidRows
        } yield stored

        /* Test the results */
        val stored = dbHandler.run(populate).futureValue
        val loaded = sut
          .fetchRecentOperationsHashByKind(
            ofKind = operationKinds,
            fromLevel = minRecordedLevel
          )
          .futureValue

        stored shouldBe >(0)
        loaded shouldBe empty
      }

      "ignore fork-invalidated data for account levels" in {

        /* Generate the data random sample */
        val (validRows, validReferencedBlock, invalidation, fork) =
          Gen
            .zip(
              //duplicate ids will fail to save on the db for violation of the PK uniqueness
              nonConflictingArbitrary[ForkValid[AccountsRow]],
              arbitrary[ForkValid[BlocksRow]],
              arbitrary[Timestamp],
              arbitrary[ju.UUID]
            )
            .sample
            .value

        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRows = validRows.map(
          _.data.copy(
            blockId = invalidBlock.hash,
            blockLevel = invalidBlock.level,
            invalidatedAsof = Some(invalidation),
            forkId = fork.toString
          )
        )

        /* Store everything on db */
        val populate = for {
          _ <- Tables.Blocks += invalidBlock
          Some(stored) <- Tables.Accounts ++= invalidRows
        } yield stored

        /* Test the results */
        val stored = dbHandler.run(populate).futureValue
        val loaded = sut.getLevelsForAccounts(invalidRows.map(row => makeAccountId(row.accountId)).toSet).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "ignore fork-invalidated data for filteres bakers" in {

        /* Generate the data random sample */
        val (validRows, validReferencedBlock, invalidation, fork) =
          Gen
            .zip(
              //duplicate ids will fail to save on the db for violation of the PK uniqueness
              nonConflictingArbitrary[ForkValid[AccountsRow]],
              arbitrary[ForkValid[BlocksRow]],
              arbitrary[Timestamp],
              arbitrary[ju.UUID]
            )
            .sample
            .value

        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRows = validRows.map(
          _.data.copy(
            blockId = invalidBlock.hash,
            blockLevel = invalidBlock.level,
            invalidatedAsof = Some(invalidation),
            forkId = fork.toString
          )
        )

        /* Store everything on db */
        val populate = for {
          _ <- Tables.Blocks += invalidBlock
          Some(stored) <- Tables.Accounts ++= invalidRows
        } yield stored

        /* Test the results */
        val stored = dbHandler.run(populate).futureValue
        val loaded = sut.getFilteredBakerAccounts(exclude = Set.empty).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "ignore fork-invalidated data for bakers by block-hash" in {

        val (validRow, validReferencedBlock, invalidation, fork) =
          arbitrary[
            (
                ForkValid[BakersRow],
                ForkValid[BlocksRow],
                Timestamp,
                ju.UUID
            )
          ].sample.value

        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRow = validRow.data.copy(
          blockId = invalidBlock.hash,
          blockLevel = invalidBlock.level,
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        val populate = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.Bakers += invalidRow
        } yield stored

        val stored = dbHandler.run(populate).futureValue
        val loaded = sut.getBakersForBlocks(List(TezosBlockHash(invalidRow.blockId))).futureValue

        stored shouldBe >(0)
        //get all rolls for any requested hash and concat them together
        loaded.foldLeft(List.empty[Voting.BakerRolls]) {
          case (allRolls, (hash, rolls)) => allRolls ++ rolls
        } shouldBe empty

      }

      "ignore fork-invalidated data for activated accounts" in {

        val (validRow, validReferencedBlock, invalidation, fork) =
          arbitrary[
            (
                ForkValid[AccountsRow],
                ForkValid[BlocksRow],
                Timestamp,
                ju.UUID
            )
          ].sample.value

        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRow = validRow.data.copy(
          isActivated = true,
          blockLevel = invalidBlock.level,
          blockId = invalidBlock.hash,
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        val populate = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.Accounts += invalidRow
        } yield stored

        val stored = dbHandler.run(populate).futureValue
        val loaded = sut.findActivatedAccountIds.futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

    }

  private object LocalGenerationUtils {

    /* Common generators should reduce clutter
     * We have common patterns of dependencies and constraints for
     * data that needs to be saved on db.
     * Those are generally FK to block keys, or uniqueness checks on PK.
     */
    import Gen.{listOfN, nonEmptyListOf}

    /* Denotes a type that is uniquely identified by a key type PK */
    trait HasKey[Entity, PK] {

      /** retrieves the unique key value from the entity instance */
      def getKey(entity: Entity): PK

    }

    /* Here we provide all instances we need in the test cases */
    object HasKey {

      /* will provide an implicit HasKey for an entity wrapped by ForkValid
       * we make use of java8 functional interfaces and define the instance as a lambda, which
       * corresponds to the single method of HasKey, i.e. getKey
       */
      implicit def forkValidWithKey[PK, E](implicit hasKey: HasKey[E, PK]): HasKey[ForkValid[E], PK] = {
        case ForkValid(entity) => implicitly[HasKey[E, PK]].getKey(entity)
      }

      /* There we provide all the key extractors for known db entites, again using functional interfaces shortcut */
      implicit val blocksRowHasKey: HasKey[BlocksRow, String] = _.hash
      implicit val accountsRowHasKey: HasKey[AccountsRow, String] = _.accountId
      implicit val accountsHistoryRowHasKey: HasKey[AccountsHistoryRow, String] = _.accountId
      implicit val bakersRowHasKey: HasKey[BakersRow, String] = _.pkh
      implicit val bakersHistoryRowHasKey: HasKey[BakersHistoryRow, String] = _.pkh
      implicit val operationGroupsRowHasKey: HasKey[OperationGroupsRow, String] = _.hash
      implicit val operationsRowHasKey: HasKey[OperationsRow, Int] = _.operationId
      implicit val bakingRightsRowHasKey: HasKey[BakingRightsRow, String] = _.delegate
      implicit val endorsingRightsRowHasKey: HasKey[EndorsingRightsRow, String] = _.delegate
      implicit val tokenBalancesRowHasKey: HasKey[TokenBalancesRow, String] = _.address
      implicit val governanceRowHasKey: HasKey[GovernanceRow, (String, String, String)] =
        gov => (gov.blockHash, gov.proposalHash, gov.votingPeriodKind)

    }

    /** Generates a list (non-empty) of Ts with no PK conflict.
      * The key is provided implicitly by an instance of [[HasKey]] for the
      * entity T we're trying to generate.
      */
    def nonConflictingArbitrary[T: Arbitrary](implicit hasKey: HasKey[T, _]): Gen[List[T]] =
      nonEmptyListOf(arbitrary[T]).retryUntil(noDuplicates(forKey = hasKey.getKey))

    /** Generates a list (non-empty) of pairs with T1 and T2s, with no PK conflict for the same type.
      * The key is provided implicitly by an instance of [[HasKey]] for the
      * entities we're trying to generate.
      */
    def nonConflictingArbitrary[T1: Arbitrary, T2: Arbitrary](
        implicit
        hasKey1: HasKey[T1, _],
        hasKey2: HasKey[T2, _]
    ): Gen[List[(T1, T2)]] =
      nonEmptyListOf(arbitrary[(T1, T2)])
        .retryUntil(noDuplicates(forKey = hasKey1.getKey, andKey = hasKey2.getKey))

    /** generates a list (non-empty) of Ts with no PK conflict and at most n elements.
      * The key is provided implicitly by an instance of [[HasKey]] for the
      * entity T we're trying to generate.
      */
    def nonConflictingArbitrary[T: Arbitrary](atMost: Int)(implicit hasKey: HasKey[T, _]): Gen[List[T]] =
      listOfN(atMost, arbitrary[T])
        .suchThat(_.nonEmpty)
        .retryUntil(noDuplicates(forKey = hasKey.getKey))

    /** generates a list (non-empty) of Ts with no PK conflict.
      * Additionally, each element should satisfy a specific property.
      * The key is provided implicitly by an instance of [[HasKey]] for the
      * entity T we're trying to generate.
      */
    def nonConflictingArbitrary[T: Arbitrary](satisfying: T => Boolean)(implicit hasKey: HasKey[T, _]): Gen[List[T]] =
      nonEmptyListOf(arbitrary[T].retryUntil(satisfying)).retryUntil(noDuplicates(forKey = hasKey.getKey))

    /** True if all entities have a distinct key, where the key is
      * computed by the passed-in extraction function
      */
    private def noDuplicates[T, K](forKey: T => K)(entities: List[T]): Boolean =
      !entities.groupBy(forKey).exists { case (key, rows) => rows.size > 1 }

    /** True if all entities have a distinct key in each list, where the keys are
      * separately computed by the passed-in extraction functions
      */
    private def noDuplicates[T, T2, K, K2](forKey: T => K, andKey: T2 => K2)(entities: List[(T, T2)]): Boolean = {
      val (lefties, righties) = entities.unzip
      noDuplicates(forKey)(lefties) && noDuplicates(andKey)(righties)
    }

    /* This is a "structural" type which collects what is necessary to be considered a data type
     * which might be invalidated.
     * This kind of definitions incurs runtime checks and as such tends to be avoided in production
     * code, and its usage is enabled only behind a specific language import.
     * We restrict its use to tests for this very reason.
     */
    type CanBeInvalidated = { def invalidatedAsof: Option[Timestamp]; def forkId: String }

    /* import flag used to enable reflection calls for the following verification methods */
    import language.reflectiveCalls

    /** return true if all the entities in the sequence have not been invalidated by a fork */
    def allValid[T <: CanBeInvalidated](valid: Seq[T]): Boolean =
      valid.forall(it => it.invalidatedAsof.isEmpty && it.forkId == Fork.mainForkId)

    /** return true if all the entities in the sequence result in having been invalidated by a fork
      * with the given id and at the specific time
      */
    def allInvalidated[T <: CanBeInvalidated](asof: Instant, forkId: String)(invalidated: Seq[T]): Boolean =
      invalidated.forall(
        it =>
          it.invalidatedAsof == Some(Timestamp.from(asof)) &&
            it.forkId == forkId
      )

  }
}
