package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.Timeouts._
import org.scalatest.Inspectors._
import org.scalatest.time.SpanSugar._
import tech.cryptonomic.conseil.tezos.MockTezosNodes.{sequenceNodes, FileBasedNode}
import tech.cryptonomic.conseil.tezos.Tables.BlocksRow
import tech.cryptonomic.conseil.tezos.TezosTypes.BlockHash
import tech.cryptonomic.conseil.config.BatchFetchConfiguration

import scala.concurrent.Future

class TezosNodeOperatorForkTest
  extends WordSpec
    with MockFactory
    with Matchers
    with ScalaFutures
    with InMemoryDatabase
    with LazyLogging {

  import FileBasedNode.getNode

  import scala.concurrent.ExecutionContext.Implicits.global

  val config = BatchFetchConfiguration(1, 1, 500, 10 seconds, 10 seconds)

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
      val sut = createTestOperator(nonForkingScenario)

      import sut.{WriteBlock, BlockFetchingResults}

      val blockActions: BlockFetchingResults =
        sut.getBlocks(reference = headHash -> 2, levelRange = 0 to 2, followFork = true)
          .futureValue(timeout = Timeout(10 seconds))

      blockActions should have size 3

      forAll(blockActions) {
        _._1 shouldBe a [WriteBlock] //ignore accounts
      }
      blockActions.collect {
        case (WriteBlock(block), _) => block.data.hash.value
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
      import sut.{WriteBlock, WriteAndMakeValidBlock, BlockFetchingResults}

      {
        //step 1
        val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 2))
        val blockActions: BlockFetchingResults =
          sut.getBlocks(reference = headHash -> 2, levelRange = 0 to 2, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 3
        forAll(blockActions) {
          _._1 shouldBe a [WriteBlock]
        }
        blockActions.collect {
          case (WriteBlock(block), _) => block.data.hash.value
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
          sut.getBlocks(reference = headHash -> 4, levelRange = 3 to 4, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 2

        forAll(blockActions) {
          _._1 shouldBe a [WriteBlock]
        }

        blockActions.collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain allOf(
          "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi", //lvl 4
          "BLvptJbhLUAZNwFd9TGwWNSEjwddeKJoz3qy1yfC8WiSKVUXA2o"  //lvl 3
        )

        //write to db
        store(sut)(blockActions).isReadyWithin(1 second) shouldBe true
      }
      {
        //step 3
        timeFrame.next() shouldBe true

        val headHash = BlockHash(mocks.getHeadHash(onBranch = 1, atLevel = 5))
        val blockActions =
          sut.getBlocks(reference = headHash -> 5, levelRange = 5 to 5, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 2
        blockActions.collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain only ("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp") //lvl 5
        blockActions.collect {
          case (WriteAndMakeValidBlock(block), _) => block.data.hash.value
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
    new TezosNodeOperator(node, network = "", batchConf = config)

  /* store rows on the db as blocks */
  private def store(sut: TezosNodeOperator)(actions: sut.BlockFetchingResults): Future[_] = {
    import slick.jdbc.PostgresProfile.api._

    val rows = actions map {
      case (sut.WriteBlock(block), accounts) =>
        toRow(block.data.hash, block.data.header.level)
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
}