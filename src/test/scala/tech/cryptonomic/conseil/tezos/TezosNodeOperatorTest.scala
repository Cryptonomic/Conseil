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
    val blockResponse = Future.successful(TezosResponseBuilder.blockResponse)
    val operationResponse = Future.successful(TezosResponseBuilder.operationsResponse)
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe~").returns(blockResponse)
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe/operations").returns(operationResponse)

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, config)

    //when
    val block: Future[TezosTypes.Block] = nodeOp.getBlock("zeronet", BlockHash("BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe"))

    //then
    block.futureValue.data.hash shouldBe BlockHash("BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c")
  }

  "getLatestBlocks" should "should correctly fetch all the blocks if no depth is passed-in" in {
    //given
    val tezosRPCInterface = stub[TezosRPCInterface]
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head~").returns(Future.successful(TezosResponseBuilder.blockResponse))
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head/operations").returns(Future.successful(TezosResponseBuilder.operationsResponse))

    (tezosRPCInterface.runBatchedGetQuery[Any] _)
      .when("zeronet", *, *, *)
      .onCall( (_, input, _, _) =>
        Future.successful(List(
          //dirty trick to find the type of input content and provide the appropriate response
          input match {
            case (_: Int) :: tail => (0, TezosResponseBuilder.batchedGetQueryResponse)
            case _ => (BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.batchedGetQuerySecondCallResponse)
          }
        ))
      )

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, config)

    //when
    val blockPages: Future[nodeOp.PaginatedBlocksResults] = nodeOp.getLatestBlocks("zeronet")

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

    (tezosRPCInterface.runBatchedGetQuery[Int] _).when("zeronet", List(0), *, *).returns(Future.successful(List((0, TezosResponseBuilder.batchedGetQueryResponse))))
    (tezosRPCInterface.runBatchedGetQuery[BlockHash] _).when("zeronet", *, *, *).returns(Future.successful(List((BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.batchedGetQuerySecondCallResponse))))

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, config)

    //when
    val blockPages: Future[nodeOp.PaginatedBlocksResults] = nodeOp.getLatestBlocks("zeronet", Some(1))

    //then
    val (pages, total) = blockPages.futureValue
    total shouldBe 1
    val results = pages.toList
    results should have length 1
    results.head.futureValue should have length 1
 }

}
