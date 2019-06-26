package tech.cryptonomic.conseil.metadata

import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.{InMemoryDatabase, Tables}
import tech.cryptonomic.conseil.generic.chain.DataTypes.{
  Aggregation,
  AggregationType,
  OperationType,
  OrderDirection,
  OutputType,
  Predicate,
  QueryOrdering
}
import tech.cryptonomic.conseil.tezos.repositories.SlickRepositories
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, BlocksRow, FeesRow}
import java.sql.Timestamp

class SlickMetadataQueryingTest
    extends WordSpec
    with InMemoryDatabase
    with ScalaFutures
    with Matchers
    with IntegrationPatience {

  //needed for most tezos-db operations
  import scala.concurrent.ExecutionContext.Implicits.global
  val repos = new SlickRepositories

  val blocksTmp = List(
    Tables.BlocksRow(
      0,
      1,
      "genesis",
      new Timestamp(0),
      0,
      "fitness",
      Some("context0"),
      Some("sigqs6AXPny9K"),
      "protocol",
      Some("YLBMy"),
      "R0NpYZuUeF",
      None
    ),
    Tables.BlocksRow(
      1,
      1,
      "R0NpYZuUeF",
      new Timestamp(1),
      0,
      "fitness",
      Some("context1"),
      Some("sigTZ2IB879wD"),
      "protocol",
      Some("YLBMy"),
      "aQeGrbXCmG",
      None
    )
  )

  "The slick repositories object" should {
      val examplePredicates = List(
        Predicate(
          field = "some_field",
          operation = OperationType.in,
          set = List(
            "valid",
            "valid_value",
            "invalid*value",
            "another;invalid,value",
            "yet.another.value"
          )
        )
      )

      "correctly sanitize values for SQL" in {
        val results = SlickRepositories.sanitizePredicates(examplePredicates).head.set
        results should contain allElementsOf List(
          "valid",
          "valid_value",
          "invalidvalue",
          "anotherinvalidvalue",
          "yet.another.value"
        )
        results.size shouldBe 5
      }
    }

  "The slick metadata repository" should {
      val sut = repos.metadataRepository

      "get all values from the table with nulls as nones" in {

        val columns = List()
        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            Tables.Blocks.baseTableRow.tableName,
            columns,
            List.empty,
            List.empty,
            List.empty,
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
            "validation_pass" -> Some(0),
            "protocol" -> Some("protocol"),
            "predecessor" -> Some("genesis"),
            "chain_id" -> Some("YLBMy"),
            "level" -> Some(0),
            "period_kind" -> None,
            "current_expected_quorum" -> None,
            "active_proposal" -> None,
            "baker" -> None,
            "nonce_hash" -> None,
            "consumed_gas" -> None,
            "meta_level" -> None,
            "meta_level_position" -> None,
            "meta_cycle" -> None,
            "meta_cycle_position" -> None,
            "meta_voting_period" -> None,
            "meta_voting_period_position" -> None,
            "expected_commitment" -> None
          ),
          Map(
            "operations_hash" -> None,
            "timestamp" -> Some(new Timestamp(1)),
            "context" -> Some("context1"),
            "proto" -> Some(1),
            "signature" -> Some("sigTZ2IB879wD"),
            "hash" -> Some("aQeGrbXCmG"),
            "fitness" -> Some("fitness"),
            "validation_pass" -> Some(0),
            "protocol" -> Some("protocol"),
            "predecessor" -> Some("R0NpYZuUeF"),
            "chain_id" -> Some("YLBMy"),
            "level" -> Some(1),
            "period_kind" -> None,
            "current_expected_quorum" -> None,
            "active_proposal" -> None,
            "baker" -> None,
            "nonce_hash" -> None,
            "consumed_gas" -> None,
            "meta_level" -> None,
            "meta_level_position" -> None,
            "meta_cycle" -> None,
            "meta_cycle_position" -> None,
            "meta_voting_period" -> None,
            "meta_voting_period_position" -> None,
            "expected_commitment" -> None
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
            0,
            "fitness",
            None,
            Some("sigqs6AXPny9K"),
            "protocol",
            Some("YLBMy"),
            "R0NpYZuUeF",
            None
          ),
          BlocksRow(
            1,
            1,
            "R0NpYZuUeF",
            new Timestamp(1),
            0,
            "fitness",
            Some("context1"),
            Some("sigTZ2IB879wD"),
            "protocol",
            Some("YLBMy"),
            "aQeGrbXCmG",
            None
          )
        )
        val columns = List("level", "proto", "context", "hash", "operations_hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
            0,
            "fitness",
            None,
            Some("sigqs6AXPny9K"),
            "protocol",
            Some("YLBMy"),
            "R0NpYZuUeF",
            None
          ),
          BlocksRow(
            1,
            1,
            "R0NpYZuUeF",
            new Timestamp(1),
            0,
            "fitness",
            Some("context1"),
            Some("sigTZ2IB879wD"),
            "protocol",
            Some("YLBMy"),
            "aQeGrbXCmG",
            None
          )
        )
        val columns = List("level", "proto", "context", "hash", "operations_hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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

        val columns = List("level", "proto", "protocol", "hash", "operations_hash")

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            Tables.Blocks.baseTableRow.tableName,
            columns,
            List.empty,
            List.empty,
            List.empty,
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
          )
        )
      }

      "get map from a block table" in {

        val columns = List("level", "proto", "protocol", "hash")

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          found <- sut.selectWithPredicates(
            Tables.Blocks.baseTableRow.tableName,
            columns,
            List.empty,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table with predicate" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table with multiple predicates" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get empty map from empty table" in {
        val columns = List("level", "proto", "protocol", "hash")
        val predicates = List.empty

        val populateAndTest = for {
          found <- sut.selectWithPredicates(
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe 'empty
      }

      "get map from a block table with eq predicate" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe 'empty
      }

      "get map from a block table with between predicate when two element fulfill it" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table with between predicate when two element fulfill it but limited to 1 element" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }
      "get map from a block table with datetime field" in {
        val columns = List("level", "proto", "protocol", "hash", "timestamp")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
          )
        )
      }
      "get map from a block table with startsWith predicate" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe 'empty
      }
      "get map from a block table with endsWith predicate" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
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
        manager = "manager",
        spendable = true,
        delegateSetable = false,
        delegateValue = None,
        counter = 0,
        script = None,
        balance = BigDecimal(1.45)
      )
      "get one element when correctly rounded value" in {
        val columns = List("account_id", "balance")
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
            Tables.Accounts.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue.map(_.mapValues(_.toString))
        result shouldBe List(Map("account_id" -> "Some(1)", "balance" -> "Some(1.45)"))
      }
      "get empty list of elements when correctly rounded value does not match" in {
        val columns = List("account_id", "balance")
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
            Tables.Accounts.baseTableRow.tableName,
            columns,
            predicates,
            List.empty,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe 'empty
      }

      "return the same results for the same query" in {
        type AnyMap = Map[String, Any]

        import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder._
        val columns = List("level", "proto", "protocol", "hash")
        val tableName = Tables.Blocks.baseTableRow.tableName
        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocksTmp
          generatedQuery <- makeQuery(tableName, columns, List.empty).as[AnyMap]
        } yield generatedQuery

        val generatedQueryResult = dbHandler.run(populateAndTest.transactionally).futureValue
        val expectedQueryResult = dbHandler
          .run(
            sql"""SELECT #${columns.head}, #${columns(1)}, #${columns(2)}, #${columns(3)} FROM #$tableName WHERE true"""
              .as[AnyMap]
          )
          .futureValue
        generatedQueryResult shouldBe expectedQueryResult
      }

      "get map from a block table and sort by level in ascending order" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            sortBy,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table and sort by level in descending order" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            sortBy,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
        )
      }

      "get map from a block table and sort by hash in descending order" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            sortBy,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
        )
      }

      "get map from a block table and sort by hash in ascending order" in {
        val columns = List("level", "proto", "protocol", "hash")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            sortBy,
            List.empty,
            OutputType.json,
            3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue
        result shouldBe List(
          Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
          Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
        )
      }

      "get map from a block table and sort by proto in descending order and by level in ascending order" in {
        val columns = List("level", "proto")
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
            Tables.Blocks.baseTableRow.tableName,
            columns,
            predicates,
            sortBy,
            List.empty,
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

      "should aggregate with COUNT function" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(1), "kind"),
          FeesRow(0, 3, 4, new Timestamp(2), "kind")
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.count, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("low", "medium", "high"),
            predicates = List.empty,
            ordering = List.empty,
            aggregation = aggregate,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("high" -> Some(8), "count_medium" -> Some(1), "low" -> Some(0)),
          Map("high" -> Some(4), "count_medium" -> Some(2), "low" -> Some(0))
        )
      }

      "should aggregate with MAX function" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(1), "kind"),
          FeesRow(0, 3, 4, new Timestamp(2), "kind")
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.max, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("low", "medium", "high"),
            predicates = List.empty,
            ordering = List.empty,
            aggregation = aggregate,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("high" -> Some(8), "max_medium" -> Some(4), "low" -> Some(0)),
          Map("high" -> Some(4), "max_medium" -> Some(3), "low" -> Some(0))
        )
      }

      "should aggregate with MIN function" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(1), "kind"),
          FeesRow(0, 3, 4, new Timestamp(2), "kind")
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.min, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("low", "medium", "high"),
            predicates = List.empty,
            ordering = List.empty,
            aggregation = aggregate,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("high" -> Some(8), "min_medium" -> Some(4), "low" -> Some(0)),
          Map("high" -> Some(4), "min_medium" -> Some(2), "low" -> Some(0))
        )
      }

      "should aggregate with SUM function" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(1), "kind"),
          FeesRow(0, 3, 4, new Timestamp(2), "kind")
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.sum, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("low", "medium", "high"),
            predicates = List.empty,
            ordering = List.empty,
            aggregation = aggregate,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("high" -> Some(8), "sum_medium" -> Some(4), "low" -> Some(0)),
          Map("high" -> Some(4), "sum_medium" -> Some(5), "low" -> Some(0))
        )
      }

      "should aggregate with SUM function and order by SUM()" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(1), "kind"),
          FeesRow(0, 3, 4, new Timestamp(2), "kind")
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.sum, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("low", "medium", "high"),
            predicates = List.empty,
            ordering = List(QueryOrdering("sum_medium", OrderDirection.desc)),
            aggregation = aggregate,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("high" -> Some(4), "sum_medium" -> Some(5), "low" -> Some(0)),
          Map("high" -> Some(8), "sum_medium" -> Some(4), "low" -> Some(0))
        )
      }

      "should order correctly by the field not existing in query)" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(1), "kind"),
          FeesRow(0, 3, 3, new Timestamp(2), "kind")
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("low", "medium"),
            predicates = List.empty,
            ordering = List(QueryOrdering("high", OrderDirection.desc)),
            aggregation = List.empty,
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

      "should correctly check use between in the timestamps" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(2), "kind"),
          FeesRow(0, 3, 4, new Timestamp(4), "kind")
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
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("timestamp"),
            predicates = List(predicate),
            ordering = List.empty,
            aggregation = List.empty,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result.flatMap(_.values.map(_.map(_.asInstanceOf[Timestamp]))) shouldBe List(
          Some(new Timestamp(2))
        )
      }

      "should correctly execute BETWEEN operation using numeric comparison instead of lexicographical" in {
        val feesTmp = List(
          FeesRow(0, 0, 0, new Timestamp(0), "kind"),
          FeesRow(0, 0, 10, new Timestamp(3), "kind"),
          FeesRow(0, 0, 2, new Timestamp(1), "kind"),
          FeesRow(0, 0, 30, new Timestamp(2), "kind")
        )

        val predicate = Predicate(
          field = "high",
          operation = OperationType.between,
          set = List(1, 3)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("high"),
            predicates = List(predicate),
            ordering = List(),
            aggregation = List.empty,
            outputType = OutputType.json,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(Map("high" -> Some(2)))
      }

      "should return correct query when asked for SQL" in {
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
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(1), "kind"),
          FeesRow(0, 3, 4, new Timestamp(2), "kind")
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.sum, None)
        )

        val expectedQuery =
          "SELECT SUM(medium) as sum_medium,low,high FROM fees WHERE true  AND medium = '4' IS false AND low BETWEEN '0' AND '1' GROUP BY low,high ORDER BY sum_medium desc LIMIT 3"
        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("low", "medium", "high"),
            predicates = predicates,
            ordering = List(QueryOrdering("sum_medium", OrderDirection.desc)),
            aggregation = aggregate,
            outputType = OutputType.sql,
            limit = 3
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(Map("sql" -> Some(expectedQuery)))
      }

      "should aggregate with multiple aggregations on the same field" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(1), "kind"),
          FeesRow(0, 3, 4, new Timestamp(2), "kind")
        )

        val aggregate = List(
          Aggregation("medium", AggregationType.sum, None),
          Aggregation("medium", AggregationType.max, None)
        )

        val populateAndTest = for {
          _ <- Tables.Fees ++= feesTmp
          found <- sut.selectWithPredicates(
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("low", "medium", "high"),
            predicates = List.empty,
            ordering = List(QueryOrdering("sum_medium", OrderDirection.desc)),
            aggregation = aggregate,
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

      "should aggregate with single aggegation when there is only one field" in {
        val feesTmp = List(
          FeesRow(0, 2, 4, new Timestamp(0), "kind"),
          FeesRow(0, 4, 8, new Timestamp(1), "kind"),
          FeesRow(0, 3, 4, new Timestamp(2), "kind")
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
            table = Tables.Fees.baseTableRow.tableName,
            columns = List("medium"),
            predicates = predicates,
            ordering = List.empty,
            outputType = OutputType.json,
            aggregation = aggregate,
            limit = 1
          )
        } yield found

        val result = dbHandler.run(populateAndTest.transactionally).futureValue

        result shouldBe List(
          Map("sum_medium" -> Some(3))
        )
      }

    }

}
