package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{
  AccountsDataFetchers,
  ApiOperations,
  BlocksDataFetchers,
  FeeOperations,
  NodeOperator,
  TezosDatabaseOperations => TezosDb,
  TezosErrors,
  TezosTypes
}
import tech.cryptonomic.conseil.tezos.TezosRemoteInstances.Akka.{ShutdownComplete, TezosNodeContext}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.io.MainOutputs.LorreOutput
import tech.cryptonomic.conseil.util.DatabaseUtil
import tech.cryptonomic.conseil.util.EffectsUtil.{runDbIO, toIO}
import tech.cryptonomic.conseil.config.{Custom, Everything, LorreAppConfig, Newest}
import tech.cryptonomic.conseil.config.Platforms

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.{Stream => _}

import cats.effect.{ContextShift, IO}
import scala.util.control.NonFatal
import slick.dbio.DBIO

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with TezosErrors with LazyLogging with LorreAppConfig with LorreOutput {
  import cats.instances.list._
  import cats.syntax.applicative._
  import cats.syntax.apply._
  import cats.syntax.functorFilter._
  import cats.syntax.traverse._

  //reads all configuration upstart, will only complete if all values are found

  val config = loadApplicationConfiguration(args)

  //stop if conf is not available
  config.left.foreach { _ =>
    sys.exit(1)
  }

  //unsafe call, will only be reached if loadedConf is a Right, otherwise the merge will fail
  val LorreAppConfig.CombinedConfiguration(
    lorreConf,
    tezosConf,
    timeoutConf,
    streamingClientConf,
    batchingConf,
    verbose
  ) = config.merge
  val ignoreProcessFailures = sys.env.get(LORRE_FAILURE_IGNORE_VAR)

  //the dispatcher is visible for all async operations in the following code
  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val dispatcher = system.dispatcher
  implicit val nodeContext = TezosNodeContext(tezosConf, timeoutConf, streamingClientConf)
  implicit val contextShift: ContextShift[IO] = IO.contextShift(dispatcher)
  implicit val timer = IO.timer(dispatcher)

  //how long to wait for graceful shutdown of system components
  val shutdownWait = 10.seconds

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown())

  //use this to run db-based async operations into IO
  def runOnDb[T](dbAction: DBIO[T]) = runDbIO(DatabaseUtil.db, dbAction)

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
  val node = new NodeOperator(
    network = nodeContext.tezosConfig.network,
    batchConf = batchingConf
  )

  /** close resources for application stop */
  private[this] def shutdown(): Unit = {
    logger.info("Doing clean-up")
    DatabaseUtil.db.close()
    val nodeShutdown =
      nodeContext
        .shutdown()
        .flatMap((_: ShutdownComplete) => system.terminate())

    Await.result(nodeShutdown, shutdownWait)
    logger.info("All things closed")
  }

  /** Tries to fetch blocks head to verify if connection with Tezos node was successfully established */
  @tailrec
  private[this] def checkTezosConnection(): Unit =
    Try {
      Await.result(node.getBareBlockHead[IO].unsafeToFuture, lorreConf.bootupConnectionCheckTimeout)
    } match {
      case Failure(e) =>
        logger.error("Could not make initial connection to Tezos", e)
        Thread.sleep(lorreConf.bootupRetryInterval.toMillis)
        checkTezosConnection()
      case Success(_) =>
        logger.info("Successfully made initial connection to Tezos")
    }

  /** The regular loop, once connection with the node is established */
  @tailrec
  private[this] def mainLoop(iteration: Int): Unit = {
    val processing = for {
      _ <- processBlocks(node)
      _ <- FeeOperations
        .processTezosAverageFees(lorreConf.numberOfFeesAveraged)
        .whenA(iteration % lorreConf.feeUpdateInterval == 0)
    } yield ()

    /* Won't stop Lorre on failure from processing the chain if explicitly set via the environment.
     * Can be useful to skip non-critical failures due to tezos incorrect data or unexpected behaviour.
     * If set, fetching errors will make Lorre proceed as expected by default (stop or wait for next cycle)
     */
    val attemptedProcessing =
      if (ignoreProcessFailures.exists(ignore => ignore.toLowerCase == "true" || ignore.toLowerCase == "yes"))
        processing.handleErrorWith {
          //swallow the error and proceed with the default behaviour
          case f @ (AccountsProcessingFailed(_, _) | BlocksProcessingFailed(_, _) | DelegatesProcessingFailed(_, _)) =>
            IO(logger.error("Failed processing but will keep on going next cycle", f))
        } else
        processing

    lorreConf.depth match {
      case Newest =>
        val cycle = for {
          _ <- attemptedProcessing.timeout(12.hours)
          _ <- IO(logger.info("Taking a nap"))
          _ <- IO.shift
          _ <- timer.sleep(lorreConf.sleepInterval)
        } yield ()
        cycle.unsafeRunSync()
        mainLoop(iteration + 1)
      case _ =>
        val cycle = for {
          _ <- attemptedProcessing.timeout(12.hours)
          _ <- IO(logger.info("Synchronization is done"))
        } yield ()
        cycle.unsafeRunSync()
    }
  }

  //show startup info on the console
  displayInfo(tezosConf)
  if (verbose.on)
    displayConfiguration(Platforms.Tezos, tezosConf, lorreConf, (LORRE_FAILURE_IGNORE_VAR, ignoreProcessFailures))

  // the main loop sequence, we might want to actually interleave those in case of failure, in the future
  // thus enabling automatic recovering when a node gets temporarily down during lorre's lifetime
  try {
    checkTezosConnection()
    mainLoop(0)
  } finally {
    shutdown()
  }

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    * Additionally stores account references that needs updating, too
    */
  private[this] def processBlocks(node: NodeOperator): IO[Unit] = {

    logger.info("Processing Tezos Blocks..")

    def writeAccounts(node: NodeOperator): IO[Unit] = {
      import cats.instances.int._
      def logOutcome(accountCounts: Int) = IO(logger.info("{} accounts were touched on the database.", accountCounts))
      def extractDelegates(taggedAccounts: List[BlockTagged[Map[AccountId, Account]]]) =
        taggedAccounts.map {
          case BlockTagged(blockHash, blockLevel, accountsMap) =>
            import TezosTypes.Syntax._
            val delegateKeys = accountsMap.values.toList
              .mapFilter(_.delegate.value)

            delegateKeys.taggedWithBlock(blockHash, blockLevel)
        }

      (runOnDb(TezosDb.getLatestAccountsFromCheckpoint) <* IO(
            logger.debug(
              "I loaded all stored account references and will proceed to fetch updated information from the chain"
            )
          )).flatMap { checkpoints =>
        val idsBeingProcessed = checkpoints.keySet
        //we process a stream of accounts as they're fetched from the node
        val accountsProcessing = node
          .getAccounts[IO](checkpoints)
          .chunkN(batchingConf.accountPageSize)
          .evalMap { accountsChunk =>
            val accountData = accountsChunk.toList
            runOnDb(TezosDb.writeAccountsAndCheckpointDelegates(accountData, extractDelegates(accountData)))
              .map(_._1) // discard the checkpoints counts, not needed for progress count
          }
          .reduceSemigroup //sums all counts of written rows using the semigroup for ints
          .evalMap(logOutcome)
          .compile
          .drain

        for {
          _ <- accountsProcessing
          _ <- runOnDb(TezosDb.cleanAccountsCheckpoint(Some(idsBeingProcessed)))
          _ <- IO(logger.debug("Done cleaning checkpoint on accounts"))
        } yield ()
      }
    }

    /** Fetches and stores all delegates from the latest blocks stored in the database. */
    def writeDelegates(node: NodeOperator): IO[Unit] = {
      import cats.instances.int._
      def logOutcome(delegatesCounts: Int) =
        IO(logger.info("{} delegates were touched on the database.", delegatesCounts))

      (runOnDb(TezosDb.getLatestDelegatesFromCheckpoint) <* IO(
            logger.debug(
              "I loaded all stored delegate references and will proceed to fetch updated information from the chain"
            )
          )).flatMap { checkpoints =>
        val keysBeingProcessed = checkpoints.keySet
        //we process a stream of accounts as they're fetched from the node
        val delegatesProcessing = node
          .getDelegates[IO](checkpoints)
          .chunkN(batchingConf.accountPageSize)
          .evalMap { delegatesChunk =>
            runOnDb(TezosDb.writeDelegatesAndCopyContracts(delegatesChunk.toList))
          }
          .reduceSemigroup
          .evalMap(logOutcome)
          .compile
          .drain

        for {
          _ <- delegatesProcessing
          _ <- runOnDb(TezosDb.cleanDelegatesCheckpoint(Some(keysBeingProcessed)))
          _ <- IO(logger.debug("Done cleaning checkpoint on delegates"))
        } yield ()
      }
    }

    def processVotes(blocks: List[Block]) =
      for {
        _ <- IO.shift
        votings <- blocks.traverse(node.getVotingDetails[IO])
        written <- (writeVotes _).tupled(votings.unzip3)
      } yield written

    def writeVotes(
      proposals: List[Voting.Proposal],
      blocksWithRolls: List[(Block, List[Voting.BakerRolls])],
      blocksWithBallots: List[(Block, List[Voting.Ballot])]
    ): IO[Option[Int]] = {
      import slickeffect.implicits._
      import cats.syntax.foldable._
      import cats.syntax.semigroup._
      import cats.instances.option._
      import cats.instances.int._

      val writeAllProposals = TezosDb.writeVotingProposals(proposals)
      //this is a nested list, each block with many baker rolls
      val writeAllRolls = blocksWithRolls.traverse {
        case (block, rolls) => TezosDb.writeVotingRolls(rolls, block)
      }
      //this is a nested list, each block with many ballot votes
      val writeAllBallots = blocksWithBallots.traverse {
        case (block, ballots) => TezosDb.writeVotingBallots(ballots, block)
      }

      /* combineAll reduce List[Option[Int]] => Option[Int] by summing all ints present
       * |+| is shorthand syntax to sum Option[Int] together using Int's sums
       * Any None in the operands will make the whole operation collapse in a None result
       */
      runOnDb(
        for {
          storedProposals <- writeAllProposals
          storedRolls <- writeAllRolls.map(_.combineAll)
          storedBallots <- writeAllBallots.map(_.combineAll)
        } yield storedProposals |+| storedRolls |+| storedBallots
      )

    }

    def toBlocksProcessingFailure[T]: Throwable => IO[T] = {
      case NonFatal(err) =>
        val errorMsg = "I failed to fetch the blocks and related data from the client and store it"
        IO(logger.error(errorMsg, err)) *> IO.raiseError(BlocksProcessingFailed(message = errorMsg, err))
      case fatal =>
        val errorMsg =
          "Something serioursly bad happened, probably unrecoverable. Please restart the process once possible"
        IO(logger.error(errorMsg, fatal)) *> IO.raiseError(fatal)
    }

    def toAccountsProcessingFailure[T]: Throwable => IO[T] = {
      case NonFatal(err) =>
        val errorMsg = "I failed to fetch the accounts from the client and update them"
        IO(logger.error(errorMsg, err)) *> IO.raiseError(AccountsProcessingFailed(message = errorMsg, err))
      case fatal =>
        val errorMsg =
          "Something serioursly bad happened, probably unrecoverable. Please restart the process once possible"
        IO(logger.error(errorMsg, fatal)) *> IO.raiseError(fatal)
    }

    def toDelegatesProcessingFailure[T]: Throwable => IO[T] = {
      case NonFatal(err) =>
        val errorMsg = "I failed to fetch the delegates from the client and update them"
        IO(logger.error(errorMsg, err)) *> IO.raiseError(DelegatesProcessingFailed(message = errorMsg, err))
      case fatal =>
        val errorMsg =
          "Something serioursly bad happened, probably unrecoverable. Please restart the process once possible"
        IO(logger.error(errorMsg, fatal)) *> IO.raiseError(fatal)
    }

    def logOutcomes(blocksCount: Int, accountsCount: Option[Int], votesCount: Option[Int]) = IO {
      logger.info(
        "Wrote {} blocks to the database, checkpoint stored for{} account updates",
        blocksCount,
        accountsCount.fold("")(" " + _)
      )
      logger.info("Wrote{} voting data records to the database", votesCount.fold("")(" " + _))
    }

    val blocksToSynchronize: IO[(node.BlockFetchingResults[IO], Int)] = lorreConf.depth match {
      case Newest => node.getBlocksNotInDatabase[IO](fetchMaxLevel)
      case Everything => node.getLatestBlocks[IO]()
      case Custom(n) => node.getLatestBlocks[IO](Some(n), lorreConf.headHash)
    }

    val getNanos: IO[Long] = timer.clock.monotonic(NANOSECONDS)

    def process(streamWithTotal: (node.BlockFetchingResults[IO], Int), startNanos: Long) = {
      val (streamIn, total) = streamWithTotal
      val logProgress = logProcessingProgress("block", total, startNanos) _
      streamIn
        .chunkN(batchingConf.blockPageSize)
        .evalTap(
          chunk => IO(logger.info("I received {} blocks from the node, about to process", chunk.size))
        )
        .evalMap { chunk =>
          val blockCheckPointMap = chunk.toList.toMap

          for {
            accountsStored <- runOnDb(TezosDb.writeBlocksAndCheckpointAccounts(blockCheckPointMap)).handleErrorWith(
              toBlocksProcessingFailure
            )
            votesStored <- processVotes(blockCheckPointMap.keys.toList).handleErrorWith(toBlocksProcessingFailure)
            _ <- logOutcomes(blockCheckPointMap.size, accountsStored, votesStored)
            _ <- writeAccounts(node).handleErrorWith(toAccountsProcessingFailure)
            _ <- writeDelegates(node).handleErrorWith(toDelegatesProcessingFailure)
          } yield chunk.size
        }
        .evalMapAccumulate(0) {
          //keep track of the running total and prints progress wrt the requested blocks
          case (runningTotal, added) =>
            val newTotal = runningTotal + added
            logProgress(newTotal) *> IO((newTotal, ()))
        }
        .compile
        .drain
    }

    /** Keeps track of time passed between different partial checkpoints of some entity processing
      * Designed to be partially applied to set properties of the whole process once, and then only compute partial completion
      *
      * @param entityName a string that will be logged to identify what kind of resource is being processed
      * @param totalToProcess how many entities there were in the first place
      * @param processStartNanos a nano-time from jvm monotonic time, used to identify when the whole processing operation began
      * @param processed how many entities were processed at the current checkpoint
      */
    def logProcessingProgress(
      entityName: String,
      totalToProcess: Int,
      processStartNanos: Long
    )(processed: Int): IO[Unit] =
      getNanos
        .map(_ - processStartNanos)
        .flatMap { elapsed =>
          val progress = processed.toDouble / totalToProcess
          IO.shift *>
            IO(
              logger.info("Completed processing {}% of total requested {}s.", "%.2f".format(progress * 100), entityName)
            ) *>
            IO(
              logger.info(
                "Estimated throughput is {}/min.",
                "%d".format(processed / (Duration(elapsed, NANOSECONDS).toMinutes))
              )
            ).whenA(elapsed > 60e9) *>
            IO(
              logger.info(
                "Estimated time to finish is {} minutes.",
                Duration(scala.math.ceil(elapsed / progress) - elapsed, NANOSECONDS).toMinutes
              )
            ).whenA(processed < totalToProcess)
        }

    //now we can actually bind all IO pieces together
    for {
      startTime <- getNanos
      streamWithTotal <- blocksToSynchronize
      _ <- process(streamWithTotal, startTime)
    } yield ()

  }

}
