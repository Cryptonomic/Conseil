package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Span}
import org.scalatest.{FlatSpec, Matchers}
import tech.cryptonomic.conseil.config.BatchFetchConfiguration
import tech.cryptonomic.conseil.generic.chain.{DataOperations, DataTypes}
import tech.cryptonomic.conseil.tezos.TezosTypes.BlockHash

import scala.concurrent.{ExecutionContext, Future}

class TezosNodeOperatorTest extends FlatSpec with MockFactory with Matchers with LazyLogging with ScalaFutures {

  val tezosRPCInterface = stub[TezosRPCInterface]
  val config = BatchFetchConfiguration(1, 1)

  implicit val executionContext = ExecutionContext.global
  implicit val defaultPatience = PatienceConfig(timeout = Span(1000, Millis))
  val apiOperations = new DataOperations {
    override def queryWithPredicates(tableName: String, query: DataTypes.Query)(implicit ec: ExecutionContext): Future[List[Map[String, Any]]] = ???

    override def fetchMaxLevel()(implicit ec: ExecutionContext): Future[Int] = ???
  }

  "getBlock" should "should correctly fetch the genesis block" in {
    //given
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe~").returns(Future.successful(TezosResponseBuilder.blockResponse))
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe/operations").returns(Future.successful(TezosResponseBuilder.operationsResponse))

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, config, apiOperations)

    //when
    val block: Future[TezosTypes.Block] = nodeOp.getBlock("zeronet", BlockHash("BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe"))

    //then
    block.futureValue.metadata.hash shouldBe BlockHash("BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c")
  }

  "getAllBlocks" should "should correctly fetch all the blocks" in {
    //given
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head~").returns(Future.successful(TezosResponseBuilder.blockResponse))
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head/operations").returns(Future.successful(TezosResponseBuilder.operationsResponse))

    (tezosRPCInterface.runBatchedGetQuery[Int] _).when("zeronet", (0 to 162099).toList, *, *).returns(Future.successful(List((0, TezosResponseBuilder.batchedGetQueryResponse))))
    (tezosRPCInterface.runBatchedGetQuery[BlockHash] _).when("zeronet", *, *, *).returns(Future.successful(List((BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.batchedGetQuerySecondCallResponse))))

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, config, apiOperations)

    //when
    val block: Future[List[(TezosTypes.Block, List[TezosTypes.AccountId])]] = nodeOp.getAllBlocks("zeronet")

    //then
    block.futureValue should have length 1
  }

  "getLatestBlocks" should "should correctly fetch latest blocks" in {
    //given
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head~").returns(Future.successful(TezosResponseBuilder.blockResponse))
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/head/operations").returns(Future.successful(TezosResponseBuilder.operationsResponse))

    (tezosRPCInterface.runBatchedGetQuery[Int] _).when("zeronet", List(0), *, *).returns(Future.successful(List((0, TezosResponseBuilder.batchedGetQueryResponse))))
    (tezosRPCInterface.runBatchedGetQuery[BlockHash] _).when("zeronet", *, *, *).returns(Future.successful(List((BlockHash("BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"), TezosResponseBuilder.batchedGetQuerySecondCallResponse))))

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, config, apiOperations)

    //when
    val block: Future[List[(TezosTypes.Block, List[TezosTypes.AccountId])]] = nodeOp.getLatestBlocks("zeronet", 1)

    //then
    block.futureValue should have length 1
  }
}
