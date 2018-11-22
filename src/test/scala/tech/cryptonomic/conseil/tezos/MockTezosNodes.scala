package tech.cryptonomic.conseil.tezos
import tech.cryptonomic.conseil.util.JsonUtil._

import scala.concurrent.Future
import scala.util.Try

/**
  * Defines mocking scenarios for testing against tezos nodes
  *
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
object MockTezosNodes {

  //endpoint to retrieves the head block
  private val headRequestUrl = "blocks/head"
  //endpoint matcher to retrieve a specific block offset, extracts the hash and the offset value
  private val HashEndpointMatch = """blocks/([A-Za-z0-9]+)~(\d+)""".r
  //endpoint matcher for operation requests, no need to extract
  private val OperationsEndpointMatch = """blocks/([A-Za-z0-9]+)/operations""".r

  private val emptyBlockOperationsResult = Future.successful("[[]]")

  /* expected hash based on branch and level
   * this must match the base file hash returned from the mock node when the head block is requested
   */
  def getHeadHash(onBranch: Int, atLevel: Int) = (onBranch, atLevel) match {
    case (_, 2) => "BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4"
    case (_, 4) => "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi"
    case (_, 5) => "BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp"
    case (0, 6) => "BMSw4mdnPTRpNtNoRBGpnYDGGH9pcxB2FHBxQeonyUunaV5bAz1"
    case (2, 6) => "BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp"
    case (_, 7) => "BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr"
    case (_, 8) => "BMeiBtFrXuVN7kcVaC4mt1dbncX2n8tb76qUeM4JCr97Cb7U84u"
    case _ => throw new IllegalArgumentException(s"no scenario defined to get a head hash for branch-$onBranch at time T$atLevel")
  }

  /* only a limited number of offsets are expected on the mock calls,
   * based on the scenario and current level
   */
  def getMaxOffsets(onBranch: Int, atLevel: Int) = (onBranch, atLevel) match {
    case (_, 2) => 2
    case (_, 4) => 1
    case (_, 5) => 1
    case (0, 6) => 1
    case (2, 6) => 2
    case (_, 7) => 1
    case (_, 8) => 3
    case _ => throw new IllegalArgumentException(s"no scenario defines expected offsets for brach-$onBranch at time T$atLevel")

  }

  /** currently defines batched-get in terms of async-get only */
  trait BaseMock extends TezosRPCInterface {

    import scala.concurrent.ExecutionContext.Implicits.global

    def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      Future.traverse(commands)(runAsyncGetQuery(network, _))

    def runGetQuery(network: String, command: String): Try[String] = ???

    def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    def runPostQuery(network: String, command: String, payload: Option[JsonString] = None): Try[String] = ???

    def runAsyncPostQuery(network: String, command: String, payload: Option[JsonString] = None): Future[String] = ???

  }

  /**
    * create a simulated node interface to return pre-canned responses
    * following a known scenario
    */
  def getNode(onBranch: Int, atLevel: Int) = new BaseMock {
    import scala.concurrent.ExecutionContext.Implicits.global

    //the head should depend on the branch and time both
    val headHash = getHeadHash(onBranch, atLevel)

    //the number of offsets to load should never be higher than this
    val maxExpectedOffset = getMaxOffsets(onBranch, atLevel)

    //use some mapping to define valid offsets per scenario?
    def isValidOffset(offset: String) = Try {
      val intOffset = offset.toInt
      intOffset >= 0 && intOffset <= maxExpectedOffset
    }.getOrElse(false)

    //will build the results based on local files by matching request params
    override def runAsyncGetQuery(network: String, command: String): Future[String] =
      command match {
        case `headRequestUrl` =>
          //will return the block at offset 0
          getStoredBlock(0, onBranch, atLevel)
        case HashEndpointMatch(`headHash`, offset) if isValidOffset(offset) =>
          getStoredBlock(offset.toInt, onBranch, atLevel)
        case HashEndpointMatch(`headHash`, offset) =>
          throw new IllegalStateException(s"The node simulated for branch-$onBranch at level-$atLevel received an unexpected block offset request in $command")
        case OperationsEndpointMatch(hash) =>
          emptyBlockOperationsResult
        case _ =>
          throw new IllegalStateException(s"Unexpected request path in $command")
      }

    /**
      * Helper function that returns the json block data stored in the forking_tests files.
      * @param offset how many levels away from the current block head
      * @param branch which test chain branch we're working off of
      * @param level  which iteration of lorre we're working off of
      * @return a full json string with the block information
      */
    private def getStoredBlock(offset: Int, branch: Int, level: Int): Future[String] =
      Future(scala.io.Source.fromFile(s"src/test/resources/forking_tests/branch-$branch/level-$level/head~$offset.json").mkString)

  }

  private type TezosNode = TezosRPCInterface

  /**
    * Allows to advance forth and back in integer steps from `0` to the provided `max`
    * Instances are not thread-safe
    * @param size the cursor will always be bound to this value
    */
  class Frame(max: Int) {

    private[MockTezosNodes] val cursor = {
      //a sychronized cell to hold a value, used to simplify a concurrent access to a var
      val synced = new scala.concurrent.SyncVar[Int]()
      synced.put(0)
      synced
    }

    /**
      * Increment the frame cursor without overflowing
      * @return `true` if the cursor actually changed
      */
    def next(): Boolean = {
      val frame = cursor.take()
      cursor.put(scala.math.min(frame + 1, max)) //no overflow
      println(s"Changed time frame from  $frame to ${cursor.get}")
      cursor.get != frame
    }

    /**
      * Decrement the frame cursor without underflowing
      * @return `true` if the cursor actually changed
      */
    def prev(): Boolean = {
      val frame = cursor.take()
      cursor.put(scala.math.max(frame - 1, 0)) //no underflow
      println(s"Changed time frame from  $frame to ${cursor.get}")
      cursor.get != frame
    }
  }

  /**
    * Creates a sequence of mock nodes, used to simulate
    * different states of the remote node in time
    * The returned `Frame` is used to move "time" ahead and back by
    * pointing to different nodes in the sequence
    */
  def sequenceNodes(first: TezosNode, rest: TezosNode*): (TezosNode, Frame) = {
    val nodes = new NodeSequence(first, rest: _*)
    (nodes, nodes.frame)
  }

  /*
   * A sequence of tezos interfaces that will delegate the get request
   * to the one currently selected by the internal `frame` variable
   * This way we can emulate the passing of time during the test in a controlled way
   */
  private class NodeSequence(first: TezosNode, rest: TezosNode*) extends BaseMock {

    private val nodes = first :: rest.toList

    val frame = new Frame(nodes.size - 1)

    override def runAsyncGetQuery(network: String, command: String): Future[String] =
      nodes(frame.cursor.get).runAsyncGetQuery(network, command)

  }

  //SCENARIO 1 on the scheme
  lazy val nonForkingScenario = getNode(onBranch = 0, atLevel = 2)


  //SCENARIO 2 on the scheme
  lazy val singleForkScenario = sequenceNodes(
    getNode(onBranch = 0, atLevel = 2),
    getNode(onBranch = 0, atLevel = 4),
    getNode(onBranch = 1, atLevel = 5)
  )

  //SCENARIO 3 on the scheme
  lazy val singleForkAlternatingScenario = sequenceNodes(
    getNode(onBranch = 0, atLevel = 2),
    getNode(onBranch = 0, atLevel = 4),
    getNode(onBranch = 1, atLevel = 5),
    getNode(onBranch = 0, atLevel = 6),
    getNode(onBranch = 1, atLevel = 7)
  )

  //SCENARIO 4 on the scheme
  lazy val twoForksAlternatingScenario = sequenceNodes(
    getNode(onBranch = 0, atLevel = 2),
    getNode(onBranch = 0, atLevel = 4),
    getNode(onBranch = 1, atLevel = 5),
    getNode(onBranch = 2, atLevel = 6),
    getNode(onBranch = 1, atLevel = 7),
    getNode(onBranch = 0, atLevel = 8)
  )

}
