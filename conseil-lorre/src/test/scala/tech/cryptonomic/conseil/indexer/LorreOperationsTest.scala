package tech.cryptonomic.conseil.indexer

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import tech.cryptonomic.conseil.common.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.common.tezos.{InMemoryDatabase, TezosDataGeneration, TezosDatabaseOperations}
import tech.cryptonomic.conseil.common.util.RandomSeed

import scala.concurrent.Await
import scala.concurrent.duration._

class LorreOperationsTest
  extends WordSpec
    with Matchers
    with InMemoryDatabase
    with ScalaFutures
    with OptionValues
    with LazyLogging
    with IntegrationPatience
    with TezosDataGeneration {

  import scala.concurrent.ExecutionContext.Implicits.global
  "LorreOperationsTest" should {
    implicit val noTokenContracts: TokenContracts = TokenContracts.fromTokens(List.empty)

    val sut = new LorreOperations {
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
      implicit val randomSeed: RandomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val generatedBlocks = generateBlocks(3, testReferenceDateTime)

      Await.result(dbHandler.run(TezosDatabaseOperations.writeBlocks(generatedBlocks)), 5.seconds)

      // when
      val result = sut.fetchBlockAtLevel(1).futureValue.value

      // then
      result.level shouldBe 1
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
  }

}
