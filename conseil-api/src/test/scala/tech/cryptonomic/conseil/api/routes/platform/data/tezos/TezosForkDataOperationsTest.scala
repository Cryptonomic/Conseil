package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import java.sql.Timestamp

import com.softwaremill.diffx.scalatest.DiffShouldMatcher._
import org.scalatest.concurrent.IntegrationPatience
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.TezosInMemoryDatabaseSetup
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow, OperationsRow}
import tech.cryptonomic.conseil.common.tezos.{Fork, Tables}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{makeAccountId, TezosBlockHash}
import tech.cryptonomic.conseil.common.testkit.ConseilSpec
import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import scala.concurrent.duration._
import java.time.Instant
import java.{util => ju}

class TezosForkDataOperationsTest
    extends ConseilSpec
    with InMemoryDatabase
    with TezosInMemoryDatabaseSetup
    with IntegrationPatience {

  import scala.concurrent.ExecutionContext.Implicits.global
  "TezosDataOperations" should {

      val sut = new TezosDataOperations(dbConfig) {
        override lazy val dbReadHandle = dbHandler
      }

      val invalidation = new Timestamp(Instant.now().toEpochMilli())
      val forkUniqueId = ju.UUID.randomUUID().toString()

      val blocksTmp = List(
        BlocksRow(
          0,
          1,
          "genesis",
          new Timestamp(0),
          "fitness",
          Some("context0"),
          Some("sigqs6AXPny9K"),
          "protocol",
          Some("YLBMy"),
          "R0NpYZuUeF",
          utcYear = 1970,
          utcMonth = 1,
          utcDay = 1,
          utcTime = "00:00:00",
          forkId = Fork.mainForkId
        ),
        BlocksRow(
          1,
          1,
          "R0NpYZuUeF",
          new Timestamp(1),
          "fitness",
          Some("context1"),
          Some("sigTZ2IB879wD"),
          "protocol",
          Some("YLBMy"),
          "aQeGrbXCmG",
          utcYear = 1970,
          utcMonth = 1,
          utcDay = 1,
          utcTime = "00:00:00",
          forkId = Fork.mainForkId
        ),
        BlocksRow(
          2,
          1,
          "TZ2IB879wD",
          new Timestamp(1),
          "fitness",
          Some("context1"),
          Some("sig2JKkCn2uE4"),
          "protocol",
          Some("YLBMy"),
          "AmmEPSs1EP",
          utcYear = 2018,
          utcMonth = 1,
          utcDay = 1,
          utcTime = "00:00:00",
          forkId = Fork.mainForkId
        )
      )

      val operationGroupsTmp = Seq(
        OperationGroupsRow(
          "protocol",
          Some("YLBMy"),
          "YLBMyqs6AX",
          "Pny9KR0NpY",
          Some("sigZuUeFTZ2IB"),
          "R0NpYZuUeF",
          0,
          forkId = Fork.mainForkId
        ),
        OperationGroupsRow(
          "protocol",
          Some("YLBMy"),
          "kL6aYpvHgu",
          "Hy1U991CqV",
          Some("siguMl6h80BRD"),
          "aQeGrbXCmG",
          1,
          forkId = Fork.mainForkId
        ),
        OperationGroupsRow(
          "protocol",
          Some("YLBMy"),
          "SAJ24saDAn",
          "HAUda1992M",
          Some("sigAMjda313JN"),
          "AmmEPSs1EP",
          2,
          forkId = Fork.mainForkId
        )
      )

      val operationsTmp = Seq(
        OperationsRow(
          operationId = 0,
          operationGroupHash = "YLBMyqs6AX",
          kind = "seed_nonce_revelation",
          blockHash = "R0NpYZuUeF",
          blockLevel = 0,
          internal = false,
          timestamp = new Timestamp(0),
          utcYear = 2018,
          utcMonth = 1,
          utcDay = 1,
          utcTime = "00:00:00",
          forkId = Fork.mainForkId
        ),
        OperationsRow(
          operationId = 1,
          operationGroupHash = "kL6aYpvHgu",
          kind = "seed_nonce_revelation",
          blockHash = "aQeGrbXCmG",
          blockLevel = 1,
          internal = false,
          timestamp = new Timestamp(1),
          utcYear = 2018,
          utcMonth = 1,
          utcDay = 1,
          utcTime = "00:00:00",
          forkId = Fork.mainForkId
        ),
        OperationsRow(
          operationId = 2,
          operationGroupHash = "SAJ24saDAn",
          kind = "seed_nonce_revelation",
          blockHash = "AmmEPSs1EP",
          blockLevel = 2,
          internal = false,
          timestamp = new Timestamp(2),
          utcYear = 2018,
          utcMonth = 1,
          utcDay = 1,
          utcTime = "00:00:00",
          forkId = Fork.mainForkId
        )
      )

      val accountsTmp = Seq(
        AccountsRow(
          "Jdah4819",
          "R0NpYZuUeF",
          balance = 2000,
          forkId = Fork.mainForkId
        ),
        AccountsRow(
          "Jmf8217N",
          "aQeGrbXCmG",
          balance = 3000,
          forkId = Fork.mainForkId
        ),
        AccountsRow(
          "Ndah178m",
          "AmmEPSs1EP",
          balance = 1000,
          forkId = Fork.mainForkId
        )
      )

      val forkInvalidBlocks = blocksTmp.map(
        _.copy(
          invalidatedAsof = Some(invalidation),
          forkId = forkUniqueId
        )
      )

      val forkInvalidOperationGroups = operationGroupsTmp.map(
        _.copy(
          invalidatedAsof = Some(invalidation),
          forkId = forkUniqueId
        )
      )

      val forkInvalidOperations = operationsTmp.map(
        _.copy(
          invalidatedAsof = Some(invalidation),
          forkId = forkUniqueId
        )
      )

      val forkInvalidAccounts = accountsTmp.map(
        _.copy(
          invalidatedAsof = Some(invalidation),
          forkId = forkUniqueId
        )
      )

      "ignore fork-invalidated data when latestBlockIO" in {
        // given
        dbHandler.run(Tables.Blocks ++= forkInvalidBlocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.OperationGroups ++= forkInvalidOperationGroups).isReadyWithin(5.seconds) shouldBe true

        // when
        val result = dbHandler.run(sut.latestBlockIO()).futureValue

        // then
        result shouldBe empty
      }

      "ignore fork-invalidated data when fetchOperationGroup" in {
        // given
        /* both invalid blocks (needed as FK for the invalid operations and group)
         * and valid ones (otherwise the query will fail for no latest block)
         * are needed
         */
        dbHandler.run(Tables.Blocks ++= forkInvalidBlocks ++ blocksTmp).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.OperationGroups ++= forkInvalidOperationGroups).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Operations ++= forkInvalidOperations).isReadyWithin(5.seconds) shouldBe true

        val input = forkInvalidOperationGroups.head.hash

        // when
        val result = sut.fetchOperationGroup(input).futureValue

        // then
        result shouldBe empty
      }

      "ignore fork-invalidated data when fetchBlock" in {
        // given
        dbHandler.run(Tables.Blocks ++= forkInvalidBlocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.OperationGroups ++= forkInvalidOperationGroups).isReadyWithin(5.seconds) shouldBe true

        val input = TezosBlockHash(forkInvalidBlocks.head.hash)

        // when
        val result = sut.fetchBlock(input).futureValue

        // then
        result shouldBe empty
      }

      "ignore fork-invalidated data when fetchAccount" in {
        // given
        dbHandler.run(Tables.Blocks ++= forkInvalidBlocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Accounts ++= forkInvalidAccounts).isReadyWithin(5.seconds) shouldBe true
        val input = makeAccountId(forkInvalidAccounts.head.accountId)

        // when
        val result = sut.fetchAccount(input).futureValue

        // then
        result shouldBe empty
      }

      "ignore fork-invalidated data when fetchLatestBlock" in {
        // given
        dbHandler.run(Tables.Blocks ++= forkInvalidBlocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.OperationGroups ++= forkInvalidOperationGroups).isReadyWithin(5.seconds) shouldBe true

        // when
        val result = sut.fetchLatestBlock().futureValue

        // then
        result shouldBe empty
      }

      "ignore fork-invalidated data when get all values from the table with nulls as nones" in {

        /* We save some valid and invalidated data together */
        val populate = Tables.Blocks ++= blocksTmp.head :: forkInvalidBlocks.tail

        dbHandler.run(populate).isReadyWithin(5.seconds) shouldBe true

        val result = sut
          .queryWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            DataTypes.Query(output = OutputType.json, limit = 3),
            true
          )
          .futureValue

        result should contain theSameElementsAs List(
          Map(
            "operations_hash" -> None,
            "timestamp" -> Some(new Timestamp(0)),
            "context" -> Some("context0"),
            "proto" -> Some(1),
            "signature" -> Some("sigqs6AXPny9K"),
            "hash" -> Some("R0NpYZuUeF"),
            "fitness" -> Some("fitness"),
            "protocol" -> Some("protocol"),
            "predecessor" -> Some("genesis"),
            "chain_id" -> Some("YLBMy"),
            "level" -> Some(0),
            "period_kind" -> None,
            "current_expected_quorum" -> None,
            "active_proposal" -> None,
            "baker" -> None,
            "consumed_gas" -> None,
            "meta_level" -> None,
            "meta_level_position" -> None,
            "meta_cycle" -> None,
            "meta_cycle_position" -> None,
            "meta_voting_period" -> None,
            "meta_voting_period_position" -> None,
            "priority" -> None,
            "utc_year" -> Some(1970),
            "utc_month" -> Some(1),
            "utc_day" -> Some(1),
            "utc_time" -> Some("00:00:00")
          )
        )
      }

      "ignore fork-invalidated data when getting a map from a block table with predicate" in {
        val fields = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.in,
            set = List("R0NpYZuUeF"),
            inverse = false
          )
        )

        val populate = Tables.Blocks ++= forkInvalidBlocks

        dbHandler.run(populate).isReadyWithin(5.seconds) shouldBe true

        val result = sut
          .queryWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            DataTypes.Query(
              fields = fields,
              predicates = predicates,
              output = OutputType.json,
              limit = 3
            ),
            true
          )
          .futureValue

        result shouldBe empty
      }

      "ignore fork-invalidated data when getting a map from a block table with multiple predicate groups" in {

        val blocksTmp = List(
          BlocksRow(
            0,
            1,
            "genesis",
            new Timestamp(0),
            "fitness",
            Some("context0"),
            Some("sigqs6AXPny9K"),
            "protocol",
            Some("chainId"),
            "blockHash1",
            utcYear = 1970,
            utcMonth = 1,
            utcDay = 1,
            utcTime = "00:00:00",
            forkId = Fork.mainForkId
          ),
          BlocksRow(
            1,
            1,
            "blockHash1",
            new Timestamp(1),
            "fitness",
            Some("context1"),
            Some("sigTZ2IB879wD"),
            "protocol",
            Some("chainId"),
            "blockHash2",
            utcYear = 1970,
            utcMonth = 1,
            utcDay = 1,
            utcTime = "00:00:00",
            forkId = Fork.mainForkId
          ),
          BlocksRow(
            2,
            1,
            "blockHash2",
            new Timestamp(2),
            "fitness",
            Some("context1"),
            Some("sigTZ2IB879wD"),
            "protocol",
            Some("chainId"),
            "blockHash3",
            utcYear = 1970,
            utcMonth = 1,
            utcDay = 1,
            utcTime = "00:00:00",
            forkId = Fork.mainForkId
          ),
          BlocksRow(
            3,
            1,
            "blockHash3",
            new Timestamp(3),
            "fitness",
            Some("context1"),
            Some("sigTZ2IB879wD"),
            "protocol",
            Some("chainId"),
            "blockHash4",
            utcYear = 1970,
            utcMonth = 1,
            utcDay = 1,
            utcTime = "00:00:00",
            invalidatedAsof = Some(invalidation),
            forkId = forkUniqueId
          )
        )

        val fields = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.in,
            set = List("blockHash1"),
            inverse = true,
            group = Some("A")
          ),
          Predicate(
            field = "hash",
            operation = OperationType.in,
            set = List("blockHash2"),
            inverse = false,
            group = Some("A")
          ),
          Predicate(
            field = "signature",
            operation = OperationType.in,
            set = List("sigTZ2IB879wD"),
            inverse = false,
            group = Some("B")
          )
        )

        val populate = Tables.Blocks ++= blocksTmp

        dbHandler.run(populate).isReadyWithin(5.seconds) shouldBe true

        val result = sut
          .queryWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            DataTypes.Query(
              fields = fields,
              predicates = predicates,
              output = OutputType.json,
              limit = 3
            ),
            true
          )
          .futureValue

        result should contain theSameElementsAs List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("blockHash2")),
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("blockHash3"))
        )
      }

      "ignore fork-invalidated data when fetching the latest block level with blocks available" in {
        val populateAndFetch = for {
          /* We save some valid and invalidated data together */
          _ <- Tables.Blocks ++= blocksTmp.head :: forkInvalidBlocks.tail
          result <- sut.fetchMaxBlockLevel
        } yield result

        val maxLevel = dbHandler.run(populateAndFetch.transactionally).futureValue

        maxLevel should equal(0)
      }

    }

}
