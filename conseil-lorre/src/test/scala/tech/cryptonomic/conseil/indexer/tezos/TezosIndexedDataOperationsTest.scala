package tech.cryptonomic.conseil.indexer.tezos

import com.typesafe.scalalogging.LazyLogging
import cats.implicits._
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.OptionValues
import org.scalatest.matchers.should.Matchers
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.Gen
import org.scalacheck.ScalacheckShapeless._
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.testkit.util.DBSafe
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BakingRights, Block, EndorsingRights, FetchRights}
import tech.cryptonomic.conseil.common.tezos.TezosOptics
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.Tables.BlocksRow
import tech.cryptonomic.conseil.indexer.tezos.TezosDataGenerationKit.ForkValid
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.{TNSContract, TokenContracts}

import scala.concurrent.Await
import scala.concurrent.duration._
import java.{util => ju}
import java.sql.Timestamp
import tech.cryptonomic.conseil.common.tezos.Tables.EndorsingRightsRow
import tech.cryptonomic.conseil.common.tezos.Tables.BakingRightsRow

class TezosIndexedDataOperationsTest
    extends AnyWordSpec
    with Matchers
    with InMemoryDatabase
    with TezosInMemoryDatabaseSetup
    with ScalaFutures
    with OptionValues
    with LazyLogging
    with IntegrationPatience
    with TezosDatabaseOperationsTestFixtures {

  import scala.concurrent.ExecutionContext.Implicits.global
  import TezosDataGenerationKit.DomainModelGeneration._
  import TezosDataGenerationKit.DataModelGeneration._

  "TezosIndexedDataOperations" should {
      implicit val noTokenContracts: TokenContracts = TokenContracts.fromConfig(List.empty)
      implicit val noTNSContracts: TNSContract = TNSContract.noContract

      val sut = new TezosIndexedDataOperations {
        override lazy val dbReadHandle = dbHandler
      }

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

        Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks)), 5.seconds)

        // when
        val result = sut.fetchBlockAtLevel(pickLevel).futureValue.value

        // then
        result.level shouldBe pickLevel
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

        Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks)), 5.seconds)

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
            fetchRights <- arbitrary[FetchRights]
            bakeRights <- Gen.nonEmptyListOf(arbitrary[BakingRights])
            delegates <- Gen.infiniteStream(arbitrary[DBSafe[String]])
            dbSafeRights = fetchRights.copy(blockHash = Some(referenceBlock.data.hash))
            dbSafeBakingRights = bakeRights.zip(delegates).map {
              case (rights, DBSafe(delegate)) => rights.copy(delegate = delegate)
            }
          } yield (dbSafeRights, dbSafeBakingRights, referenceBlock)

          generator.sample.value
        }

        info(s"verifying with ${bakingRights.size} rights")

        val populate = for {
          _ <- TezosDatabaseOperations.writeBlocks(List(block))
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
            fetchRights <- arbitrary[FetchRights]
            endorseRights <- Gen.nonEmptyListOf(arbitrary[EndorsingRights])
            delegates <- Gen.infiniteStream(arbitrary[DBSafe[String]])
            dbSafeRights = fetchRights.copy(blockHash = Some(referenceBlock.data.hash))
            dbSafeEndorsingRights = endorseRights.zip(delegates).map {
              case (rights, DBSafe(delegate)) => rights.copy(delegate = delegate)
            }
          } yield (dbSafeRights, dbSafeEndorsingRights, referenceBlock)

          generator.sample.value
        }

        info(s"verifying with ${endorsingRights.size} rights")

        val populate = for {
          _ <- TezosDatabaseOperations.writeBlocks(List(block))
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

    }

}
