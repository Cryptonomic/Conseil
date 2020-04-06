package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.generic.chain.DataTypes.{OperationType, Predicate, Query}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.util.RandomSeed

import scala.concurrent.Await
import scala.concurrent.duration._
import tech.cryptonomic.conseil.tezos.michelson.contracts.{TNSContracts, TokenContracts}

class ApiOperationsTest
    extends WordSpec
    with Matchers
    with InMemoryDatabase
    with ScalaFutures
    with OptionValues
    with LazyLogging
    with IntegrationPatience
    with TezosDataGeneration {

  import scala.concurrent.ExecutionContext.Implicits.global
  "ApiOperationsTest" should {
      implicit val noTokenContracts = TokenContracts.fromConfig(List.empty)
      implicit val noTNSContracts = TNSContracts.fromConfig(List.empty)

      val sut = new ApiOperations {
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

      "fetchBlockAtLevel for missing entry" in {
        //when
        val result = sut.fetchBlockAtLevel(1).futureValue

        //then
        result shouldBe None
      }

      "fetchBlockAtLevel for a matching entry" in {
        // given
        implicit val randomSeed: RandomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val generatedBlocks = generateBlocks(3, testReferenceDateTime)

        Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks)), 5.seconds)

        // when
        val result = sut.fetchBlockAtLevel(1).futureValue.value

        // then
        result.level shouldBe 1
      }

      "sanitizeForSql alphanumeric string" in {
        // given
        val input = "xyz123"

        // when
        val result = ApiOperations.sanitizeForSql(input)

        // then
        result shouldBe "xyz123"
      }

      "sanitizeForSql alphanumeric string with supported characters" in {
        // given
        val input = "xyz+123_abc: pqr"

        // when
        val result = ApiOperations.sanitizeForSql(input)

        // then
        result shouldBe "xyz+123_abc: pqr"
      }

      "sanitizeForSql alphanumeric string with unsupported characters" in {
        // given
        val input = ";xyz$%*)("

        // when
        val result = ApiOperations.sanitizeForSql(input)

        // then
        result shouldBe "xyz"
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
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

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

      "fetchMaxLevel when DB is empty" in {
        // when
        val result = sut.fetchMaxLevel().futureValue

        // then
        result shouldBe -1
      }

      "fetchMaxLevel" in {
        // given
        implicit val randomSeed: RandomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val basicBlocks = generateBlocks(3, testReferenceDateTime)
        val generatedBlocks = basicBlocks.zipWithIndex map {
              case (block, idx) =>
                //need to use different seeds to generate unique hashes for groups
                val group = generateOperationGroup(block, generateOperations = true)(randomSeed + idx)
                block.copy(operationGroups = List(group))
            }
        Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks)), 5.seconds)

        // when
        val result = sut.fetchMaxLevel().futureValue

        // then
        result shouldBe 3
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

      "sanitizeDatePartAggregation and leave all valid characters" in {
        // given
        val input = "DD-MM-YYYY"

        // when
        val result = ApiOperations.sanitizeDatePartAggregation(input)

        // then
        result shouldBe "DD-MM-YYYY"
      }

      "sanitizeDatePartAggregation and remove invalid characters" in {
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
