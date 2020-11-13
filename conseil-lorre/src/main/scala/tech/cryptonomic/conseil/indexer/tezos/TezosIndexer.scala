package tech.cryptonomic.conseil.indexer.tezos

import akka.Done
import akka.actor.{ActorSystem, Terminated}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import com.typesafe.scalalogging.LazyLogging
import mouse.any._
import cats.instances.future._
import tech.cryptonomic.conseil.common.config.Platforms.{BlockchainPlatform, TezosConfiguration}
import tech.cryptonomic.conseil.common.config._
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.util.DatabaseUtil
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.{TNSContract, TokenContracts}
import tech.cryptonomic.conseil.indexer.config.LorreAppConfig.LORRE_FAILURE_IGNORE_VAR
import tech.cryptonomic.conseil.indexer.LorreIndexer
import tech.cryptonomic.conseil.indexer.LorreIndexer.ShutdownComplete
import tech.cryptonomic.conseil.indexer.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.indexer.config._
import tech.cryptonomic.conseil.indexer.forks.ForkHandler
import tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging
import tech.cryptonomic.conseil.indexer.tezos.TezosErrors._
import tech.cryptonomic.conseil.indexer.tezos.forks.TezosForkInvalidatingAmender
import tech.cryptonomic.conseil.indexer.tezos.processing._
import tech.cryptonomic.conseil.indexer.tezos.processing.AccountsResetHandler.{AccountResetEvents, UnhandledResetEvents}

import scala.annotation.tailrec
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import tech.cryptonomic.conseil.indexer.tezos.forks.TezosForkSearchEngine

/** Class responsible for indexing data for Tezos BlockChain
  * @param ignoreProcessFailures `true` if non-critical errors while fetchign data should simply resume the indexer logic and retry
  * @param lorreConf keeps necessary configuration values
  * @param nodeOperator access to the remote node to read data
  * @param indexedData access to locally indexed data
  * @param blocksProcessor module providing entity-specific operations
  * @param accountsProcessor module providing entity-related operations
  * @param bakersProcessor module providing entity-related operations
  * @param rightsProcessor module providing entity-related operations
  * @param accountsResetHandler module handling global events that could trigger global accounts reprocessing
  * @param forkHandler module for regular verification of fork occurrences in the chain and correction of data
  * @param terminationSequence a function to clean up any pending resource upon shutdown of the indexer
  */
class TezosIndexer private (
    ignoreProcessFailures: Boolean,
    lorreConf: LorreConfiguration,
    nodeOperator: TezosNodeOperator,
    indexedData: TezosIndexedDataOperations,
    blocksProcessor: BlocksProcessor,
    accountsProcessor: AccountsProcessor,
    bakersProcessor: BakersProcessor,
    rightsProcessor: BakingAndEndorsingRightsProcessor,
    accountsResetHandler: AccountsResetHandler,
    forkHandler: ForkHandler[Future, TezosBlockHash],
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
  if (lorreConf.blockRightsFetching.enabled)
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
      accountResetEvents: AccountResetEvents
  ): Unit = {
    val noOp = Future.successful(())
    val processing = for {
      maxLevel <- indexedData.fetchMaxLevel
      reloadedAccountEvents <- processFork(maxLevel)
      unhandled <- accountsResetHandler.applyUnhandledAccountsResets(
        reloadedAccountEvents.getOrElse(accountResetEvents)
      )
      _ <- processTezosBlocks(maxLevel)
      _ <- if (iteration % lorreConf.feeUpdateInterval == 0)
        TezosFeeOperations.processTezosAverageFees(lorreConf.feesAverageTimeWindow)
      else
        noOp
      _ <- rightsProcessor.updateRightsTimestamps()
    } yield Some(unhandled)

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
            None //we have no meaningful data as results, so we return nothing
        } else processing

    //if something went wrong and wasn't recovered, this will actually blow the app
    val unhandledResetEvents = Await.result(attemptedProcessing, atMost = Duration.Inf) match {
      case None => //retry processing the same reset events for accounts
        accountResetEvents
      case Some(UnhandledResetEvents(events)) =>
        events
    }

    lorreConf.depth match {
      case Newest =>
        logger.info("Taking a nap")
        Thread.sleep(lorreConf.sleepInterval.toMillis)
        mainLoop(iteration + 1, unhandledResetEvents)
      case _ =>
        logger.info("Synchronization is done")
    }
  }

  /** Search for any possible forks happened between the last sync cycle and now.
    * If a fork is detected, corrections will be applied.
    *
    * @param maxIndexedLevel how far has the indexer gone
    * @return the actual AccountResetEvents still to be processed, if a fork happened, else no meaningful value
    */
  private def processFork(maxIndexedLevel: BlockLevel): Future[Option[AccountResetEvents]] = {
    lazy val emptyOutcome = Future.successful(Option.empty)
    //nothing to check if no block was indexed yet
    if (maxIndexedLevel != indexedData.defaultBlockLevel)
      forkHandler.handleFork(maxIndexedLevel).flatMap {
        case None =>
          logger.debug(s"No fork detected up to $maxIndexedLevel")
          emptyOutcome
        case Some((forkId, invalidations)) =>
          logger.warn(
            s"A fork was detected somewhere before the currently indexed level $maxIndexedLevel. $invalidations entries were invalidated and connected to fork $forkId"
          )
          /* locally processed events were invalidated on db, we need to reload them afresh */
          accountsResetHandler
            .unprocessedResetRequestLevels(lorreConf.chainEvents)
            .map(Some(_))

      } else emptyOutcome
  }

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    * Additionally stores account references that needs updating, too
    *
    * @param maxIndexedLevel the highest level reached locally
    */
  private def processTezosBlocks(maxIndexedLevel: BlockLevel): Future[Done] = {
    import cats.instances.future._
    import cats.syntax.flatMap._

    logger.info("Processing Tezos Blocks..")

    val blockPagesToSynchronize = lorreConf.depth match {
      case Newest => nodeOperator.getBlocksNotInDatabase(maxIndexedLevel)
      case Everything => nodeOperator.getLatestBlocks()
      case Custom(n) => nodeOperator.getLatestBlocks(Some(n), lorreConf.headHash.map(TezosBlockHash))
    }

    /* collects the hashes of the blocks in the results */
    def extractProcessedHashes(fetched: nodeOperator.BlockFetchingResults): Set[TezosBlockHash] =
      fetched.map {
        case (block, _) => block.data.hash
      }.toSet

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
                      accountsProcessor.markBakerAccounts(extractProcessedHashes(fetchingResults)) >>
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
    val accountResetsToHandle =
      Await.result(
        accountsResetHandler.unprocessedResetRequestLevels(lorreConf.chainEvents),
        atMost = 5.seconds
      )
    mainLoop(0, accountResetsToHandle)
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
    val indexedData = new TezosIndexedDataOperations(db)

    /* collects data from the remote tezos node */
    val nodeOperator = new TezosNodeOperator(
      new TezosNodeInterface(conf, callsConf, streamingClientConf),
      selectedNetwork,
      batchingConf
    )

    /* provides operations to handle rights to bake and endorse blocks */
    val rightsProcessor = new BakingAndEndorsingRightsProcessor(
      nodeOperator,
      indexedData,
      db,
      lorreConf.blockRightsFetching
    )

    /* handles standard accounts data */
    val accountsProcessor =
      new AccountsProcessor(nodeOperator, indexedData, batchingConf, lorreConf.blockRightsFetching)

    /* handles wide-range accounts refresh due to occasional special events */
    val accountsResetHandler = new AccountsResetHandler(db, indexedData)

    /* handles bakers data */
    val bakersProcessor = new BakersProcessor(nodeOperator, db, batchingConf, lorreConf.blockRightsFetching)

    /* Reads csv resources to initialize db tables and smart contracts objects */
    def parseCSVConfigurations(): TokenContracts = {
      /* This will derive automatically all needed implicit arguments to convert csv rows into a proper table row
       * Using generic representations for case classes, provided by the `shapeless` library, it will create the
       * appropriate `kantan.csv.Decoder` for
       *  - headers, as extracted by the case class definition which, should match name and order of the csv header row
       *  - table row types, which can be converted to shapeless HLists, which so happens to be of any case class.
       *
       * What shapeless does, is provide an automatic conversion, from a case class to a typed generic list of values,
       * where each element has a type corresponding to a field in the case class, and a "label" that is
       * a sort of string extracted at compile-time via macros, to figure out the field names.
       * Such special list, is a `shapeless.HList`, or heterogeneous list, sort of a dynamic tuple,
       * to be built by adding/removing individual elements in the list, recursively.
       * This allows generic libraries like kantan to define codecs for generic HLists and,
       * using shapeless, to adapt any case class to his specific HList, at compile-time.
       *
       * For additional information, refer to the project wiki, and
       * - http://nrinaudo.github.io/kantan.csv/
       * - http://www.shapeless.io/
       */
      import kantan.csv.generic._
      //we add any missing implicit decoder for column types, not provided by default from the library
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

    /* read known token smart contracts from configuration, which represents crypto-assets internal to the chain
     * along with other static definitions to save in registry tables
     */
    implicit val tokens: TokenContracts = parseCSVConfigurations()

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

    /* A single component will provide all required search functions for the fork-handler */
    val forkSearchEngine = new TezosForkSearchEngine(
      nodeOps = nodeOperator,
      indexedOps = indexedData
    )

    /* Handles almost every detail of the process when a fork happens,
     * apart from the detail of updating the global chain events to
     * be processed.
     * Those are kept by TezosIndexer in memory, therefore they need to
     * be refreshed from persistent storage after the invalidation.
     */
    val forkHandler: ForkHandler[Future, TezosBlockHash] =
      new ForksProcessor(
        nodeSearch = forkSearchEngine.idsNodeSearch,
        nodeDataSearch = forkSearchEngine.blocksNodeSearch,
        indexerSearch = forkSearchEngine.idsIndexerSearch,
        amender = TezosForkInvalidatingAmender(db)
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
      indexedData,
      blocksProcessor,
      accountsProcessor,
      bakersProcessor,
      rightsProcessor,
      accountsResetHandler,
      forkHandler,
      gracefulTermination
    )
  }
}
