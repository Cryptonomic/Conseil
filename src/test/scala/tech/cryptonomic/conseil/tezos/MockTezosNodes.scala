package tech.cryptonomic.conseil.tezos
import java.io.FileNotFoundException

import tech.cryptonomic.conseil.util.JsonUtil._

import scala.concurrent.Future
import scala.util.Try

/** Defines mocking scenarios for testing against tezos nodes */
object MockTezosNodes {

  //endpoint to retrieves the head block
  private val headRequestUrl = "blocks/head"
  //endpoint matcher to retrieve a specific block offset, extracts the hash and the offset value
  private val HashEndpointMatch = """blocks/([A-Za-z0-9]+)~(\d+)""".r
  //endpoint matcher for operation requests, no need to extract
  private val OperationsEndpointMatch = """blocks/([A-Za-z0-9]+)/operations""".r
  //endpoint matcher for votes, no need to extract
  private val VotesEndpointMatch = """blocks/([A-Za-z0-9]+)~*/votes/(.+)""".r

  private val emptyBlockOperationsResult = Future.successful("[[]]")
  private val votesQuorumResult = Future.successful("1000")
  private val votesProposalResult = Future.successful("null")
  private val votesPeriodResult = Future.successful(""" "proposal" """)

  /* expected head hash based on branch and level
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
    case _ => throw new IllegalArgumentException(s"no scenario defined to get a head hash for branch-$onBranch at level-$atLevel")
  }

  /** currently defines batched-get in terms of async-get only */
  trait BaseMock extends TezosRPCInterface {

    import scala.concurrent.ExecutionContext.Implicits.global

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      Future.traverse(ids)(id => runAsyncGetQuery(network, mapToCommand(id)) map (id -> _))

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonString] = None): Try[String] = ???

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonString] = None): Future[String] = ???

  }

  object FileBasedNode {

    import scala.concurrent.ExecutionContext.Implicits.global

    //utility to check offset parsing
    val isValidOffset: String => Boolean = offset => Try(offset.toInt).isSuccess

    /**
      * create a simulated node interface to return pre-canned responses
      * following a known scenario
      * @param onBranch the currently main branch
      * @param atLevel  top level of the chain for the node
      * @param forkDetection optional hash of the block that will be different for that of the same level stored on db
      */
    def getNode(onBranch: Int, atLevel: Int, forkDetection: Option[String] = None) = new BaseMock {


      //the head should depend on the branch and time both
      val headHash = getHeadHash(onBranch, atLevel)

      //used on forks to start revalidate/invalidate existing overwritten blocks
      //it's the block at which stored values diverge from the current active chain (not necessarily the forking point)
      val forkedHash = forkDetection.getOrElse("")

      //will build the results based on local files by matching request params
      override def runAsyncGetQuery(network: String, command: String): Future[String] =
        command match {
          case `headRequestUrl` =>
            //will return the block at offset 0
            getStoredBlock(0, onBranch, atLevel)
          case HashEndpointMatch(`headHash`, offset) if isValidOffset(offset) =>
            getStoredBlock(offset.toInt, onBranch, atLevel)
              .recoverWith { case ex: FileNotFoundException =>
                Future.failed(new IllegalArgumentException(s"The node simulated for branch-$onBranch at level-$atLevel received an unexpected block offset request in $command", ex))
              }
          case HashEndpointMatch(`forkedHash`, offset) if forkDetection.nonEmpty =>
            getStoredBlock(offset.toInt, onBranch, atLevel, filePrefix = "fork")
              .recoverWith { case ex: FileNotFoundException =>
                Future.failed(new IllegalArgumentException(s"The node simulated for branch-$onBranch at level-$atLevel received an unexpected block offset request in $command", ex))
              }
          case OperationsEndpointMatch(_) =>
            emptyBlockOperationsResult //ignoring the matched hash
          case VotesEndpointMatch(_, "current_quorum") =>
            votesQuorumResult //ignoring the matched hash
          case VotesEndpointMatch(_, "current_period_kind") =>
            votesPeriodResult //ignoring the matched hash
          case VotesEndpointMatch(_, "current_proposal") =>
            votesProposalResult //ignoring the matched hash
          case _ =>
            throw new IllegalStateException(s"Unexpected request path in $command")
        }

      /**
        * Helper function that returns the json block data stored in the forking_tests files.
        *
        * @param offset     how many levels away from the current block head
        * @param branch     which test chain branch we're working off of
        * @param level      which iteration of lorre we're working off of
        * @param filePrefix identifies the file name on storage, used to handle both regular latest block requests and follow-fork
        * @return a full json string with the block information, or a failure if no file exists for such parameters
        */
      def getStoredBlock(offset: Int, branch: Int, level: Int, filePrefix: String = "head"): Future[String] =
        Future(scala.io.Source.fromFile(s"src/test/resources/forking_tests/branch-$branch/level-$level/$filePrefix~$offset.json").mkString)

    }
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

}
