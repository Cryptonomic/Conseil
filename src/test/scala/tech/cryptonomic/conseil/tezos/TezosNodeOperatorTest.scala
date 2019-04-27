package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers, WordSpec}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.Inspectors._
import org.scalatest.time.SpanSugar._
import tech.cryptonomic.conseil.tezos.MockTezosNodes.{sequenceNodes, FileBasedNode}
import tech.cryptonomic.conseil.tezos.Tables.BlocksRow
import tech.cryptonomic.conseil.tezos.TezosTypes.BlockHash
import tech.cryptonomic.conseil.config.BatchFetchConfiguration

import scala.concurrent.Future
import scala.concurrent.duration._


class TezosNodeOperatorTest
  extends WordSpec
    with MockFactory
    with Matchers
    with ScalaFutures
    with InMemoryDatabase
    with IntegrationPatience
    with LazyLogging {

  import FileBasedNode.getNode

  import scala.concurrent.ExecutionContext.Implicits.global

  val config = BatchFetchConfiguration(1, 1, 500, 10 seconds, 10 seconds)

  override val inMemoryDbName = "node-ops-test"
  val mocks = MockTezosNodes

  /*
   * The following diagrams outlines all testing scenarios available
   *
   * In each scenario we can imagine a "snapshot" of the node and the results
   * that it is expected to return from the block request, based on the
   * exact level (Ln) for that time-frame and the corresponding "main" branch
   *
   * Most snapshot for the same time-frame will return the same results. It
   * doesn't needs to be so, but it simplifies the data definition
   *
   * SCENARIO 1: no fork
   * - time ->
   *
   *
   * -----[L2]---------------  branch-0
   *
   *
   * SCENARIO 2: single fork
   * - time ->
   *
   *
   *            |-----------[L5]----------  branch-1
   * -----[L2]--|---[L4]------------------  branch-0
   *
   *
   * SCENARIO 3: single fork alternating with the original
   * - time ->
   *
   *
   *            |-----------[L5]----------[L7]------  branch-1
   * -----[L2]--|---[L4]------------[L6]------------  branch-0
   *
   *
   * SCENARIO 4: two forks alternating with the original
   * - time ->
   *
   *
   *            |-------------------[L6]---------------  branch-2
   *            |-----------[L5]----------[L7]---------  branch-1
   * -----[L2]--|---[L4]-------------------------[L8]--  branch-0
   *
   */

  "The Node Operator" should {

    val network = "tezos"

    "load blocks to save in the no-fork scenario" in {
      //SCENARIO 1 on the scheme
      lazy val nonForkingScenario = getNode(onBranch = 0, atLevel = 2)
      val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 2))
      val sut= createTestOperator(nonForkingScenario)

      import sut.WriteBlock

      val blockActions =
        sut.getBlocks(network, 0, 2, startBlockHash = headHash, followFork = true)
            .futureValue(timeout = Timeout(10.seconds))

      blockActions should have size 3

      forAll(blockActions) {
        _ shouldBe a [WriteBlock]
      }
      blockActions.collect {
        case WriteBlock(block) => block.metadata.hash.value
      } should contain allOf (
        "BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4", //lvl 2
        "BMACW5bXgRNEsjnL3thC8BBWVpYr3qfu6xPNXqB2RpHdrph8KbG", //lvl 1
        "BLockGenesisGenesisGenesisGenesisGenesis67853hJiJiM"  //lvl 0
      )
    }

    "load blocks to save in the single-fork scenario" in {
      //SCENARIO 2 on the scheme
      lazy val (singleForkScenario, timeFrame) = sequenceNodes(
        getNode(onBranch = 0, atLevel = 2),
        getNode(onBranch = 0, atLevel = 4),
        getNode(onBranch = 1, atLevel = 5, forkDetection = Some("BLTyS5z4VEPBQzReVLs4WxmpwfRZyczYybxp3CpeJrCBRw17p6z"))
      )
      val sut = createTestOperator(singleForkScenario)
      import sut.{WriteBlock, WriteAndMakeValidBlock}

      {
        //step 1
        val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 2))
        val blockActions =
          sut.getBlocks(network, 0, 2, startBlockHash = headHash, followFork = true)
            .futureValue(timeout = Timeout(10.seconds))

        blockActions should have size 3
        forAll(blockActions) {
          _ shouldBe a [WriteBlock]
        }
        blockActions.collect {
          case WriteBlock(block) => block.metadata.hash.value
        } should contain allOf(
          "BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4", //lvl 2
          "BMACW5bXgRNEsjnL3thC8BBWVpYr3qfu6xPNXqB2RpHdrph8KbG", //lvl 1
          "BLockGenesisGenesisGenesisGenesisGenesis67853hJiJiM"  //lvl 0
        )

        //write to db
        store(sut)(blockActions).isReadyWithin(1.second) shouldBe true

      }
      {
        //step 2
        timeFrame.next() shouldBe true
        val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 4))
        val blockActions =
          sut.getBlocks(network, 3, 4, startBlockHash = headHash, followFork = true)
            .futureValue(timeout = Timeout(10.seconds))

        blockActions should have size 2
        forAll(blockActions) {
          _ shouldBe a [WriteBlock]
        }
        blockActions.collect {
          case WriteBlock(block) => block.metadata.hash.value
        } should contain allOf(
          "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi", //lvl 4
          "BLvptJbhLUAZNwFd9TGwWNSEjwddeKJoz3qy1yfC8WiSKVUXA2o"  //lvl 3
        )

        //write to db
        store(sut)(blockActions).isReadyWithin(1.second) shouldBe true
      }
      {
        //step 3
        timeFrame.next() shouldBe true

        val headHash = BlockHash(mocks.getHeadHash(onBranch = 1, atLevel = 5))
        val blockActions =
          sut.getBlocks(network, 5, 5, startBlockHash = headHash, followFork = true)
            .futureValue(timeout = Timeout(10.seconds))

        blockActions should have size 2
        blockActions.collect {
          case WriteBlock(block) => block.metadata.hash.value
        } should contain only ("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp") //lvl 5
        blockActions.collect {
          case WriteAndMakeValidBlock(block) => block.metadata.hash.value
        } should contain only ("BLTyS5z4VEPBQzReVLs4WxmpwfRZyczYybxp3CpeJrCBRw17p6z") //lvl 4

      }

      //double check
      timeFrame.next() shouldBe false

    }

  }

  /* create an operator instance to test, using
   * - a custom node interface for scenario
   * - a test database access
   */
  private def createTestOperator(node: TezosRPCInterface) =
    new TezosNodeOperator(node) {
      override lazy val operations =
        new ApiOperations { lazy val dbHandle = dbHandler }
    }

  /* store rows on the db as blocks */
  private def store(sut: TezosNodeOperator)(actions: List[sut.BlockAction]): Future[_] = {
    import slick.jdbc.H2Profile.api._

    val rows = actions map {
      case sut.WriteBlock(block) =>
        toRow(block.metadata.hash, block.metadata.header.level)
    }

    dbHandler.run(Tables.Blocks ++= rows)
  }

  private val toRow: (BlockHash, Int) => BlocksRow = (hash, level) =>
    BlocksRow(
      hash = hash.value,
      level = level,
      proto = 0,
      predecessor = "",
      timestamp = new Timestamp(0L),
      validationPass = 0,
      fitness = "",
      protocol = ""
    )


  //SCENARIO 3 on the scheme
  lazy val singleForkAlternatingScenario = sequenceNodes(
    getNode(onBranch = 0, atLevel = 2),
    getNode(onBranch = 0, atLevel = 4),
    getNode(onBranch = 1, atLevel = 5, forkDetection = Some("BLTyS5z4VEPBQzReVLs4WxmpwfRZyczYybxp3CpeJrCBRw17p6z")),
    getNode(onBranch = 0, atLevel = 6, forkDetection = Some("BM2sQM8aKp2vjTTvHifCyp1b1JVYuvcxcy2tU5mSYHnK6FfvfYD")),
    getNode(onBranch = 1, atLevel = 7, forkDetection = Some("BLGM6zuKbwxAYemB1zLAgdpmDcZMukztT7KLr6f1kK9djigNk6J"))
  )

  //SCENARIO 4 on the scheme
  lazy val twoForksAlternatingScenario = sequenceNodes(
    getNode(onBranch = 0, atLevel = 2),
    getNode(onBranch = 0, atLevel = 4),
    getNode(onBranch = 1, atLevel = 5, forkDetection = Some("BLTyS5z4VEPBQzReVLs4WxmpwfRZyczYybxp3CpeJrCBRw17p6z")),
    getNode(onBranch = 2, atLevel = 6, forkDetection = Some("BMBthHtaQT5vJJXWm3djp9CJrjgdSpouDJW1MMM2vLYyjdVeLnt")),
    getNode(onBranch = 1, atLevel = 7, forkDetection = Some("BLGM6zuKbwxAYemB1zLAgdpmDcZMukztT7KLr6f1kK9djigNk6J")),
    getNode(onBranch = 0, atLevel = 8, forkDetection = Some("BMKgJeHauF6JdDexxxzhFmmCFuyEokv5gfyvXfy68cVEHZUUZis"))
  )

  "getBlock" should "correctly fetch the genesis block" in {
    //given
    val tezosRPCInterface = stub[TezosRPCInterface]
    val blockResponse = Future.successful(TezosResponseBuilder.genesisBlockResponse)
    (tezosRPCInterface.runAsyncGetQuery _).when("zeronet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe~").returns(blockResponse)

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, "zeronet", config)

    //when
    val block: Future[TezosTypes.Block] = nodeOp.getBlock(BlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe"))

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
      .onCall( (_, input, command, _) =>
        Future.successful(List(
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
      .onCall((_, input, command, _) =>
        Future.successful(List(
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
      .onCall((_, input, command, _) =>
        Future.successful(List(
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
        ))
      )

    val nodeOp: TezosNodeOperator = new TezosNodeOperator(tezosRPCInterface, "zeronet", config)

    //when
    val blockPages: Future[nodeOp.PaginatedBlocksResults] = nodeOp.getLatestBlocks(Some(1), Some(BlockHash("BLJKK4VRwZk7qzw64NfErGv69X4iWngdzfBABULks3Nd33grU6c")))

    //then
    val (pages, total) = blockPages.futureValue
    total shouldBe 1
    val results = pages.toList
    results should have length 1
    results.head.futureValue should have length 1
  }

}
