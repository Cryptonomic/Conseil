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
import tech.cryptonomic.conseil.common.tezos.Tables
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
import tech.cryptonomic.conseil.indexer.tezos.processing._

import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/** Class responsible for indexing data for Tezos BlockChain */
class TezosIndexer private (
    ignoreProcessFailures: Boolean,
    lorreConf: LorreConfiguration,
    nodeOperator: TezosNodeOperator,
    blocksProcessor: BlocksProcessor,
    accountsProcessor: AccountsProcessor,
    bakersProcessor: BakersProcessor,
    rightsProcessor: BakingAndEndorsingRightsProcessor,
    accountsEventsProcessor: AccountsEventsProcessor,
    terminationSequence: () => Future[ShutdownComplete]
)(
    implicit
    system: ActorSystem,
    materializer: ActorMaterializer,
    dispatcher: ExecutionContext
) extends LazyLogging
    with LorreIndexer
    with LorreProgressLogging {

  /** Schedules method for fetching baking rights */
  system.scheduler.schedule(lorreConf.blockRightsFetching.initDelay, lorreConf.blockRightsFetching.interval)(
    rightsProcessor.writeFutureRights()
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
      accountsRefreshLevels: AccountsEventsProcessor.AccountUpdatesEvents
  ): Unit = {
    val noOp = Future.successful(())
    val processing = for {
      nextRefreshes <- accountsEventsProcessor.processAccountRefreshes(accountsRefreshLevels)
      _ <- processTezosBlocks()
      _ <- if (iteration % lorreConf.feeUpdateInterval == 0)
        TezosFeeOperations.processTezosAverageFees(lorreConf.numberOfFeesAveraged)
      else
        noOp
      _ <- rightsProcessor.updateRightsTimestamps()
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
            blocksProcessor
              .processBlocksPage(fetchingResults)
              .flatTap(
                _ =>
                  accountsProcessor.processTezosAccountsCheckpoint() >>
                      bakersProcessor.processTezosBakersCheckpoint() >>
                      rightsProcessor.processBakingAndEndorsingRights(fetchingResults)
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
        accountsEventsProcessor.unprocessedLevelsForRefreshingAccounts(lorreConf.chainEvents),
        atMost = 5.seconds
      )
    mainLoop(0, accountRefreshesToRun)
  }

  override def stop(): Future[ShutdownComplete] = terminationSequence()

}

object TezosIndexer extends LazyLogging {

  /** * Creates the Indexer which is dedicated for Tezos BlockChain */
  def fromConfig(
      lorreConf: LorreConfiguration,
      conf: TezosConfiguration,
      callsConf: NetworkCallsConfiguration,
      streamingClientConf: HttpStreamingConfiguration,
      batchingConf: BatchFetchConfiguration
  ): LorreIndexer = {
    val selectedNetwork = conf.network

    implicit val system: ActorSystem = ActorSystem("lorre-tezos-indexer")
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    implicit val dispatcher: ExecutionContext = system.dispatcher

    val ignoreProcessFailuresOrigin: Option[String] = sys.env.get(LORRE_FAILURE_IGNORE_VAR)
    val ignoreProcessFailures: Boolean =
      ignoreProcessFailuresOrigin.exists(ignore => ignore == "true" || ignore == "yes")

    /* Here we collect all internal service operations and resources, needed to run the indexer */
    val db = DatabaseUtil.lorreDb
    val indexedData = new TezosIndexedDataOperations

    /* collects data from the remote tezos node */
    val nodeOperator = new TezosNodeOperator(
      new TezosNodeInterface(conf, callsConf, streamingClientConf),
      selectedNetwork,
      batchingConf,
      indexedData
    )

    /* provides operations to handle rights to bake and endorse blocks */
    val rightsProcessor = new BakingAndEndorsingRightsProcessor(
      db,
      lorreConf.blockRightsFetching,
      nodeOperator,
      indexedData
    )

    /* handles standard accounts data */
    val accountsProcessor = new AccountsProcessor(nodeOperator, db, batchingConf, lorreConf.blockRightsFetching)

    /* handles occasional global accounts refreshes due to very specific events */
    val accountsEventsProcessor = new AccountsEventsProcessor(db, indexedData)

    /* handles bakers data */
    val bakersProcessor = new BakersProcessor(nodeOperator, db, batchingConf, lorreConf.blockRightsFetching)

    /* Reads csv resources to initialize db tables and smart contracts objects */
    def initAnyCsvConfig(): TokenContracts = {
      import kantan.csv.generic._
      import tech.cryptonomic.conseil.common.util.ConfigUtil.Csv._

      /* Inits tables with values from CSV files */
      TezosDb.initTableFromCsv(db, Tables.KnownAddresses, selectedNetwork)
      TezosDb.initTableFromCsv(db, Tables.BakerRegistry, selectedNetwork)

      /* Here we want to initialize the registered tokens and additionally get the token data back
       * since it's needed to process calls to the same token smart contracts as the chain evolves
       */
      val tokenContracts: TokenContracts = {

        val tokenContractsFuture =
          TezosDb.initTableFromCsv(db, Tables.RegisteredTokens, selectedNetwork).map {
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

      //return the contracts definitions
      tokenContracts
    }

    /* read known token smart contracts from configuration, which represents crypto-currencies internal to the chain
     * along with other static definitions to save in registry tables
     */
    implicit val tokens: TokenContracts = initAnyCsvConfig()

    /* This is a smart contract acting as a Naming Service which associates accounts hashes to registered memorable names.
     * It's read from configuration and includes the possibility that none is actually defined
     */
    implicit val tns: TNSContract =
      conf.tns match {
        case None =>
          logger.warn("No configuration found to initialize TNS for {}.", selectedNetwork)
          TNSContract.noContract
        case Some(conf) =>
          TNSContract.fromConfig(conf)
      }

    //build operations on tns based on the implicit contracts defined before
    val tnsOperations = new TezosNamesOperations(tns, nodeOperator)

    /* this is the principal data processor, handling paginated blocks, and the correlated data within */
    val blocksProcessor = new BlocksProcessor(
      nodeOperator,
      db,
      tnsOperations,
      accountsProcessor,
      bakersProcessor
    )

    /* the shutdown sequence to free resources */
    val gracefulTermination = () =>
      for {
        _ <- Future.successful(db.close())
        _: ShutdownComplete <- nodeOperator.node.shutdown()
        _: Terminated <- system.terminate()
      } yield ShutdownComplete

    new TezosIndexer(
      ignoreProcessFailures,
      lorreConf,
      nodeOperator,
      blocksProcessor,
      accountsProcessor,
      bakersProcessor,
      rightsProcessor,
      accountsEventsProcessor,
      gracefulTermination
    )
  }
}
