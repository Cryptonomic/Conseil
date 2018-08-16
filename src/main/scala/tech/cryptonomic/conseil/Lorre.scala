package tech.cryptonomic.conseil

import akka.Done
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{FeeOperations, TezosNodeInterface, TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with LazyLogging {

  //re-use the dispatcher pool from the node interface
  implicit val dispatcher = TezosNodeInterface.system.dispatcher

  private val network =
    if (args.length > 0) args(0)
    else {
      Console.err.println("""
      | No tezos network was provided to connect to
      | Please provide a valid network as an argument to the command line""".stripMargin)
      sys.exit(1)
    }

  private val conf = ConfigFactory.load
  private val awaitTimeInSeconds = conf.getInt("dbAwaitTimeInSeconds")
  private val sleepIntervalInSeconds = conf.getInt("lorre.sleepIntervalInSeconds")
  private val feeUpdateInterval = conf.getInt("lorre.feeUpdateInterval")
  private val purgeAccountsInterval = conf.getInt("lorre.purgeAccountsInterval")

  lazy val db = DatabaseUtil.db

  val tezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)

  @tailrec
  def mainLoop(iteration: Int): Unit = {
      processTezosBlocks()
      processTezosAccounts()
      if (iteration % feeUpdateInterval == 0) {
        FeeOperations.processTezosAverageFees()
      }
      if (iteration % purgeAccountsInterval == 0) {
        TezosDb.purgeOldAccounts()
      }
      logger.info("Taking a nap")
      Thread.sleep(sleepIntervalInSeconds * 1000)
      mainLoop(iteration + 1)
  }

  sys.addShutdownHook{
    logger.info("Handling system shutdown")
    tezosNodeOperator.node.shutdown()
    db.close()
  }
  logger.info("About to start processing on the {} network", network)
  try {mainLoop(0)} finally {sys.exit()}


  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    */
  def processTezosBlocks(): Try[Unit] = {
    logger.info("Processing Tezos Blocks..")
    tezosNodeOperator.getBlocksNotInDatabase(network, followFork = true) match {
      case Success(blocks) =>
        Try {
          val dbFut = TezosDb.writeBlocksToDatabase(blocks, db)
          dbFut onComplete {
            case Success(_) => logger.info(s"Wrote ${blocks.size} blocks to the database.")
            case Failure(e) => logger.error(s"Could not write blocks to the database because $e")
          }
          Await.result(dbFut, Duration.Inf)
        }
      case Failure(e) =>
        logger.error("Could not fetch blocks from client", e)
        throw e
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
    tezosNodeOperator.getLatestAccounts(network).flatMap {
      case Some(accountsInfo) =>
        db.run(TezosDb.writeAccountsIO(accountsInfo)).andThen {
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