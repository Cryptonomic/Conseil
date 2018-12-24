
package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.Done
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{ApiOperations, FeeOperations, TezosErrors, TezosNodeInterface, TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, Block}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with TezosErrors with LazyLogging {

  //keep this import here to make it evident where we spawn our async code
  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val dispatcher = system.dispatcher

  //how long to wait for graceful shutdown of system components
  private[this] val shutdownWait = 10.seconds

  private val (platform, network) =
    if (args.length > 1) (args(0), args(1))
    else {
      Console.err.println("""
      | No tezos platform and/or network was provided to connect to
      | Please provide a valid platform/network as an argument to the command line""".stripMargin)
      sys.exit(1)
    }

  private val conf = ConfigFactory.load
  private val sleepIntervalInSeconds = conf.getInt("lorre.sleepIntervalInSeconds")
  private val feeUpdateInterval = conf.getInt("lorre.feeUpdateInterval")

  lazy val db = Database.forConfig(s"databases.$platform.$network.conseildb")
  val tezosNodeOperator = new TezosNodeOperator(new TezosNodeInterface(), ApiOperations(db))

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown)

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
      accountIds <- processTezosBlocks()
      _ <- processTezosAccounts(accountIds)
      _ <-
        if (iteration % feeUpdateInterval == 0)
          FeeOperations.processTezosAverageFees()
        else
          noOp
    } yield ()

    /* Will stop Lorre on failure from account processing, unless overriden by the environment to keep on running.
     * Temporarily used to avoid losing accounts updates on error, with the expectation that ops will do cleanup
     * Onging work is planned to store the list of touched accounts betweem blokcs and accounts fetching, hence,
     * on failure, the next cycle will resume processing pending account loads
     */
    val processResult =
      if (sys.env.get("LORRE_FAILURE_IGNORE").forall(ignore => ignore == "true" || ignore == "yes"))
        processing
      else
        processing.recover {
          case f @ AccountsProcessingFailed(_, _) => throw f
          case _ => () //swallowed
        }

    Await.result(processResult, atMost = Duration.Inf)
    logger.info("Taking a nap")
    Thread.sleep(sleepIntervalInSeconds * 1000)
    mainLoop(iteration + 1)
  }

  logger.info("About to start processing on the {} network", network)

  try {mainLoop(0)} finally {shutdown()}

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    */
  def processTezosBlocks(): Future[Map[Block, List[AccountId]]] = {
    logger.info("Processing Tezos Blocks..")
    tezosNodeOperator.getBlocksNotInDatabase(network, followFork = true).flatMap {
      blocksWithAccounts =>
        //ignore the account ids for storage
        val blocks= blocksWithAccounts.map { case (block, _) => block }
        db.run(TezosDb.writeBlocks(blocks))
          .andThen {
            case Success(_) => logger.info("Wrote {} blocks to the database", blocks.size)
            case Failure(e) => logger.error(s"Could not write blocks to the database because $e")
          }
          .map(_ => blocksWithAccounts.toMap)
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
  def processTezosAccounts(accountsInvolved: Map[Block, List[AccountId]]): Future[Done] = {
    logger.info("Processing latest Tezos accounts data..")
    tezosNodeOperator.getAccountsForBlocks(network, accountsInvolved).flatMap {
      case accountsInfo =>
        db.run(TezosDb.writeAccounts(accountsInfo)).andThen {
          case Success(rows) =>
            logger.info("{} accounts were touched on the database.", rows)
          case Failure(e) =>
            logger.error("Could not write accounts to the database")
        }
    }.transform {
      case Failure(e) =>
        val error = "I failed to fetch accounts from client and update them"
        logger.error(error, e)
        Failure(AccountsProcessingFailed(message = error, e))
      case success => Success(Done)
    }
  }

}