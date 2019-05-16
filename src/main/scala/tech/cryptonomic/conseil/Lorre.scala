package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.Done
import mouse.any._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{TezosTypes, FeeOperations, ShutdownComplete, TezosErrors, TezosNodeInterface, TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.tezos.TezosTypes.{BlockTagged, Account, AccountId, Delegate, PublicKeyHash}
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

  //how long to wait for graceful shutdown of system components
  val shutdownWait = 10.seconds

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown())

  lazy val db = DatabaseUtil.db
  val tezosNodeOperator = new TezosNodeOperator(new TezosNodeInterface(tezosConf, callsConf, streamingClientConf), tezosConf.network, batchingConf)

  /** close resources for application stop */
  private[this] def shutdown(): Unit = {
    logger.info("Doing clean-up")
    db.close()
    val nodeShutdown =
      tezosNodeOperator.node
        .shutdown()
        .flatMap((_: ShutdownComplete) => system.terminate())

    Await.result(nodeShutdown, shutdownWait)
    logger.info("All things closed")
  }

  /** Tries to fetch blocks head to verify if connection with Tezos node was successfully established */
  @tailrec
  private[this] def checkTezosConnection(): Unit = {
    Try {
      Await.result(tezosNodeOperator.getBlockHead(), lorreConf.bootupConnectionCheckTimeout)
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
      _ <- processTezosBlocks()
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

  displayInfo(tezosConf)
  if (verbose.on) displayConfiguration(Platforms.Tezos, tezosConf, (LORRE_FAILURE_IGNORE_VAR, ignoreProcessFailures))

  try {
    checkTezosConnection()
    mainLoop(0)
  } finally {shutdown()}

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    * Additionally stores account references that needs updating, too
    */
  private[this] def processTezosBlocks(): Future[Done] = {
    import cats.instances.future._
    import cats.syntax.apply._

    logger.info("Processing Tezos Blocks..")

    val blockPagesToSynchronize = lorreConf.depth match {
      case Newest => tezosNodeOperator.getBlocksNotInDatabase()
      case Everything => tezosNodeOperator.getLatestBlocks()
      case Custom(n) => tezosNodeOperator.getLatestBlocks(Some(n), lorreConf.headHash)
    }

    /* will store a single page of block results */
    def processBlocksPage(results: Future[tezosNodeOperator.BlockFetchingResults]): Future[Int] =
      results.flatMap {
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
            _ <- processVotesForBlocks(blocksWithAccounts.map{case (block, _) => block}) andThen logVotingOutcome
          } yield blocksWithAccounts.size

      }

    blockPagesToSynchronize.flatMap {
      // Fails the whole process if any page processing fails
      case (pages, total) => Future.fromTry(Try {
        //custom progress tracking for blocks
        val logProgress = logProcessingProgress(entityName = "block", totalToProcess = total, processStartNanos = System.nanoTime()) _

        /* Process in a strictly sequential way, to avoid opening an unbounded number of requests on the http-client.
        * The size and partition of results is driven by the NodeOperator itself, where each page contains a
        * "thunked" future of the result.
        * Such future will be actually started only as the page iterator is scanned, one element at the time
        */
        pages.foldLeft(0) {
          (processed, nextPage) =>
            //wait for each page to load, before looking at the next, thus  starting the new computation
            val justDone = Await.result(
              processBlocksPage(nextPage) <*
                processTezosAccounts() <*
                processTezosDelegates(),
              atMost = batchingConf.blockPageProcessingTimeout)
            processed + justDone <| logProgress
        }
      })
    } transform {
      case Failure(e) =>
        val error = "Could not fetch blocks from client"
        logger.error(error, e)
        Failure(BlocksProcessingFailed(message = error, e))
      case Success(_) => Success(Done)
    }

  }

  /**
    * Fetches voting data for the blocks and stores any relevant
    * result into the appropriate database table
    */
  def processVotesForBlocks(blocks: List[TezosTypes.Block]): Future[Option[Int]] = {
    import cats.syntax.traverse._
    import cats.syntax.foldable._
    import cats.syntax.semigroup._
    import cats.instances.list._
    import cats.instances.option._
    import cats.instances.int._
    import slickeffect.implicits._


    tezosNodeOperator.getVotingDetails(blocks).flatMap {
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


  /** Fetches and stores all accounts from the latest blocks stored in the database. */
  private[this] def processTezosAccounts(): Future[Done] = {
    import cats.instances.list._
    import cats.syntax.functorFilter._

    logger.info("Processing latest Tezos data for updated accounts...")

    def logOutcome: PartialFunction[Try[(Int, Option[Int])], Unit] = {
      case Success((accountsRows, delegateCheckpointRows)) =>
        logger.info("{} accounts were touched on the database. Checkpoint stored for{} delegates.", accountsRows, delegateCheckpointRows.fold("")(" " + _))
      case Failure(e) =>
        logger.error("Could not write accounts to the database")
    }

    def processAccountsPage(accountsInfo: Future[List[BlockTagged[Map[AccountId, Account]]]]): Future[Int] =
      accountsInfo.flatMap{
        taggedAccounts =>
          import TezosTypes.Syntax._
          val delegatesCheckpoint = taggedAccounts.map {
            case BlockTagged(blockHash, blockLevel, accountsMap) =>
              val delegateKeys = accountsMap.values
                .toList
                .mapFilter(_.delegate.value)

                delegateKeys.taggedWithBlock(blockHash, blockLevel)
          }
          db.run(TezosDb.writeAccountsAndCheckpointDelegates(taggedAccounts, delegatesCheckpoint))
            .andThen(logOutcome)
            .map(_._1) // discard the checkpoints counts, not needed for progress count
      }

    val saveAccounts = db.run(TezosDb.getLatestAccountsFromCheckpoint) map {
      checkpoints =>
        logger.debug("I loaded all stored account references and will proceed to fetch updated information from the chain")
        val (pages, total) = tezosNodeOperator.getAccountsForBlocks(checkpoints)
        //custom progress tracking for accounts
        val logProgress = logProcessingProgress(entityName = "account", totalToProcess = total, processStartNanos = System.nanoTime()) _

        /* Process in a strictly sequential way, to avoid opening an unbounded number of requests on the http-client.
        * The size and partition of results is driven by the NodeOperator itself, were each page contains a
        * "thunked" future of the result.
        * Such future will be actually started only as the page iterator is scanned, one element at the time
        */
        pages.foldLeft(0) {
          (processed, nextPage) =>
            //wait for each page to load, before looking at the next, thus  starting the new computation
            val justDone = Await.result(processAccountsPage(nextPage), atMost = batchingConf.accountPageProcessingTimeout)
            processed + justDone <| logProgress
        }

        checkpoints
    }

    logger.debug("Selecting all accounts touched in the checkpoint table, this might take a while...")
    saveAccounts.andThen {
      //additional cleanup, that can fail with no downsides
      case Success(checkpoints) =>
        val processed = Some(checkpoints.keySet)
        logger.debug("Cleaning checkpointed accounts..")
        Await.result(db.run(TezosDb.cleanAccountsCheckpoint(processed)), atMost = batchingConf.accountPageProcessingTimeout)
        logger.debug("Done cleaning checkpointed accounts.")
      case _ =>
        ()
    }.transform {
      case Failure(e) =>
        val error = "I failed to fetch accounts from client and update them"
        logger.error(error, e)
        Failure(AccountsProcessingFailed(message = error, e))
      case success => Success(Done)
    }
  }

  /** Fetches and stores all delegates from the latest blocks stored in the database. */
  private[this] def processTezosDelegates(): Future[Done] = {

    logger.info("Processing latest Tezos data for account delegates...")

    def logOutcome: PartialFunction[Try[Int], Unit] = {
      case Success(delegateRows) =>
        logger.info("{} delegates were touched on the database.", delegateRows)
      case Failure(e) =>
        logger.error("Could not write delegates to the database")
    }

    def processDelegatesPage(delegatesInfo: Future[List[BlockTagged[Map[PublicKeyHash, Delegate]]]]): Future[Int] =
      delegatesInfo.flatMap{
        taggedDelegates =>
          db.run(TezosDb.writeDelegatesAndCopyContracts(taggedDelegates))
            .andThen(logOutcome)
      }

    val saveDelegates = db.run(TezosDb.getLatestDelegatesFromCheckpoint) map {
      checkpoints =>
        logger.debug("I loaded all stored delegate references and will proceed to fetch updated information from the chain")
        val (pages, total) = tezosNodeOperator.getDelegatesForBlocks(checkpoints)
        //custom progress tracking for accounts
        val logProgress = logProcessingProgress(entityName = "delegate", totalToProcess = total, processStartNanos = System.nanoTime()) _

        /* Process in a strictly sequential way, to avoid opening an unbounded number of requests on the http-client.
        * The size and partition of results is driven by the NodeOperator itself, were each page contains a
        * "thunked" future of the result.
        * Such future will be actually started only as the page iterator is scanned, one element at the time
        */
        pages.foldLeft(0) {
          (processed, nextPage) =>
            //wait for each page to load, before looking at the next, thus  starting the new computation
            val justDone = Await.result(processDelegatesPage(nextPage), atMost = batchingConf.accountPageProcessingTimeout)
            processed + justDone <| logProgress
        }

        checkpoints
    }

    saveDelegates.andThen {
      //additional cleanup, that can fail with no downsides
      case Success(checkpoints) =>
        val processed = Some(checkpoints.keySet)
        logger.debug("Cleaning checkpointed delegates..")
        Await.result(db.run(TezosDb.cleanDelegatesCheckpoint(processed)), atMost = batchingConf.accountPageProcessingTimeout)
        logger.debug("Done cleaning checkpointed delegates.")
      case _ =>
        ()
    }.transform {
      case Failure(e) =>
        val error = "I failed to fetch delegates from client and update them"
        logger.error(error, e)
        Failure(DelegatesProcessingFailed(message = error, e))
      case success => Success(Done)
    }

  }

  /** Keeps track of time passed between different partial checkpoints of some entity processing
    * Designed to be partially applied to set properties of the whole process once, and then only compute partial completion
    *
    * @param entityName a string that will be logged to identify what kind of resource is being processed
    * @param totalToProcess how many entities there were in the first place
    * @param processStartNanos a nano-time from jvm monotonic time, used to identify when the whole processing operation began
    * @param processed how many entities were processed at the current checkpoint
    */
  private[this] def logProcessingProgress(entityName: String, totalToProcess: Int, processStartNanos: Long)(processed: Int): Unit = {
    val elapsed = System.nanoTime() - processStartNanos
    val progress = processed.toDouble/totalToProcess
    logger.info("Completed processing {}% of total requested {}s", "%.2f".format(progress * 100), entityName)

    val etaMins = Duration(scala.math.ceil(elapsed / progress) - elapsed, NANOSECONDS).toMinutes
    if (processed < totalToProcess && etaMins > 1) logger.info("Estimated time to finish is around {} minutes", etaMins)
  }


}