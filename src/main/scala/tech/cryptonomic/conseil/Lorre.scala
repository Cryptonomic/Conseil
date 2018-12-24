
package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.Done
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{TezosErrors, FeeOperations, TezosNodeInterface, TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.util.DatabaseUtil

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

  sealed class Depth()
  case class Everything() extends Depth
  case class Newest() extends Depth
  case class Custom(depth: Int) extends Depth

  case class Config(private val d: Int = -1, networks: Seq[String] = Seq()) {
    def depth = {
      d match {
        case -1 => Everything
        case 0 => Newest
        case n: Int => Custom(n)
      }
    }
  }

  val parser = new scopt.OptionParser[Config]("lorre") {

    arg[String]("network").required().action( (x, c) =>
      c.copy(networks = c.networks :+ x) ).text("which network to use")

    opt[Int]("depth")
      .action((depth, c) => c.copy(d = depth))
      .validate(it => if (it >= -1) success else failure("Value <depth> must be >= -1") )
      .text("how many blocks to synchronize starting with head (use -1 for everything and 0 for only new ones)")

    help("help").text("prints this usage text")
  }

  private val config: Config = parser.parse(args, Config()).getOrElse(sys.exit(1))
  private val network = config.networks.head
  private val depth = config.depth

  private val conf = ConfigFactory.load
  private val sleepIntervalInSeconds = conf.getInt("lorre.sleepIntervalInSeconds")
  private val feeUpdateInterval = conf.getInt("lorre.feeUpdateInterval")

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
      _ <- processTezosBlocks()
      _ <- processTezosAccounts()
      _ <-
        if (iteration % feeUpdateInterval == 0)
          FeeOperations.processTezosAverageFees()
        else
          noOp
    } yield ()

    /* Won't stop Lorre on failure from account processing, unless overriden by the environment to halt.
     * Can be used to investigate issues on consistently failing account processing
     */
    val processResult =
      if (sys.env.get("LORRE_FAILURE_IGNORE").forall(ignore => ignore == "false" || ignore == "no"))
        processing.recover {
          case f @ AccountsProcessingFailed(_, _) => throw f
          case _ => () //swallowed
        }
      else processing

    Await.result(processResult, atMost = Duration.Inf)
    logger.info("Taking a nap")
    Thread.sleep(sleepIntervalInSeconds * 1000)
    mainLoop(iteration + 1)
  }

  logger.info("About to start processing on the {} network", network)

  try {mainLoop(0)} finally {shutdown()}

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    * Additionally stores account references that needs updating, too
    */
  def processTezosBlocks(): Future[Done] = {
    logger.info("Processing Tezos Blocks..")

    val blocksToSynchronize = depth match {
      case Everything => tezosNodeOperator.getAllBlocks(network)
      case Newest => tezosNodeOperator.getBlocksNotInDatabase(network)
      case Custom(n) => tezosNodeOperator.getLastBlocks(network, n)
    }

    blocksToSynchronize.flatMap {
      blocksWithAccounts =>
        db.run(TezosDb.writeBlocksAndCheckpointAccounts(blocksWithAccounts.toMap))
          .andThen {
            case Success(accountsCount) =>
              logger.info("Wrote {} blocks to the database, checkpoint stored for{} account updates", blocksWithAccounts.size, accountsCount.fold("")(" " + _))
            case Failure(e) =>
              logger.error(s"Could not write blocks or accounts checkpoints to the database because $e")
          }
          .map(_ => Done)
    }.andThen {
      case Failure(e) =>
        logger.error("Could not fetch blocks from client", e)
    }
  }

  /**
    * Fetches and stores all accounts from the latest blocks stored in the database.
    *
    * NOTE: as the call is now async, it won't stop the application on error as before, so
    * we should evaluate how to handle failed processing
    */
  def processTezosAccounts(): Future[Done] = {
    logger.info("Processing latest Tezos accounts data..")

    def logOutcome[A](outcome: Future[A]): Future[A] = outcome.andThen {
      case Success(rows) =>
        logger.info("{} accounts were touched on the database.", rows)
      case Failure(e) =>
        logger.error("Could not write accounts to the database")
    }

    val saveAccounts = for {
      checkpoints <- db.run(TezosDb.getLatestAccountsFromCheckpoint)
      accountsInfo <- tezosNodeOperator.getAccountsForBlocks(network, checkpoints)
      accountsStored <- logOutcome(db.run(TezosDb.writeAccounts(accountsInfo)))
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