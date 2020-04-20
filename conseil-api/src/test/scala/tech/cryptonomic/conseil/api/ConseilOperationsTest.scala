package tech.cryptonomic.conseil.api

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{OperationType, Predicate, Query}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.common.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.common.tezos.{InMemoryDatabase, TezosDataGeneration, TezosDatabaseOperations}
import tech.cryptonomic.conseil.common.util.RandomSeed

import scala.concurrent.duration._
import scala.concurrent.Await


class ConseilOperationsTest
  extends WordSpec
    with Matchers
    with InMemoryDatabase
    with ScalaFutures
    with OptionValues
    with LazyLogging
    with IntegrationPatience
    with TezosDataGeneration {

  import scala.concurrent.ExecutionContext.Implicits.global
  "ConseilOperationsTest" should {
    implicit val noTokenContracts: TokenContracts = TokenContracts.fromTokens(List.empty)

    val sut = new ConseilOperations {
      override lazy val dbReadHandle = dbHandler
    }

    "latestBlockIO for empty DB" in {
      // when
      val result = dbHandler.run(sut.latestBlockIO()).futureValue

      // then
      result shouldBe None
    }

    "latestBlockIO" in {
      // given
      implicit val randomSeed: RandomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val basicBlocks = generateBlocks(5, testReferenceDateTime)
      val generatedBlocks = basicBlocks.zipWithIndex map {
        case (block, idx) =>
          //need to use different seeds to generate unique hashes for groups
          val group = generateOperationGroup(block, generateOperations = true)(randomSeed + idx)
          block.copy(operationGroups = List(group))
      }
      Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks)), 5.seconds)

      // when
      val result = dbHandler.run(sut.latestBlockIO()).futureValue.get

      // then
      result.level shouldBe 5
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
      implicit val randomSeed: RandomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val basicBlocks = generateBlocks(7, testReferenceDateTime)
      val generatedBlocks = basicBlocks.zipWithIndex map {
        case (block, idx) =>
          //need to use different seeds to generate unique hashes for groups
          val group = generateOperationGroup(block, generateOperations = true)(randomSeed + idx)
          block.copy(operationGroups = List(group))
      }
      Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks)), 5.seconds)
      val input = generatedBlocks.head.operationGroups.head.hash

      // when
      val result = sut.fetchOperationGroup(input.value).futureValue.get

      // then
      result.operation_group.hash shouldBe input.value
    }

    "sanitizeFields" in {
      // given
      val input = List.empty

      // when
      val result = sut.sanitizeFields(input)

      // then
      result shouldBe List.empty
    }

    "fetchBlock when DB is empty" in {
      // given
      val input = BlockHash("xyz")

      // when
      val result = sut.fetchBlock(input).futureValue

      // then
      result shouldBe None
    }

    "fetchBlock" in {
      // given
      implicit val randomSeed: RandomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val basicBlocks = generateBlocks(5, testReferenceDateTime)
      val generatedBlocks = basicBlocks.zipWithIndex map {
        case (block, idx) =>
          //need to use different seeds to generate unique hashes for groups
          val group = generateOperationGroup(block, generateOperations = true)(randomSeed + idx)
          block.copy(operationGroups = List(group))
      }
      Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks)), 5.seconds)
      val input = basicBlocks.head.data.hash

      // when
      val result = sut.fetchBlock(input).futureValue.get

      // then
      result.block.level shouldBe basicBlocks.head.data.header.level
    }

    "fetchAccount is empty" in {
      // given
      val input = AccountId("xyz")

      // when
      val result = sut.fetchAccount(input).futureValue

      // then
      result shouldBe None
    }

    "fetchAccount" in {
      // given
      implicit val randomSeed: RandomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val expectedCount = 3

      val block = generateBlocks(1, testReferenceDateTime).head
      val accountsInfo = generateAccounts(expectedCount, block.data.hash, 1)

      val input = accountsInfo.content.head._1
      Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(List(block))), 5.seconds)
      Await.result(dbHandler.run(TezosDatabaseOperations.writeAccounts(List(accountsInfo))), 5.seconds)

      // when
      val result = sut.fetchAccount(input).futureValue.get

      // then
      result.account.accountId shouldBe input.id
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

    "fetchLatestBlock when DB is empty" in {
      // when
      val result = sut.fetchLatestBlock().futureValue

      // then
      result shouldBe None
    }

    "fetchLatestBlock" in {
      // given
      implicit val randomSeed: RandomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val basicBlocks = generateBlocks(7, testReferenceDateTime)
      val generatedBlocks = basicBlocks.zipWithIndex map {
        case (block, idx) =>
          //need to use different seeds to generate unique hashes for groups
          val group = generateOperationGroup(block, generateOperations = true)(randomSeed + idx)
          block.copy(operationGroups = List(group))
      }
      Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks)), 5.seconds)

      // when
      val result = sut.fetchLatestBlock().futureValue.get

      // then
      result.level shouldBe 7
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
