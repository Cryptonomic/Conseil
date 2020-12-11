package tech.cryptonomic.conseil.indexer.tezos

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import tech.cryptonomic.conseil.common.tezos.TezosTypes
import tech.cryptonomic.conseil.common.tezos.TezosTypes.TezosBlockHash
import tech.cryptonomic.conseil.indexer.config.BatchFetchConfiguration

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.language.postfixOps
import tech.cryptonomic.conseil.common.tezos.TezosTypes.GenesisMetadata
import tech.cryptonomic.conseil.common.testkit.LoggingTestSupport

class TezosNodeOperatorTest
    extends AnyFlatSpec
    with MockFactory
    with Matchers
    with ScalaFutures
    with LoggingTestSupport
    with IntegrationPatience {
  import ExecutionContext.Implicits.global

  "getBlock" should "correctly fetch the genesis block" in {
      //given
      val tezosRPCInterface = stub[TezosRPCInterface]
      val blockResponse = Future.successful(TezosResponseBuilder.genesisBlockResponse)
      val genesisHash = TezosBlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe")

      /* the node call to get the genesis block, by hash */
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe~")
        .returns(blockResponse)

      val sut: TezosNodeOperator =
        new TezosNodeOperator(tezosRPCInterface, "zeronet", Fixtures.batchConfig)

      //when
      val block: Future[TezosTypes.Block] = sut.getBlock(genesisHash)

      //then
      block.futureValue.data.hash shouldBe genesisHash
      block.futureValue.data.metadata shouldBe GenesisMetadata
      block.futureValue.operationGroups shouldBe empty
      block.futureValue.votes shouldBe TezosTypes.CurrentVotes.empty
    }

  "getLatestBlocks" should "correctly fetch all the blocks if no depth is passed-in" in {
      //given
      val tezosRPCInterface = Fixtures.mockNodeInterface()

      val sut: TezosNodeOperator =
        new TezosNodeOperator(tezosRPCInterface, "zeronet", Fixtures.batchConfig)

      //when
      val blockPages: Future[sut.PaginatedBlocksResults] = sut.getLatestBlocks()

      //then
      val (pages, total) = blockPages.futureValue
      total shouldBe 3
      val results = pages.toList
      results should have length 1
      results.head.futureValue should have length 1

    }

  "getLatestBlocks" should "correctly fetch latest blocks" in {
      //given
      val tezosRPCInterface = Fixtures.mockNodeInterface()

      val sut: TezosNodeOperator =
        new TezosNodeOperator(tezosRPCInterface, "zeronet", Fixtures.batchConfig)

      //when
      val blockPages: Future[sut.PaginatedBlocksResults] = sut.getLatestBlocks(Some(1))

      //then
      val (pages, total) = blockPages.futureValue
      total shouldBe 1
      val results = pages.toList
      results should have length 1
      results.head.futureValue should have length 1
    }

  "getLatestBlocks" should "correctly fetch blocks starting from a given head" in {
      //given
      val tezosRPCInterface =
        Fixtures.mockNodeInterface(blockReferenceHash = "BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c")
      val sut: TezosNodeOperator =
        new TezosNodeOperator(tezosRPCInterface, "zeronet", Fixtures.batchConfig)

      //when
      val blockPages: Future[sut.PaginatedBlocksResults] =
        sut.getLatestBlocks(Some(1), Some(TezosBlockHash("BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c")))

      //then
      val (pages, total) = blockPages.futureValue
      total shouldBe 1
      val results = pages.toList
      results should have length 1
      results.head.futureValue should have length 1
    }

  "getBlocksNotInDatabase" should "correclty fetch blocks from head down to the reported indexed level" in {
      //given
      val tezosRPCInterface = Fixtures.mockNodeInterface()

      /* we simulate that blocks up to level 2 are already stored
       * since the head has level 3, only 1 block is expected
       */
      val sut: TezosNodeOperator =
        new TezosNodeOperator(tezosRPCInterface, "zeronet", Fixtures.batchConfig)

      //when
      val blockPages: Future[sut.PaginatedBlocksResults] = sut.getBlocksNotInDatabase(maxIndexedLevel = 2)

      //then
      val (pages, total) = blockPages.futureValue
      total shouldBe 1
      val results = pages.toList
      results should have length 1
      results.head.futureValue should have length 1
    }

  /* provides prebuilt data for the test execution */
  private object Fixtures {

    /* default configuration for fetch batching */
    val batchConfig = BatchFetchConfiguration(1, 1, 500, 10 seconds, 10 seconds, 10 seconds)

    /* mocks the rpc interface with proper responses based on the expected reference [head] block hash
     * as given by the parameter
     */
    def mockNodeInterface(
        blockReferenceHash: String = "BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c"
    ): TezosRPCInterface = {

      val tezosRPCInterface = stub[TezosRPCInterface]

      /* here we define future wrapped responses from the json builder */
      val blockResponse = Future.successful(TezosResponseBuilder.blockResponse)
      val operationsResponse = Future.successful(TezosResponseBuilder.operationsResponse)
      val votesPeriodKind = Future.successful(TezosResponseBuilder.votesPeriodKind)
      val votesQuorum = Future.successful(TezosResponseBuilder.votesQuorum)
      val votesProposal = Future.successful(TezosResponseBuilder.votesProposal)

      /* Here follows all required calls to get data from the node
       * which the mock will need to handle, using the responses already
       * defined above.
       * We expect the node to be called to get the data for the "head" block first
       * including all data for the block: operations, quorum, proposal.
       *
       * After having received the head data, all previous levels will be requested
       * based on the head level (which, from test data definition, is 3).
       * Therefore a batch request will ensue for offsets 0, 1, 2 and all related
       * batched data for: operations, quorum, proposal
       */

      /* here's the call to get the latest head (no offset) */
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", "blocks/head~")
        .returns(blockResponse)
      /* here's the call to get a specific block reference when such is provided from the lorre input */
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", s"blocks/$blockReferenceHash~")
        .returns(blockResponse)
      /* here's the call to the reference head operations */
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", s"blocks/$blockReferenceHash/operations")
        .returns(operationsResponse)
      /* here's the call to the reference head voting period kind */
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", s"blocks/$blockReferenceHash~/votes/current_period_kind")
        .returns(votesPeriodKind)
      /* here's the call to the reference head current quorum required for voting */
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", s"blocks/$blockReferenceHash~/votes/current_quorum")
        .returns(votesQuorum)
      /* here's the call to the reference head current protocol proposal, if any */
      (tezosRPCInterface.runAsyncGetQuery _)
        .when("zeronet", s"blocks/$blockReferenceHash~/votes/current_proposal")
        .returns(votesProposal)

      /* here's the batch calls for the offset blocks and related data
       * we match the input type to figure out what kind of information is requested
       * from the node.
       */
      (tezosRPCInterface.runBatchedGetQuery[Any] _)
        .when("zeronet", *, *, *)
        .onCall(
          (_, input, command, _) =>
            Future.successful(
              List(
                //dirty trick to find the type of input content and provide the appropriate response
                input match {
                  /* A list of numbers represent offsets from the head block */
                  case (_: Long) :: tail => (0, TezosResponseBuilder.batchedGetBlockQueryResponse)
                  /* A list of hashes whose url matches operations requests for those blocks */
                  case (hash: TezosBlockHash) :: tail if command(hash) endsWith "operations" =>
                    (hash, TezosResponseBuilder.batchedGetOperationsQueryResponse)
                  /* A list of hashes whose url matches the voting period kind requests for those blocks */
                  case (hash: TezosBlockHash) :: tail if command(hash) endsWith "votes/current_period_kind" =>
                    (hash, TezosResponseBuilder.votesPeriodKind)
                  /* A list of hashes whose url matches quorum requests for those blocks */
                  case (hash: TezosBlockHash) :: tail if command(hash) endsWith "votes/current_quorum" =>
                    (hash, TezosResponseBuilder.votesQuorum)
                  /* A list of hashes whose url matches current proposal requests for those blocks */
                  case (hash: TezosBlockHash) :: tail if command(hash) endsWith "votes/current_proposal" =>
                    (hash, TezosResponseBuilder.votesProposal)
                }
              )
            )
        )

      tezosRPCInterface

    }
  }
}
