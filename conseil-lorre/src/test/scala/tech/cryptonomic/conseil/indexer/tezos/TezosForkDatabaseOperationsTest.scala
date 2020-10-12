package tech.cryptonomic.conseil.indexer.tezos

import scala.concurrent.duration._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.testkit.util.DBSafe
import tech.cryptonomic.conseil.common.tezos.Fork
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{PublicKeyHash, TezosBlockHash, Voting}
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.Tables.{
  AccountsHistoryRow,
  AccountsRow,
  BakersHistoryRow,
  BakersRow,
  BakingRightsRow,
  BlocksRow,
  EndorsingRightsRow,
  FeesRow,
  GovernanceRow,
  OperationGroupsRow,
  OperationsRow,
  ProcessedChainEventsRow,
  TokenBalancesRow
}
import TezosDataGenerationKit.ForkValid
import java.{util => ju}
import java.time.Instant
import java.sql.Timestamp

/** Here we verify that any ∂ata saved persistently, that is marked as
  * belonging to a forked branch having been invalidated by latest chain
  * evolution, will not appear when querying the [[TezosDatabaseOperations]].
  */
class TezosForkDatabaseOperationsTest
    extends AnyWordSpec
    with InMemoryDatabase
    with TezosInMemoryDatabaseSetup
    with Matchers
    with ScalaFutures
    with OptionValues
    with LazyLogging
    with IntegrationPatience {

  import scala.concurrent.ExecutionContext.Implicits.global

  import TezosDataGenerationKit.DataModelGeneration._
  import TezosDataGenerationKit.DomainModelGeneration._
  import LocalGenerationUtils._

  val sut = TezosDatabaseOperations

  "The database operations" should {

      /* The common idea in the tests for forked values is that we generate random entities,
       * to be stored on the database, originally with no invalidation field.
       * We then set the invalidation fields to guarantee they are indeed invalidated as by a fork.
       * Finally we use the operations to verify that they won't be loaded, as if they're not stored.
       *
       * It might happen that relational constraints on the db must be satisfied, usually requiring
       * a block row to be available to be referred by the invalidated entity. We generate the
       * associated row and invalidate it too, for consistency.
       *
       * The invalidation values (e.g. timestamps, fork-id) are themselves randomly generated.
       */

      "not return fork-invalidated data for baking rights" in {

        val (validRow, validReferencedBlock, invalidation, fork) =
          arbitrary[(ForkValid[BakingRightsRow], ForkValid[BlocksRow], Timestamp, ju.UUID)].sample.value
        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRow = validRow.data.copy(
          blockLevel = invalidBlock.level,
          blockHash = Some(invalidBlock.hash),
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )
        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.BakingRights += invalidRow
          loaded <- sut.getBakingRightsForLevel(invalidRow.blockLevel)
        } yield (stored, loaded)

        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded.exists(_.invalidatedAsof.isDefined) shouldBe false

      }

      "not return fork-invalidated data for endorsing rights" in {

        val (validRow, validReferencedBlock, invalidation, fork) =
          arbitrary[(ForkValid[EndorsingRightsRow], ForkValid[BlocksRow], Timestamp, ju.UUID)].sample.value
        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRow = validRow.data.copy(
          blockLevel = invalidBlock.level,
          blockHash = Some(invalidBlock.hash),
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )
        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.EndorsingRights += invalidRow
          loaded <- sut.getEndorsingRightsForLevel(invalidRow.blockLevel)
        } yield (stored, loaded)

        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded.exists(_.invalidatedAsof.isDefined) shouldBe false

      }

      "not return fork-invalidated data for governance" in {

        val (validRow, invalidation, fork) =
          arbitrary[(ForkValid[GovernanceRow], Timestamp, ju.UUID)]
            .retryUntil(_._1.data.level.isDefined) //we need to check a governance row by level, so it has to be there!
            .sample
            .value
        val invalidRow = validRow.data.copy(
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )
        val populateAndFetch = for {
          stored <- Tables.Governance += invalidRow
          loaded <- sut.getGovernanceForLevel(invalidRow.level.value)
        } yield (stored, loaded)

        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded.exists(_.invalidatedAsof.isDefined) shouldBe false

      }

      "not return fork-invalidated data for ballot counts by cycle" in {
        /* Generate the data random sample */
        val (validRows, validReferencedGroup, validReferencedBlock, invalidation, fork) =
          Gen
            .zip(
              nonConflictingArbitrary[ForkValid[OperationsRow]](
                satisfying = (row: ForkValid[OperationsRow]) =>
                  //we need to count ballot votes and check by cycle, so it has to be there!
                  row.data.cycle.isDefined && row.data.ballot.isDefined
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
        val invalidRows = validRows.map(
          _.data.copy(
            blockHash = invalidBlock.hash,
            blockLevel = invalidBlock.level,
            operationGroupHash = invalidGroup.hash,
            kind = "ballot",
            invalidatedAsof = Some(invalidation),
            forkId = fork.toString
          )
        )

        /* Store everything on db */
        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          _ <- Tables.OperationGroups += invalidGroup
          Some(stored) <- Tables.Operations ++= invalidRows
          loaded <- sut.getBallotOperationsForCycle(invalidRows.head.cycle.value)
        } yield (stored, loaded)

        /* Test the results */
        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe Voting.BallotCounts(0, 0, 0)

      }

      "not return fork-invalidated data for ballot counts by level" in {
        /* Generate the data random sample */
        val (validRows, validReferencedGroup, validReferencedBlock, invalidation, fork) =
          Gen
            .zip(
              nonConflictingArbitrary[ForkValid[OperationsRow]](
                //we need to count ballot votes, so it has to be there!
                satisfying = (row: ForkValid[OperationsRow]) => row.data.ballot.isDefined
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
        val invalidRows = validRows.map(
          _.data.copy(
            blockHash = invalidBlock.hash,
            blockLevel = invalidBlock.level,
            operationGroupHash = invalidGroup.hash,
            kind = "ballot",
            invalidatedAsof = Some(invalidation),
            forkId = fork.toString
          )
        )

        /* Store everything on db */
        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          _ <- Tables.OperationGroups += invalidGroup
          Some(stored) <- Tables.Operations ++= invalidRows
          loaded <- sut.getBallotOperationsForLevel(invalidRows.head.blockLevel)
        } yield (stored, loaded)

        /* Test the results */
        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe Voting.BallotCounts(0, 0, 0)

      }

      "not return fork-invalidated data for proposal hashes by operation cycle" in {
        val (validRow, validReferencedGroup, validReferencedBlock, invalidation, fork) =
          arbitrary[
            (
                ForkValid[OperationsRow],
                ForkValid[OperationGroupsRow],
                ForkValid[BlocksRow],
                Timestamp,
                ju.UUID
            )
          ].retryUntil {
            case (ForkValid(operation), _, _, _, _) =>
              operation.proposal.isDefined && operation.cycle.isDefined
          } //we need to check some existing proposal operation row by cycle, so it has to be there!
          .sample.value

        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidGroup = validReferencedGroup.data.copy(
          blockId = invalidBlock.hash,
          blockLevel = invalidBlock.level,
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )
        val invalidRow = validRow.data.copy(
          blockHash = invalidBlock.hash,
          blockLevel = invalidBlock.level,
          operationGroupHash = invalidGroup.hash,
          kind = "proposals",
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          _ <- Tables.OperationGroups += invalidGroup
          stored <- Tables.Operations += invalidRow
          loaded <- sut.getProposalOperationHashesByCycle(invalidRow.cycle.value)
        } yield (stored, loaded)

        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "not return fork-invalidated data for baker levels" in {

        /* Generate the data random sample */
        val (validRows, validReferencedBlock, invalidation, fork) =
          Gen
            .zip(
              //duplicate ids will fail to save on the db for violation of the PK uniqueness
              nonConflictingArbitrary[ForkValid[BakersRow]],
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
        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          Some(stored) <- Tables.Bakers ++= invalidRows
          loaded <- sut.getLevelsForBakers(invalidRows.map(row => PublicKeyHash(row.pkh)).toSet)
        } yield (stored, loaded)

        /* Test the results */
        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "not mark fork-invalidated accounts history as bakers" in {

        /* Generate the data random sample */
        val (validRow, validReferencedBlock, ForkValid(baker), invalidation, fork) =
          arbitrary[
            (
                ForkValid[AccountsHistoryRow],
                ForkValid[BlocksRow],
                ForkValid[BakersRow],
                Timestamp,
                ju.UUID
            )
          ].sample.value

        /* Stores the same block hash both as invalidated and valid.
         * The block will have the hash referenced by the history entry, but should not update
         * the entry itself, even when it matches the baker pkh.
         * The case might happen when a fork "jumps" back'n'forth between two alternatives and blocks
         * with the associated data is first invalidated, but later re-added to the system upon
         * the invalidated fork being restored as the valid one.
         * We want the invalidated history to remain there unchanged as evidence of what happened, even
         * though a new baker entry has the same pkh.
         */

        //this will be a baker stored for the valid block
        val validBaker = baker.copy(blockId = validReferencedBlock.data.hash)
        //a block referencing the invalidated account data, invalidated itself
        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        //the invalidated account data, matching the valid baker id
        val invalidRow = validRow.data.copy(
          blockId = invalidBlock.hash,
          blockLevel = invalidBlock.level,
          isBaker = false,
          accountId = baker.pkh,
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        /* Store everything on db */
        val populate = for {
          _ <- Tables.Blocks ++= List(invalidBlock, validReferencedBlock.data)
          storedAccounts <- Tables.AccountsHistory += invalidRow
          storedBakers <- Tables.Bakers += validBaker
        } yield storedAccounts + storedBakers

        /* Test it's there */
        val stored = dbHandler.run(populate).futureValue

        stored shouldBe >(0)

        /* Test the results */
        val updateAndFetch = for {
          updatedRows <- sut.updateAccountsHistoryWithBakers(Set(TezosBlockHash(validBaker.blockId)))
          historyRows <- Tables.AccountsHistory.result
        } yield (updatedRows, historyRows)

        val (updates, history) = dbHandler.run(updateAndFetch).futureValue

        updates shouldBe 0
        history.exists(_.isBaker) shouldBe false

      }

      "not mark fork-invalidated accounts as bakers" in {

        /* Generate the data random sample */
        val (validRow, validReferencedBlock, ForkValid(baker), invalidation, fork) =
          arbitrary[
            (
                ForkValid[AccountsRow],
                ForkValid[BlocksRow],
                ForkValid[BakersRow],
                Timestamp,
                ju.UUID
            )
          ].sample.value

        /* Stores the same block hash both as invalidated and valid.
         * The block will have the hash referenced by the account entry, but should not update
         * the entry itself, even when it matches the baker pkh.
         * The case might happen when a fork "jumps" back'n'forth between two alternatives and blocks
         * with the associated data is first invalidated, but later re-added to the system upon
         * the invalidated fork being restored as the valid one.
         * We want the invalidated account to remain there unchanged as evidence of what happened, even
         * though a new baker entry has the same pkh.
         */

        //this will be a baker stored for the valid block
        val validBaker = baker.copy(blockId = validReferencedBlock.data.hash)
        //a block referencing the invalidated account data, invalidated itself
        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        //the invalidated account data, matching the valid baker id
        val invalidRow = validRow.data.copy(
          blockId = invalidBlock.hash,
          blockLevel = invalidBlock.level,
          isBaker = false,
          accountId = baker.pkh,
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        /* Store everything on db */
        val populate = for {
          _ <- Tables.Blocks ++= List(invalidBlock, validReferencedBlock.data)
          storedAccounts <- Tables.Accounts += invalidRow
          storedBakers <- Tables.Bakers += validBaker
        } yield storedAccounts + storedBakers

        /* Test it's there */
        val stored = dbHandler.run(populate).futureValue

        stored shouldBe >(0)

        /* Test the results */
        val updateAndFetch = for {
          updatedRows <- sut.updateAccountsWithBakers(Set(TezosBlockHash(validBaker.blockId)))
          accountRows <- Tables.Accounts.result
        } yield (updatedRows, accountRows)

        val (updates, loaded) = dbHandler.run(updateAndFetch).futureValue

        updates shouldBe 0
        loaded.exists(_.isBaker) shouldBe false

      }

      "not return fork-invalidated data for bakers" in {

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

        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.Bakers += invalidRow
          loaded <- sut.getBakers()
        } yield (stored, loaded)

        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "not use fork-invalidated operation to compute average fees" in {

        /* Generate the data random sample */
        val (validRows, validReferencedGroup, validReferencedBlock, invalidation, fork) =
          Gen
            .zip(
              //duplicate ids will fail to save on the db for violation of the PK uniqueness
              nonConflictingArbitrary[ForkValid[OperationsRow]],
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
        val invalidRows = validRows.map(
          _.data.copy(
            blockHash = invalidBlock.hash,
            blockLevel = invalidBlock.level,
            operationGroupHash = invalidGroup.hash,
            kind = "transaction",
            invalidatedAsof = Some(invalidation),
            forkId = fork.toString
          )
        )

        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          _ <- Tables.OperationGroups += invalidGroup
          Some(stored) <- Tables.Operations ++= invalidRows
          loaded <- sut.FeesStatistics.calculateAverage(daysPast = 10)
        } yield (stored, loaded)

        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "invalidate blocks" in {

        /* Generate the data random sample */
        val (validRows, forkLevel, invalidation, fork) =
          Gen
            .zip(
              //duplicate ids will fail to save on the db for violation of the PK uniqueness
              nonConflictingArbitrary[ForkValid[BlocksRow]],
              Gen.posNum[Long],
              TezosDataGenerationKit.instantGenerator,
              arbitrary[ju.UUID]
            )
            .retryUntil {
              case (rows, level, _, _) => rows.exists(_.data.level >= level)
            }
            .sample
            .value

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* Save everything and run the invalidation process, fetch everything back */
        val populateAndInvalidate = for {
          _ <- Tables.Blocks ++= validRows.map(_.data)
          invalidated <- sut.ForkInvalidation.blocks.invalidate(
            fromLevel = forkLevel,
            asOf = invalidation,
            forkId = fork.toString
          )
          loaded <- Tables.Blocks.sortBy(_.level.asc).result
        } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(populateAndInvalidate).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.level < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")
      }

      "invalidate operation groups" in {

        /* Generate the data random sample
         * We need no more blocks than rows under test, because we guarantee levels
         * above the fork on blocks, and only after we assign a row to some block.
         * To make sure that at least some row will be invalidated we need to use
         * all the blocks to at least one row, if not more.
         */
        val (validRows, validReferencedBlocks, forkLevel, invalidation, fork) = {
          val generator = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- nonConflictingArbitrary[ForkValid[OperationGroupsRow]]
            blocks <- nonConflictingArbitrary[ForkValid[BlocksRow]](atMost = rows.size)
            level <- Gen.posNum[Long]
            instant <- TezosDataGenerationKit.instantGenerator
            id <- arbitrary[ju.UUID]
          } yield (rows, blocks, level, instant, id)

          /* We care only about block levels being impacted by the fork, because we'll
           * then update any target row to have a reference to some block in the list.
           */
          generator.retryUntil {
            case (_, blocks, level, _, _) => blocks.exists(_.data.level >= level)
          }.sample.value

        }

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* basic trick to periodically concatenate a finite list into an infinite stream */
        lazy val cyclicRefs: Stream[BlocksRow] = validReferencedBlocks.map(_.data).toStream #::: cyclicRefs

        /* we now assign a reference block to each row, to guarantee db constraints are preserved */
        val rowsToStore = validRows
          .map(_.data)
          .zip(cyclicRefs)
          .map {
            case (row, block) =>
              row.copy(
                blockId = block.hash,
                blockLevel = block.level
              )
          }

        /* Save everything and run the invalidation process, fetch everything back
         * We need to be sure that corresponding blocks are invalidated with the same fork-id
         * to guarantee FK consistency
         */
        val populate = for {
          _ <- Tables.Blocks ++= validReferencedBlocks.map(_.data)
          _ <- Tables.OperationGroups ++= rowsToStore
        } yield ()

        dbHandler.run(populate).isReadyWithin(5.seconds) shouldBe true

        val invalidateAndFetch =
          for {
            _ <- sut.deferConstraints()
            _ <- sut.ForkInvalidation.blocks.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            invalidated <- sut.ForkInvalidation.operationGroups.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.OperationGroups.sortBy(_.blockLevel.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.blockLevel < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate operations" in {

        /* Generate the data random sample
         * We need no more blocks than rows under test, because we guarantee levels
         * above the fork on blocks, and only after we assign a row to some block.
         * To make sure that at least some row will be invalidated we need to use
         * all the blocks to at least one row, if not more.
         *
         * To generate containing groups we first generate a pair of group and operation.
         * Then we split the list of pairs to individual, same-sized, lists, and apply
         * any check on key uniqueness.
         * Finally we match group and operation of the same pair together and with a block.
         */
        val (validRows, validReferencedBlocks, forkLevel, invalidation, fork) = {
          val generator = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- nonConflictingArbitrary[ForkValid[OperationsRow], ForkValid[OperationGroupsRow]]
            blocks <- nonConflictingArbitrary[ForkValid[BlocksRow]](atMost = rows.size)
            instant <- TezosDataGenerationKit.instantGenerator
            id <- arbitrary[ju.UUID]
            level <- Gen.posNum[Long]
          } yield (rows, blocks, level, instant, id)

          /* We care only about block levels being impacted by the fork, because we'll
           * then update any target row to have a reference to some block in the list.
           */
          generator.retryUntil {
            case (_, blocks, level, _, _) => blocks.exists(_.data.level >= level)
          }.sample.value

        }

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* basic trick to periodically concatenate a finite list into an infinite stream */
        lazy val cyclicRefs: Stream[BlocksRow] = validReferencedBlocks.map(_.data).toStream #::: cyclicRefs

        /* we now assign a reference block to each row, to guarantee db constraints are preserved */
        val (rowsToStore, groupRowsToStore) = validRows
          .zip(cyclicRefs)
          .map {
            case ((ForkValid(operation), ForkValid(group)), block) =>
              val opToStore = operation.copy(
                blockHash = block.hash,
                blockLevel = block.level,
                operationGroupHash = group.hash
              )
              val groupToStore = group.copy(
                blockId = block.hash,
                blockLevel = block.level
              )
              (opToStore, groupToStore)
          }
          .unzip

        /* Save everything and run the invalidation process, fetch everything back
         * We need to be sure that corresponding blocks are invalidated with the same fork-id
         * to guarantee FK consistency
         */
        val populate = for {
          _ <- Tables.Blocks ++= validReferencedBlocks.map(_.data)
          _ <- Tables.OperationGroups ++= groupRowsToStore
          _ <- Tables.Operations ++= rowsToStore
        } yield ()

        dbHandler.run(populate).isReadyWithin(5.seconds) shouldBe true

        val invalidateAndFetch =
          for {
            _ <- sut.deferConstraints()
            _ <- sut.ForkInvalidation.blocks.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            _ <- sut.ForkInvalidation.operationGroups.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            invalidated <- sut.ForkInvalidation.operations.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.Operations.sortBy(_.blockLevel.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.blockLevel < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate accounts" in {
        /* Generate the data random sample
         * We need no more blocks than rows under test, because we guarantee levels
         * above the fork on blocks, and only after we assign a row to some block.
         * To make sure that at least some row will be invalidated we need to use
         * all the blocks to at least one row, if not more.
         */
        val (validRows, validReferencedBlocks, forkLevel, invalidation, fork) = {
          val generator = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- nonConflictingArbitrary[ForkValid[AccountsRow]]
            blocks <- nonConflictingArbitrary[ForkValid[BlocksRow]](atMost = rows.size)
            level <- Gen.posNum[Long]
            instant <- TezosDataGenerationKit.instantGenerator
            id <- arbitrary[ju.UUID]
          } yield (rows, blocks, level, instant, id)

          /* We care only about block levels being impacted by the fork, because we'll
           * then update any target row to have a reference to some block in the list.
           */
          generator.retryUntil {
            case (_, blocks, level, _, _) => blocks.exists(_.data.level >= level)
          }.sample.value

        }

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* basic trick to periodically concatenate a finite list into an infinite stream */
        lazy val cyclicRefs: Stream[BlocksRow] = validReferencedBlocks.map(_.data).toStream #::: cyclicRefs

        /* we now assign a reference block to each row, to guarantee db constraints are preserved */
        val rowsToStore = validRows
          .map(_.data)
          .zip(cyclicRefs)
          .map {
            case (row, block) =>
              row.copy(
                blockId = block.hash,
                blockLevel = block.level
              )
          }

        /* Save everything and run the invalidation process, fetch everything back
         * We need to be sure that corresponding blocks are invalidated with the same fork-id
         * to guarantee FK consistency
         */
        val populate = for {
          _ <- Tables.Blocks ++= validReferencedBlocks.map(_.data)
          _ <- Tables.Accounts ++= rowsToStore
        } yield ()

        dbHandler.run(populate).isReadyWithin(5.seconds) shouldBe true

        val invalidateAndFetch =
          for {
            _ <- sut.deferConstraints()
            _ <- sut.ForkInvalidation.blocks.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            invalidated <- sut.ForkInvalidation.accounts.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.Accounts.sortBy(_.blockLevel.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.blockLevel < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate account history" in {
        /* Generate the data random sample */
        val (validRows, forkLevel, invalidation, fork) =
          Gen
            .zip(
              //duplicate ids will fail to save on the db for violation of the PK uniqueness
              nonConflictingArbitrary[ForkValid[AccountsHistoryRow]],
              Gen.posNum[Long],
              TezosDataGenerationKit.instantGenerator,
              arbitrary[ju.UUID]
            )
            .retryUntil {
              /* We need to have at least a non-empty sample to invalidate */
              case (rows, level, _, _) => rows.exists(_.data.blockLevel >= level)
            }
            .sample
            .value

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* Save everything and run the invalidation process, fetch everything back */
        val invalidateAndFetch =
          for {
            _ <- Tables.AccountsHistory ++= validRows.map(_.data)
            invalidated <- sut.ForkInvalidation.accountsHistory.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.AccountsHistory.sortBy(_.blockLevel.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.blockLevel < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate bakers" in {
        /* Generate the data random sample
         * We need no more blocks than rows under test, because we guarantee levels
         * above the fork on blocks, and only after we assign a row to some block.
         * To make sure that at least some row will be invalidated we need to use
         * all the blocks to at least one row, if not more.
         */
        val (validRows, validReferencedBlocks, forkLevel, invalidation, fork) = {
          val generator = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- nonConflictingArbitrary[ForkValid[BakersRow]]
            blocks <- nonConflictingArbitrary[ForkValid[BlocksRow]](atMost = rows.size)
            level <- Gen.posNum[Long]
            instant <- TezosDataGenerationKit.instantGenerator
            id <- arbitrary[ju.UUID]
          } yield (rows, blocks, level, instant, id)

          /* We care only about block levels being impacted by the fork, because we'll
           * then update any target row to have a reference to some block in the list.
           */
          generator.retryUntil {
            case (_, blocks, level, _, _) => blocks.exists(_.data.level >= level)
          }.sample.value

        }

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* basic trick to periodically concatenate a finite list into an infinite stream */
        lazy val cyclicRefs: Stream[BlocksRow] = validReferencedBlocks.map(_.data).toStream #::: cyclicRefs

        /* we now assign a reference block to each row, to guarantee db constraints are preserved */
        val rowsToStore = validRows
          .map(_.data)
          .zip(cyclicRefs)
          .map {
            case (row, block) =>
              row.copy(
                blockId = block.hash,
                blockLevel = block.level
              )
          }

        /* Save everything and run the invalidation process, fetch everything back
         * We need to be sure that corresponding blocks are invalidated with the same fork-id
         * to guarantee FK consistency
         */
        val populate = for {
          _ <- Tables.Blocks ++= validReferencedBlocks.map(_.data)
          _ <- Tables.Bakers ++= rowsToStore
        } yield ()

        dbHandler.run(populate).isReadyWithin(5.seconds) shouldBe true

        val invalidateAndFetch =
          for {
            _ <- sut.deferConstraints()
            _ <- sut.ForkInvalidation.blocks.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            invalidated <- sut.ForkInvalidation.bakers.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.Bakers.sortBy(_.blockLevel.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.blockLevel < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate baker history" in {
        /* Generate the data random sample */
        val (validRows, forkLevel, invalidation, fork) =
          Gen
            .zip(
              //duplicate ids will fail to save on the db for violation of the PK uniqueness
              nonConflictingArbitrary[ForkValid[BakersHistoryRow]],
              Gen.posNum[Long],
              TezosDataGenerationKit.instantGenerator,
              arbitrary[ju.UUID]
            )
            .retryUntil {
              /* We need to have at least a non-empty sample to invalidate */
              case (rows, level, _, _) => rows.exists(_.data.blockLevel >= level)
            }
            .sample
            .value

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* Save everything and run the invalidation process, fetch everything back */
        val invalidateAndFetch =
          for {
            _ <- Tables.BakersHistory ++= validRows.map(_.data)
            invalidated <- sut.ForkInvalidation.bakersHistory.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.BakersHistory.sortBy(_.blockLevel.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.blockLevel < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate governance" in {
        /* Generate the data random sample */
        val (validRows, forkLevel, invalidation, fork) =
          Gen
            .zip(
              //duplicate ids will fail to save on the db for violation of the PK uniqueness
              nonConflictingArbitrary[ForkValid[GovernanceRow]],
              Gen.posNum[Long],
              TezosDataGenerationKit.instantGenerator,
              arbitrary[ju.UUID]
            )
            .retryUntil {
              /* We need to have at least a non-empty sample to invalidate */
              case (rows, level, _, _) => rows.exists(_.data.level.exists(_ >= level))
            }
            .sample
            .value

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* Save everything and run the invalidation process, fetch everything back */
        val invalidateAndFetch =
          for {
            _ <- Tables.Governance ++= validRows.map(_.data)
            invalidated <- sut.ForkInvalidation.governance.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.Governance.sortBy(_.level.nullsFirst.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.level.forall(_ < forkLevel))

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate token balances" in {
        /* Generate the data random sample */
        val (validRows, forkLevel, invalidation, fork) =
          Gen
            .zip(
              //duplicate ids will fail to save on the db for violation of the PK uniqueness
              nonConflictingArbitrary[ForkValid[TokenBalancesRow]],
              Gen.posNum[Long],
              TezosDataGenerationKit.instantGenerator,
              arbitrary[ju.UUID]
            )
            .retryUntil {
              /* We need to have at least a non-empty sample to invalidate */
              case (rows, level, _, _) => rows.exists(_.data.blockLevel >= level)
            }
            .sample
            .value

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* Save everything and run the invalidation process, fetch everything back */
        val invalidateAndFetch =
          for {
            _ <- Tables.TokenBalances ++= validRows.map(_.data)
            invalidated <- sut.ForkInvalidation.tokenBalances.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.TokenBalances.sortBy(_.blockLevel.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.blockLevel < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate baking rights" in {
        /* Generate the data random sample
         * We need no more blocks than rows under test, because we guarantee levels
         * above the fork on blocks, and only after we assign a row to some block.
         * To make sure that at least some row will be invalidated we need to use
         * all the blocks to at least one row, if not more.
         */
        val (validRows, validReferencedBlocks, forkLevel, invalidation, fork) = {
          val generator = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- nonConflictingArbitrary[ForkValid[BakingRightsRow]]
            blocks <- nonConflictingArbitrary[ForkValid[BlocksRow]](atMost = rows.size)
            level <- Gen.posNum[Long]
            instant <- TezosDataGenerationKit.instantGenerator
            id <- arbitrary[ju.UUID]
          } yield (rows, blocks, level, instant, id)

          /* We care only about block levels being impacted by the fork, because we'll
           * then update any target row to have a reference to some block in the list.
           */
          generator.retryUntil {
            case (_, blocks, level, _, _) => blocks.exists(_.data.level >= level)
          }.sample.value

        }

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* basic trick to periodically concatenate a finite list into an infinite stream */
        lazy val cyclicRefs: Stream[BlocksRow] = validReferencedBlocks.map(_.data).toStream #::: cyclicRefs

        /* we now assign a reference block to each row, to guarantee db constraints are preserved */
        val rowsToStore = validRows
          .map(_.data)
          .zip(cyclicRefs)
          .map {
            case (row, block) =>
              row.copy(
                blockHash = row.blockHash.map(_ => block.hash),
                blockLevel = block.level
              )
          }

        /* Save everything and run the invalidation process, fetch everything back
         * We need to be sure that corresponding blocks are invalidated with the same fork-id
         * to guarantee FK consistency
         */
        val populate = for {
          _ <- Tables.Blocks ++= validReferencedBlocks.map(_.data)
          _ <- Tables.BakingRights ++= rowsToStore
        } yield ()

        dbHandler.run(populate).isReadyWithin(5.seconds) shouldBe true

        val invalidateAndFetch =
          for {
            _ <- sut.deferConstraints()
            _ <- sut.ForkInvalidation.blocks.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            invalidated <- sut.ForkInvalidation.bakingRights.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.BakingRights.sortBy(_.blockLevel.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.blockLevel < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate endorsing rights" in {
        /* Generate the data random sample
         * We need no more blocks than rows under test, because we guarantee levels
         * above the fork on blocks, and only after we assign a row to some block.
         * To make sure that at least some row will be invalidated we need to use
         * all the blocks to at least one row, if not more.
         */
        val (validRows, validReferencedBlocks, forkLevel, invalidation, fork) = {
          val generator = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- nonConflictingArbitrary[ForkValid[EndorsingRightsRow]]
            blocks <- nonConflictingArbitrary[ForkValid[BlocksRow]](atMost = rows.size)
            level <- Gen.posNum[Long]
            instant <- TezosDataGenerationKit.instantGenerator
            id <- arbitrary[ju.UUID]
          } yield (rows, blocks, level, instant, id)

          /* We care only about block levels being impacted by the fork, because we'll
           * then update any target row to have a reference to some block in the list.
           */
          generator.retryUntil {
            case (_, blocks, level, _, _) => blocks.exists(_.data.level >= level)
          }.sample.value

        }

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* basic trick to periodically concatenate a finite list into an infinite stream */
        lazy val cyclicRefs: Stream[BlocksRow] = validReferencedBlocks.map(_.data).toStream #::: cyclicRefs

        /* we now assign a reference block to each row, to guarantee db constraints are preserved */
        val rowsToStore = validRows
          .map(_.data)
          .zip(cyclicRefs)
          .map {
            case (row, block) =>
              row.copy(
                blockHash = row.blockHash.map(_ => block.hash),
                blockLevel = block.level
              )
          }

        /* Save everything and run the invalidation process, fetch everything back
         * We need to be sure that corresponding blocks are invalidated with the same fork-id
         * to guarantee FK consistency
         */
        val populate = for {
          _ <- Tables.Blocks ++= validReferencedBlocks.map(_.data)
          _ <- Tables.EndorsingRights ++= rowsToStore
        } yield ()

        dbHandler.run(populate).isReadyWithin(5.seconds) shouldBe true

        val invalidateAndFetch =
          for {
            _ <- sut.deferConstraints()
            _ <- sut.ForkInvalidation.blocks.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            invalidated <- sut.ForkInvalidation.endorsingRights.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.EndorsingRights.sortBy(_.blockLevel.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.blockLevel < forkLevel)

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate fees " in {
        /* Generate the data random sample */
        val (validRows, forkLevel, invalidation, fork) = {
          val generator = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- Gen
              .nonEmptyListOf(arbitrary[ForkValid[FeesRow]])
            level <- Gen.posNum[Long]
            instant <- TezosDataGenerationKit.instantGenerator
            id <- arbitrary[ju.UUID]
          } yield (rows, level, instant, id)

          /* We need to have at least a non-empty sample to invalidate */
          generator.retryUntil {
            case (rows, level, _, _) => rows.exists(_.data.level.exists(_ >= level))
          }.sample.value

        }

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* Save everything and run the invalidation process, fetch everything back */
        val invalidateAndFetch =
          for {
            _ <- Tables.Fees ++= validRows.map(_.data)
            invalidated <- sut.ForkInvalidation.fees.invalidate(
              fromLevel = forkLevel,
              asOf = invalidation,
              forkId = fork.toString
            )
            loaded <- Tables.Fees.sortBy(_.level.nullsFirst.asc).result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we also want non-empty results to verify */
        loaded should not be empty

        /* Split the sorted results into pre-fork-level and post-fork-level */
        val (preFork, postFork) = loaded.span(_.level.forall(_ < forkLevel))

        allValid(preFork) shouldBe true

        allInvalidated(asof = invalidation, forkId = fork.toString)(postFork) shouldBe true

        info(s"resulting in ${preFork.size} elements before the fork and ${postFork.size} elements invalidated")

      }

      "invalidate processed events " in {
        /* Generate the data random sample */
        val (validRows, forkLevel, invalidation, fork) = {
          val generator = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- nonConflictingArbitrary[DBSafe[ProcessedChainEventsRow]]
            level <- Gen.posNum[Long]
            instant <- TezosDataGenerationKit.instantGenerator
            id <- arbitrary[ju.UUID]
          } yield (rows, level, instant, id)

          /* We need to have at least a non-empty sample to invalidate */
          generator.retryUntil {
            case (rows, level, _, _) => rows.exists(_.value.eventLevel >= level)
          }.sample.value

        }

        info(s"verifying with ${validRows.size} total elements and fork level $forkLevel")

        /* Save everything and run the invalidation process, fetch everything back */
        val invalidateAndFetch =
          for {
            _ <- Tables.ProcessedChainEvents ++= validRows.map(_.value)
            invalidated <- sut.ForkInvalidation.deleteProcessedEvents(fromLevel = forkLevel)
            loaded <- Tables.ProcessedChainEvents.result
          } yield (invalidated, loaded)

        val (invalidCount, loaded) = dbHandler.run(invalidateAndFetch.transactionally).futureValue

        /* we expect to have some invalidation and that the fork level will discriminate */
        invalidCount shouldBe >(0)

        /* we expect no more events after the fork */
        (loaded.isEmpty || loaded.forall(_.eventLevel < forkLevel)) shouldBe true

        info(s"resulting in ${loaded.size} remaining elements after the invalidation")
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
        case ForkValid(entity) => hasKey.getKey(entity)
      }

      /* will provide an implicit HasKey for an entity wrapped by DBSafe, like the one for ForkValid */
      implicit def dbSafeWithKey[PK, E](implicit hasKey: HasKey[E, PK]): HasKey[DBSafe[E], PK] = {
        case DBSafe(entity) => hasKey.getKey(entity)
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
      implicit val processedChainEventsRowHasKey: HasKey[ProcessedChainEventsRow, (Long, String)] =
        ev => (ev.eventLevel, ev.eventType)

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
