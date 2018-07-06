package tech.cryptonomic.conseil

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations, TezosNodeInterface, TezosNodeOperator}
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Duration, _}
import scala.util.{Failure, Success, Try}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with LazyLogging {

  private val conf = ConfigFactory.load
  val sleepIntervalInSeconds = conf.getInt("lorre.sleepIntervalInSeconds")

  lazy val db = DatabaseUtil.db
  val tezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)

  try {
    while(true) {
      logger.info("Fetching blocks")
      processTezosBlocks()
      logger.info("Fetching accounts")
      processTezosAccounts()
      logger.info("Taking a nap")
      Thread.sleep(sleepIntervalInSeconds * 1000)
    }
  } finally db.close()

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    */
  def processTezosBlocks(): Try[Unit] = {
    logger.info("Processing Tezos Blocks..")
    tezosNodeOperator.getBlocksNotInDatabase("zeronet", followFork = true) match {
      case Success(blocks) =>
        Try {
          val dbFut = TezosDatabaseOperations.writeBlocksToDatabase(blocks, db)
          dbFut onComplete {
            case Success(_) => logger.info(s"Wrote ${blocks.size} blocks to the database.")
            case Failure(e) => logger.error(s"Could not write blocks to the database because $e")
          }
          Await.result(dbFut, Duration.apply(conf.getInt("dbAwaitTimeInSeconds"), SECONDS))
        }
      case Failure(e) =>
        logger.error(s"Could not fetch blocks from client because $e")
        throw e
    }
  }

  /**
    * Fetches and stores all accounts from the latest block stored in the database.
    */
  def processTezosAccounts(): Try[Unit] = {
    logger.info("Processing latest Tezos accounts data..")
    tezosNodeOperator.getLatestAccounts("zeronet") match {
      case Success(accountsInfo) =>
        Try {
          val dbFut = TezosDatabaseOperations.writeAccountsToDatabase(accountsInfo, db)
          dbFut onComplete {
            case Success(_) => logger.info(s"Wrote ${accountsInfo.accounts.size} accounts to the database.")
            case Failure(e) => logger.error(s"Could not write accounts to the database because $e")
          }
          Await.result(dbFut, Duration.apply(conf.getInt("dbAwaitTimeInSeconds"), SECONDS))
        }
      case Failure(e) =>
        logger.error(s"Could not fetch accounts from client because $e")
        throw e
    }
  }


}