package tech.cryptonomic.conseil.tezos

import scala.language.postfixOps
import scala.concurrent.duration._
import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.generic.chain.RpcHandler
import cats.Id
import cats.data.{Kleisli, Reader}
import tech.cryptonomic.conseil.config.BatchFetchConfiguration

class TezosNodeOperatorTest
  extends FlatSpec
  with MockFactory
  with Matchers
  with LazyLogging
  with TezosNodeOperatorTestImplicits {
  //used to log within the test node using the testsuite's own logger
  val outerLogger = logger

  // create a test instance
  val sut: NodeOperator = new NodeOperator(
    network = "network",
    batchConf = BatchFetchConfiguration(
      blockFetchConcurrencyLevel = 100,
      accountFetchConcurrencyLevel = 100,
      blockPageSize = 100
    )
  )

  "getBlock" should "correctly fetch the genesis block" in withInstances {
    //given
    implicit extraBlockFetcher => implicit quorumFetcher => implicit proposalFetcher =>

      //test dataFetcher for blocks
      implicit val blockFetchProvider = Reader(
        (_: BlockHash) => testBlockFetcher(TezosResponseBuilder.genesisBlockResponse)
      )

      //when
      val block: TezosTypes.Block = sut.getBlock[Id](BlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe"))

      //then
      block.data.hash shouldBe BlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe")
  }

  // "getLatestBlocks" should "correctly fetch all the blocks if no depth is passed-in" in withInstances {
  //   implicit extraBlockFetcher => implicit quorumFetcher => implicit proposalFetcher =>
  //     //given
  //     //test remote caller, with no special effect on input or output, returning a String, based on expected calls
  //     implicit val testRpc = new RemoteRpc[Id, Id, Const[String, ?]] {
  //       type CallConfig = Any
  //       type PostPayload = Nothing
  //       val Quorum = "blocks/.+/votes/current_quorum".r
  //       val Proposal = "blocks/.+/votes/current_proposal".r

  //       override def runGetCall[CallId](callConfig: Any, request: CallId, commandMap: CallId => String): Const[String,CallId] =
  //         Const(commandMap(request) match {
  //             case "blocks/head~" => TezosResponseBuilder.blockResponse
  //             case "blocks/head~/operations" => TezosResponseBuilder.operationsResponse
  //             case "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c/operations" => TezosResponseBuilder.operationsResponse
  //             case Quorum() => TezosResponseBuilder.votesQuorum
  //             case Proposal() => TezosResponseBuilder.votesProposal
  //           }
  //         )

  //       override def runPostCall[CallId](callConfig: Any, request: CallId, commandMap: CallId => String, payload: Option[Nothing]): Const[String,CallId] = ???
  //     }

  //     //test dataFetcher for blocks
  //     implicit val blockFetchProvider = Reader(
  //       (_: BlockHash) => testBlockFetcher(TezosResponseBuilder.batchedGetBlockQueryResponse)
  //     )

  //     //when
  //     val blocksResults: sut.BlockFetchingResults = sut.getLatestBlocks[Id]()

  //     //then
  //     blocksResults should have size 1
  //     val (Block(data, ops, votes), accounts) = blocksResults.head
  //     data.hash.value shouldBe "BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"
  //     data.header.level shouldBe 162385

  //     ops shouldBe empty
  //     votes shouldEqual CurrentVotes.empty
  //     accounts shouldBe empty

  // }

  // "getLatestBlocks" should "correctly fetch latest blocks" in withInstances {
  //   implicit extraBlockFetcher => implicit quorumFetcher => implicit proposalFetcher =>
  //     //given
  //     //test remote caller, with no special effect on input or output, returning a String, based on expected calls
  //     implicit val testRpc = new RemoteRpc[Id, Id, Const[String, ?]] {
  //       type CallConfig = Any
  //       type PostPayload = Nothing
  //       val Quorum = "blocks/.+/votes/current_quorum".r
  //       val Proposal = "blocks/.+/votes/current_proposal".r

  //       override def runGetCall[CallId](callConfig: Any, request: CallId, commandMap: CallId => String): Const[String,CallId] =
  //         Const(commandMap(request) match {
  //             case "blocks/head~" => TezosResponseBuilder.blockResponse
  //             case "blocks/head~/operations" => TezosResponseBuilder.operationsResponse
  //             case "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c/operations" => TezosResponseBuilder.operationsResponse
  //             case Quorum() => TezosResponseBuilder.votesQuorum
  //             case Proposal() => TezosResponseBuilder.votesProposal
  //           }
  //         )

  //       override def runPostCall[CallId](callConfig: Any, request: CallId, commandMap: CallId => String, payload: Option[Nothing]): Const[String,CallId] = ???
  //     }

  //     //test dataFetcher for blocks
  //     implicit val blockFetchProvider = Reader(
  //       (_: BlockHash) => testBlockFetcher(TezosResponseBuilder.batchedGetBlockQueryResponse)
  //     )

  //     //when
  //     val blocksResults: sut.BlockFetchingResults = sut.getLatestBlocks[Id](depth = Some(1))

  //     //then
  //     blocksResults should have size 1

  //     val (Block(data, ops, votes), accounts) = blocksResults.head
  //     data.hash.value shouldBe "BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"
  //     data.header.level shouldBe 162385

  //     ops shouldBe empty
  //     votes shouldEqual CurrentVotes.empty
  //     accounts shouldBe empty

  // }

  // "getLatestBlocks" should "correctly fetch blocks starting from a given head" in withInstances {
  //   implicit extraBlockFetcher => implicit quorumFetcher => implicit proposalFetcher =>
  //     //given
  //     //test remote caller, with no special effect on input or output, returning a String, based on expected calls
  //     implicit val testRpc = new RemoteRpc[Id, Id, Const[String, ?]] {
  //       type CallConfig = Any
  //       type PostPayload = Nothing
  //       val Quorum = "blocks/.+/votes/current_quorum".r
  //       val Proposal = "blocks/.+/votes/current_proposal".r

  //       override def runGetCall[CallId](callConfig: Any, request: CallId, commandMap: CallId => String): Const[String,CallId] =
  //         Const(commandMap(request) match {
  //             case "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~" => TezosResponseBuilder.blockResponse
  //             case "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c~/operations" => TezosResponseBuilder.operationsResponse
  //             case "blocks/BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c/operations" => TezosResponseBuilder.operationsResponse
  //             case Quorum() => TezosResponseBuilder.votesQuorum
  //             case Proposal() => TezosResponseBuilder.votesProposal
  //           }
  //         )

  //       override def runPostCall[CallId](callConfig: Any, request: CallId, commandMap: CallId => String, payload: Option[Nothing]): Const[String,CallId] = ???
  //     }

  //     //test dataFetcher for blocks
  //     implicit val blockFetchProvider = Reader(
  //       (_: BlockHash) => testBlockFetcher(TezosResponseBuilder.batchedGetBlockQueryResponse)
  //     )

  //     //when
  //     val blocksResults: sut.BlockFetchingResults = sut.getLatestBlocks[Id](depth = Some(1), headHash = Some(BlockHash("BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c")))

  //     //then
  //     blocksResults should have size 1

  //     val (Block(data, ops, votes), accounts) = blocksResults.head
  //     data.hash.value shouldBe "BMKoXSqeytk6NU3pdL7q8GLN8TT7kcodU1T6AUxeiGqz2gffmEF"
  //     data.header.level shouldBe 162385

  //     ops shouldBe empty
  //     votes shouldEqual CurrentVotes.empty
  //     accounts shouldBe empty
  // }

}
