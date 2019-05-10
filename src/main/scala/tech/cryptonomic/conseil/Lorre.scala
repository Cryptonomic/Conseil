package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{
  AccountsDataFetchers,
  ApiOperations,
  BlocksDataFetchers,
  FeeOperations,
  NodeOperator,
  ShutdownComplete,
  TezosDatabaseOperations => TezosDb,
  TezosErrors
}
import tech.cryptonomic.conseil.tezos.TezosRemoteInstances.Akka.RemoteContext
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.io.MainOutputs.LorreOutput
import tech.cryptonomic.conseil.util.DatabaseUtil
import tech.cryptonomic.conseil.util.EffectsUtil.toIO
import tech.cryptonomic.conseil.config.{Custom, Everything, LorreAppConfig, Newest}
import tech.cryptonomic.conseil.config.Platforms

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.{Stream => _}

import cats.effect.{ContextShift, IO}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with TezosErrors with LazyLogging with LorreAppConfig with LorreOutput {
  import com.typesafe.scalalogging.Logger
  import cats.instances.list._
  import cats.syntax.applicative._
  import cats.syntax.apply._
  import cats.syntax.traverse._

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
  implicit val contextShift: ContextShift[IO] = IO.contextShift(dispatcher)

  //how long to wait for graceful shutdown of system components
  val shutdownWait = 10.seconds

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown())

  lazy val db = DatabaseUtil.db

  /* this is needed later to connect api logic to the fetcher,
   * being dependent on the specific effect -- i.e. Future, IO -- we can't
   * devise a more generic solution, yet...
   */
  private[this] val fetchMaxLevel: IO[Int] = toIO(ApiOperations.fetchMaxLevel())

  /* Get all DataFetcheres and RpcHandler instances implicitly in scope,
   * so that they're available for later use by the node methods requiring them.
   * First we create new instances of the traits containing them, then we import
   * the content with a wildcard import
   */
  val blockFetchers = BlocksDataFetchers(nodeContext)
  val accountFetchers = AccountsDataFetchers(nodeContext)

  import blockFetchers._
  import accountFetchers._

  //create a new node operator, with custom configuration
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
      Await.result(node.getBlockHead[IO].unsafeToFuture, lorreConf.bootupConnectionCheckTimeout)
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
    val processing = for {
      _ <- processBlocks(node)
      _ <- toIO(FeeOperations.processTezosAverageFees(lorreConf.numberOfFeesAveraged))
            .whenA(iteration % lorreConf.feeUpdateInterval == 0)
    } yield ()

    /* Won't stop Lorre on failure from processing the chain, unless overridden by the environment to halt.
     * Can be used to investigate issues on consistently failing block or account processing.
     * Otherwise, any error will make Lorre proceed as expected by default (stop or wait for next cycle)
     */
    val attemptedProcessing =
      if (ignoreProcessFailures.forall(ignore => ignore == "false" || ignore == "no"))
        processing
      else
        processing.handleErrorWith {
          //swallow the error and proceed with the default behaviour
          case f@(AccountsProcessingFailed(_, _) | BlocksProcessingFailed(_, _)) =>
            IO(logger.error("Failed processing but will keep on going next cycle", f))
        }

    Await.result((attemptedProcessing).unsafeToFuture(), atMost = Duration.Inf)

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
  if (verbose.on) displayConfiguration(Platforms.Tezos, tezosConf, lorreConf, (LORRE_FAILURE_IGNORE_VAR, ignoreProcessFailures))

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
  private[this] def processBlocks(node: NodeOperator): IO[Unit] = {

    logger.info("Processing Tezos Blocks..")

    def logOutcomes(blocksCount: Int, accountsCount: Option[Int], votesCount: Option[Int]) = IO {
      logger.info("Wrote {} blocks to the database, checkpoint stored for{} account updates", blocksCount, accountsCount.fold("")(" " + _))
      logger.info("Wrote{} voting data records to the database", votesCount.fold("")(" " + _))
    }

    def writeAccounts(node: NodeOperator): IO[Unit] = {
      def logOutcome(accountCounts: Int) = IO(logger.info("{} accounts were touched on the database.", accountCounts))

      for {
        checkpoints  <- toIO(db.run(TezosDb.getLatestAccountsFromCheckpoint)) <* IO(logger.debug("I loaded all stored account references and will proceed to fetch updated information from the chain"))
        underProcess =  checkpoints.keySet
        info         <- node.getAccounts[IO](checkpoints)
        stored       <- toIO(db.run(TezosDb.writeAccounts(info)))
        _            <- toIO(db.run(TezosDb.cleanAccountsCheckpoint(Some(underProcess))))
        _            <- logOutcome(stored)
      } yield ()
    }

    def processVotes(blocks: List[Block]) = for {
      _  <- IO.shift
      votings <- blocks.traverse(node.getVotingDetails[IO])
      written <- (write _).tupled(votings.unzip3)
    } yield written

    def write(
      proposals: List[Voting.Proposal],
      blocksWithBakers: List[(Block, List[Voting.BakerRolls])],
      blocksWithBallots: List[(Block, List[Voting.Ballot])]
    ): IO[Option[Int]] = {
      import slickeffect.implicits._
      import cats.syntax.foldable._
      import cats.syntax.semigroup._
      import cats.instances.option._
      import cats.instances.int._
      import slick.dbio.DBIO

      val writeAllProposals = TezosDb.writeVotingProposals(proposals)
      //this is a nested list, each block with many baker rolls
      val writeAllBakers = blocksWithBakers.traverse[DBIO, Option[Int]]((TezosDb.writeVotingBakers _).tupled)
      //this is a nested list, each block with many ballot votes
      val writeAllBallots = blocksWithBallots.traverse((TezosDb.writeVotingBallots _).tupled)

      /* combineAll reduce List[Option[Int]] => Option[Int] by summing all ints present
      * |+| is shorthand syntax to sum Option[Int] together using Int's sums
      * Any None in the operands will make the whole operation collapse in a None result
      */
      toIO(db.run(for {
          storedProposals <- writeAllProposals
          storedBakers <- writeAllBakers.map(_.combineAll)
          storedBallots <-  writeAllBallots.map(_.combineAll)
        } yield storedProposals |+| storedBakers |+| storedBallots
      ))

    }

    val blocksToSynchronize: IO[node.BlockFetchingResults[IO]] = lorreConf.depth match {
      case Newest => node.getBlocksNotInDatabase[IO](fetchMaxLevel)
      case Everything => node.getLatestBlocks[IO]()
      case Custom(n) => node.getLatestBlocks[IO](Some(n), lorreConf.headHash)
    }

    blocksToSynchronize.flatMap { results =>
      results.chunkN(batchingConf.blockPageSize)
        .evalTap(
          chunk => IO(logger.info("processing chunk {}", chunk))
        )
        .evalMap {
          chunk =>
            val blockCheckPointMap = chunk.toList.toMap

            for {
              accountsStored <- toIO(db.run(TezosDb.writeBlocksAndCheckpointAccounts(blockCheckPointMap)))
              votesStored    <- processVotes(blockCheckPointMap.keys.toList)
              _              <- logOutcomes(blockCheckPointMap.size, accountsStored, votesStored)
              _              <- writeAccounts(node)
            } yield ()
        }
        .compile
        .drain
    }

  }

}