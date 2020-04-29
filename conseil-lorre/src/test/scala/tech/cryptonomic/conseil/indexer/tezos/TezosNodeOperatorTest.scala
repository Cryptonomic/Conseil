package tech.cryptonomic.conseil.indexer.tezos

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FlatSpec, Matchers}
import tech.cryptonomic.conseil.common.tezos.TezosTypes
import tech.cryptonomic.conseil.common.tezos.TezosTypes.BlockHash
import tech.cryptonomic.conseil.indexer.config.BatchFetchConfiguration

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps

class TezosNodeOperatorTest
    extends FlatSpec
    with MockFactory
    with Matchers
    with LazyLogging
    with ScalaFutures
    with IntegrationPatience {
  import ExecutionContext.Implicits.global

  val config = BatchFetchConfiguration(1, 1, 500, 10 seconds, 10 seconds, 10 seconds)

  "getBlock" should "correctly fetch the genesis block" in {
      //given
      val tezosRPCInterface = stub[TezosRPCInterface]
      val blockResponse = Future.successful(TezosResponseBuilder.genesisBlockResponse)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe~")
        .returns(blockResponse)

      val apiOps: TezosNodeOperations = new TezosNodeOperations
      val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, "zeronet", config, apiOps)

      //when
      val block: Future[TezosTypes.Block] =
        nodeOp.getBlock(BlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe"))

      //then
      block.futureValue.data.hash shouldBe BlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe")
    }

  "getLatestBlocks" should "correctly fetch all the blocks if no depth is passed-in" in {
      //given
      val tezosRPCInterface = stub[TezosRPCInterface]
      val blockResponse = Future.successful(TezosResponseBuilder.blockResponse)
      val operationsResponse = Future.successful(TezosResponseBuilder.operationsResponse)
      val votesQuorum = Future.successful(TezosResponseBuilder.votesQuorum)
      val votesProposal = Future.successful(TezosResponseBuilder.votesProposal)

      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/head~")
        .returns(blockResponse)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c/operations")
        .returns(operationsResponse)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~/votes/current_quorum")
        .returns(votesQuorum)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~/votes/current_proposal")
        .returns(votesProposal)

      (tezosRPCInterface.runBatchedGetQuery[Any] _)
        .when("zeronet", *, *, *)
        .onCall(
          (_, input, command, _) =>
            Future.successful(
              List(
                //dirty trick to find the type of input content and provide the appropriate response
                input match {
                  case (_: Int) :: tail => (0, TezosResponseBuilder.batchedGetBlockQueryResponse)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "operations" =>
                    (hash, TezosResponseBuilder.batchedGetOperationsQueryResponse)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_quorum" =>
                    (hash, TezosResponseBuilder.votesQuorum)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_proposal" =>
                    (hash, TezosResponseBuilder.votesProposal)
                }
              )
            )
        )

      val apiOps: TezosNodeOperations = new TezosNodeOperations
      val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, "zeronet", config, apiOps)

      //when
      val blockPages: Future[nodeOp.PaginatedBlocksResults] = nodeOp.getLatestBlocks()

      //then
      val (pages, total) = blockPages.futureValue
      total shouldBe 3
      val results = pages.toList
      results should have length 1
      results.head.futureValue should have length 1

    }

  "getLatestBlocks" should "correctly fetch latest blocks" in {
      //given
      val tezosRPCInterface = stub[TezosRPCInterface]
      val blockResponse = Future.successful(TezosResponseBuilder.blockResponse)
      val operationsResponse = Future.successful(TezosResponseBuilder.operationsResponse)
      val votesPeriodKind = Future.successful(TezosResponseBuilder.votesPeriodKind)
      val votesQuorum = Future.successful(TezosResponseBuilder.votesQuorum)
      val votesProposal = Future.successful(TezosResponseBuilder.votesProposal)

      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/head~")
        .returns(blockResponse)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c/operations")
        .returns(operationsResponse)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~/votes/current_period_kind")
        .returns(votesPeriodKind)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~/votes/current_quorum")
        .returns(votesQuorum)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~/votes/current_proposal")
        .returns(votesProposal)

      (tezosRPCInterface.runBatchedGetQuery[Any] _)
        .when("zeronet", *, *, *)
        .onCall(
          (_, input, command, _) =>
            Future.successful(
              List(
                //dirty trick to find the type of input content and provide the appropriate response
                input match {
                  case (_: Int) :: tail => (0, TezosResponseBuilder.batchedGetBlockQueryResponse)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "operations" =>
                    (hash, TezosResponseBuilder.batchedGetOperationsQueryResponse)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_period_kind" =>
                    (hash, TezosResponseBuilder.votesPeriodKind)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_quorum" =>
                    (hash, TezosResponseBuilder.votesQuorum)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_proposal" =>
                    (hash, TezosResponseBuilder.votesProposal)
                }
              )
            )
        )

      val apiOps: TezosNodeOperations = new TezosNodeOperations
      val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, "zeronet", config, apiOps)

      //when
      val blockPages: Future[nodeOp.PaginatedBlocksResults] = nodeOp.getLatestBlocks(Some(1))

      //then
      val (pages, total) = blockPages.futureValue
      total shouldBe 1
      val results = pages.toList
      results should have length 1
      results.head.futureValue should have length 1
    }

  "getLatestBlocks" should "correctly fetch blocks starting from a given head" in {
      //given
      val tezosRPCInterface = stub[TezosRPCInterface]
      val blockResponse = Future.successful(TezosResponseBuilder.blockResponse)
      val operationsResponse = Future.successful(TezosResponseBuilder.operationsResponse)
      val votesPeriodKind = Future.successful(TezosResponseBuilder.votesPeriodKind)
      val votesQuorum = Future.successful(TezosResponseBuilder.votesQuorum)
      val votesProposal = Future.successful(TezosResponseBuilder.votesProposal)

      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~")
        .returns(blockResponse)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c/operations")
        .returns(operationsResponse)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~/votes/current_period_kind")
        .returns(votesPeriodKind)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~/votes/current_quorum")
        .returns(votesQuorum)
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~/votes/current_proposal")
        .returns(votesProposal)

      (tezosRPCInterface.runBatchedGetQuery[Any] _)
        .when("zeronet", *, *, *)
        .onCall(
          (_, input, command, _) =>
            Future.successful(
              List(
                //dirty trick to find the type of input content and provide the appropriate response
                input match {
                  case (_: Int) :: tail => (0, TezosResponseBuilder.batchedGetBlockQueryResponse)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "operations" =>
                    (hash, TezosResponseBuilder.batchedGetOperationsQueryResponse)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_period_kind" =>
                    (hash, TezosResponseBuilder.votesPeriodKind)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_quorum" =>
                    (hash, TezosResponseBuilder.votesQuorum)
                  case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_proposal" =>
                    (hash, TezosResponseBuilder.votesProposal)
                }
              )
            )
        )

      val apiOps: TezosNodeOperations = new TezosNodeOperations
      val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, "zeronet", config, apiOps)

      //when
      val blockPages: Future[nodeOp.PaginatedBlocksResults] =
        nodeOp.getLatestBlocks(Some(1), Some(BlockHash("BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c")))

      //then
      val (pages, total) = blockPages.futureValue
      total shouldBe 1
      val results = pages.toList
      results should have length 1
      results.head.futureValue should have length 1
    }

}
