package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.generic.chain.DataTypes.{OperationType, Predicate, Query}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}

class ApiOperationsTest extends WordSpec
  with Matchers
  with InMemoryDatabase
  with ScalaFutures
  with OptionValues
  with LazyLogging
  with IntegrationPatience {

  import scala.concurrent.ExecutionContext.Implicits.global
  "ApiOperations2Test" should {
    val sut = new ApiOperations {
      override lazy val dbReadHandle = dbHandler
    }

    "latestBlockIO" in {
      // when
      val result = dbHandler.run(sut.latestBlockIO()).futureValue

      // then
      result shouldBe None
    }

    "sanitizeForSql" in {
      // given
      val input = "xyz"

      // when
      val result = ApiOperations.sanitizeForSql(input)

      // then
      result shouldBe "xyz"
    }

    "fetchOperationGroup" in {
      // given
      val input = ""

      // when
      val result = sut.fetchOperationGroup(input).failed.futureValue

      // then
      result shouldBe a[NoSuchElementException]
    }

    "sanitizeFields" in {
      // given
      val input = List.empty

      // when
      val result = sut.sanitizeFields(input)

      // then
      result shouldBe List.empty
    }

    "fetchBlock" in {
      // given
      val input = BlockHash("xyz")

      // when
      val result = sut.fetchBlock(input).futureValue

      // then
      result shouldBe None
    }

    "fetchAccount" in {
      // given
      val input = AccountId("xyz")

      // when
      val result = sut.fetchAccount(input).futureValue

      // then
      result shouldBe None
    }

    "sanitizePredicates" in {
      // given
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

      // when
      val results = sut.sanitizePredicates(examplePredicates).head.set

      // then
      results should contain allElementsOf List(
        "valid",
        "valid_value",
        "invalidvalue",
        "anotherinvalidvalue",
        "yet.another.value"
      )
      results.size shouldBe 5

    }

    "fetchMaxLevel" in {
      // when
      val result = sut.fetchMaxLevel().futureValue

      // then
      result shouldBe -1
    }


    "fetchLatestBlock" in {
      // when
      val result = sut.fetchLatestBlock().futureValue

      // then
      result shouldBe None
    }

    "sanitizeDatePartAggregation" in {
      // given
      val input = "xyz "

      // when
      val result = ApiOperations.sanitizeDatePartAggregation(input)

      // then
      result shouldBe ""
    }

    "queryWithPredicates" in {
      // given
      val table = "blocks"
      val query = Query()

      // when
      val result = sut.queryWithPredicates(table, query).futureValue

      // then
      result shouldBe List.empty
    }

  }
}
