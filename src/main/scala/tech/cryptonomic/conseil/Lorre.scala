package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.Done
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{
  AccountsDataFetchers,
  ApiOperations,
  BlocksDataFetchers,
  FeeOperations,
  NodeOperator,
  ShutdownComplete,
  TezosDatabaseOperations => TezosDb,
  TezosErrors,
  TezosRemoteInstances
}
import tech.cryptonomic.conseil.tezos.TezosRemoteInstances.Akka.RemoteContext
import tech.cryptonomic.conseil.tezos.TezosTypes.Block
import tech.cryptonomic.conseil.io.MainOutputs.LorreOutput
import tech.cryptonomic.conseil.util.DatabaseUtil
import tech.cryptonomic.conseil.config.{Custom, Everything, LorreAppConfig, Newest}
import tech.cryptonomic.conseil.config.Platforms

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with TezosErrors with LazyLogging with LorreAppConfig with LorreOutput {
  import com.typesafe.scalalogging.Logger
  import cats.instances.future._
  import cats.instances.list._

  //reads all configuration upstart, will only complete if all values are found
  val config = loadApplicationConfiguration(args)

  //stop if conf is not available
  config.left.foreach { _ => sys.exit(1) }

  //unsafe call, will only be reached if loadedConf is a Right, otherwise the merge will fail
  val LorreAppConfig.CombinedConfiguration(lorreConf, tezosConf, callsConf, streamingClientConf, batchingConf, verbose) = config.merge
  val ignoreProcessFailures = sys.env.get(LORRE_FAILURE_IGNORE_VAR)

  //the dispatcher is visible for all async operations in the following code
  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val dispatcher = system.dispatcher
  implicit val nodeContext = RemoteContext(tezosConf, callsConf, streamingClientConf)

  //how long to wait for graceful shutdown of system components
  val shutdownWait = 10.seconds

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown())

  lazy val db = DatabaseUtil.db

  /* this is needed later to connect api logic to the fetcher,
   * being dependent on the specific effect, i.e. Future, we can't
   * devise a more generic solution, yet...
   */
  private[this] val fetchMaxLevel = ApiOperations.fetchMaxLevel _

  //get all DataFetcheres and RemoteRpc instances needed in scope, for later use by the node methods
  import TezosRemoteInstances.Akka.Futures._
  val blockFetchers = BlocksDataFetchers(batchingConf.blockOperationsConcurrencyLevel)
  val accountFetchers = AccountsDataFetchers(batchingConf.accountConcurrencyLevel)

  import blockFetchers._
  import accountFetchers._

  //create a new operator, with custom configuration
  val node = new NodeOperator {
    override val network: String = nodeContext.tezosConfig.network
    override lazy val logger: Logger = Logger(classOf[NodeOperator])
  }

  /** close resources for application stop */
  private[this] def shutdown(): Unit = {
    logger.info("Doing clean-up")
    db.close()
    val nodeShutdown =
      nodeContext.shutdown()
        .flatMap((_: ShutdownComplete) => system.terminate())

    Await.result(nodeShutdown, shutdownWait)
    logger.info("All things closed")
  }

  /** Tries to fetch blocks head to verify if connection with Tezos node was successfully established */
  @tailrec
  private[this] def checkTezosConnection(): Unit = {
    Try {
      Await.result(node.getBlockHead[Future](), lorreConf.bootupConnectionCheckTimeout)
    } match {
      case Failure(e) =>
        logger.error("Could not make initial connection to Tezos", e)
        Thread.sleep(lorreConf.bootupRetryInterval.toMillis)
        checkTezosConnection()
      case Success(_) =>
        logger.info("Successfully made initial connection to Tezos")
    }
  }

  /** The regular loop, once connection with the node is established */
  @tailrec
  private[this] def mainLoop(iteration: Int): Unit = {
    val noOp = Future.successful(())
    val processing = for {
      _ <- processBlocks(node)
      _ <- processAccounts(node)
      _ <-
        if (iteration % lorreConf.feeUpdateInterval == 0)
          FeeOperations.processTezosAverageFees(lorreConf.numberOfFeesAveraged)
        else
          noOp
    } yield ()

    /* Won't stop Lorre on failure from processing the chain, unless overridden by the environment to halt.
     * Can be used to investigate issues on consistently failing block or account processing.
     * Otherwise, any error will make Lorre proceed as expected by default (stop or wait for next cycle)
     */
    val attemptedProcessing =
      if (ignoreProcessFailures.forall(ignore => ignore == "false" || ignore == "no"))
        processing
      else
        processing.recover {
          //swallow the error and proceed with the default behaviour
          case f@(AccountsProcessingFailed(_, _) | BlocksProcessingFailed(_, _)) =>
            logger.error("Failed processing but will keep on going next cycle", f)
        }

    Await.result(attemptedProcessing, atMost = Duration.Inf)

    lorreConf.depth match {
      case Newest =>
        logger.info("Taking a nap")
        Thread.sleep(lorreConf.sleepInterval.toMillis)
        mainLoop(iteration + 1)
      case _ =>
        logger.info("Synchronization is done")
    }
  }

  //show startup info on the console
  displayInfo(tezosConf)
  if (verbose.on) displayConfiguration(Platforms.Tezos, tezosConf, (LORRE_FAILURE_IGNORE_VAR, ignoreProcessFailures))

  // the main loop sequence, we might want to actually interleave those in case of failure, in the future
  // thus enabling automatic recovering when a node gets temporarily down during lorre's lifetime
  try {
    checkTezosConnection()
    mainLoop(0)
  } finally {shutdown()}

  /**
  * Fetches all blocks not in the database from the Tezos network and adds them to the database.
  * Additionally stores account references that needs updating, too
  */
  private[this] def processBlocks(node: NodeOperator): Future[Done] = {

    /* additional voting data fetching */
    def processVotes(blocks: List[Block]): Future[Option[Int]] = {
      import cats.syntax.traverse._
      import cats.syntax.foldable._
      import cats.syntax.semigroup._
      import cats.instances.list._
      import cats.instances.option._
      import cats.instances.int._
      import slickeffect.implicits._

      node.getVotingDetails[Future](blocks).flatMap {
        case (proposals, bakersBlocks, ballotsBlocks) =>
        //this is a single list
        val writeProposal = TezosDb.writeVotingProposals(proposals)
        //this is a nested list, each block with many baker rolls
        val writeBakers = bakersBlocks.traverse {
          case (block, bakers) => TezosDb.writeVotingBakers(bakers, block)
        }
        //this is a nested list, each block with many ballot votes
        val writeBallots = ballotsBlocks.traverse {
          case (block, ballots) => TezosDb.writeVotingBallots(ballots, block)
        }

        /* combineAll reduce List[Option[Int]] => Option[Int] by summing all ints present
        * |+| is shorthand syntax to sum Option[Int] together using Int's sums
         * Any None in the operands will make the whole operation collapse in a None result
         */
        for {
          storedProposals <- db.run(writeProposal)
          storedBakers <- db.run(writeBakers).map(_.combineAll)
          storedBallots <-  db.run(writeBallots).map(_.combineAll)
        } yield storedProposals |+| storedBakers |+| storedBallots
      }

    }

    logger.info("Processing Tezos Blocks..")

    val blocksToSynchronize = lorreConf.depth match {
      case Newest => node.getBlocksNotInDatabase[Future](fetchMaxLevel)
      case Everything => node.getLatestBlocks[Future]()
      case Custom(n) => node.getLatestBlocks[Future](Some(n), lorreConf.headHash)
    }

    val blockSync = blocksToSynchronize.flatMap {
      blocksWithAccounts =>
        def logBlockOutcome[A]: PartialFunction[Try[Option[A]], Unit] = {
          case Success(accountsCount) =>
            logger.info("Wrote {} blocks to the database, checkpoint stored for{} account updates", blocksWithAccounts.size, accountsCount.fold("")(" " + _))
          case Failure(e) =>
            logger.error("Could not write blocks or accounts checkpoints to the database.", e)
        }

        def logVotingOutcome[A]: PartialFunction[Try[Option[A]], Unit] = {
          case Success(votesCount) =>
            logger.info("Wrote{} voting data records to the database", votesCount.fold("")(" " + _))
          case Failure(e) =>
            logger.error("Could not write voting data to the database", e)
        }

        for {
          _ <- db.run(TezosDb.writeBlocksAndCheckpointAccounts(blocksWithAccounts.toMap)) andThen logBlockOutcome
          _ <- processVotes(blocksWithAccounts.map{case (block, _) => block}) andThen logVotingOutcome
        } yield blocksWithAccounts.size
    }

    blockSync transform {
      case Failure(e) =>
        val error = "Could not fetch blocks from client"
        logger.error(error, e)
        Failure(BlocksProcessingFailed(message = error, e))
      case Success(_) => Success(Done)
    }

  }

  /**
    * Fetches and stores all accounts from the latest blocks stored in the database.
    *
    * NOTE: as the call is now async, it won't stop the application on error as before, so
    * we should evaluate how to handle failed processing
    */
  private[this] def processAccounts(node: NodeOperator): Future[Done] = {
    db.run(TezosDb.getLatestAccountsFromCheckpoint) flatMap {
      checkpoints =>
        val underProcess = checkpoints.keySet

        logger.debug("I loaded all stored account references and will proceed to fetch updated information from the chain")

        def logOutcome[A]: PartialFunction[Try[A], Unit] = {
          case Success(rows) =>
            logger.info("{} accounts were touched on the database.", rows)
          case Failure(e) =>
            logger.error("Could not write accounts to the database")
        }

        node.getAccountsForBlocks[Future](checkpoints).flatMap(
          info =>
            db.run(TezosDb.writeAccounts(info)) andThen logOutcome
        ).andThen {
          //additional cleanup, that can fail with no downsides
          case Success(checkpoints) =>
            val processed = Some(underProcess)
            db.run(TezosDb.cleanAccountsCheckpoint(processed))
        }.transform {
          case Failure(e) =>
            val error = "I failed to fetch accounts from client and update them"
            logger.error(error, e)
            Failure(AccountsProcessingFailed(message = error, e))
          case success => Success(Done)
        }

    }
  }

}