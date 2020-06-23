package tech.cryptonomic.conseil.indexer.tezos

import akka.Done
import akka.actor.{ActorSystem, Terminated}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.LazyLogging
import mouse.any._
import tech.cryptonomic.conseil.common.config.Platforms.{BlockchainPlatform, TezosConfiguration}
import tech.cryptonomic.conseil.common.config._
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.{TNSContract, TokenContracts}
import tech.cryptonomic.conseil.common.tezos.{Tables, TezosTypes}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.ContractId
import tech.cryptonomic.conseil.common.util.DatabaseUtil
import tech.cryptonomic.conseil.indexer.config.LorreAppConfig.LORRE_FAILURE_IGNORE_VAR
import tech.cryptonomic.conseil.indexer.LorreIndexer
import tech.cryptonomic.conseil.indexer.LorreIndexer.ShutdownComplete
import tech.cryptonomic.conseil.indexer.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.indexer.config.{
  BatchFetchConfiguration,
  Custom,
  Everything,
  HttpStreamingConfiguration,
  LorreConfiguration,
  NetworkCallsConfiguration,
  Newest
}
import tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging
import tech.cryptonomic.conseil.indexer.tezos.TezosErrors._

import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import tech.cryptonomic.conseil.indexer.tezos.processing.BakingAndEndorsingRightsProcessing
import tech.cryptonomic.conseil.indexer.tezos.processing.AccountsEventsProcessing
import tech.cryptonomic.conseil.indexer.tezos.processing.BakersProcessing
import tech.cryptonomic.conseil.indexer.tezos.processing.AccountsProcessing
import tech.cryptonomic.conseil.indexer.tezos.processing.BlocksProcessing

/** * Class responsible for indexing data for Tezos BlockChain */
class TezosIndexer(
    lorreConf: LorreConfiguration,
    tezosConf: TezosConfiguration,
    callsConf: NetworkCallsConfiguration,
    streamingClientConf: HttpStreamingConfiguration,
    batchingConf: BatchFetchConfiguration
) extends LazyLogging
    with LorreIndexer
    with LorreProgressLogging {

  implicit private val system: ActorSystem = ActorSystem("lorre-tezos-indexer")
  implicit private val materializer: ActorMaterializer = ActorMaterializer()
  implicit private val dispatcher: ExecutionContext = system.dispatcher

  private val ignoreProcessFailuresOrigin: Option[String] = sys.env.get(LORRE_FAILURE_IGNORE_VAR)
  private val ignoreProcessFailures: Boolean =
    ignoreProcessFailuresOrigin.exists(ignore => ignore == "true" || ignore == "yes")

  /* Here we collect all internal service operations and resources, needed to run the indexer */
  private val db = DatabaseUtil.lorreDb
  private val indexedData = new TezosIndexedDataOperations

  /* collects data from the remote tezos node */
  private val nodeOperator = new TezosNodeOperator(
    new TezosNodeInterface(tezosConf, callsConf, streamingClientConf),
    tezosConf.network,
    batchingConf,
    indexedData
  )

  /* provides operations to handle rights to bake and endorse blocks */
  private val rightsProcessing = new BakingAndEndorsingRightsProcessing(
    db,
    lorreConf.blockRightsFetching,
    nodeOperator,
    indexedData
  )

  /* handles occasional global accounts refreshes due to very specific events */
  private val accountsSpecialEvents = new AccountsEventsProcessing(db, indexedData)

  /* handles standard accounts data */
  private val accountsProcessing = new AccountsProcessing(nodeOperator, db, batchingConf, lorreConf.blockRightsFetching)

  /* handles bakers data */
  private val bakersProcessing = new BakersProcessing(nodeOperator, db, batchingConf, lorreConf.blockRightsFetching)

  /* read known token smart contracts from configuration, which represents crypto-currencies internal to the chain */
  implicit private val tokens: TokenContracts = initAnyCsvConfig()

  /* Reads csv resources to initialize db tables and smart contracts objects */
  private def initAnyCsvConfig(): TokenContracts = {
    import kantan.csv.generic._
    import tech.cryptonomic.conseil.common.util.ConfigUtil.Csv._
    val tokenContracts: TokenContracts = {

      val tokenContractsFuture =
        TezosDb.initTableFromCsv(db, Tables.RegisteredTokens, tezosConf.network).map {
          case (tokenRows, _) =>
            TokenContracts.fromConfig(
              tokenRows.map {
                case Tables.RegisteredTokensRow(_, tokenName, standard, accountId, _) =>
                  ContractId(accountId) -> standard
              }
            )

        }

      Await.result(tokenContractsFuture, 5.seconds)
    }

    /** Inits tables with values from CSV files */
    TezosDb.initTableFromCsv(db, Tables.KnownAddresses, tezosConf.network)
    TezosDb.initTableFromCsv(db, Tables.BakerRegistry, tezosConf.network)

    //return the contracts definitions
    tokenContracts
  }

  /* This is a smart contract acting as a Naming Service for accounts hashes to registered memorable names
   * It's read from configuration and includes the possibility that none is actually defined
   */
  implicit private val tns: TNSContract =
    tezosConf.tns match {
      case None =>
        logger.warn("No configuration found to initialize TNS for {}.", tezosConf.network)
        TNSContract.noContract
      case Some(conf) =>
        TNSContract.fromConfig(conf)
    }

  //build operations on tns based on the implicit contracts defined before
  private val tnsOperations = new TezosNamesOperations(tns, nodeOperator)

  /* this is the principal data processor, handling paginated blocks, and the correlated data within */
  private val blockProcessing = new BlocksProcessing(
    nodeOperator,
    db,
    tnsOperations,
    accountsProcessing,
    bakersProcessing
  )

  /** Schedules method for fetching baking rights */
  system.scheduler.schedule(lorreConf.blockRightsFetching.initDelay, lorreConf.blockRightsFetching.interval)(
    rightsProcessing.writeFutureRights()
  )

  /** Tries to fetch blocks head to verify if connection with Tezos node was successfully established */
  @tailrec
  private def checkTezosConnection(): Unit =
    Try {
      Await.result(nodeOperator.getBareBlockHead(), lorreConf.bootupConnectionCheckTimeout)
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
  private def mainLoop(
      iteration: Int,
      accountsRefreshLevels: AccountsEventsProcessing.AccountUpdatesEvents
  ): Unit = {
    val noOp = Future.successful(())
    val processing = for {
      nextRefreshes <- accountsSpecialEvents.processAccountRefreshes(accountsRefreshLevels)
      _ <- processTezosBlocks()
      _ <- if (iteration % lorreConf.feeUpdateInterval == 0)
        TezosFeeOperations.processTezosAverageFees(lorreConf.numberOfFeesAveraged)
      else
        noOp
      _ <- rightsProcessing.updateRightsTimestamps()
    } yield Some(nextRefreshes)

    /* Won't stop Lorre on failure from processing the chain, unless overridden by the environment to halt.
     * Can be used to investigate issues on consistently failing block or account processing.
     * Otherwise, any error will make Lorre proceed as expected by default (stop or wait for next cycle)
     */
    val attemptedProcessing =
      if (ignoreProcessFailures)
        processing.recover {
          //swallow the error and proceed with the default behaviour
          case f @ (AccountsProcessingFailed(_, _) | BlocksProcessingFailed(_, _) | BakersProcessingFailed(_, _)) =>
            logger.error("Failed processing but will keep on going next cycle", f)
            None //we have no meaningful response to provide
        } else processing

    //if something went wrong and wasn't recovered, this will actually blow the app
    val updatedLevels = Await.result(attemptedProcessing, atMost = Duration.Inf)

    lorreConf.depth match {
      case Newest =>
        logger.info("Taking a nap")
        Thread.sleep(lorreConf.sleepInterval.toMillis)
        mainLoop(iteration + 1, updatedLevels.getOrElse(accountsRefreshLevels))
      case _ =>
        logger.info("Synchronization is done")
    }
  }

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    * Additionally stores account references that needs updating, too
    */
  private def processTezosBlocks(): Future[Done] = {
    import cats.instances.future._
    import cats.syntax.flatMap._

    logger.info("Processing Tezos Blocks..")

    val blockPagesToSynchronize = lorreConf.depth match {
      case Newest => nodeOperator.getBlocksNotInDatabase()
      case Everything => nodeOperator.getLatestBlocks()
      case Custom(n) => nodeOperator.getLatestBlocks(Some(n), lorreConf.headHash)
    }

    blockPagesToSynchronize.flatMap {
      // Fails the whole process if any page processing fails
      case (pages, total) =>
        //custom progress tracking for blocks
        val logProgress =
          logProcessingProgress(entityName = "block", totalToProcess = total, processStartNanos = System.nanoTime()) _

        // Process each page on his own, and keep track of the progress
        Source
          .fromIterator(() => pages)
          .mapAsync[nodeOperator.BlockFetchingResults](1)(identity)
          .mapAsync(1) { fetchingResults =>
            blockProcessing
              .processBlocksPage(fetchingResults)
              .flatTap(
                _ =>
                  accountsProcessing.processTezosAccountsCheckpoint() >>
                      bakersProcessing.processTezosBakersCheckpoint() >>
                      rightsProcessing.processBakingAndEndorsingRights(fetchingResults)
              )
          }
          .runFold(0) { (processed, justDone) =>
            processed + justDone <| logProgress
          }
    } transform {
      case Failure(accountFailure @ AccountsProcessingFailed(_, _)) =>
        Failure(accountFailure)
      case Failure(delegateFailure @ BakersProcessingFailed(_, _)) =>
        Failure(delegateFailure)
      case Failure(e) =>
        val error = "Could not fetch blocks from client"
        logger.error(error, e)
        Failure(BlocksProcessingFailed(message = error, e))
      case Success(_) => Success(Done)
    }

  }

  override val platform: BlockchainPlatform = Platforms.Tezos

  override def start(): Unit = {
    checkTezosConnection()
    val accountRefreshesToRun =
      Await.result(
        accountsSpecialEvents.unprocessedLevelsForRefreshingAccounts(lorreConf.chainEvents),
        atMost = 5.seconds
      )
    mainLoop(0, accountRefreshesToRun)
  }

  override def stop(): Future[ShutdownComplete] =
    for {
      _ <- Future.successful(db.close())
      _: ShutdownComplete <- nodeOperator.node.shutdown()
      _: Terminated <- system.terminate()
    } yield ShutdownComplete
}

object TezosIndexer extends LazyLogging {

  /** * Creates the Indexer which is dedicated for Tezos BlockChain */
  def fromConfig(
      lorreConf: LorreConfiguration,
      conf: TezosConfiguration,
      callsConf: NetworkCallsConfiguration,
      streamingClientConf: HttpStreamingConfiguration,
      batchingConf: BatchFetchConfiguration
  ): LorreIndexer =
    new TezosIndexer(lorreConf, conf, callsConf, streamingClientConf, batchingConf)

}
