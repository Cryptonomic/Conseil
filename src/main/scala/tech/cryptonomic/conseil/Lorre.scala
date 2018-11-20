
package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.Done
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{FeeOperations, TezosNodeInterface, TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil
import tech.cryptonomic.conseil.config.LorreAppConfig

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with LazyLogging with LorreAppConfig {

  //reads all configuration upstart, will only complete if all values are found
  val config = loadApplicationConfiguration(args)

  //stop if conf is not available
  config.left.foreach { _ => sys.exit(1) }

  //unsafe call, will only be reached if loadedConf is a Right
  val LorreAppConfig.CombinedConfiguration(lorreConf, tezosConf, sodiumConf, batchingConf) = config.merge

  //the dispatcher is visible for all async operations in the following code
  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val dispatcher = system.dispatcher

  implicit val sodium = sodiumConf

  //how long to wait for graceful shutdown of system components
  private[this] val shutdownWait = 10.seconds

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown)

  lazy val db = DatabaseUtil.db
  val tezosNodeOperator = new TezosNodeOperator(new TezosNodeInterface(tezosConf), batchingConf)

  private[this] def shutdown(): Unit = {
    logger.info("Doing clean-up")
    db.close()
    val nodeShutdown =
      tezosNodeOperator.node
        .shutdown()
        .flatMap(ShutdownComplete => system.terminate())
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
        _ <-
        if (iteration % lorreConf.purgeAccountsInterval == 0)
          purge()
        else
          noOp
    } yield ()

    Await.ready(processing, atMost = Duration.Inf)
    logger.info("Taking a nap")
    Thread.sleep(lorreConf.sleepInterval.toMillis)
    mainLoop(iteration + 1)
  }

  logger.info("About to start processing on the {} network", tezosConf.network)

  try {mainLoop(0)} finally {shutdown()}

  /** purges old accounts */
  def purge(): Future[Done] = {
    val purged = db.run(TezosDb.purgeOldAccounts())

    purged.andThen {
      case Success(howMany) => logger.info("{} accounts where purged from old block levels.", howMany)
      case Failure(e) => logger.error("Could not purge old block-levels accounts", e)
    }.map(_ => Done)
  }

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    */
  def processTezosBlocks(): Future[Done] = {
    logger.info("Processing Tezos Blocks..")
    tezosNodeOperator.getBlocksNotInDatabase(tezosConf.network, followFork = true).flatMap {
      blocks =>
        db.run(TezosDb.writeBlocks(blocks)).andThen {
          case Success(_) => logger.info("Wrote {} blocks to the database", blocks.size)
          case Failure(e) => logger.error(s"Could not write blocks to the database because $e")
        }.map(_ => Done)
    }.andThen {
      case Failure(e) =>
        logger.error("Could not fetch blocks from client", e)
    }
  }

  /**
    * Fetches and stores all accounts from the latest block stored in the database.
    *
    * NOTE: as the call is now async, it won't stop the application on error as before, so
    * we should evaluate how to handle failed processing
    */
  def processTezosAccounts(): Future[Done] = {
    logger.info("Processing latest Tezos accounts data..")
    tezosNodeOperator.getLatestAccounts(tezosConf.network).flatMap {
      case Some(accountsInfo) =>
        db.run(TezosDb.writeAccounts(accountsInfo)).andThen {
          case Success(_) => logger.info("Wrote {} accounts to the database.", accountsInfo.accounts.size)
          case Failure(e) => logger.error("Could not write accounts to the database", e)
        }.map(_ => Done)
      case None =>
        logger.info("No latest block to update, no accounts will be added to the database")
        Future.successful(Done)
    }.andThen {
      case Failure(e) =>
        logger.error("Could not fetch accounts from client", e)
    }
  }

}