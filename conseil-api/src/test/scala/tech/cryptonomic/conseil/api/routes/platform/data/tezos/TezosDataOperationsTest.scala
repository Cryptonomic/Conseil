package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import java.sql.Timestamp

import com.softwaremill.diffx.scalatest.DiffShouldMatcher._
import org.scalatest.concurrent.IntegrationPatience
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.TezosInMemoryDatabaseSetup
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query, SimpleField, _}
import tech.cryptonomic.conseil.common.testkit.{ConseilSpec, InMemoryDatabase}
import tech.cryptonomic.conseil.common.tezos.Tables.{
  AccountsHistoryRow,
  AccountsRow,
  BlocksRow,
  FeesRow,
  OperationGroupsRow,
  OperationsRow
}
import tech.cryptonomic.conseil.common.tezos.{Fork, Tables}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{makeAccountId, TezosBlockHash}

import scala.concurrent.duration._

class TezosDataOperationsTest
    extends ConseilSpec
    with InMemoryDatabase
    with TezosInMemoryDatabaseSetup
    with IntegrationPatience {

  import scala.concurrent.ExecutionContext.Implicits.global

  "TezosDataOperations" should {

      val sut = new TezosDataOperations(dbConfig) {
        override lazy val dbReadHandle = dbHandler
      }

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
        AccountsRow("Jdah4819", "R0NpYZuUeF", balance = 2000, forkId = Fork.mainForkId),
        AccountsRow("Jmf8217N", "aQeGrbXCmG", balance = 3000, forkId = Fork.mainForkId),
        AccountsRow("Ndah178m", "AmmEPSs1EP", balance = 1000, forkId = Fork.mainForkId)
      )

      "latestBlockIO for empty DB" in {
        // when
        val result = dbHandler.run(sut.latestBlockIO()).futureValue

        // then
        result shouldBe None
      }

      "latestBlockIO" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocksTmp).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.OperationGroups ++= operationGroupsTmp).isReadyWithin(5.seconds) shouldBe true

        // when
        val result = dbHandler.run(sut.latestBlockIO()).futureValue.value

        // then
        result.level shouldBe 2
      }

      "fetchOperationGroup when DB is empty" in {
        // given
        val input = "xyz"

        // when
        val result = sut.fetchOperationGroup(input).failed.futureValue

        // then
        result shouldBe a[NoSuchElementException]
      }

      "fetchOperationGroup" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocksTmp).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.OperationGroups ++= operationGroupsTmp).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Operations ++= operationsTmp).isReadyWithin(5.seconds) shouldBe true

        val input = operationGroupsTmp.head.hash

        // when
        val result = sut.fetchOperationGroup(input).futureValue.value

        // then
        result.operation_group.hash shouldBe input
      }

      "fetchBlock when DB is empty" in {
        // given
        val input = TezosBlockHash("xyz")

        // when
        val result = sut.fetchBlock(input).futureValue

        // then
        result shouldBe None
      }

      "fetchBlock" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocksTmp).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.OperationGroups ++= operationGroupsTmp).isReadyWithin(5.seconds) shouldBe true

        val input = TezosBlockHash(blocksTmp.head.hash)

        // when
        val result = sut.fetchBlock(input).futureValue.value

        // then
        result.block.level shouldBe blocksTmp.head.level
      }

      "fetchAccount is empty" in {
        // given
        val input = makeAccountId("xyz")

        // when
        val result = sut.fetchAccount(input).futureValue

        // then
        result shouldBe None
      }

      "fetchAccount" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocksTmp).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Accounts ++= accountsTmp).isReadyWithin(5.seconds) shouldBe true
        val input = makeAccountId(accountsTmp.head.accountId)

        // when
        val result = sut.fetchAccount(input).futureValue.value

        // then
        result.account.accountId shouldBe input.value
      }

      "fetchLatestBlock when DB is empty" in {
        // when
        val result = sut.fetchLatestBlock().futureValue

        // then
        result shouldBe None
      }

      "fetchLatestBlock" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocksTmp).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.OperationGroups ++= operationGroupsTmp).isReadyWithin(5.seconds) shouldBe true

        // when
        val result = sut.fetchLatestBlock().futureValue.value

        // then
        result.level shouldBe 2
      }

      "queryWithPredicates" in {
        // given
        val table = "blocks"
        val query = Query.empty

        // when
        val result = sut.queryWithPredicates("tezos", table, query).futureValue

        // then
        result shouldBe List.empty
      }

      "get all values from the table with nulls as nones" in {

        val columns = List()
        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            List.empty,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
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
          ),
          Map(
            "operations_hash" -> None,
            "timestamp" -> Some(new Timestamp(1)),
            "context" -> Some("context1"),
            "proto" -> Some(1),
            "signature" -> Some("sigTZ2IB879wD"),
            "hash" -> Some("aQeGrbXCmG"),
            "fitness" -> Some("fitness"),
            "protocol" -> Some("protocol"),
            "predecessor" -> Some("R0NpYZuUeF"),
            "chain_id" -> Some("YLBMy"),
            "level" -> Some(1),
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
          ),
          Map(
            "operations_hash" -> None,
            "timestamp" -> Some(new Timestamp(1)),
            "context" -> Some("context1"),
            "proto" -> Some(1),
            "signature" -> Some("sig2JKkCn2uE4"),
            "hash" -> Some("AmmEPSs1EP"),
            "fitness" -> Some("fitness"),
            "protocol" -> Some("protocol"),
            "predecessor" -> Some("TZ2IB879wD"),
            "chain_id" -> Some("YLBMy"),
            "level" -> Some(2),
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
            "utc_year" -> Some(2018),
            "utc_month" -> Some(1),
            "utc_day" -> Some(1),
            "utc_time" -> Some("00:00:00")
          )
        )
      }

      "get values where context is null" in {
        val blocksTmp = List(
          BlocksRow(
            0,
            1,
            "genesis",
            new Timestamp(0),
            "fitness",
            None,
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
          )
        )
        val columns = List(
          SimpleField("level"),
          SimpleField("proto"),
          SimpleField("context"),
          SimpleField("hash"),
          SimpleField("operations_hash")
        )
        val predicates = List(
          Predicate(
            field = "context",
            operation = OperationType.isnull,
            set = List.empty,
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map(
            "level" -> Some(0),
            "proto" -> Some(1),
            "context" -> None,
            "hash" -> Some("R0NpYZuUeF"),
            "operations_hash" -> None
          )
        )
      }

      "get values where context is NOT null" in {
        val blocksTmp = List(
          BlocksRow(
            0,
            1,
            "genesis",
            new Timestamp(0),
            "fitness",
            None,
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
          )
        )
        val columns = List(
          SimpleField("level"),
          SimpleField("proto"),
          SimpleField("context"),
          SimpleField("hash"),
          SimpleField("operations_hash")
        )
        val predicates = List(
          Predicate(
            field = "context",
            operation = OperationType.isnull,
            set = List.empty,
            inverse = true
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map(
            "level" -> Some(1),
            "proto" -> Some(1),
            "context" -> Some("context1"),
            "hash" -> Some("aQeGrbXCmG"),
            "operations_hash" -> None
          )
        )
      }

      "get null values from the table as none" in {

        val columns = List(
          SimpleField("level"),
          SimpleField("proto"),
          SimpleField("protocol"),
          SimpleField("hash"),
          SimpleField("operations_hash")
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            List.empty,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result should contain theSameElementsAs List(
          Map(
            "level" -> Some(0),
            "proto" -> Some(1),
            "protocol" -> Some("protocol"),
            "hash" -> Some("R0NpYZuUeF"),
            "operations_hash" -> None
          ),
          Map(
            "level" -> Some(1),
            "proto" -> Some(1),
            "protocol" -> Some("protocol"),
            "hash" -> Some("aQeGrbXCmG"),
            "operations_hash" -> None
          ),
          Map(
            "operations_hash" -> None,
            "proto" -> Some(1),
            "hash" -> Some("AmmEPSs1EP"),
            "protocol" -> Some("protocol"),
            "level" -> Some(2)
          )
        )
      }

      "get map from a block table" in {

        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            List.empty,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("AmmEPSs1EP"))
        )
      }

      "get map from a block table with predicate" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.in,
            set = List("R0NpYZuUeF"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
        )
      }

      "get map from a block table with inverse predicate" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.in,
            set = List("R0NpYZuUeF"),
            inverse = true
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("AmmEPSs1EP"))
        )
      }

      "get map from a block table with multiple predicates" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.in,
            set = List("R0NpYZuUeF"),
            inverse = true
          ),
          Predicate(
            field = "hash",
            operation = OperationType.in,
            set = List("aQeGrbXCmG"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table with multiple predicate groups" in {

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
            forkId = Fork.mainForkId
          )
        )

        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
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

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result should contain theSameElementsAs List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("blockHash2")),
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("blockHash3")),
          Map("level" -> Some(3), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("blockHash4"))
        )
      }

      "get empty map from empty table" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List.empty

        val populateAndTest = for {
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe 'empty
      }

      "get map from a block table with eq predicate" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.eq,
            set = List("aQeGrbXCmG"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table with eq predicate on numeric type" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "level",
            operation = OperationType.eq,
            set = List(1),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table with like predicate when starts with pattern" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.like,
            set = List("aQeGr"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table with like predicate when ends with pattern" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.like,
            set = List("rbXCmG"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table with like predicate when pattern is in the middle" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.like,
            set = List("rbX"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table with less than predicate when one element fulfils it" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "level",
            operation = OperationType.lt,
            set = List(1),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
        )
      }

      "get empty map from a block table with less than predicate when no elements fulfil it" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "level",
            operation = OperationType.lt,
            set = List(0),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe 'empty
      }

      "get map from a block table with between predicate when two element fulfill it" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "level",
            operation = OperationType.between,
            set = List(-10, 10),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("AmmEPSs1EP"))
        )
      }

      "get map from a block table with between predicate when two element fulfill it but limited to 1 element" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "level",
            operation = OperationType.between,
            set = List(-10, 10),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            1
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
        )
      }

      "get map from a block table with between predicate when one element fulfill it" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "level",
            operation = OperationType.between,
            set = List(1, 10),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("AmmEPSs1EP"))
        )
      }
      "get map from a block table with datetime field" in {
        val columns = List(
          SimpleField("level"),
          SimpleField("proto"),
          SimpleField("protocol"),
          SimpleField("hash"),
          SimpleField("timestamp")
        )
        val predicates = List(
          Predicate(
            field = "timestamp",
            operation = OperationType.gt,
            set = List(new Timestamp(0)),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map(
            "level" -> Some(1),
            "proto" -> Some(1),
            "protocol" -> Some("protocol"),
            "hash" -> Some("aQeGrbXCmG"),
            "timestamp" -> Some(new Timestamp(1))
          ),
          Map(
            "timestamp" -> Some(new Timestamp(1)),
            "proto" -> Some(1),
            "hash" -> Some("AmmEPSs1EP"),
            "protocol" -> Some("protocol"),
            "level" -> Some(2)
          )
        )
      }
      "get map from a block table with startsWith predicate" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.startsWith,
            set = List("R0Np"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
        )
      }
      "get empty map from a block table with startsWith predicate" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.startsWith,
            set = List("YZuUeF"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe 'empty
      }
      "get map from a block table with endsWith predicate" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.endsWith,
            set = List("ZuUeF"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
        )
      }
      "get empty map from a block table with endsWith predicate" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List(
          Predicate(
            field = "hash",
            operation = OperationType.endsWith,
            set = List("R0NpYZ"),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe 'empty
      }

      val accRow = AccountsRow(
        accountId = 1.toString,
        blockId = "R0NpYZuUeF",
        blockLevel = 0,
        counter = None,
        script = None,
        balance = BigDecimal(1.45),
        forkId = Fork.mainForkId
      )
      "get one element when correctly rounded value" in {
        val columns = List(SimpleField("account_id"), SimpleField("balance"))
        val predicates = List(
          Predicate(
            field = "balance",
            operation = OperationType.eq,
            set = List(1.5),
            inverse = false,
            precision = Some(1)
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          _ <- Tables.Accounts += accRow
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Accounts.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue.map(_.mapValues(_.toString))
        result shouldBe List(Map("account_id" -> "Some(1)", "balance" -> "Some(1.45)"))
      }
      "get empty list of elements when correctly rounded value does not match" in {
        val columns = List(SimpleField("account_id"), SimpleField("balance"))
        val predicates = List(
          Predicate(
            field = "balance",
            operation = OperationType.eq,
            set = List(1.5),
            inverse = false,
            precision = Some(2)
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          _ <- Tables.Accounts += accRow
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Accounts.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe 'empty
      }

      "return the same results for the same query" in {
        type AnyMap = Map[String, Any]

        import tech.cryptonomic.conseil.common.util.DatabaseUtil.QueryBuilder._
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val tableSpace = "tezos"
        val tableName = s"$tableSpace.${Tables.Blocks.baseTableRow.tableName}"
        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          generatedQuery <- makeQuery(tableName, columns, List.empty).as[AnyMap]
        } yield generatedQuery

        val generatedQueryResult = dbHandler.run(populateAndTest.transactionally).futureValue
        val expectedQueryResult = dbHandler
          .run(
            sql"""SELECT #${columns.head.field}, #${columns(1).field}, #${columns(2).field}, #${columns(3).field} FROM #$tableName WHERE true"""
              .as[AnyMap]
          )
          .futureValue
        generatedQueryResult shouldBe expectedQueryResult
      }

      "get map from a block table and sort by level in ascending order" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List()
        val sortBy = List(
          QueryOrdering(
            field = "level",
            direction = OrderDirection.asc
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            sortBy,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("AmmEPSs1EP"))
        )
      }

      "get map from a block table and sort by level in descending order" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List()
        val sortBy = List(
          QueryOrdering(
            field = "level",
            direction = OrderDirection.desc
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            sortBy,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("AmmEPSs1EP")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
        )
      }

      "get map from a block table and sort by hash in descending order" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List()
        val sortBy = List(
          QueryOrdering(
            field = "hash",
            direction = OrderDirection.desc
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            sortBy,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("AmmEPSs1EP"))
        )
      }

      "get map from a block table and sort by hash in ascending order" in {
        val columns = List(SimpleField("level"), SimpleField("proto"), SimpleField("protocol"), SimpleField("hash"))
        val predicates = List()
        val sortBy = List(
          QueryOrdering(
            field = "hash",
            direction = OrderDirection.asc
          )
        )

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            sortBy,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(2), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("AmmEPSs1EP")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
        )
      }

      "get map from a block table and sort by proto in descending order and by level in ascending order" in {
        val columns = List(SimpleField("level"), SimpleField("proto"))
        val predicates = List()
        val sortBy = List(
          QueryOrdering(
            field = "proto",
            direction = OrderDirection.desc
          ),
          QueryOrdering(
            field = "level",
            direction = OrderDirection.asc
          )
        )

        val blocksTmp2 = blocksTmp.head.copy(level = 2, proto = 2, hash = "aQeGrbXCmF") :: blocksTmp

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp2
          found <- sut.selectWithPredicates(
            "tezos",
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            sortBy,
            None,
            None,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(2), "proto" -> Some(2)),
          Map("level" -> Some(0), "proto" -> Some(1)),
          Map("level" -> Some(1), "proto" -> Some(1))
        )
      }

      "aggregate with COUNT function" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.count, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium"), SimpleField("high")),
            predicates = List.empty,
            ordering = List.empty,
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result should contain theSameElementsAs List(
          Map("high" -> Some(8), "count_medium" -> Some(1), "low" -> Some(0)),
          Map("high" -> Some(4), "count_medium" -> Some(2), "low" -> Some(0))
        )
      }

      "aggregate with MAX function" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.max, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium"), SimpleField("high")),
            predicates = List.empty,
            ordering = List.empty,
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result should contain theSameElementsAs List(
          Map("high" -> Some(8), "max_medium" -> Some(4), "low" -> Some(0)),
          Map("high" -> Some(4), "max_medium" -> Some(3), "low" -> Some(0))
        )
      }

      "aggregate with MIN function" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.min, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium"), SimpleField("high")),
            predicates = List.empty,
            ordering = List.empty,
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result should contain theSameElementsAs List(
          Map("high" -> Some(8), "min_medium" -> Some(4), "low" -> Some(0)),
          Map("high" -> Some(4), "min_medium" -> Some(2), "low" -> Some(0))
        )
      }

      "aggregate with SUM function" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.sum, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium"), SimpleField("high")),
            predicates = List.empty,
            ordering = List.empty,
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result should contain theSameElementsAs List(
          Map("high" -> Some(8), "sum_medium" -> Some(4), "low" -> Some(0)),
          Map("high" -> Some(4), "sum_medium" -> Some(5), "low" -> Some(0))
        )
      }

      "aggregate with SUM function and order by SUM()" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.sum, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium"), SimpleField("high")),
            predicates = List.empty,
            ordering = List(QueryOrdering("sum_medium", OrderDirection.desc)),
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result should contain theSameElementsAs List(
          Map("high" -> Some(4), "sum_medium" -> Some(5), "low" -> Some(0)),
          Map("high" -> Some(8), "sum_medium" -> Some(4), "low" -> Some(0))
        )
      }

      "order correctly by the field not existing in query)" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 3, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium")),
            predicates = List.empty,
            ordering = List(QueryOrdering("high", OrderDirection.desc)),
            aggregation = List.empty,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("medium" -> Some(4), "low" -> Some(0)), // high = Some(8)
          Map("medium" -> Some(2), "low" -> Some(0)), // high = Some(4)
          Map("medium" -> Some(3), "low" -> Some(0)) // high = Some(3)
        )
      }

      "correctly check use between in the timestamps" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(2), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(4), "kind", forkId = Fork.mainForkId)
        )

        val predicate = Predicate(
          field = "timestamp",
          operation = OperationType.between,
          set = List(
            new Timestamp(1),
            new Timestamp(3)
          )
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("timestamp")),
            predicates = List(predicate),
            ordering = List.empty,
            aggregation = List.empty,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result.flatMap(_.values.map(_.map(_.asInstanceOf[Timestamp]))) shouldBe List(
          Some(new Timestamp(2))
        )
      }

      "correctly execute BETWEEN operation using numeric comparison instead of lexicographical" in {
        val feesTmp = List(
          FeesRow(0, 0, 0, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 0, 10, new Timestamp(3), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 0, 2, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 0, 30, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val predicate = Predicate(
          field = "high",
          operation = OperationType.between,
          set = List(1, 3)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("high")),
            predicates = List(predicate),
            ordering = List(),
            aggregation = List.empty,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(Map("high" -> Some(2)))
      }

      "return correct query when asked for SQL" in {
        val predicates = List(
          Predicate(
            field = "medium",
            operation = OperationType.eq,
            set = List(4),
            inverse = true,
            precision = None
          ),
          Predicate(
            field = "low",
            operation = OperationType.between,
            set = List(0, 1),
            inverse = false,
            precision = None
          )
        )

        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.sum, None)
        )

        val expectedQuery =
          "SELECT SUM(medium) as sum_medium,low,high FROM tezos.fees WHERE true  AND medium = '4' IS false AND low BETWEEN '0' AND '1' GROUP BY low,high ORDER BY sum_medium desc LIMIT 3"
        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium"), SimpleField("high")),
            predicates = predicates,
            aggregation = aggregate,
            ordering = List(QueryOrdering("sum_medium", OrderDirection.desc)),
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.sql,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(Map("sql" -> Some(expectedQuery)))
      }

      "aggregate with multiple aggregations on the same field" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.sum, None),
          Aggregation("medium", AggregationType.max, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium"), SimpleField("high")),
            predicates = List.empty,
            ordering = List(QueryOrdering("sum_medium", OrderDirection.desc)),
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("sum_medium" -> Some(5), "max_medium" -> Some(3), "low" -> Some(0), "high" -> Some(4)),
          Map("sum_medium" -> Some(4), "max_medium" -> Some(4), "low" -> Some(0), "high" -> Some(8))
        )
      }

      "aggregate with single aggegation when there is only one field" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.sum, None)
        )

        val predicates = List(
          Predicate(
            field = "medium",
            operation = OperationType.gt,
            set = List(2),
            inverse = false
          ),
          Predicate(
            field = "high",
            operation = OperationType.lt,
            set = List(5),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("medium")),
            predicates = predicates,
            ordering = List.empty,
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            limit = 1,
            outputType = OutputType.json
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("sum_medium" -> Some(3))
        )
      }

      "aggregate with correct predicate field when aggregation is using predicate" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind1", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind2", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind1", forkId = Fork.mainForkId),
          FeesRow(0, 2, 4, new Timestamp(3), "kind4", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation(
            field = "medium",
            function = AggregationType.count,
            predicate = Some(
              AggregationPredicate(
                operation = OperationType.gt,
                set = List(1),
                inverse = false
              )
            )
          )
        )

        val predicates = List(
          Predicate(
            field = "high",
            operation = OperationType.lt,
            set = List(5),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("medium"), SimpleField("kind")),
            predicates = predicates,
            ordering = List.empty,
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            limit = 4,
            outputType = OutputType.json
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map(
            "count_medium" -> Some(2),
            "kind" -> Some("kind1")
          )
        )
      }

      "aggregate correctly with multiple aggregation fields with predicate" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind1", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, new Timestamp(1), "kind2", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, new Timestamp(2), "kind1", forkId = Fork.mainForkId),
          FeesRow(0, 2, 4, new Timestamp(3), "kind2", forkId = Fork.mainForkId),
          FeesRow(1, 2, 4, new Timestamp(4), "kind3", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation(
            field = "medium",
            function = AggregationType.count,
            predicate = Some(
              AggregationPredicate(
                operation = OperationType.gt,
                set = List(0),
                inverse = false
              )
            )
          ),
          Aggregation(
            field = "low",
            function = AggregationType.sum,
            predicate = Some(
              AggregationPredicate(
                operation = OperationType.eq,
                set = List(0),
                inverse = false
              )
            )
          )
        )

        val predicates = List(
          Predicate(
            field = "high",
            operation = OperationType.lt,
            set = List(5),
            inverse = false
          )
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium"), SimpleField("kind")),
            predicates = predicates,
            ordering = List.empty,
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            limit = 5,
            outputType = OutputType.json
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map(
            "sum_low" -> Some(0),
            "count_medium" -> Some(2),
            "kind" -> Some("kind1")
          ),
          Map(
            "sum_low" -> Some(0),
            "count_medium" -> Some(1),
            "kind" -> Some("kind2")
          )
        )
      }

      "aggregate with datePart aggregation" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, Timestamp.valueOf("2000-01-01 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 8, Timestamp.valueOf("2000-01-02 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, Timestamp.valueOf("2000-01-02 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, Timestamp.valueOf("2000-01-02 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, Timestamp.valueOf("2000-01-03 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, Timestamp.valueOf("2000-01-03 00:00:00"), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.count, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("medium"), FormattedField("timestamp", FormatType.datePart, "YYYY-MM-DD")),
            predicates = List.empty,
            ordering = List(QueryOrdering("count_medium", OrderDirection.desc)),
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 10
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("date_part_timestamp" -> Some("2000-01-02"), "count_medium" -> Some(3)),
          Map("date_part_timestamp" -> Some("2000-01-03"), "count_medium" -> Some(2)),
          Map("date_part_timestamp" -> Some("2000-01-01"), "count_medium" -> Some(1))
        )

      }

      "aggregate with distinct count aggregation" in {
        val feesTmp = List(
          FeesRow(1, 1, 1, Timestamp.valueOf("2000-01-01 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(2, 1, 1, Timestamp.valueOf("2000-01-02 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(3, 2, 1, Timestamp.valueOf("2000-01-02 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(4, 2, 1, Timestamp.valueOf("2000-01-02 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(5, 3, 2, Timestamp.valueOf("2000-01-03 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(6, 3, 2, Timestamp.valueOf("2000-01-03 00:00:00"), "kind", forkId = Fork.mainForkId)
        )

        val aggregate = List(
          Aggregation("low", AggregationType.count),
          Aggregation("medium", AggregationType.countDistinct)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(SimpleField("low"), SimpleField("medium"), SimpleField("high")),
            predicates = List.empty,
            ordering = List(QueryOrdering("count_distinct_medium", OrderDirection.desc)),
            aggregation = aggregate,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 10
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("count_low" -> Some(4), "count_distinct_medium" -> Some(2), "high" -> Some(1)),
          Map("count_low" -> Some(2), "count_distinct_medium" -> Some(1), "high" -> Some(2))
        )
      }

      "map date with datePart aggregation when it is only type of aggregation" in {
        val feesTmp = List(
          FeesRow(0, 1, 4, Timestamp.valueOf("2000-01-01 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 2, 8, Timestamp.valueOf("2000-01-02 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 3, 4, Timestamp.valueOf("2000-01-02 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 4, 4, Timestamp.valueOf("2000-01-02 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 5, 4, Timestamp.valueOf("2000-01-03 00:00:00"), "kind", forkId = Fork.mainForkId),
          FeesRow(0, 6, 4, Timestamp.valueOf("2000-01-03 00:00:00"), "kind", forkId = Fork.mainForkId)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.Fees.baseTableRow.tableName,
            columns = List(FormattedField("timestamp", FormatType.datePart, "YYYY-MM-DD")),
            predicates = List.empty,
            ordering = List(QueryOrdering("date_part_timestamp", OrderDirection.desc)),
            aggregation = List.empty,
            temporalPartition = None,
            snapshot = None,
            outputType = OutputType.json,
            limit = 10
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("date_part_timestamp" -> Some("2000-01-03")),
          Map("date_part_timestamp" -> Some("2000-01-03")),
          Map("date_part_timestamp" -> Some("2000-01-02")),
          Map("date_part_timestamp" -> Some("2000-01-02")),
          Map("date_part_timestamp" -> Some("2000-01-02")),
          Map("date_part_timestamp" -> Some("2000-01-01"))
        )
      }

      "correctly use query on temporal table" in {

        val accountsHistoryRow = AccountsHistoryRow(
          accountId = "id",
          blockId = "blockid",
          balance = BigDecimal(1.0),
          blockLevel = 1,
          asof = new Timestamp(1),
          forkId = Fork.mainForkId
        )

        val populateAndTest = for {
          _ <- Tables.AccountsHistory += accountsHistoryRow
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.AccountsHistory.baseTableRow.tableName,
            columns = List(SimpleField("account_id"), SimpleField("block_id"), SimpleField("asof")),
            predicates = List.empty,
            ordering = List(),
            aggregation = List.empty,
            temporalPartition = Some("account_id"),
            snapshot = Some(Snapshot("asof", new Timestamp(1))),
            outputType = OutputType.json,
            limit = 10
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map(
            "account_id" -> Some("id"),
            "block_id" -> Some("blockid"),
            "asof" -> Some(new Timestamp(1)),
            "r" -> Some(1)
          )
        )

      }

      "get the balance of an account at a specific timestamp where there are multiple entities for given account_id" in {

        val accountsHistoryRows = List(
          AccountsHistoryRow(
            accountId = "id1",
            blockId = "blockid1",
            balance = BigDecimal(1.0),
            blockLevel = 1,
            asof = new Timestamp(1),
            forkId = Fork.mainForkId
          ),
          AccountsHistoryRow(
            accountId = "id1",
            blockId = "blockid2",
            balance = BigDecimal(2.0),
            blockLevel = 2,
            asof = new Timestamp(2),
            forkId = Fork.mainForkId
          ),
          AccountsHistoryRow(
            accountId = "id1",
            blockId = "blockid3",
            balance = BigDecimal(3.0),
            blockLevel = 3,
            asof = new Timestamp(3),
            forkId = Fork.mainForkId
          )
        )

        val populateAndTest = for {
          _ <- Tables.AccountsHistory ++= accountsHistoryRows
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.AccountsHistory.baseTableRow.tableName,
            columns = List(SimpleField("account_id"), SimpleField("block_id"), SimpleField("asof")),
            predicates = List.empty,
            ordering = List(),
            aggregation = List.empty,
            temporalPartition = Some("account_id"),
            snapshot = Some(Snapshot("asof", new Timestamp(2))),
            outputType = OutputType.json,
            limit = 10
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map(
            "account_id" -> Some("id1"),
            "block_id" -> Some("blockid2"),
            "asof" -> Some(new Timestamp(2)),
            "r" -> Some(1)
          )
        )
      }

      "get the balance of an account at a specific timestamp" in {

        val accountsHistoryRows = List(
          AccountsHistoryRow(
            accountId = "id1",
            blockId = "blockid1",
            balance = BigDecimal(1.0),
            blockLevel = 1,
            asof = new Timestamp(1),
            forkId = Fork.mainForkId
          ),
          AccountsHistoryRow(
            accountId = "id2",
            blockId = "blockid2",
            balance = BigDecimal(2.0),
            blockLevel = 2,
            asof = new Timestamp(2),
            forkId = Fork.mainForkId
          ),
          AccountsHistoryRow(
            accountId = "id3",
            blockId = "blockid3",
            balance = BigDecimal(3.0),
            blockLevel = 3,
            asof = new Timestamp(3),
            forkId = Fork.mainForkId
          )
        )

        val populateAndTest = for {
          _ <- Tables.AccountsHistory ++= accountsHistoryRows
          found <- sut.selectWithPredicates(
            "tezos",
            table = Tables.AccountsHistory.baseTableRow.tableName,
            columns = List(SimpleField("account_id"), SimpleField("block_id"), SimpleField("asof")),
            predicates = List.empty,
            ordering = List(),
            aggregation = List.empty,
            temporalPartition = Some("account_id"),
            snapshot = Some(Snapshot("asof", new Timestamp(2))),
            outputType = OutputType.json,
            limit = 10
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map(
            "account_id" -> Some("id1"),
            "block_id" -> Some("blockid1"),
            "asof" -> Some(new Timestamp(1)),
            "r" -> Some(1)
          ),
          Map(
            "account_id" -> Some("id2"),
            "block_id" -> Some("blockid2"),
            "asof" -> Some(new Timestamp(2)),
            "r" -> Some(1)
          )
        )

      }

      "return the default when fetching the latest block level and there's no block stored" in {
        val expected = -1
        val maxLevel = dbHandler
          .run(
            sut.fetchMaxBlockLevel
          )
          .futureValue

        maxLevel should equal(expected)
      }

      "fetch the latest block level when blocks are available" in {
        val populateAndFetch = for {
          _ <- Tables.Blocks ++= blocksTmp
          result <- sut.fetchMaxBlockLevel
        } yield result

        val maxLevel = dbHandler.run(populateAndFetch.transactionally).futureValue

        maxLevel should equal(2)
      }

      "fetch nothing if looking up a non-existent operation group by hash" in {
        dbHandler.run(sut.operationsForGroup("no-group-here")).futureValue shouldBe None
      }

      "fetch existing operations with their group on a existing hash" in {
        val block = blocksTmp.head
        val group = operationGroupsTmp.head
        val ops = operationsTmp.take(1)

        val populateAndFetch = for {
          _ <- Tables.Blocks += block
          _ <- Tables.OperationGroups += group
          ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
          result <- sut.operationsForGroup(group.hash)
        } yield (result, ids)

        val (Some((groupRow, operationRows)), operationIds) = dbHandler.run(populateAndFetch).futureValue

        groupRow.hash shouldEqual group.hash
        operationRows should have size ops.size
        operationRows.map(_.operationId).toList should contain theSameElementsAs operationIds

      }
    }

}
