package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.Done
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{FeeOperations, ShutdownComplete, TezosErrors, TezosNodeInterface, TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil
import tech.cryptonomic.conseil.config.{Custom, Everything, LorreAppConfig, Newest}

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with TezosErrors with LazyLogging with LorreAppConfig {

  //reads all configuration upstart, will only complete if all values are found
  val config = loadApplicationConfiguration(args)

  //stop if conf is not available
  config.left.foreach { _ => sys.exit(1) }

  //unsafe call, will only be reached if loadedConf is a Right
  val LorreAppConfig.CombinedConfiguration(lorreConf, tezosConf, callsConf, streamingClientConf, batchingConf) = config.merge

  //the dispatcher is visible for all async operations in the following code
  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val dispatcher = system.dispatcher

  //how long to wait for graceful shutdown of system components
  val shutdownWait = 10.seconds

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown())

  lazy val db = DatabaseUtil.db
  val tezosNodeOperator = new TezosNodeOperator(new TezosNodeInterface(tezosConf, callsConf, streamingClientConf), batchingConf)

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

  @tailrec
  def mainLoop(iteration: Int): Unit = {
    val noOp = Future.successful(())
    val processing = for {
      _ <- processTezosBlocks()
      _ <- processTezosAccounts()
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
      if (sys.env.get("LORRE_FAILURE_IGNORE").forall(ignore => ignore == "false" || ignore == "no"))
        processing
      else
        processing.recover {
          //swallow the error and proceed with the default behaviour
          case f@(AccountsProcessingFailed(_, _) | BlocksProcessingFailed(_, _)) => ()
        }

    Await.result(attemptedProcessing, atMost = Duration.Inf)

    tezosConf.depth match {
      case Newest =>
        logger.info("Taking a nap")
        Thread.sleep(lorreConf.sleepInterval.toMillis)
        mainLoop(iteration + 1)
      case _ =>
        logger.info("Synchronization is done")
    }
  }

  logger.info("""
    | =========================***=========================
    |  Lorre v.{}
    |  {}
    | =========================***=========================
    |
    |  About to start processing data on the {} network
    |""".stripMargin,
    BuildInfo.version,
    BuildInfo.gitHeadCommit.fold("")(hash => s"[commit-hash: ${hash.take(7)}]"),
    tezosConf.network
  )

  /**
    * Tries to fetch blocks head to verify if connection with Tezos node was successfully established
    */
  @tailrec
  def checkConnection(): Unit = {
    Try {
      Await.result(tezosNodeOperator.getBlockHead(tezosConf.network), lorreConf.connectionCheckTimeout)
    } match {
      case Failure(e) =>
        logger.error("Could not connect to the Tezos node", e)
        Thread.sleep(lorreConf.retryInterval.toMillis)
        checkConnection()
      case Success(_) =>
        logger.info("Successfully connected to the Tezos node")
    }
  }

  try {
    checkConnection()
    mainLoop(0)
  } finally {shutdown()}

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    * Additionally stores account references that needs updating, too
    */
  def processTezosBlocks(): Future[Done] = {
    logger.info("Processing Tezos Blocks..")

    val blockPagesToSynchronize = tezosConf.depth match {
      case Newest => tezosNodeOperator.getBlocksNotInDatabase(tezosConf.network)
      case Everything => tezosNodeOperator.getLatestBlocks(tezosConf.network)
      case Custom(n) => tezosNodeOperator.getLatestBlocks(tezosConf.network, Some(n))
    }

    /* will store a single page of block results */
    def processBlocksPage(results: Future[tezosNodeOperator.BlockFetchingResults]): Future[Int] =
      results.flatMap {
        blocksWithAccounts =>

          def logOutcome[A](outcome: Future[Option[A]]): Future[Option[A]] = outcome.andThen {
            case Success(accountsCount) =>
              logger.info("Wrote {} blocks to the database, checkpoint stored for{} account updates", blocksWithAccounts.size, accountsCount.fold("")(" " + _))
            case Failure(e) =>
              logger.error(s"Could not write blocks or accounts checkpoints to the database because $e")
          }

          logOutcome(db.run(TezosDb.writeBlocksAndCheckpointAccounts(blocksWithAccounts.toMap)))
            .map(_ => blocksWithAccounts.size)

      }


    blockPagesToSynchronize.flatMap {
      // Fails the whole process if any page processing fails
      case (pages, total) => Future.fromTry(Try {
        val start = System.nanoTime()

        def logProgress(processed: Int) = {
          val elapsed = System.nanoTime() - start
          val progress = processed.toDouble/total
          logger.info("Completed processing {}% of total requested blocks", "%.2f".format(progress * 100))

          val etaMins = Duration(scala.math.ceil(elapsed / progress) - elapsed, NANOSECONDS).toMinutes
          if (processed < total && etaMins > 1) logger.info("Estimated time to finish is around {} minutes", etaMins)

          processed
        }

        /* Process in a strictly sequential way, to avoid opening an unbounded number of requests on the http-client.
        * The size and partition of results is driven by the NodeOperator itself, were each page contains a
        * "thunked" future of the result.
        * Such future will be actually started only as the page iterator is scanned, one element at the time
        */
        pages.foldLeft(0) {
          (processed, nextPage) =>
            //wait for each page to load, before looking at the next and implicitly start the new computation
            logProgress(processed + Await.result(processBlocksPage(nextPage), atMost = 5 minutes))
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
    * Fetches and stores all accounts from the latest blocks stored in the database.
    *
    * NOTE: as the call is now async, it won't stop the application on error as before, so
    * we should evaluate how to handle failed processing
    */
  def processTezosAccounts(): Future[Done] = {
    logger.info("Processing latest Tezos accounts data... no estimate available.")

    def logOutcome[A](outcome: Future[A]): Future[A] = outcome.andThen {
      case Success(rows) =>
        logger.info("{} accounts were touched on the database.", rows)
      case Failure(e) =>
        logger.error("Could not write accounts to the database")
    }

    val saveAccounts = for {
      checkpoints <- db.run(TezosDb.getLatestAccountsFromCheckpoint)
      accountsInfo <- tezosNodeOperator.getAccountsForBlocks(tezosConf.network, checkpoints)
      _ <- logOutcome(db.run(TezosDb.writeAccounts(accountsInfo)))
    } yield checkpoints

    saveAccounts.andThen {
      //additional cleanup, that can fail with no downsides
      case Success(checkpoints) =>
        val processed = Some(checkpoints.keySet)
        db.run(TezosDb.cleanAccountsCheckpoint(processed))
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

}