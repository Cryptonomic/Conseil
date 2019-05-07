package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.Inspectors._
import org.scalatest.time.SpanSugar._
import tech.cryptonomic.conseil.tezos.MockTezosForkingNodes.{sequenceNodes, FileBasedNode}
import tech.cryptonomic.conseil.tezos.Tables.{BlocksRow, InvalidatedBlocksRow}
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

  val mocks = MockTezosForkingNodes

  val testApi = new ApiOperations {
    override val dbHandle = dbHandler
  }

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
      blockActions collect {
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
      val putOnDb = store(sut) _
      import sut.{WriteBlock, WriteAndMakeValidBlock, BlockFetchingResults}

      {
        //time 0
        val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 2))
        val blockActions: BlockFetchingResults =
          sut.getBlocks(reference = headHash -> 2, levelRange = 0 to 2, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 3
        forAll(blockActions) {
          _._1 shouldBe a [WriteBlock]
        }
        blockActions collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain allOf(
          "BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4", //lvl 2
          "BMACW5bXgRNEsjnL3thC8BBWVpYr3qfu6xPNXqB2RpHdrph8KbG", //lvl 1
          "BLockGenesisGenesisGenesisGenesisGenesis67853hJiJiM"  //lvl 0
        )

        //write to db
        putOnDb(blockActions).isReadyWithin(1.second) shouldBe true

      }
      {
        //time 1
        timeFrame.next() shouldBe true
        val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 4))
        val blockActions =
          sut.getBlocks(reference = headHash -> 4, levelRange = 3 to 4, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 2

        forAll(blockActions) {
          _._1 shouldBe a [WriteBlock]
        }

        blockActions collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain allOf(
          "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi", //lvl 4
          "BLvptJbhLUAZNwFd9TGwWNSEjwddeKJoz3qy1yfC8WiSKVUXA2o"  //lvl 3
        )

        //write to db
        putOnDb(blockActions).isReadyWithin(1 second) shouldBe true
      }
      {
        //time 2
        timeFrame.next() shouldBe true

        val headHash = BlockHash(mocks.getHeadHash(onBranch = 1, atLevel = 5))
        val blockActions =
          sut.getBlocks(reference = headHash -> 5, levelRange = 5 to 5, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 2
        blockActions collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain only "BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp" //lvl 5
        blockActions collect {
          case (WriteAndMakeValidBlock(block), _) => block.data.hash.value
        } should contain only "BLTyS5z4VEPBQzReVLs4WxmpwfRZyczYybxp3CpeJrCBRw17p6z" //lvl 4

      }

      //double check
      timeFrame.next() shouldBe false

    }

    "load blocks to save in the single-alternating-fork scenario" in {
      //SCENARIO 3 on the scheme
      lazy val (singleForkAlternatingScenario, timeFrame) = sequenceNodes(
        getNode(onBranch = 0, atLevel = 2),
        getNode(onBranch = 0, atLevel = 4),
        getNode(onBranch = 1, atLevel = 5, forkDetection = Some("BLTyS5z4VEPBQzReVLs4WxmpwfRZyczYybxp3CpeJrCBRw17p6z")),
        getNode(onBranch = 0, atLevel = 6, forkDetection = Some("BM2sQM8aKp2vjTTvHifCyp1b1JVYuvcxcy2tU5mSYHnK6FfvfYD")),
        getNode(onBranch = 1, atLevel = 7, forkDetection = Some("BLGM6zuKbwxAYemB1zLAgdpmDcZMukztT7KLr6f1kK9djigNk6J"))
      )

      val sut = createTestOperator(singleForkAlternatingScenario)
      val putOnDb = store(sut) _
      import sut.{WriteBlock, WriteAndMakeValidBlock, RevalidateBlock, BlockFetchingResults}

      {
        //time 0
        val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 2))
        val blockActions: BlockFetchingResults =
          sut.getBlocks(reference = headHash -> 2, levelRange = 0 to 2, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 3
        forAll(blockActions) {
          _._1 shouldBe a [WriteBlock]
        }
        blockActions collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain allOf(
          "BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4", //lvl 2
          "BMACW5bXgRNEsjnL3thC8BBWVpYr3qfu6xPNXqB2RpHdrph8KbG", //lvl 1
          "BLockGenesisGenesisGenesisGenesisGenesis67853hJiJiM"  //lvl 0
        )

        //write to db
        putOnDb(blockActions).isReadyWithin(1.second) shouldBe true
      }
      {
        //time 1
        timeFrame.next() shouldBe true
        val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 4))
        val blockActions =
          sut.getBlocks(reference = headHash -> 4, levelRange = 3 to 4, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 2

        forAll(blockActions) {
          _._1 shouldBe a [WriteBlock]
        }

        blockActions collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain allOf(
          "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi", //lvl 4
          "BLvptJbhLUAZNwFd9TGwWNSEjwddeKJoz3qy1yfC8WiSKVUXA2o"  //lvl 3
        )

        //write to db
        putOnDb(blockActions).isReadyWithin(1 second) shouldBe true
      }
      {
        //time 2
        timeFrame.next() shouldBe true

        val headHash = BlockHash(mocks.getHeadHash(onBranch = 1, atLevel = 5))
        val blockActions =
          sut.getBlocks(reference = headHash -> 5, levelRange = 5 to 5, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 2
        blockActions collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain only "BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp" //lvl 5
        blockActions collect {
          case (WriteAndMakeValidBlock(block), _) => block.data.hash.value
        } should contain only "BLTyS5z4VEPBQzReVLs4WxmpwfRZyczYybxp3CpeJrCBRw17p6z" //lvl 4

        //write to db
        putOnDb(blockActions).isReadyWithin(1 second) shouldBe true

      }
      {
        //time 3
        timeFrame.next() shouldBe true
        val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 6))
        val blockActions =
          sut.getBlocks(reference = headHash -> 6, levelRange = 6 to 6, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 3

        blockActions collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain only "BMSw4mdnPTRpNtNoRBGpnYDGGH9pcxB2FHBxQeonyUunaV5bAz1" //lvl 6
        blockActions collect {
          case (WriteAndMakeValidBlock(block), _) => block.data.hash.value
        } should contain only "BM2sQM8aKp2vjTTvHifCyp1b1JVYuvcxcy2tU5mSYHnK6FfvfYD" //lvl 5
        blockActions collect {
          case (RevalidateBlock(block), _) => block.data.hash.value
        } should contain only "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi" //lvl 4

        //write to db
        putOnDb(blockActions).isReadyWithin(1 second) shouldBe true
      }
      {
        //time 4
        timeFrame.next() shouldBe true
        val headHash = BlockHash(mocks.getHeadHash(onBranch = 1, atLevel = 7))
        val blockActions =
          sut.getBlocks(reference = headHash -> 7, levelRange = 7 to 7, followFork = true)
            .futureValue(timeout = Timeout(10 seconds))

        blockActions should have size 4

        blockActions collect {
          case (WriteBlock(block), _) => block.data.hash.value
        } should contain only "BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr" //lvl 7
        blockActions collect {
          case (WriteAndMakeValidBlock(block), _) => block.data.hash.value
        } should contain only "BLGM6zuKbwxAYemB1zLAgdpmDcZMukztT7KLr6f1kK9djigNk6J" //lvl 6
        blockActions collect {
          case (RevalidateBlock(block), _) => block.data.hash.value
        } should contain allOf (
          "BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp", //lvl 5
          "BLTyS5z4VEPBQzReVLs4WxmpwfRZyczYybxp3CpeJrCBRw17p6z"  //lvl 4
        )
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
    new TezosNodeOperator(node, network = "tezos", batchConf = config) {
      override lazy val operations = testApi
    }

  /* store rows on the db as blocks */
  private def store(sut: TezosNodeOperator)(actions: sut.BlockFetchingResults): Future[_] = {
    import slick.jdbc.PostgresProfile.api._
    import sut.{WriteBlock, WriteAndMakeValidBlock, RevalidateBlock}

    val rows = actions collect {
      case (WriteBlock(block), _) =>
        toRow(block.data.hash, block.data.header.level)
      case (WriteAndMakeValidBlock(block), _) =>
        toRow(block.data.hash, block.data.header.level)
    }

    val addingBlocks = Tables.Blocks ++= rows

    val validationRows = actions collect {
      case act @ ((WriteAndMakeValidBlock(_), _) | (RevalidateBlock(_), _)) =>
        val validBlock = act._1.block
        toValidRow(validBlock.data.hash, validBlock.data.header.level)
    }

    if (validationRows.isEmpty) {
      dbHandler.run(addingBlocks)
    } else {
      val affectedLevels = validationRows.map(_.level)

      //prepare new entries for blocks that will need invalidation
      val blocksToInvalidate = dbHandler.run(
        Tables.Blocks
          .filter(_.level inSet affectedLevels)
          .map(row => (row.hash, row.level, true.bind))
          .to[List]
          .result
      ).futureValue.map(Tables.InvalidatedBlocksRow.tupled)

      //first invalidate all related existing rows, append the new invalidations
      val invalidate = Tables.InvalidatedBlocks
        .filter(row => row.level inSet affectedLevels)
        .map(_.isInvalidated)
        .update(true) :: blocksToInvalidate.map(Tables.InvalidatedBlocks.insertOrUpdate)

      //then rewrite only those that aren't invalid anymore
      val revalidate = validationRows.map(Tables.InvalidatedBlocks.insertOrUpdate)

      dbHandler.run(DBIO.sequence( addingBlocks :: invalidate ::: revalidate)) andThen {
        case _ =>
          println("newly invalidated table")
          dbHandler.run(Tables.InvalidatedBlocks.result).foreach(_.foreach(println))
      }

    }

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

  private val toValidRow: (BlockHash, Int) => InvalidatedBlocksRow = (hash, level) =>
    InvalidatedBlocksRow(
      hash = hash.value,
      level = level,
      isInvalidated = false,
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