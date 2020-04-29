package tech.cryptonomic.conseil.indexer.tezos

import akka.Done
import akka.actor.{ActorSystem, Terminated}
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import com.typesafe.scalalogging.LazyLogging
import mouse.any._
import org.slf4j.LoggerFactory
import tech.cryptonomic.conseil.common.config.Platforms.{BlockchainPlatform, TezosConfiguration}
import tech.cryptonomic.conseil.common.config._
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BlockHash, FetchRights, _}
import tech.cryptonomic.conseil.common.tezos.michelson.contracts.{TNSContract, TokenContracts}
import tech.cryptonomic.conseil.common.tezos.{DatabaseConversions, FeeOperations, Tables, TezosOptics, TezosTypes, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.common.util.DatabaseUtil
import tech.cryptonomic.conseil.indexer.config.LorreAppConfig.LORRE_FAILURE_IGNORE_VAR
import tech.cryptonomic.conseil.indexer.LorreIndexer
import tech.cryptonomic.conseil.indexer.LorreIndexer.ShutdownComplete
import tech.cryptonomic.conseil.indexer.config.{BatchFetchConfiguration, Custom, Everything, HttpStreamingConfiguration, LorreConfiguration, NetworkCallsConfiguration, Newest}
import tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging
import tech.cryptonomic.conseil.indexer.tezos.TezosErrors._
import tech.cryptonomic.conseil.indexer.tezos.TezosNodeOperator.LazyPages

import scala.annotation.tailrec
import scala.collection.SortedSet
import scala.concurrent.duration.{Duration, _}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

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

  private type AccountUpdatesEvents = SortedSet[(Int, ChainEvent.AccountIdPattern)]

  private val db = DatabaseUtil.lorreDb
  private val operations = new TezosNodeOperations

  private val nodeOperator = new TezosNodeOperator(
    new TezosNodeInterface(tezosConf, callsConf, streamingClientConf),
    tezosConf.network,
    batchingConf,
    operations
  )

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
                case Tables.RegisteredTokensRow(_, tokenName, standard, accountId) =>
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

  /** Schedules method for fetching baking rights */
  system.scheduler.schedule(lorreConf.blockRightsFetching.initDelay, lorreConf.blockRightsFetching.interval)(
    writeFutureRights()
  )

  /** Updates timestamps in the baking/endorsing rights tables */
  private def updateRightsTimestamps(): Future[Unit] = {
    import cats.implicits._
    val blockHead = nodeOperator.getBareBlockHead()

    blockHead.flatMap { blockData =>
      val headLevel = blockData.header.level
      val blockLevelsToUpdate = List.range(headLevel + 1, headLevel + lorreConf.blockRightsFetching.updateSize)
      val br = nodeOperator.getBatchBakingRightsByLevels(blockLevelsToUpdate).flatMap { bakingRightsResult =>
        val brResults = bakingRightsResult.values.flatten
        logger.info(s"Got ${brResults.size} baking rights")
        db.run(TezosDb.updateBakingRightsTimestamp(brResults.toList))
      }
      val er = nodeOperator.getBatchEndorsingRightsByLevel(blockLevelsToUpdate).flatMap { endorsingRightsResult =>
        val erResults = endorsingRightsResult.values.flatten
        logger.info(s"Got ${erResults.size} endorsing rights")
        db.run(TezosDb.updateEndorsingRightsTimestamp(erResults.toList))
      }
      (br, er).mapN {
        case (bb, ee) =>
          logger.info("Updated {} baking rights and {} endorsing rights rows", bb.sum, ee.sum)
      }
    }
  }

  /** Fetches future baking and endorsing rights to insert it into the DB */
  private def writeFutureRights(): Unit = {
    val berLogger = LoggerFactory.getLogger("RightsFetcher")

    import cats.implicits._

    berLogger.info("Fetching future baking and endorsing rights")
    val blockHead = nodeOperator.getBareBlockHead()
    val brLevelFut = operations.fetchMaxBakingRightsLevel()
    val erLevelFut = operations.fetchMaxEndorsingRightsLevel()

    (blockHead, brLevelFut, erLevelFut).mapN { (head, brLevel, erLevel) =>
      val headLevel = head.header.level
      val rightsStartLevel = math.max(brLevel, erLevel) + 1
      berLogger.info(
        s"Current Tezos block head level: $headLevel DB stored baking rights level: $brLevel DB stored endorsing rights level: $erLevel"
      )

      val length = TezosOptics.Blocks
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
          nodeOperator.getBatchBakingRightsByLevels(partition.toList).flatMap { bakingRightsResult =>
            val brResults = bakingRightsResult.values.flatten
            berLogger.info(s"Got ${brResults.size} baking rights")
            db.run(TezosDb.insertBakingRights(brResults.toList))
          }
          nodeOperator.getBatchEndorsingRightsByLevel(partition.toList).flatMap { endorsingRightsResult =>
            val erResults = endorsingRightsResult.values.flatten
            berLogger.info(s"Got ${erResults.size} endorsing rights")
            db.run(TezosDb.insertEndorsingRights(erResults.toList))
          }
        }
        .runWith(Sink.ignore)
    }.flatten
    ()
  }

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
  private def mainLoop(
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
      _ <- updateRightsTimestamps()
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

  /* Possibly updates all accounts if the current block level is past any of the given ones
   * @param events the relevant levels, each with its own selection pattern, that calls for a refresh
   */
  private def processAccountRefreshes(events: AccountUpdatesEvents): Future[AccountUpdatesEvents] =
    if (events.nonEmpty) {
      for {
        storedHead <- operations.fetchMaxLevel
        updated <- if (events.exists(_._1 <= storedHead)) {
          val (past, toCome) = events.partition(_._1 <= storedHead)
          val (levels, selectors) = past.unzip
          logger.info(
            "A block was reached that requires an update of account data as specified in the configuration file. A full refresh is now underway. Relevant block levels: {}",
            levels.mkString(", ")
          )
          operations.fetchBlockAtLevel(levels.max).flatMap {
            case Some(referenceBlockForRefresh) =>
              val (hashRef, levelRef, timestamp, cycle) =
                (
                  BlockHash(referenceBlockForRefresh.hash),
                  referenceBlockForRefresh.level,
                  referenceBlockForRefresh.timestamp.toInstant,
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
  private def processTezosBlocks(): Future[Done] = {
    import TezosTypes.Syntax._
    import cats.instances.future._
    import cats.syntax.flatMap._

    logger.info("Processing Tezos Blocks..")

    val blockPagesToSynchronize = lorreConf.depth match {
      case Newest => nodeOperator.getBlocksNotInDatabase()
      case Everything => nodeOperator.getLatestBlocks()
      case Custom(n) => nodeOperator.getLatestBlocks(Some(n), lorreConf.headHash)
    }

    /* will store a single page of block results */
    def processBlocksPage(
        results: nodeOperator.BlockFetchingResults
    ): Future[Int] = {
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
                  TezosOptics.Blocks.extractCycle(block),
                  TezosOptics.Blocks.extractPeriod(block.data.metadata)
                )
        }.unzip

      for {
        _ <- db.run(TezosDb.writeBlocksAndCheckpointAccounts(blocks, accountUpdates)) andThen logBlockOutcome
        _ <- tnsOperations.processNamesRegistrations(blocks).flatMap(db.run)
        votingData <- processVotesForBlocks(results.map { case (block, _) => block })
        rollsData = votingData.map {
          case (block, rolls) => block.data.hash -> rolls
        }
        delegateCheckpoints <- processAccountsForBlocks(accountUpdates, rollsData) // should this fail, we still recover data from the checkpoint
        _ <- processBakersForBlocks(delegateCheckpoints) // same as above
        _ <- processBlocksForGovernance(blocks, votingData)
      } yield results.size

    }

    /** Processes blocks and fetches needed data to produce Governance rows */
    def processBlocksForGovernance(
        blocks: List[Block],
        listings: List[(Block, List[Voting.BakerRolls])]
    ): Future[Unit] = {
      import DatabaseConversions._
      import tech.cryptonomic.conseil.common.util.Conversion.Syntax._

      for {
        proposals: List[(BlockHash, Option[ProtocolId])] <- nodeOperator.getProposals(blocks.map(_.data))
        blockHashesWithProposals = proposals.filter(_._2.isDefined).map(_._1)
        blocksWithProposals = blocks.filter(blockData => blockHashesWithProposals.contains(blockData.data.hash))
        ballotCountsPerCycle <- Future.traverse(blocksWithProposals) { block =>
          db.run(TezosDb.getBallotOperationsForCycle(TezosTypes.discardGenesis(block.data.metadata).level.cycle))
            .map(block -> _)
        }
        ballotCountsPerLevel <- Future.traverse(blocksWithProposals) { block =>
          db.run(TezosDb.getBallotOperationsForLevel(block.data.header.level))
            .map(block -> _)
        }
        ballots <- nodeOperator.getVotes(blocksWithProposals)
        governanceRows = groupGovernanceDataByBlock(
          blocksWithProposals,
          proposals.toMap,
          listings.map { case (block, listing) => block.data.header.level -> listing }.toMap,
          ballots.toMap,
          ballotCountsPerCycle.toMap,
          ballotCountsPerLevel.toMap
        ).map(_.convertTo[Tables.GovernanceRow])
        _ <- db.run(TezosDb.insertGovernance(governanceRows))
      } yield ()
    }

    /** Groups data needed for generating Governance */
    def groupGovernanceDataByBlock(
        blocks: List[Block],
        proposals: Map[BlockHash, Option[ProtocolId]],
        listings: Map[Int, List[Voting.BakerRolls]],
        ballots: Map[Block, List[Voting.Ballot]],
        ballotCountsPerCycle: Map[Block, Voting.BallotCounts],
        ballotCountsPerLevel: Map[Block, Voting.BallotCounts]
    ): List[
      (
          BlockData,
          Option[ProtocolId],
          List[Voting.BakerRolls],
          List[Voting.BakerRolls],
          List[Voting.Ballot],
          Option[Voting.BallotCounts],
          Option[Voting.BallotCounts]
      )
    ] = blocks.map { block =>
      val proposal = proposals.get(block.data.hash).flatten
      val listing = listings.get(block.data.header.level).toList.flatten
      val prevListings = listings.get(block.data.header.level - 1).toList.flatten
      val listingByBlock = listing.diff(prevListings)
      val ballot = ballots.get(block).toList.flatten
      val ballotCountPerCycle = ballotCountsPerCycle.get(block)
      val ballotCountPerLevel = ballotCountsPerLevel.get(block)
      (block.data, proposal, listing, listingByBlock, ballot, ballotCountPerCycle, ballotCountPerLevel)
    }

    def processBakingAndEndorsingRights(fetchingResults: nodeOperator.BlockFetchingResults): Future[Unit] = {
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
        nodeOperator.getBatchBakingRights(blockHashesWithCycleAndGovernancePeriod),
        nodeOperator.getBatchEndorsingRights(blockHashesWithCycleAndGovernancePeriod)
      ).mapN { (br, er) =>
        val updatedEndorsingRights = updateEndorsingRights(er, fetchingResults)
        (db.run(TezosDb.upsertBakingRights(br)), db.run(TezosDb.upsertEndorsingRights(updatedEndorsingRights)))
      }.void
    }

    /** Updates endorsing rights with endorsed block */
    def updateEndorsingRights(
        endorsingRights: Map[FetchRights, List[EndorsingRights]],
        fetchingResults: nodeOperator.BlockFetchingResults
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
      case (pages, total) =>
        //custom progress tracking for blocks
        val logProgress =
          logProcessingProgress(entityName = "block", totalToProcess = total, processStartNanos = System.nanoTime()) _

        // Process each page on his own, and keep track of the progress
        Source
          .fromIterator(() => pages)
          .mapAsync[nodeOperator.BlockFetchingResults](1)(identity)
          .mapAsync(1) { fetchingResults =>
            processBlocksPage(fetchingResults)
              .flatTap(
                _ =>
                  processTezosAccountsCheckpoint >> processTezosBakersCheckpoint >> processBakingAndEndorsingRights(
                        fetchingResults
                      )
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

  /**
    * Fetches voting data for the blocks and stores any relevant
    * result into the appropriate database table
    */
  private def processVotesForBlocks(
      blocks: List[TezosTypes.Block]
  ): Future[List[(Block, List[Voting.BakerRolls])]] =
    nodeOperator.getVotingDetails(blocks)

  /* Fetches accounts from account-id and saves those associated with the latest operations
   * (i.e.the highest block level)
   * @return the bakers key-hashes found for the accounts passed-in, grouped by block reference
   */
  private def processAccountsForBlocks(
      updates: List[BlockTagged[List[AccountId]]],
      votingData: List[(BlockHash, List[Voting.BakerRolls])]
  ): Future[List[BlockTagged[List[PublicKeyHash]]]] = {
    logger.info("Processing latest Tezos data for updated accounts...")

    def keepMostRecent(associations: List[(AccountId, BlockReference)]): Map[AccountId, BlockReference] =
      associations.foldLeft(Map.empty[AccountId, BlockReference]) { (collected, entry) =>
        val key = entry._1
        if (collected.contains(key)) collected else collected + (key -> entry._2)
      }

    val sorted = updates.flatMap {
      case BlockTagged(hash, level, timestamp, cycle, period, ids) =>
        ids.map(_ -> (hash, level, timestamp, cycle, period))
    }.sortBy {
      case (id, (hash, level, timestamp, cycle, period)) => level
    }(Ordering[Int].reverse)

    val toBeFetched = keepMostRecent(sorted)

    AccountsProcessor.process(toBeFetched, votingData.toMap)
  }

  /** Fetches and stores all accounts from the latest blocks still in the checkpoint */
  private def processTezosAccountsCheckpoint(): Future[Done] = {
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

  private def processBakersForBlocks(
      updates: List[BlockTagged[List[PublicKeyHash]]]
  ): Future[Done] = {
    logger.info("Processing latest Tezos data for account bakers...")

    def keepMostRecent(associations: List[(PublicKeyHash, BlockReference)]): Map[PublicKeyHash, BlockReference] =
      associations.foldLeft(Map.empty[PublicKeyHash, BlockReference]) { (collected, entry) =>
        val key = entry._1
        if (collected.contains(key)) collected else collected + (key -> entry._2)
      }

    val sorted = updates.flatMap {
      case BlockTagged(hash, level, timestamp, cycle, period, ids) =>
        ids.map(_ -> (hash, level, timestamp, cycle, period))
    }.sortBy {
      case (id, (hash, level, timestamp, cycle, period)) => level
    }(Ordering[Int].reverse)

    val toBeFetched = keepMostRecent(sorted)

    BakersProcessor.process(toBeFetched)
  }

  /** Fetches and stores all bakers from the latest blocks still in the checkpoint */
  private def processTezosBakersCheckpoint(): Future[Done] = {
    logger.info("Selecting all bakers left in the checkpoint table...")
    db.run(TezosDb.getLatestBakersFromCheckpoint) flatMap { checkpoints =>
      if (checkpoints.nonEmpty) {
        logger.info(
          "I loaded all of {} checkpointed ids from the DB and will proceed to fetch updated bakers information from the chain",
          checkpoints.size
        )
        BakersProcessor.process(checkpoints, onlyProcessLatest = true)
      } else {
        logger.info("No data to fetch from the bakers checkpoint")
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
    ): Future[List[BlockTagged[DelegateKeys]]] = {
      import cats.Monoid
      import cats.instances.future._
      import cats.instances.int._
      import cats.instances.list._
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
            "{} accounts were touched on the database. Checkpoint stored for{} bakers.",
            accountsRows.fold("The")(String.valueOf),
            delegateCheckpointRows.fold("")(" " + _)
          )
      }

      def extractBakersInfo(
          taggedAccounts: Seq[BlockTagged[AccountsIndex]]
      ): (List[BlockTagged[AccountsIndex]], List[BlockTagged[DelegateKeys]]) = {
        val taggedList = taggedAccounts.toList

        def extractBakerKey(account: Account): Option[PublicKeyHash] =
          PartialFunction.condOpt(account.delegate) {
            case Some(Right(pkh)) => pkh
            case Some(Left(Protocol4Delegate(_, Some(pkh)))) => pkh
          }

        val taggedBakersKeys = taggedList.map {
          case BlockTagged(blockHash, blockLevel, timestamp, cycle, period, accountsMap) =>
            import TezosTypes.Syntax._
            val bakerKeys = accountsMap.values.toList
              .mapFilter(extractBakerKey)

            bakerKeys.taggedWithBlock(blockHash, blockLevel, timestamp, cycle, period)
        }
        (taggedList, taggedBakersKeys)
      }

      def processAccountsPage(
          taggedAccounts: List[BlockTagged[Map[AccountId, Account]]],
          taggedBakerKeys: List[BlockTagged[DelegateKeys]]
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
          db.run(TezosDb.writeAccountsAndCheckpointBakers(accountsWithHistory, taggedBakerKeys))
            .map {
              case (accountWrites, accountHistoryWrites, bakerCheckpoints) =>
                (accountWrites, bakerCheckpoints, taggedBakerKeys)
            }
            .andThen(logWriteFailure)
        }
      }

      def getInactiveBakersWithTaggedAccounts(
          taggedAccounts: List[BlockTagged[Map[AccountId, Account]]]
      ): Future[List[(BlockTagged[Map[AccountId, Account]], List[Tables.AccountsRow])]] =
        Future.traverse(taggedAccounts) { blockTaggedAccounts =>
          if (blockTaggedAccounts.blockLevel % lorreConf.blockRightsFetching.cycleSize == 1) {
            nodeOperator.fetchActiveBakers(taggedAccounts.map(x => (x.blockLevel, x.blockHash))).flatMap {
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
              case (AccountId(id), (_, updateLevel, _, _, _)) =>
                currentlyStored.exists { case (storedId, storedLevel) => storedId == id && storedLevel > updateLevel }
            }
          }
        } else Future.successful(ids)

      logger.info("Ready to fetch updated accounts information from the chain")

      // updates account pages with baker information
      def updateAccountPages(pages: LazyPages[nodeOperator.AccountFetchingResults]) = pages.map { pageFut =>
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
      val saveAccounts = (pages: LazyPages[nodeOperator.AccountFetchingResults]) =>
        Source
          .fromIterator(() => pages)
          .mapAsync(1)(identity) //extracts the future value as an element of the stream
          .mapConcat(identity) //concatenates the list of values as single-valued elements in the stream
          .grouped(batchingConf.blockPageSize) //re-arranges the process batching
          .map(extractBakersInfo)
          .mapAsync(1)((processAccountsPage _).tupled)
          .runFold(Monoid[(Option[Int], Option[Int], List[BlockTagged[DelegateKeys]])].empty) { (processed, justDone) =>
            processed |+| justDone
          } andThen logOutcome

      val fetchAndStore = for {
        (accountPages, _) <- prunedUpdates().map(nodeOperator.getAccountsForBlocks)
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

  private object BakersProcessor {

    /* Fetches the data from the chain node and stores bakers into the data store.
     * @param ids a pre-filtered map of delegate hashes with latest block referring to them
     */
    def process(ids: Map[PublicKeyHash, BlockReference], onlyProcessLatest: Boolean = false): Future[Done] = {
      import cats.Monoid
      import cats.instances.future._
      import cats.instances.int._
      import cats.instances.option._
      import cats.syntax.flatMap._
      import cats.syntax.monoid._

      def logWriteFailure: PartialFunction[Try[_], Unit] = {
        case Failure(e) =>
          logger.error(s"Could not write bakers to the database", e)
      }

      def logOutcome: PartialFunction[Try[Option[Int]], Unit] = {
        case Success(rows) =>
          logger.info("{} bakers were touched on the database.", rows.fold("The")(String.valueOf))
      }

      def processBakersPage(
          taggedBakers: Seq[BlockTagged[Map[PublicKeyHash, Delegate]]],
          rolls: List[Voting.BakerRolls]
      ): Future[Option[Int]] = {

        val enrichedBakers = taggedBakers
          .map(
            blockTagged =>
              blockTagged.copy(content = blockTagged.content.map {
                case (key, baker) =>
                  val rollsSum = rolls.filter(_.pkh.value == key.value).map(_.rolls).sum
                  (key, baker.updateRolls(rollsSum))
              })
          )

        db.run(TezosDb.writeBakersAndCopyContracts(enrichedBakers.toList))
          .andThen(logWriteFailure)
      }

      def cleanup[T] = (_: T) => {
        //can fail with no real downsides
        val processed = Some(ids.keySet)
        logger.info("Cleaning {} processed bakers from the checkpoint...", ids.size)
        db.run(TezosDb.cleanBakersCheckpoint(processed))
          .map(cleaned => logger.info("Done cleaning {} bakers checkpoint rows.", cleaned))
      }

      //if needed, we get the stored levels and only keep updates that are more recent
      def prunedUpdates(): Future[Map[PublicKeyHash, BlockReference]] =
        if (onlyProcessLatest) db.run {
          TezosDb.getLevelsForDelegates(ids.keySet).map { currentlyStored =>
            ids.filterNot {
              case (PublicKeyHash(pkh), (_, updateLevel, _, _, _)) =>
                currentlyStored.exists {
                  case (storedPkh, storedLevel) => storedPkh == pkh && storedLevel > updateLevel
                }
            }
          }
        } else Future.successful(ids)

      logger.info("Ready to fetch updated bakers information from the chain")

      /* Streams the (unevaluated) incoming data, actually fetching the results.
       * We use combinators to keep the ongoing requests' flow under control, taking advantage of
       * akka-streams automatic backpressure control.
       * The results are grouped to optimize for database storage.
       * We do this to re-aggregate results from pages which are now based on single blocks,
       * which would lead to inefficient storage performances as-is.
       */
      val saveBakers = (pages: LazyPages[nodeOperator.DelegateFetchingResults]) =>
        Source
          .fromIterator(() => pages)
          .mapAsync(1)(identity) //extracts the future value as an element of the stream
          .mapConcat(identity) //concatenates the list of values as single-valued elements in the stream
          .grouped(batchingConf.blockPageSize) //re-arranges the process batching
          .mapAsync(1)(taggedBakers => {

            val hashIds = taggedBakers.toList.map(_.blockHash.value)
            val rolls = nodeOperator.getRolls(hashIds)

            rolls.map((taggedBakers, _))
          })
          .mapAsync(1) {
            case (bakers, rolls) => processBakersPage(bakers, rolls)
          }
          .runFold(Monoid[Option[Int]].empty) { (processed, justDone) =>
            processed |+| justDone
          } andThen logOutcome

      val fetchAndStore = for {
        (bakerPages, _) <- prunedUpdates().map(nodeOperator.getBakersForBlocks)
        _ <- saveBakers(bakerPages) flatTap cleanup
      } yield Done

      fetchAndStore.transform(
        identity,
        e => {
          val error = "I failed to fetch bakers from client and update them"
          logger.error(error, e)
          BakersProcessingFailed(message = error, e)
        }
      )

    }
  }

  override val platform: BlockchainPlatform = Platforms.Tezos

  override def start(): Unit = {
    checkTezosConnection()
    val accountRefreshesToRun = Await.result(unprocessedLevelsForRefreshingAccounts(), atMost = 5.seconds)
    mainLoop(0, accountRefreshesToRun)
  }

  override def stop(): Future[ShutdownComplete] =
    for {
      _ <- Future.successful(db.close())
      _: ShutdownComplete <- nodeOperator.node.shutdown()
      _: Terminated <- system.terminate()
    } yield ShutdownComplete
}

object TezosIndexer {

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
