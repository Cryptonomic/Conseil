
package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos._
import tech.cryptonomic.conseil.util.DatabaseUtil

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with LazyLogging {

  //keep this import here to make it evident where we spawn our async code
  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val dispatcher = system.dispatcher

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
  val tezosNodeOperator = new TezosNodeOperator(TezosNodeInterface())

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown())

  private[this] def shutdown(): Unit = {
    logger.info("Doing clean-up")
    db.close()
    Await.result(system.terminate(), 10.seconds)
    logger.info("All things closed")
  }

  @tailrec
  def mainLoop(iteration: Int): Unit = {
    val noOp = Future.successful(())
    val processing = for {
      _ <- processTezosBlocks()
      _ <- processTezosAccounts()
      _ <-
        if (iteration % feeUpdateInterval == 0)
          FeeOperations.processTezosAverageFees()
        else
          noOp
        _ <-
        if (iteration % purgeAccountsInterval == 0)
          TezosDatabaseOperations.purgeOldAccounts()
        else
          noOp
    } yield ()
    Await.ready(processing, atMost = Duration.Inf)
    logger.info("Taking a nap")
    Thread.sleep(sleepIntervalInSeconds * 1000)
    mainLoop(iteration + 1)
  }

  logger.info("About to start processing on the {} network", network)

  try {mainLoop(0)} finally {shutdown()}

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    */
  def processTezosBlocks(): Future[Unit] = {
    logger.info("Processing Tezos Blocks...")
    val stored = tezosNodeOperator.getBlocksNotInDatabase(network, followFork = true).flatMap {
        blocks =>
          TezosDatabaseOperations.writeBlocksToDatabase(blocks, db).andThen {
              case Success(_) =>
                logger.info("Wrote {} blocks to the database", blocks.size)
              case Failure(e) => logger.error(s"Could not write blocks to the database because $e")
            }
      }

    stored.failed.foreach( e => logger.error("Could not fetch blocks from client", e))

    stored
  }

  /**
    * Fetches and stores all accounts from the latest block stored in the database.
    */
  def processTezosAccounts(): Future[Unit] = {
    logger.info("Processing latest Tezos accounts data..")
    tezosNodeOperator.getLatestAccounts(network) transform {
      case Success(accountsInfo) =>
        Try {
          val dbFut = TezosDatabaseOperations.writeAccountsToDatabase(accountsInfo, db)
          dbFut onComplete {
            case Success(_) => logger.info(s"Wrote ${accountsInfo.accounts.size} accounts to the database.")
            case Failure(e) => logger.error(s"Could not write accounts to the database because $e")
          }
          Await.result(dbFut, Duration.Inf)
        }
      case Failure(e) =>
        logger.error("Could not fetch accounts from client", e)
        throw e
    }
  }





}