
package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.Done
import akka.stream.scaladsl._
import akka.stream._
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{FeeOperations, TezosNodeInterface, TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil
import tech.cryptonomic.conseil.tezos.TezosTypes._

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with LazyLogging {

  //keep this import here to make it evident where we spawn our async code
  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val dispatcher = system.dispatcher
  implicit val materializer = ActorMaterializer()

  //how long to wait for graceful shutdown of system components
  private[this] val shutdownWait = 10.seconds

  private val network =
    if (args.length > 0) args(0)
    else {
      Console.err.println("""
      | No tezos network was provided to connect to
      | Please provide a valid network as an argument to the command line""".stripMargin)
      sys.exit(1)
    }

  private val conf = ConfigFactory.load
  private val sleepIntervalInSeconds = conf.getInt("lorre.sleepIntervalInSeconds")
  private val feeUpdateInterval = conf.getInt("lorre.feeUpdateInterval")
  private val purgeAccountsInterval = conf.getInt("lorre.purgeAccountsInterval")

  lazy val db = DatabaseUtil.db
  val tezosNodeOperator = new TezosNodeOperator(new TezosNodeInterface())

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
      _ <- streamTezosBlocks()
      _ <- streamTezosAccounts()
      _ <-
        if (iteration % feeUpdateInterval == 0)
          FeeOperations.processTezosAverageFees()
        else
          noOp
        _ <-
        if (iteration % purgeAccountsInterval == 0)
          purge()
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
  def streamTezosBlocks(): Future[Done] = {
    logger.info("Processing Tezos Blocks..")

    val dbWriteSink: Sink[Seq[Block], Future[Done]] = Flow[Seq[Block]].mapAsync(parallelism = 3) {
      blocks =>
        db.run(TezosDb.writeBlocks(blocks.toList))
          .andThen {
            case Success(_) => logger.info("Wrote {} blocks to the database", blocks.size)
            case Failure(e) => logger.error(s"Could not write blocks to the database because $e")
          }
    }.toMat(Sink.ignore)(Keep.right)

    tezosNodeOperator.streamBlocksNotInDatabase(network)
      .log(
        name = "blocks-reading",
        extract = block => s"received block at level ${block.metadata.header.level}")
      .withAttributes(
        Attributes.logLevels(
          onElement = akka.event.Logging.InfoLevel,
          onFinish = akka.event.Logging.InfoLevel
        )
      ).groupedWithin(100, 10.seconds)
      .runWith(dbWriteSink)

  }

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    */
  def processTezosBlocks(): Future[Done] = {
    logger.info("Processing Tezos Blocks..")
    tezosNodeOperator.getBlocksNotInDatabase(network, followFork = true).flatMap {
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
    tezosNodeOperator.getLatestAccounts(network).flatMap {
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

  def streamTezosAccounts(): Future[Done] = {
    logger.info("Processing latest Tezos accounts data..")
    tezosNodeOperator.streamLatestAccounts(network).flatMap {
      case Some(accountsSource) =>

        val dbWriteSink: Sink[Seq[AccountAtBlock], Future[Done]] = Flow[Seq[AccountAtBlock]].mapAsync(parallelism = 3) {
          accounts =>
            db.run(TezosDb.writeAccounts(accounts.toList))
              .andThen {
                case Success(_) => logger.info("Wrote {} accounts to the database", accounts.size)
                case Failure(e) => logger.error("Could not write accounts to the database because", e)
              }
        }.toMat(Sink.ignore)(Keep.right)

        accountsSource.log(
          name = "accounts-reading",
          extract = account => s"received account id ${account.id}"
        )
        .withAttributes(
          Attributes.logLevels(
            onElement = akka.event.Logging.InfoLevel,
            onFinish = akka.event.Logging.InfoLevel
          )
        ).groupedWithin(100, 30.seconds)
        .runWith(dbWriteSink)

      case None =>
        logger.info("No latest block to update, no accounts will be added to the database")
        Future.successful(Done)
    }.andThen {
      case Failure(e) =>
        logger.error("Could not fetch accounts from client", e)
    }
  }

}