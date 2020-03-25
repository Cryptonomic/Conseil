package tech.cryptonomic.conseil

import java.sql.Timestamp
import java.time.Instant

import akka.actor.ActorSystem
import akka.Done
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.ActorMaterializer
import mouse.any._
import com.typesafe.scalalogging.LazyLogging
import kantan.codecs.strings.StringDecoder
import org.slf4j.LoggerFactory
import slick.lifted.{AbstractTable, TableQuery}
import tech.cryptonomic.conseil.tezos.{
  ApiOperations,
  DatabaseConversions,
  FeeOperations,
  ShutdownComplete,
  Tables,
  TezosErrors,
  TezosNodeInterface,
  TezosNodeOperator,
  TezosTypes,
  TezosDatabaseOperations => TezosDb
}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.io.MainOutputs.LorreOutput
import tech.cryptonomic.conseil.util.{ConfigUtil, DatabaseUtil}
import tech.cryptonomic.conseil.config._
import tech.cryptonomic.conseil.tezos.TezosDatabaseOperations.insertWhenEmpty
import tech.cryptonomic.conseil.tezos.TezosNodeOperator.{FetchRights, LazyPages}
import java.time.format.DateTimeFormatter

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.collection.SortedSet
import tech.cryptonomic.conseil.tezos.TezosTypes.BlockHash

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with TezosErrors with LazyLogging with LorreAppConfig with LorreOutput {

  type AccountUpdatesEvents = SortedSet[(Int, ChainEvent.AccountIdPattern)]

  //reads all configuration upstart, will only complete if all values are found

  val config = loadApplicationConfiguration(args)

  //stop if conf is not available
  config.left.foreach { _ =>
    sys.exit(1)
  }

  //unsafe call, will only be reached if loadedConf is a Right, otherwise the merge will fail
  val LorreAppConfig.CombinedConfiguration(
    lorreConf,
    tezosConf,
    callsConf,
    streamingClientConf,
    batchingConf,
    verbose
  ) = config.merge
  val ignoreProcessFailures = sys.env.get(LORRE_FAILURE_IGNORE_VAR)

  //the dispatcher is visible for all async operations in the following code
  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val dispatcher = system.dispatcher

  //how long to wait for graceful shutdown of system components
  val shutdownWait = 10.seconds

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown())

  lazy val db = DatabaseUtil.lorreDb
  lazy val apiOperations = new ApiOperations

  val tezosNodeOperator = new TezosNodeOperator(
    new TezosNodeInterface(tezosConf, callsConf, streamingClientConf),
    tezosConf.network,
    batchingConf,
    apiOperations
  )

//  /** Inits registered tokens at startup */
//
//
//  import kantan.csv._
//
//  implicit val timestampDecoder: CellDecoder[java.sql.Timestamp] = (e: String) => {
//    val format = DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z (zzzz)")
//    StringDecoder
//      .makeSafe("Instant")(s => Instant.from(format.parse(s)))(e)
//      .map(Timestamp.from)
//      .left
//      .map(x => DecodeError.TypeError(x.message))
//  }
//
//  TezosDb.initTableFromCsv(Tables.RegisteredTokens, tezosConf.network, separator = '|')
//  TezosDb.initTableFromCsv(Tables.KnownAddresses, tezosConf.network)
//  TezosDb.initTableFromCsv(Tables.BakerRegistry, tezosConf.network)

  //  import kantan.csv.generic._
//
//  val tokenRows: List[Tables.RegisteredTokensRow] =
//    ConfigUtil.Csv.readTableRowsFromCsv(Tables.RegisteredTokens, tezosConf.network, separator = '|')
//
//  db.run(TezosDb.insertWhenEmpty(Tables.RegisteredTokens, tokenRows)) andThen {
//      case Success(_) => logger.info(s"Written ${tokenRows.size} registered token rows")
//      case Failure(e) => logger.error("Could not fill registered_tokens table", e)
//    }
  /** Inits tables with values from CSV files */
  import kantan.csv.generic._
  initTableFromCsv(Tables.RegisteredTokens, tezosConf.network, separator = '|')
  initTableFromCsv(Tables.KnownAddresses, tezosConf.network)

  import shapeless._
  import shapeless.ops.hlist._

  import kantan.csv._

  /** Reads and inserts CSV file to the database for the given table */
  def initTableFromCsv[A <: AbstractTable[_], H <: HList](table: TableQuery[A], network: String, separator: Char = ',')(
      implicit hd: HeaderDecoder[A#TableElementType],
      g: Generic.Aux[A#TableElementType, H],
      m: Mapper.Aux[tech.cryptonomic.conseil.util.ConfigUtil.Csv.Trimmer.type, H, H]
  ): Future[Option[ProposalSupporters]] = {
    val rows = ConfigUtil.Csv.readTableRowsFromCsv(table, network, separator)
    db.run(insertWhenEmpty(table, rows)) andThen {
      case Success(_) => logger.info(s"Written ${rows.size} ${table.baseTableRow.tableName} rows")
      case Failure(e) => logger.error(s"Could not fill ${table.baseTableRow.tableName} table", e)
    }
  }

  /** Schedules method for fetching baking rights */
  system.scheduler.schedule(lorreConf.blockRightsFetching.initDelay, lorreConf.blockRightsFetching.interval)(
    writeFutureRights()
  )

  /** Fetches future baking and endorsing rights to insert it into the DB */
  def writeFutureRights(): Unit = {
    val berLogger = LoggerFactory.getLogger("RightsFetcher")

    import cats.implicits._

    implicit val mat = ActorMaterializer()
    berLogger.info("Fetching future baking and endorsing rights")
    val blockHead = tezosNodeOperator.getBareBlockHead()
    val brLevelFut = apiOperations.fetchMaxBakingRightsLevel()
    val erLevelFut = apiOperations.fetchMaxEndorsingRightsLevel()

    (blockHead, brLevelFut, erLevelFut).mapN { (head, brLevel, erLevel) =>
      val headLevel = head.header.level
      val rightsStartLevel = math.max(brLevel, erLevel) + 1
      berLogger.info(
        s"Current Tezos block head level: $headLevel DB stored baking rights level: $brLevel DB stored endorsing rights level: $erLevel"
      )

      val length = DatabaseConversions
        .extractCyclePosition(head.metadata)
        .map { cyclePosition =>
          // calculates amount of future rights levels to be fetched based on cycle_position, cycle_size and amount cycles to fetch
          (lorreConf.blockRightsFetching.cycleSize - cyclePosition) + lorreConf.blockRightsFetching.cycleSize * lorreConf.blockRightsFetching.cyclesToFetch
        }
        .getOrElse(0)

      berLogger.info(s"Level and position to fetch ($headLevel, $length)")
      val range = List.range(Math.max(headLevel + 1, rightsStartLevel), headLevel + length)
      Source
        .fromIterator(() => range.toIterator)
        .grouped(lorreConf.blockRightsFetching.fetchSize)
        .mapAsync(1) { partition =>
          tezosNodeOperator.getBatchBakingRightsByLevels(partition.toList).flatMap { bakingRightsResult =>
            val brResults = bakingRightsResult.values.flatten
            berLogger.info(s"Got ${brResults.size} baking rights")
            db.run(TezosDb.insertBakingRights(brResults.toList))
          }
          tezosNodeOperator.getBatchEndorsingRightsByLevel(partition.toList).flatMap { endorsingRightsResult =>
            val erResults = endorsingRightsResult.values.flatten
            berLogger.info(s"Got ${erResults.size} endorsing rights")
            db.run(TezosDb.insertEndorsingRights(erResults.toList))
          }
        }
        .runWith(Sink.ignore)
    }.flatten
    ()
  }

  /** close resources for application stop */
  private[this] def shutdown(): Unit = {
    logger.info("Doing clean-up")
    db.close()
    val nodeShutdown =
      tezosNodeOperator.node
        .shutdown()
        .flatMap((_: ShutdownComplete) => system.terminate())

    Await.result(nodeShutdown, shutdownWait)
    logger.info("All things closed")
  }

  /** Tries to fetch blocks head to verify if connection with Tezos node was successfully established */
  @tailrec
  private[this] def checkTezosConnection(): Unit =
    Try {
      Await.result(tezosNodeOperator.getBareBlockHead(), lorreConf.bootupConnectionCheckTimeout)
    } match {
      case Failure(e) =>
        logger.error("Could not make initial connection to Tezos", e)
        Thread.sleep(lorreConf.bootupRetryInterval.toMillis)
        checkTezosConnection()
      case Success(_) =>
        logger.info("Successfully made initial connection to Tezos")
    }

  // Finds unprocessed levels for account refreshes (i.e. when there is a need to reload all accounts data from the chain)
  private def unprocessedLevelsForRefreshingAccounts(): Future[AccountUpdatesEvents] =
    lorreConf.chainEvents.collectFirst {
      case ChainEvent.AccountsRefresh(levelsNeedingRefresh) if levelsNeedingRefresh.nonEmpty =>
        db.run(TezosDb.fetchProcessedEventsLevels(ChainEvent.accountsRefresh.render)).map { levels =>
          //used to remove processed events
          val processed = levels.map(_.intValue).toSet
          //we want individual event levels with the associated pattern, such that we can sort them by level
          val unprocessedEvents = levelsNeedingRefresh.toList.flatMap {
            case (accountPattern, levels) => levels.filterNot(processed).sorted.map(_ -> accountPattern)
          }
          SortedSet(unprocessedEvents: _*)
        }
    }.getOrElse(Future.successful(SortedSet.empty))

  /** The regular loop, once connection with the node is established */
  @tailrec
  private[this] def mainLoop(
      iteration: Int,
      accountsRefreshLevels: AccountUpdatesEvents
  ): Unit = {
    val noOp = Future.successful(())
    val processing = for {
      nextRefreshes <- processAccountRefreshes(accountsRefreshLevels)
      _ <- processTezosBlocks()
      _ <- if (iteration % lorreConf.feeUpdateInterval == 0)
        FeeOperations.processTezosAverageFees(lorreConf.numberOfFeesAveraged)
      else
        noOp
    } yield Some(nextRefreshes)

    /* Won't stop Lorre on failure from processing the chain, unless overridden by the environment to halt.
     * Can be used to investigate issues on consistently failing block or account processing.
     * Otherwise, any error will make Lorre proceed as expected by default (stop or wait for next cycle)
     */
    val attemptedProcessing =
      if (ignoreProcessFailures.exists(ignore => ignore == "true" || ignore == "yes"))
        processing.recover {
          //swallow the error and proceed with the default behaviour
          case f @ (AccountsProcessingFailed(_, _) | BlocksProcessingFailed(_, _) | DelegatesProcessingFailed(_, _)) =>
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

  displayInfo(tezosConf)
  if (verbose.on)
    displayConfiguration(Platforms.Tezos, tezosConf, lorreConf, (LORRE_FAILURE_IGNORE_VAR, ignoreProcessFailures))

  try {
    checkTezosConnection()
    val accountRefreshesToRun = Await.result(unprocessedLevelsForRefreshingAccounts(), atMost = 5.seconds)
    mainLoop(0, accountRefreshesToRun)
  } finally {
    shutdown()
  }

  /* Possibly updates all accounts if the current block level is past any of the given ones
   * @param events the relevant levels, each with its own selection pattern, that calls for a refresh
   */
  private def processAccountRefreshes(events: AccountUpdatesEvents): Future[AccountUpdatesEvents] =
    if (events.nonEmpty) {
      for {
        storedHead <- apiOperations.fetchMaxLevel
        updated <- if (events.exists(_._1 <= storedHead)) {
          val (past, toCome) = events.partition(_._1 <= storedHead)
          val (levels, selectors) = past.unzip
          logger.info(
            "A block was reached that requires an update of account data as specified in the configuration file. A full refresh is now underway. Relevant block levels: {}",
            levels.mkString(", ")
          )
          apiOperations.fetchBlockAtLevel(levels.max).flatMap {
            case Some(referenceBlockForRefresh) =>
              val (hashRef, levelRef, timestamp, cycle) =
                (
                  BlockHash(referenceBlockForRefresh.hash),
                  referenceBlockForRefresh.level,
                  referenceBlockForRefresh.timestamp.toInstant(),
                  referenceBlockForRefresh.metaCycle
                )
              db.run(
                  //put all accounts in checkpoint, log the past levels to the db, keep the rest for future cycles
                  TezosDb.refillAccountsCheckpointFromExisting(hashRef, levelRef, timestamp, cycle, selectors.toSet) >>
                      TezosDb.writeProcessedEventsLevels(
                        ChainEvent.accountsRefresh.render,
                        levels.map(BigDecimal(_)).toList
                      )
                )
                .andThen {
                  case Success(accountsCount) =>
                    logger.info(
                      "Checkpoint stored for{} account updates in view of the full refresh.",
                      accountsCount.fold("")(" " + _)
                    )
                  case Failure(err) =>
                    logger.error(
                      "I failed to store the accounts refresh updates in the checkpoint",
                      err
                    )
                }
                .map(_ => toCome) //keep the yet unreached levels and pass them on
            case None =>
              logger.warn(
                "I couldn't find in Conseil the block data at level {}, required for the general accounts update, and this is actually unexpected. I'll retry the whole operation at next cycle.",
                levels.max
              )
              Future.successful(events)
          }
        } else Future.successful(events)
      } yield updated
    } else Future.successful(events)

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    * Additionally stores account references that needs updating, too
    */
  private[this] def processTezosBlocks(): Future[Done] = {
    import cats.instances.future._
    import cats.syntax.flatMap._
    import TezosTypes.Syntax._

    logger.info("Processing Tezos Blocks..")

    val blockPagesToSynchronize = lorreConf.depth match {
      case Newest => tezosNodeOperator.getBlocksNotInDatabase()
      case Everything => tezosNodeOperator.getLatestBlocks()
      case Custom(n) => tezosNodeOperator.getLatestBlocks(Some(n), lorreConf.headHash)
    }

    /* will store a single page of block results */
    def processBlocksPage(
        results: tezosNodeOperator.BlockFetchingResults
    )(implicit mat: ActorMaterializer): Future[Int] = {
      def logBlockOutcome[A]: PartialFunction[Try[Option[A]], Unit] = {
        case Success(accountsCount) =>
          logger.info(
            "Wrote {} blocks to the database, checkpoint stored for{} account updates",
            results.size,
            accountsCount.fold("")(" " + _)
          )
        case Failure(e) =>
          logger.error("Could not write blocks or accounts checkpoints to the database.", e)
      }

      //ignore the account ids for storage, and prepare the checkpoint account data
      //we do this on a single sweep over the list, pairing the results and then unzipping the outcome
      val (blocks, accountUpdates) =
        results.map {
          case (block, accountIds) =>
            block -> accountIds.taggedWithBlock(
                  block.data.hash,
                  block.data.header.level,
                  Some(block.data.header.timestamp.toInstant),
                  DatabaseConversions.extractCycle(block)
                )
        }.unzip

      for {
        _ <- db.run(TezosDb.writeBlocksAndCheckpointAccounts(blocks, accountUpdates)) andThen logBlockOutcome
        votingData <- processVotesForBlocks(results.map { case (block, _) => block })
        rollsData = votingData.map {
          case (block, rolls) => block.data.hash -> rolls
        }
        delegateCheckpoints <- processAccountsForBlocks(accountUpdates, rollsData) // should this fail, we still recover data from the checkpoint
        _ <- processDelegatesForBlocks(delegateCheckpoints) // same as above
        _ <- processBlocksForGovernance(blocks, votingData)
      } yield results.size

    }

    /** Processes blocks and fetches needed data to produce Governance rows*/
    def processBlocksForGovernance(
        blocks: List[Block],
        listings: List[(Block, List[Voting.BakerRolls])]
    ): Future[Unit] = {
      import DatabaseConversions._
      import tech.cryptonomic.conseil.util.Conversion.Syntax._

      for {
        proposals: List[(BlockHash, Option[ProtocolId])] <- tezosNodeOperator.getProposals(blocks.map(_.data))
        blockHashesWithProposals = proposals.filter(_._2.isDefined).map(_._1)
        blocksWithProposals = blocks.filter(blockData => blockHashesWithProposals.contains(blockData.data.hash))
        ballotCounts <- Future.traverse(blocksWithProposals) { block =>
          db.run(TezosDb.getBallotOperationsForCycle(TezosTypes.discardGenesis(block.data.metadata).level.cycle))
            .map(block -> _)
        }
        ballots <- tezosNodeOperator.getVotes(blocksWithProposals)
        governanceRows = groupGovernanceDataByBlock(
          blocksWithProposals,
          proposals.toMap,
          listings.toMap,
          ballots.toMap,
          ballotCounts.toMap
        ).map(_.convertTo[Tables.GovernanceRow])
        _ <- db.run(TezosDb.insertGovernance(governanceRows))
      } yield ()
    }

    /** Groups data needed for generating Governance */
    def groupGovernanceDataByBlock(
        blocks: List[Block],
        proposals: Map[BlockHash, Option[ProtocolId]],
        listings: Map[Block, List[Voting.BakerRolls]],
        ballots: Map[Block, List[Voting.Ballot]],
        ballotCounts: Map[Block, Voting.BallotCounts]
    ): List[
      (BlockData, Option[ProtocolId], List[Voting.BakerRolls], List[Voting.Ballot], Option[Voting.BallotCounts])
    ] = blocks.map { block =>
      val proposal = proposals.get(block.data.hash).flatten
      val listing = listings.get(block).toList.flatten
      val ballot = ballots.get(block).toList.flatten
      val ballotCount = ballotCounts.get(block)
      (block.data, proposal, listing, ballot, ballotCount)
    }

    def processBakingAndEndorsingRights(fetchingResults: tezosNodeOperator.BlockFetchingResults): Future[Unit] = {
      import cats.implicits._

      val blockHashesWithCycleAndGovernancePeriod = fetchingResults.map { results =>
        {
          val data = results._1.data
          val hash = data.hash
          data.metadata match {
            case GenesisMetadata => FetchRights(None, None, Some(hash))
            case BlockHeaderMetadata(_, _, _, _, _, level) =>
              FetchRights(Some(level.cycle), Some(level.voting_period), Some(hash))

          }
        }
      }

      (
        tezosNodeOperator.getBatchBakingRights(blockHashesWithCycleAndGovernancePeriod),
        tezosNodeOperator.getBatchEndorsingRights(blockHashesWithCycleAndGovernancePeriod)
      ).mapN { (br, er) =>
        val updatedEndorsingRights = updateEndorsingRights(er, fetchingResults)
        (db.run(TezosDb.upsertBakingRights(br)), db.run(TezosDb.upsertEndorsingRights(updatedEndorsingRights)))
      }.void
    }

    /** Updates endorsing rights with endorsed block */
    def updateEndorsingRights(
        endorsingRights: Map[FetchRights, List[EndorsingRights]],
        fetchingResults: tezosNodeOperator.BlockFetchingResults
    ): Map[FetchRights, List[EndorsingRights]] =
      endorsingRights.map {
        case (fetchRights, endorsingRightsList) =>
          fetchRights -> endorsingRightsList.map { rights =>
                val endorsedBlock = fetchingResults.find {
                  case (block, _) =>
                    fetchRights.blockHash.contains(block.data.hash)
                }.flatMap {
                  case (block, _) =>
                    block.operationGroups.flatMap {
                      _.contents.collect {
                        case e: Endorsement if e.metadata.delegate.value == rights.delegate => e
                      }.map(_.level)
                    }.headOption
                }
                rights.copy(endorsedBlock = endorsedBlock)
              }
      }

    blockPagesToSynchronize.flatMap {
      // Fails the whole process if any page processing fails
      case (pages, total) => {
        //this will be used for the pages streaming, and within all sub-processing doing a similar paging trick
        implicit val mat = ActorMaterializer()

        //custom progress tracking for blocks
        val logProgress =
          logProcessingProgress(entityName = "block", totalToProcess = total, processStartNanos = System.nanoTime()) _

        // Process each page on his own, and keep track of the progress
        Source
          .fromIterator(() => pages)
          .mapAsync[tezosNodeOperator.BlockFetchingResults](1)(identity)
          .mapAsync(1) { fetchingResults =>
            processBlocksPage(fetchingResults)
              .flatTap(
                _ =>
                  processTezosAccountsCheckpoint >> processTezosDelegatesCheckpoint >> processBakingAndEndorsingRights(
                        fetchingResults
                      )
              )
          }
          .runFold(0) { (processed, justDone) =>
            processed + justDone <| logProgress
          }

      }
    } transform {
      case Failure(accountFailure @ AccountsProcessingFailed(_, _)) =>
        Failure(accountFailure)
      case Failure(delegateFailure @ DelegatesProcessingFailed(_, _)) =>
        Failure(delegateFailure)
      case Failure(e) =>
        val error = "Could not fetch blocks from client"
        logger.error(error, e)
        Failure(BlocksProcessingFailed(message = error, e))
      case Success(_) => Success(Done)
    }

  }

  /**
    * Fetches voting data for the blocks and stores any relevant
    * result into the appropriate database table
    */
  private[this] def processVotesForBlocks(
      blocks: List[TezosTypes.Block]
  ): Future[List[(Block, List[Voting.BakerRolls])]] =
    tezosNodeOperator.getVotingDetails(blocks)

  /* Fetches accounts from account-id and saves those associated with the latest operations
   * (i.e.the highest block level)
   * @return the delegates key-hashes found for the accounts passed-in, grouped by block reference
   */
  private[this] def processAccountsForBlocks(
      updates: List[BlockTagged[List[AccountId]]],
      votingData: List[(BlockHash, List[Voting.BakerRolls])]
  )(implicit mat: ActorMaterializer): Future[List[BlockTagged[List[PublicKeyHash]]]] = {
    logger.info("Processing latest Tezos data for updated accounts...")

    def keepMostRecent(associations: List[(AccountId, BlockReference)]): Map[AccountId, BlockReference] =
      associations.foldLeft(Map.empty[AccountId, BlockReference]) { (collected, entry) =>
        val key = entry._1
        if (collected.contains(key)) collected else collected + (key -> entry._2)
      }

    val sorted = updates.flatMap {
      case BlockTagged(hash, level, timestamp, cycle, ids) =>
        ids.map(_ -> (hash, level, timestamp, cycle))
    }.sortBy {
      case (id, (hash, level, timestamp, cycle)) => level
    }(Ordering[Int].reverse)

    val toBeFetched = keepMostRecent(sorted)

    AccountsProcessor.process(toBeFetched, votingData.toMap)
  }

  /** Fetches and stores all accounts from the latest blocks still in the checkpoint */
  private[this] def processTezosAccountsCheckpoint(implicit mat: ActorMaterializer): Future[Done] = {
    logger.info("Selecting all accounts left in the checkpoint table...")
    db.run(TezosDb.getLatestAccountsFromCheckpoint) flatMap { checkpoints =>
      if (checkpoints.nonEmpty) {
        logger.info(
          "I loaded all of {} checkpointed ids from the DB and will proceed to fetch updated accounts information from the chain",
          checkpoints.size
        )
        // here we need to get missing bakers for the given block
        db.run(TezosDb.getBakersForBlocks(checkpoints.values.map(_._1).toList)).flatMap { bakers =>
          AccountsProcessor.process(checkpoints, bakers.toMap, onlyProcessLatest = true).map(_ => Done)
        }

      } else {
        logger.info("No data to fetch from the accounts checkpoint")
        Future.successful(Done)
      }
    }
  }

  private[this] def processDelegatesForBlocks(
      updates: List[BlockTagged[List[PublicKeyHash]]]
  )(implicit mat: ActorMaterializer): Future[Done] = {
    logger.info("Processing latest Tezos data for account delegates...")

    def keepMostRecent(associations: List[(PublicKeyHash, BlockReference)]): Map[PublicKeyHash, BlockReference] =
      associations.foldLeft(Map.empty[PublicKeyHash, BlockReference]) { (collected, entry) =>
        val key = entry._1
        if (collected.contains(key)) collected else collected + (key -> entry._2)
      }

    val sorted = updates.flatMap {
      case BlockTagged(hash, level, timestamp, cycle, ids) =>
        ids.map(_ -> (hash, level, timestamp, cycle))
    }.sortBy {
      case (id, (hash, level, timestamp, cycle)) => level
    }(Ordering[Int].reverse)

    val toBeFetched = keepMostRecent(sorted)

    DelegatesProcessor.process(toBeFetched)
  }

  /** Fetches and stores all delegates from the latest blocks still in the checkpoint */
  private[this] def processTezosDelegatesCheckpoint(implicit mat: ActorMaterializer): Future[Done] = {
    logger.info("Selecting all delegates left in the checkpoint table...")
    db.run(TezosDb.getLatestDelegatesFromCheckpoint) flatMap { checkpoints =>
      if (checkpoints.nonEmpty) {
        logger.info(
          "I loaded all of {} checkpointed ids from the DB and will proceed to fetch updated delegates information from the chain",
          checkpoints.size
        )
        DelegatesProcessor.process(checkpoints, onlyProcessLatest = true)
      } else {
        logger.info("No data to fetch from the delegates checkpoint")
        Future.successful(Done)
      }
    }
  }

  private object AccountsProcessor {

    type AccountsIndex = Map[AccountId, Account]
    type DelegateKeys = List[PublicKeyHash]

    /* Fetches the data from the chain node and stores accounts into the data store.
     * @param ids a pre-filtered map of account ids with latest block referring to them
     * @param onlyProcessLatest verify that no recent update was made to the account before processing each id
     *        (default = false)
     */
    def process(
        ids: Map[AccountId, BlockReference],
        votingData: Map[BlockHash, List[Voting.BakerRolls]],
        onlyProcessLatest: Boolean = false
    )(implicit mat: ActorMaterializer): Future[List[BlockTagged[DelegateKeys]]] = {
      import cats.Monoid
      import cats.instances.future._
      import cats.instances.list._
      import cats.instances.int._
      import cats.instances.option._
      import cats.instances.tuple._
      import cats.syntax.flatMap._
      import cats.syntax.functorFilter._
      import cats.syntax.monoid._

      def logWriteFailure: PartialFunction[Try[_], Unit] = {
        case Failure(e) =>
          logger.error("Could not write accounts to the database")
      }

      def logOutcome: PartialFunction[Try[(Option[Int], Option[Int], _)], Unit] = {
        case Success((accountsRows, delegateCheckpointRows, _)) =>
          logger.info(
            "{} accounts were touched on the database. Checkpoint stored for{} delegates.",
            accountsRows.fold("The")(String.valueOf),
            delegateCheckpointRows.fold("")(" " + _)
          )
      }

      def extractDelegatesInfo(
          taggedAccounts: Seq[BlockTagged[AccountsIndex]]
      ): (List[BlockTagged[AccountsIndex]], List[BlockTagged[DelegateKeys]]) = {
        val taggedList = taggedAccounts.toList
        def extractDelegateKey(account: Account): Option[PublicKeyHash] =
          PartialFunction.condOpt(account.delegate) {
            case Some(Right(pkh)) => pkh
            case Some(Left(Protocol4Delegate(_, Some(pkh)))) => pkh
          }
        val taggedDelegatesKeys = taggedList.map {
          case BlockTagged(blockHash, blockLevel, timestamp, cycle, accountsMap) =>
            import TezosTypes.Syntax._
            val delegateKeys = accountsMap.values.toList
              .mapFilter(extractDelegateKey)

            delegateKeys.taggedWithBlock(blockHash, blockLevel, timestamp, cycle)
        }
        (taggedList, taggedDelegatesKeys)
      }

      def processAccountsPage(
          taggedAccounts: List[BlockTagged[Map[AccountId, Account]]],
          taggedDelegateKeys: List[BlockTagged[DelegateKeys]]
      ): Future[(Option[Int], Option[Int], List[BlockTagged[DelegateKeys]])] = {
        // we fetch active delegates per block so we can filter out current active bakers
        // at this point in time and put the information about it in the separate row
        // (there is no operation like bakers deactivation)
        val accountsWithHistoryFut = for {
          activatedOperations <- fetchActivationOperationsByLevel(taggedAccounts.map(_.blockLevel).distinct)
          activatedAccounts <- db.run(TezosDb.findActivatedAccountIds)
          updatedTaggedAccounts = updateTaggedAccountsWithIsActivated(
            taggedAccounts,
            activatedOperations,
            activatedAccounts.toList
          )
          inactiveBakerAccounts <- getInactiveBakersWithTaggedAccounts(updatedTaggedAccounts)
        } yield inactiveBakerAccounts

        accountsWithHistoryFut.flatMap { accountsWithHistory =>
          db.run(TezosDb.writeAccountsAndCheckpointDelegates(accountsWithHistory, taggedDelegateKeys))
            .map {
              case (accountWrites, accountHistoryWrites, delegateCheckpoints) =>
                (accountWrites, delegateCheckpoints, taggedDelegateKeys)
            }
            .andThen(logWriteFailure)
        }
      }

      def getInactiveBakersWithTaggedAccounts(
          taggedAccounts: List[BlockTagged[Map[AccountId, Account]]]
      ): Future[List[(BlockTagged[Map[AccountId, Account]], List[Tables.AccountsRow])]] =
        Future.traverse(taggedAccounts) { blockTaggedAccounts =>
          if (blockTaggedAccounts.blockLevel % lorreConf.blockRightsFetching.cycleSize == 1) {
            tezosNodeOperator.fetchActiveDelegates(taggedAccounts.map(x => (x.blockLevel, x.blockHash))).flatMap {
              activeBakers =>
                val activeBakersIds = activeBakers.toMap.apply(blockTaggedAccounts.blockHash)
                db.run {
                  TezosDb
                    .getInactiveBakersFromAccounts(activeBakersIds)
                    .map(blockTaggedAccounts -> _)
                }
            }
          } else {
            Future.successful(blockTaggedAccounts -> List.empty)
          }
        }

      def updateTaggedAccountsWithIsActivated(
          taggedAccounts: List[BlockTagged[AccountsIndex]],
          activatedOperations: Map[Int, Seq[Option[String]]],
          activatedAccountIds: List[String]
      ): List[BlockTagged[Map[AccountId, Account]]] =
        taggedAccounts.map { taggedAccount =>
          val activatedAccountsHashes = activatedOperations.get(taggedAccount.blockLevel).toList.flatten
          taggedAccount.copy(
            content = taggedAccount.content.mapValues { account =>
              val hash = account.manager.map(_.value)
              if ((activatedAccountsHashes ::: activatedAccountIds.map(Some(_))).contains(hash)) {
                account.copy(isActivated = Some(true))
              } else account
            }
          )
        }

      def fetchActivationOperationsByLevel(levels: List[Int]): Future[Map[Int, Seq[Option[String]]]] = {
        import slick.jdbc.PostgresProfile.api._
        db.run {
          DBIO.sequence {
            levels.map { level =>
              TezosDb.fetchRecentOperationsHashByKind(Set("activate_account"), level).map(x => level -> x)
            }
          }.map(_.toMap)
        }
      }

      def cleanup[T] = (_: T) => {
        //can fail with no real downsides
        val processed = Some(ids.keySet)
        logger.info("Cleaning {} processed accounts from the checkpoint...", ids.size)
        db.run(TezosDb.cleanAccountsCheckpoint(processed))
          .map(cleaned => logger.info("Done cleaning {} accounts checkpoint rows.", cleaned))
      }

      //if needed, we get the stored levels and only keep updates that are more recent
      def prunedUpdates(): Future[Map[AccountId, BlockReference]] =
        if (onlyProcessLatest) db.run {
          TezosDb.getLevelsForAccounts(ids.keySet).map { currentlyStored =>
            ids.filterNot {
              case (AccountId(id), (_, updateLevel, _, _)) =>
                currentlyStored.exists { case (storedId, storedLevel) => storedId == id && storedLevel > updateLevel }
            }
          }
        } else Future.successful(ids)

      logger.info("Ready to fetch updated accounts information from the chain")

      // updates account pages with baker information
      def updateAccountPages(pages: LazyPages[tezosNodeOperator.AccountFetchingResults]) = pages.map { pageFut =>
        pageFut.map { accounts =>
          accounts.map { taggedAccounts =>
            votingData
              .get(taggedAccounts.blockHash)
              .map { rolls =>
                val affectedAccounts = rolls.map(_.pkh.value)
                val accUp = taggedAccounts.content.map {
                  case (accId, acc) if affectedAccounts.contains(accId.id) =>
                    accId -> acc.copy(isBaker = Some(true))
                  case x => x
                }
                taggedAccounts.copy(content = accUp)
              }
              .getOrElse(taggedAccounts)
          }
        }
      }

      /* Streams the (unevaluated) incoming data, actually fetching the results.
       * We use combinators to keep the ongoing requests' flow under control, taking advantage of
       * akka-streams automatic backpressure control.
       * The results are grouped to optimize for database storage.
       * We do this to re-aggregate results from pages which are now based on single blocks,
       * which would lead to inefficient storage performances as-is.
       */
      val saveAccounts = (pages: LazyPages[tezosNodeOperator.AccountFetchingResults]) =>
        Source
          .fromIterator(() => pages)
          .mapAsync(1)(identity) //extracts the future value as an element of the stream
          .mapConcat(identity) //concatenates the list of values as single-valued elements in the stream
          .grouped(batchingConf.blockPageSize) //re-arranges the process batching
          .map(extractDelegatesInfo)
          .mapAsync(1)((processAccountsPage _).tupled)
          .runFold(Monoid[(Option[Int], Option[Int], List[BlockTagged[DelegateKeys]])].empty) { (processed, justDone) =>
            processed |+| justDone
          } andThen logOutcome

      val fetchAndStore = for {
        (accountPages, _) <- prunedUpdates().map(tezosNodeOperator.getAccountsForBlocks)
        updatedPages = updateAccountPages(accountPages)
        (stored, checkpoints, delegateKeys) <- saveAccounts(updatedPages) flatTap cleanup
      } yield delegateKeys

      fetchAndStore.transform(
        identity,
        e => {
          val error = "I failed to fetch accounts from client and update them"
          logger.error(error, e)
          AccountsProcessingFailed(message = error, e)
        }
      )
    }
  }

  private object DelegatesProcessor {

    /* Fetches the data from the chain node and stores delegates into the data store.
     * @param ids a pre-filtered map of delegate hashes with latest block referring to them
     */
    def process(ids: Map[PublicKeyHash, BlockReference], onlyProcessLatest: Boolean = false)(
        implicit mat: ActorMaterializer
    ): Future[Done] = {
      import cats.Monoid
      import cats.instances.int._
      import cats.instances.option._
      import cats.instances.future._
      import cats.syntax.monoid._
      import cats.syntax.flatMap._

      def logWriteFailure: PartialFunction[Try[_], Unit] = {
        case Failure(e) =>
          logger.error(s"Could not write delegates to the database", e)
      }

      def logOutcome: PartialFunction[Try[Option[Int]], Unit] = {
        case Success(rows) =>
          logger.info("{} delegates were touched on the database.", rows.fold("The")(String.valueOf))
      }

      def processDelegatesPage(
          taggedDelegates: Seq[BlockTagged[Map[PublicKeyHash, Delegate]]],
          rolls: List[Voting.BakerRolls]
      ): Future[Option[Int]] = {

        val enrichedDelegates = taggedDelegates
          .map(
            blockTagged =>
              blockTagged.copy(content = blockTagged.content.map {
                case (key, delegate) =>
                  val rollsSum = rolls.filter(_.pkh.value == key.value).map(_.rolls).sum
                  (key, delegate.updateRolls(rollsSum))
              })
          )

        db.run(TezosDb.writeDelegatesAndCopyContracts(enrichedDelegates.toList))
          .andThen(logWriteFailure)
      }

      def cleanup[T] = (_: T) => {
        //can fail with no real downsides
        val processed = Some(ids.keySet)
        logger.info("Cleaning {} processed delegates from the checkpoint...", ids.size)
        db.run(TezosDb.cleanDelegatesCheckpoint(processed))
          .map(cleaned => logger.info("Done cleaning {} delegates checkpoint rows.", cleaned))
      }

      //if needed, we get the stored levels and only keep updates that are more recent
      def prunedUpdates(): Future[Map[PublicKeyHash, BlockReference]] =
        if (onlyProcessLatest) db.run {
          TezosDb.getLevelsForDelegates(ids.keySet).map { currentlyStored =>
            ids.filterNot {
              case (PublicKeyHash(pkh), (_, updateLevel, _, _)) =>
                currentlyStored.exists {
                  case (storedPkh, storedLevel) => storedPkh == pkh && storedLevel > updateLevel
                }
            }
          }
        } else Future.successful(ids)

      logger.info("Ready to fetch updated delegates information from the chain")

      /* Streams the (unevaluated) incoming data, actually fetching the results.
       * We use combinators to keep the ongoing requests' flow under control, taking advantage of
       * akka-streams automatic backpressure control.
       * The results are grouped to optimize for database storage.
       * We do this to re-aggregate results from pages which are now based on single blocks,
       * which would lead to inefficient storage performances as-is.
       */
      val saveDelegates = (pages: LazyPages[tezosNodeOperator.DelegateFetchingResults]) =>
        Source
          .fromIterator(() => pages)
          .mapAsync(1)(identity) //extracts the future value as an element of the stream
          .mapConcat(identity) //concatenates the list of values as single-valued elements in the stream
          .grouped(batchingConf.blockPageSize) //re-arranges the process batching
          .mapAsync(1)(taggedDelegates => {

            val hashIds = taggedDelegates.toList.map(_.blockHash.value)
            val rolls = tezosNodeOperator.getRolls(hashIds)

            rolls.map((taggedDelegates, _))
          })
          .mapAsync(1) {
            case (delegates, rolls) => processDelegatesPage(delegates, rolls)
          }
          .runFold(Monoid[Option[Int]].empty) { (processed, justDone) =>
            processed |+| justDone
          } andThen logOutcome

      val fetchAndStore = for {
        (delegatesPages, _) <- prunedUpdates().map(tezosNodeOperator.getDelegatesForBlocks)
        _ <- saveDelegates(delegatesPages) flatTap cleanup
      } yield Done

      fetchAndStore.transform(
        identity,
        e => {
          val error = "I failed to fetch delegates from client and update them"
          logger.error(error, e)
          DelegatesProcessingFailed(message = error, e)
        }
      )

    }
  }

  /** Keeps track of time passed between different partial checkpoints of some entity processing
    * Designed to be partially applied to set properties of the whole process once, and then only compute partial completion
    *
    * @param entityName a string that will be logged to identify what kind of resource is being processed
    * @param totalToProcess how many entities there were in the first place
    * @param processStartNanos a nano-time from jvm monotonic time, used to identify when the whole processing operation began
    * @param processed how many entities were processed at the current checkpoint
    */
  private[this] def logProcessingProgress(entityName: String, totalToProcess: Int, processStartNanos: Long)(
      processed: Int
  ): Unit = {
    val elapsed = System.nanoTime() - processStartNanos
    val progress = processed.toDouble / totalToProcess
    logger.info("================================== Progress Report ==================================")
    logger.info("Completed processing {}% of total requested {}s", "%.2f".format(progress * 100), entityName)

    val etaMins = Duration(scala.math.ceil(elapsed / progress) - elapsed, NANOSECONDS).toMinutes
    if (processed < totalToProcess && etaMins > 1) logger.info("Estimated time to finish is around {} minutes", etaMins)
    logger.info("=====================================================================================")
  }

}
