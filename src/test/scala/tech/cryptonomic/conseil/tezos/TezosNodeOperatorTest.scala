package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.SpanSugar._
import tech.cryptonomic.conseil.tezos.MockTezosNodes.{sequenceNodes, FileBasedNode}
import tech.cryptonomic.conseil.tezos.TezosTypes.BlockHash

class TezosNodeOperatorTest
  extends WordSpec
    with MockFactory
    with Matchers
    with ScalaFutures
    with InMemoryDatabase
    with LazyLogging {

  import FileBasedNode.getNode

  import scala.concurrent.ExecutionContext.Implicits.global

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

    "load latest blocks when there's no fork in the chain" in {
      //SCENARIO 1 on the scheme
      lazy val nonForkingScenario = getNode(onBranch = 0, atLevel = 2)
      val headHash = BlockHash(mocks.getHeadHash(onBranch = 0, atLevel = 2))
      val sut= createTestOperator(nonForkingScenario)

      val blockActions =
        sut.getBlocks(network, 0, 2, startBlockHash = headHash, followFork = true)
            .futureValue(timeout = Timeout(10.seconds))

      blockActions should have size 3
      blockActions.map(_.action) should contain only sut.WriteBlock
      blockActions.map(_.block.metadata.hash.value) should contain allOf (
        "BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4",
        "BMACW5bXgRNEsjnL3thC8BBWVpYr3qfu6xPNXqB2RpHdrph8KbG",
        "BLockGenesisGenesisGenesisGenesisGenesis67853hJiJiM"
      )
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


  //SCENARIO 2 on the scheme
  lazy val singleForkScenario = sequenceNodes(
    getNode(onBranch = 0, atLevel = 2),
    getNode(onBranch = 0, atLevel = 4),
    getNode(onBranch = 1, atLevel = 5, forkDetection = Some("BLTyS5z4VEPBQzReVLs4WxmpwfRZyczYybxp3CpeJrCBRw17p6z"))
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
