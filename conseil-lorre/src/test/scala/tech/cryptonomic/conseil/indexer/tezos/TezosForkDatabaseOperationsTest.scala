package tech.cryptonomic.conseil.indexer.tezos

import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.OptionValues
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import org.scalacheck.Gen
import org.scalacheck.Arbitrary.arbitrary
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.testkit.util.DBSafe
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{makeAccountId, Block, PublicKeyHash, TezosBlockHash, Voting}
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.Tables.{
  AccountsHistoryRow,
  AccountsRow,
  BakersRow,
  BakingRightsRow,
  BlocksRow,
  EndorsingRightsRow,
  GovernanceRow,
  OperationGroupsRow,
  OperationsRow
}
import TezosDataGenerationKit.ForkValid

import java.{util => ju}
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
        val (validRows, validReferencedGroup, validReferencedBlock, invalidation, fork) = {
          val generators = for {
            rows <- Gen.nonEmptyListOf(
              arbitrary[ForkValid[OperationsRow]]
                .retryUntil(op => op.data.cycle.isDefined && op.data.ballot.isDefined) //we need to count ballot votes and check by cycle, so it has to be there!
            )
            group <- arbitrary[ForkValid[OperationGroupsRow]]
            block <- arbitrary[ForkValid[BlocksRow]]
            ts <- arbitrary[Timestamp]
            id <- arbitrary[ju.UUID]
          } yield (rows, group, block, ts, id)

          generators.sample.value
        }
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
        val (validRows, validReferencedGroup, validReferencedBlock, invalidation, fork) = {
          val generators = for {
            rows <- Gen.nonEmptyListOf(
              arbitrary[ForkValid[OperationsRow]]
                .retryUntil(_.data.ballot.isDefined) //we need to count ballot votes, so it has to be there!
            )
            group <- arbitrary[ForkValid[OperationGroupsRow]]
            block <- arbitrary[ForkValid[BlocksRow]]
            ts <- arbitrary[Timestamp]
            id <- arbitrary[ju.UUID]
          } yield (rows, group, block, ts, id)

          generators.sample.value
        }
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
            case (ForkValid(op: OperationsRow), _, _, _, _) =>
              op.proposal.isDefined && op.cycle.isDefined
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

      "not return fork-invalidated data for account levels" in {
        /* group by key and count the resulting buckets' size to detect duplicates */
        def noDuplicateKeys(accounts: List[ForkValid[AccountsRow]]): Boolean =
          !accounts.groupBy(_.data.accountId).exists { case (key, rows) => rows.size > 1 }

        /* Generate the data random sample */
        val (validRows, validReferencedBlock, invalidation, fork) = {
          val generators = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- Gen.nonEmptyListOf(arbitrary[ForkValid[AccountsRow]]).retryUntil(noDuplicateKeys)
            block <- arbitrary[ForkValid[BlocksRow]]
            ts <- arbitrary[Timestamp]
            id <- arbitrary[ju.UUID]
          } yield (rows, block, ts, id)

          generators.sample.value
        }
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
          Some(stored) <- Tables.Accounts ++= invalidRows
          loaded <- sut.getLevelsForAccounts(invalidRows.map(row => makeAccountId(row.accountId)).toSet)
        } yield (stored, loaded)

        /* Test the results */
        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "not return fork-invalidated data for baker levels" in {
        /* group by key and count the resulting buckets' size to detect duplicates */
        def noDuplicateKeys(bakers: List[ForkValid[BakersRow]]): Boolean =
          !bakers.groupBy(_.data.pkh).exists { case (key, rows) => rows.size > 1 }

        /* Generate the data random sample */
        val (validRows, validReferencedBlock, invalidation, fork) = {
          val generators = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- Gen.nonEmptyListOf(arbitrary[ForkValid[BakersRow]]).retryUntil(noDuplicateKeys)
            block <- arbitrary[ForkValid[BlocksRow]]
            ts <- arbitrary[Timestamp]
            id <- arbitrary[ju.UUID]
          } yield (rows, block, ts, id)

          generators.sample.value
        }
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

      "not return fork-invalidated data for filteres bakers" in {
        /* group by key and count the resulting buckets' size to detect duplicates */
        def noDuplicateKeys(accounts: List[ForkValid[AccountsRow]]): Boolean =
          !accounts.groupBy(_.data.accountId).exists { case (key, rows) => rows.size > 1 }

        /* Generate the data random sample */
        val (validRows, validReferencedBlock, invalidation, fork) = {
          val generators = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- Gen.nonEmptyListOf(arbitrary[ForkValid[AccountsRow]]).retryUntil(noDuplicateKeys)
            block <- arbitrary[ForkValid[BlocksRow]]
            ts <- arbitrary[Timestamp]
            id <- arbitrary[ju.UUID]
          } yield (rows, block, ts, id)

          generators.sample.value
        }
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
          Some(stored) <- Tables.Accounts ++= invalidRows
          loaded <- sut.getFilteredBakerAccounts(exclude = Set.empty)
        } yield (stored, loaded)

        /* Test the results */
        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "not mark fork-invalidated accounts history as bakers" in {

        /* Generate the data random sample */
        val (validRow, validReferencedBlock, rolls, invalidation, fork) =
          arbitrary[(ForkValid[AccountsHistoryRow], ForkValid[BlocksRow], Voting.BakerRolls, Timestamp, ju.UUID)].sample.value
        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRow = validRow.data.copy(
          blockId = invalidBlock.hash,
          blockLevel = invalidBlock.level,
          isBaker = false,
          accountId = rolls.pkh.value,
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        /* Store everything on db */
        val populate = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.AccountsHistory += invalidRow
        } yield stored

        /* Test it's there */
        val stored = dbHandler.run(populate).futureValue

        stored shouldBe >(0)

        /* Generate a new block that will be used to reference the bakers rolls matching the history entry
         * we just saved.
         * The block will have the hash referenced by the history entry, but should not update
         * the history entry itself.
         * The case might happen when a fork "jumps" back'n'forth between two alternatives and blocks
         * with the associated data is first invalidated, but later re-added to the system upon
         * the invalidated fork being restored as the valid one.
         * We want the invalidated history to remain there unchanged as evidence of what happened.
         */

        val block = arbitrary[DBSafe[Block]].map {
          case DBSafe(block @ Block(data, operations, votes)) =>
            block.copy(
              data = data.copy(hash = TezosBlockHash(invalidRow.blockId))
            )
        }.sample.value

        /* Test the results */
        val updateAndFetch = for {
          updatedRows <- sut.updateAccountsHistoryWithBakers(List(rolls), block)
          historyRows <- Tables.AccountsHistory.result
        } yield (updatedRows, historyRows)

        val (updates, history) = dbHandler.run(updateAndFetch).futureValue

        updates shouldBe 0
        history.find(_.isBaker) shouldBe empty

      }

      "not mark fork-invalidated accounts as bakers" in {

        /* Generate the data random sample */
        val (validRow, validReferencedBlock, rolls, invalidation, fork) =
          arbitrary[(ForkValid[AccountsRow], ForkValid[BlocksRow], Voting.BakerRolls, Timestamp, ju.UUID)].sample.value
        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRow = validRow.data.copy(
          blockId = invalidBlock.hash,
          blockLevel = invalidBlock.level,
          isBaker = false,
          accountId = rolls.pkh.value,
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )

        /* Store everything on db */
        val populate = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.Accounts += invalidRow
        } yield stored

        /* Test it's there */
        val stored = dbHandler.run(populate).futureValue

        stored shouldBe >(0)

        /* Generate a new block that will be used to reference the bakers rolls matching the history entry
         * we just saved.
         * The block will have the hash referenced by the history entry, but should not update
         * the history entry itself.
         * The case might happen when a fork "jumps" back'n'forth between two alternatives and blocks
         * with the associated data is first invalidated, but later re-added to the system upon
         * the invalidated fork being restored as the valid one.
         * We want the invalidated history to remain there unchanged as evidence of what happened.
         */

        val block = arbitrary[DBSafe[Block]].map {
          case DBSafe(block @ Block(data, operations, votes)) =>
            block.copy(data = data.copy(hash = TezosBlockHash(invalidRow.blockId)))
        }.sample.value

        /* Test the results */
        val updateAndFetch = for {
          updatedRows <- sut.updateAccountsWithBakers(List(rolls), block)
          historyRows <- Tables.Accounts.result
        } yield (updatedRows, historyRows)

        val (updates, history) = dbHandler.run(updateAndFetch).futureValue

        updates shouldBe 0
        history.find(_.isBaker) shouldBe empty

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

      "not return fork-invalidated data for bakers by block-hash" in {

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
          loaded <- sut.getBakersForBlocks(List(TezosBlockHash(invalidRow.blockId)))
        } yield (stored, loaded)

        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        //get all rolls for any requested hash and concat them together
        loaded.foldLeft(List.empty[Voting.BakerRolls]) {
          case (allRolls, (hash, rolls)) => allRolls ++ rolls
        } shouldBe empty

      }

      "not use fork-invalidated operation to compute average fees" in {

        /* group by key and count the resulting buckets' size to detect duplicates */
        def noDuplicateKeys(ops: List[ForkValid[OperationsRow]]): Boolean =
          !ops.groupBy(_.data.operationId).exists { case (key, rows) => rows.size > 1 }

        /* Generate the data random sample */
        val (validRows, validReferencedGroup, validReferencedBlock, invalidation, fork) = {
          val generators = for {
            //duplicate ids will fail to save on the db for violation of the PK uniqueness
            rows <- Gen.nonEmptyListOf(arbitrary[ForkValid[OperationsRow]]).retryUntil(noDuplicateKeys)
            group <- arbitrary[ForkValid[OperationGroupsRow]]
            block <- arbitrary[ForkValid[BlocksRow]]
            ts <- arbitrary[Timestamp]
            id <- arbitrary[ju.UUID]
          } yield (rows, group, block, ts, id)

          generators.sample.value
        }

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
          loaded <- sut.calculateAverageFees(kind = "transaction", numberOfFeesAveraged = invalidRows.size)
        } yield (stored, loaded)

        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "not return fork-invalidated data for activated accounts" in {

        val (validRow, validReferencedBlock, invalidation, fork) =
          arbitrary[(ForkValid[AccountsRow], ForkValid[BlocksRow], Timestamp, ju.UUID)].sample.value
        val invalidBlock = validReferencedBlock.data.copy(invalidatedAsof = Some(invalidation), forkId = fork.toString)
        val invalidRow = validRow.data.copy(
          isActivated = true,
          blockLevel = invalidBlock.level,
          blockId = invalidBlock.hash,
          invalidatedAsof = Some(invalidation),
          forkId = fork.toString
        )
        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          stored <- Tables.Accounts += invalidRow
          loaded <- sut.findActivatedAccountIds
        } yield (stored, loaded)

        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty

      }

      "not return fork-invalidated data for latest operation hashes by kind" in {
        /* Generate the data random sample */
        val (validRows, validReferencedGroup, validReferencedBlock, invalidation, fork) = {
          val generators = for {
            rows <- Gen.nonEmptyListOf(
              arbitrary[ForkValid[OperationsRow]].retryUntil(_.data.blockLevel > 0) //add a bit more realism
            )
            group <- arbitrary[ForkValid[OperationGroupsRow]]
            block <- arbitrary[ForkValid[BlocksRow]]
            ts <- arbitrary[Timestamp]
            id <- arbitrary[ju.UUID]
          } yield (rows, group, block, ts, id)

          generators.sample.value
        }
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
        val populateAndFetch = for {
          _ <- Tables.Blocks += invalidBlock
          _ <- Tables.OperationGroups += invalidGroup
          Some(stored) <- Tables.Operations ++= invalidRows
          loaded <- sut.fetchRecentOperationsHashByKind(ofKind = operationKinds, fromLevel = minRecordedLevel)
        } yield (stored, loaded)

        /* Test the results */
        val (stored, loaded) = dbHandler.run(populateAndFetch).futureValue

        stored shouldBe >(0)
        loaded shouldBe empty
      }

    }

}
