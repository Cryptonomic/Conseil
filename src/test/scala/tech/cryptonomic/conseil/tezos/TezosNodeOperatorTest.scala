package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{FlatSpec, Matchers}
import tech.cryptonomic.conseil.config.BatchFetchConfiguration
import tech.cryptonomic.conseil.tezos.TezosTypes.BlockHash

import scala.concurrent.{ExecutionContext, Future}

class TezosNodeOperatorTest extends FlatSpec with MockFactory with Matchers with LazyLogging with ScalaFutures with IntegrationPatience {
  import ExecutionContext.Implicits.global

  val config = BatchFetchConfiguration(1, 1, 500)

  "getBlock" should "should correctly fetch the genesis block" in {
    //given
    val tezosRPCInterface = stub[TezosRPCInterface]
    val blockResponse = Future.successful(TezosResponseBuilder.genesisBlockResponse)
    val operationResponse = Future.successful(TezosResponseBuilder.operationsResponse)
    val votesPeriodKind = Future.successful(TezosResponseBuilder.votesPeriodKind)
    val votesQuorum = Future.successful(TezosResponseBuilder.votesQuorum)
    val votesProposal = Future.successful(TezosResponseBuilder.votesProposal)
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe~").returns(blockResponse)
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe/operations").returns(operationResponse)
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe/").returns(operationResponse)
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe/votes/current_period_kind").returns(votesPeriodKind)
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe/votes/current_quorum").returns(votesQuorum)
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe/votes/current_proposal").returns(votesProposal)

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, "zeronet", config)

    //when
    val block: Future[TezosTypes.Block] = nodeOp.getBlock(BlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe"))

    //then
    block.futureValue.data.hash shouldBe BlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe")
  }

  "getLatestBlocks" should "should correctly fetch all the blocks if no depth is passed-in" in {
    //given
    val tezosRPCInterface = stub[TezosRPCInterface]
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head~").returns(Future.successful(TezosResponseBuilder.blockResponse))
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head/operations").returns(Future.successful(TezosResponseBuilder.operationsResponse))

    (tezosRPCInterface.runBatchedGetQuery[Any] _)
      .when("zeronet", *, *, *)
      .onCall( (_, input, command, _) =>
        Future.successful(List(
          //dirty trick to find the type of input content and provide the appropriate response
          input match {
            case (_: Int) :: tail => (0, TezosResponseBuilder.batchedGetBlockQueryResponse)
            case (hash: BlockHash) :: tail if command(hash) contains "operations" => (BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.batchedGetOperationsQueryResponse)
            case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_period_kind" => (BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.votesPeriodKind)
            case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_quorum" => (BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.votesQuorum)
            case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_proposal" => (BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.votesProposal)
          }
        ))
      )

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, "zeronet", config)

    //when
    val blockPages: Future[nodeOp.PaginatedBlocksResults] = nodeOp.getLatestBlocks()

    //then
    val (pages, total) = blockPages.futureValue
    total shouldBe 3
    val results = pages.toList
    results should have length 1
    results.head.futureValue should have length 1

  }

  "getLatestBlocks" should "should correctly fetch latest blocks" in {
    //given
    val tezosRPCInterface = stub[TezosRPCInterface]
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head~").returns(Future.successful(TezosResponseBuilder.blockResponse))
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head/operations").returns(Future.successful(TezosResponseBuilder.operationsResponse))

    (tezosRPCInterface.runBatchedGetQuery[Int] _).when("zeronet", List(0), *, *).returns(Future.successful(List((0, TezosResponseBuilder.batchedGetBlockQueryResponse))))
    (tezosRPCInterface.runBatchedGetQuery[BlockHash] _)
      .when("zeronet", *, *, *)
      .onCall( (_, input, command, _) =>
        Future.successful(List(
          //dirty trick to find the type of input content and provide the appropriate response
          input match {
            case (hash: BlockHash) :: tail if command(hash) contains "operations" => (BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.batchedGetOperationsQueryResponse)
            case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_period_kind" => (BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.votesPeriodKind)
            case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_quorum" => (BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.votesQuorum)
            case (hash: BlockHash) :: tail if command(hash) endsWith "votes/current_proposal" => (BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.votesProposal)
          }
        ))
      )

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, "zeronet", config)

    //when
    val blockPages: Future[nodeOp.PaginatedBlocksResults] = nodeOp.getLatestBlocks(Some(1))

    //then
    val (pages, total) = blockPages.futureValue
    total shouldBe 1
    val results = pages.toList
    results should have length 1
    results.head.futureValue should have length 1
 }

}
