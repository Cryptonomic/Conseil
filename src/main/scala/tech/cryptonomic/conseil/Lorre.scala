package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{
  TezosTypes,
  AccountsDataFetchers,
  ApiOperations,
  BlocksDataFetchers,
  FeeOperations,
  NodeOperations,
  TezosErrors,
  TezosDatabaseOperations => TezosDb
}
import tech.cryptonomic.conseil.tezos.TezosRpc.Akka.{ShutdownComplete, TezosNodeContext}
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  Account,
  AccountId,
  Block,
  BlockReference,
  BlockTagged,
  Delegate,
  PublicKeyHash,
  Voting
}
import tech.cryptonomic.conseil.tezos.TezosTypes.Syntax._
import tech.cryptonomic.conseil.io.MainOutputs.LorreOutput
import tech.cryptonomic.conseil.util.DatabaseUtil
import tech.cryptonomic.conseil.util.EffectsUtil.{runDbIO, toIO}
import tech.cryptonomic.conseil.config.{Custom, Everything, LorreAppConfig, Newest}
import tech.cryptonomic.conseil.config.Platforms

import scala.concurrent.duration._
import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.{Stream => _}
import slick.dbio.DBIO
import cats.syntax.apply._
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functorFilter._
import cats.syntax.foldable._
import cats.syntax.parallel._
import cats.syntax.semigroup._
import cats.syntax.traverse._
import cats.effect.{ContextShift, IO}
import cats.effect.IO
import fs2.{Chunk, Stream}
import scala.util.control.NonFatal

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with TezosErrors with LazyLogging with LorreAppConfig with LorreOutput {

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
    timeoutConf,
    streamingClientConf,
    batchingConf,
    verbose
  ) = config.merge
  val ignoreProcessFailures = sys.env.get(LORRE_FAILURE_IGNORE_VAR)

  //the dispatcher is visible for all async operations in the following code
  implicit val system: ActorSystem = ActorSystem("lorre-system")
  implicit val dispatcher = system.dispatcher
  implicit val nodeContext = TezosNodeContext(tezosConf, timeoutConf, streamingClientConf)
  implicit val contextShift: ContextShift[IO] = IO.contextShift(dispatcher)
  implicit val timer = IO.timer(dispatcher)

  //how long to wait for graceful shutdown of system components
  val shutdownWait = 10.seconds

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown())

  /** use this to run db-based async operations into IO */
  def runOnDb[T](action: DBIO[T]) = runDbIO(DatabaseUtil.db, action)

  /* this is needed later to connect api logic to the fetcher,
   * being dependent on the specific effect -- i.e. Future, IO -- we can't
   * devise a more generic solution, yet...
   */
  private[this] val fetchMaxStoredLevel: IO[Int] = toIO(ApiOperations.fetchMaxLevel())

  /* Get all DataFetcheres and RpcHandler instances implicitly in scope,
   * so that they're available for later use by the node methods requiring them.
   * First we create new instances of the traits containing them, then we import
   * the content with a wildcard import
   */
  val blockFetchers = BlocksDataFetchers(nodeContext)
  val accountFetchers = AccountsDataFetchers(nodeContext)

  import blockFetchers._
  import accountFetchers._

  //create a new node operator, with his own configuration
  val node = new NodeOperations(
    network = nodeContext.tezosConfig.network,
    batchConf = batchingConf
  )

  /** close resources for application stop */
  private[this] def shutdown(): Unit = {
    logger.info("Doing clean-up")
    DatabaseUtil.db.close()
    val nodeShutdown =
      nodeContext
        .shutdown()
        .flatMap((_: ShutdownComplete) => system.terminate())

    Await.result(nodeShutdown, shutdownWait)
    logger.info("All things closed")
  }

  /** Tries to fetch blocks head to verify if connection with Tezos node was successfully established */
  @tailrec
  private[this] def checkTezosConnection(): Unit =
    Try {
      Await.result(node.getBareBlockHead[IO].unsafeToFuture, lorreConf.bootupConnectionCheckTimeout)
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
  private[this] def mainLoop(iteration: Int): Unit = {
    val processing = for {
      _ <- processTezosBlocks()
      _ <- FeeOperations
        .processTezosAverageFees(lorreConf.numberOfFeesAveraged)
        .whenA(iteration % lorreConf.feeUpdateInterval == 0)
    } yield ()

    /* Won't stop Lorre on failure from processing the chain, if explicitly set via environment.
     * Can be useful to skip non-critical failures due to tezos incorrect data or unexpected behaviour.
     * If set, fetching errors will make Lorre proceed as expected by default (stop or wait for next cycle)
     */
    val attemptedProcessing =
      if (ignoreProcessFailures.exists(ignore => ignore.toLowerCase == "true" || ignore.toLowerCase == "yes"))
        processing.handleErrorWith {
          //swallow the error and proceed with the default behaviour
          case f @ (AccountsProcessingFailed(_, _) | BlocksProcessingFailed(_, _) | DelegatesProcessingFailed(_, _)) =>
            IO(logger.error("Failed processing but will keep on going next cycle", f))
        } else
        processing

    lorreConf.depth match {
      case Newest =>
        val cycle = for {
          _ <- attemptedProcessing.timeout(12.hours)
          _ <- IO(logger.info("Taking a nap"))
          _ <- IO.shift
          _ <- timer.sleep(lorreConf.sleepInterval)
        } yield ()
        cycle.unsafeRunSync()
        mainLoop(iteration + 1)
      case _ =>
        val cycle = for {
          _ <- attemptedProcessing.timeout(12.hours)
          _ <- IO(logger.info("Synchronization is done"))
        } yield ()
        cycle.unsafeRunSync()
    }
  }

  displayInfo(tezosConf)
  if (verbose.on)
    displayConfiguration(Platforms.Tezos, tezosConf, lorreConf, (LORRE_FAILURE_IGNORE_VAR, ignoreProcessFailures))

  try {
    checkTezosConnection()
    mainLoop(0)
  } finally {
    shutdown()
  }

  /* an IO operation returning the monotonic clock value each time it runs */
  private[this] lazy val getNanos: IO[Long] = timer.clock.monotonic(NANOSECONDS)

  /**
    * Fetches all blocks not in the database from the Tezos network and adds them to the database.
    * Additionally stores account and delegates that needs updating, too
    */
  private[this] def processTezosBlocks(): IO[Unit] = {

    /* handles a single chunk/page of block data at a time */
    def processChunk(chunk: Chunk[(Block, List[AccountId])]): IO[Int] = {
      //support functions
      def logBlockOutcome[A]: Option[A] => IO[Unit] = { accountsCount =>
        IO {
          logger.info(
            "Wrote {} blocks to the database, checkpoint stored for{} account updates",
            chunk.size,
            accountsCount.fold("")(" " + _)
          )
        }
      }

      def logVotingOutcome[A]: Option[A] => IO[Unit] = { votesCount =>
        IO(logger.info("Wrote{} voting data records to the database", votesCount.fold("")(" " + _)))
      }

      //ignore the account ids for storage, and prepare the checkpoint account data
      //we do this on a single sweep over the list, pairing the results and then unzipping the outcome
      val (blocks, accountUpdates) =
        chunk.map {
          case (block, accountIds) =>
            block -> accountIds.taggedWithBlock(block.data.hash, block.data.header.level)
        }.toList.unzip

      //each step should handle errors too
      /* Here we persist all things related to this chunk of blocks, sequentially:
       * votes, accounts, delegates.
       * We then return the number of blocks to count them and notify progress.
       * Should teh last steps fail, we still recover data from the checkpoint tables
       */
      for {
        _ <- runOnDb(TezosDb.writeBlocksAndCheckpointAccounts(blocks, accountUpdates))
          .flatTap(logBlockOutcome)
          .handleErrorWith(toBlocksProcessingFailure)
        _ <- processVotes(blocks)
          .flatTap(logVotingOutcome)
          .handleErrorWith(toBlocksProcessingFailure)
        delegateCheckpoints <- processAccountsForBlocks(accountUpdates).handleErrorWith(toAccountsProcessingFailure)
        _ <- processDelegatesForBlocks(delegateCheckpoints).handleErrorWith(toDelegatesProcessingFailure)
      } yield chunk.size

    }

    /* describes the actual processing given a stream of node data, the expected size, a start */
    def processIncomingData(nodeData: Stream[IO, (Block, List[AccountId])], total: Int, startTime: Long) = {
      //custom progress tracking for blocks
      val logProgress =
        logProcessingProgress(entityName = "block", totalToProcess = total, processStartNanos = startTime) _

      nodeData
        .chunkN(batchingConf.blockPageSize)
        .evalTap(
          chunk => IO(logger.info("About to handle {} blocks from the node", chunk.size))
        )
        .evalMap {
          //process the chunk and, if there's any residual in the checkpoint tables, tries to handle those too
          processChunk(_) flatTap (_ => processTezosAccountsCheckpoint >> processTezosDelegatesCheckpoint)
        }
        .evalMapAccumulate(0) {
          //keep track of the running total and prints progress wrt. the requested blocks
          (processed, justDone) =>
            val runningTotal = processed + justDone
            (runningTotal, ()).pure[IO] <* logProgress(runningTotal)
        }
        .compile
        .drain
    }

    val blocksToSynchronize = lorreConf.depth match {
      case Newest => node.getBlocksNotInDatabase(fetchMaxStoredLevel)
      case Everything => node.getLatestBlocks[IO]()
      case Custom(n) => node.getLatestBlocks[IO](Some(n), lorreConf.headHash)
    }

    // combine all effectful computations
    for {
      _ <- IO(logger.info("Processing Tezos Blocks.."))
      start <- getNanos
      toProcess <- blocksToSynchronize
      (dataStream, levels) = toProcess //workaround to missing withFilter implementation
      _ <- processIncomingData(dataStream, levels, start)
    } yield ()
  }

  /**
    * Fetches voting data for the blocks and stores any relevant
    * result into the appropriate database table
    */
  private[this] def processVotes(blocks: List[TezosTypes.Block]): IO[Option[Int]] = {
    import cats.instances.list._
    import cats.instances.option._
    import cats.instances.int._
    import slickeffect.implicits._
    import slick.jdbc.PostgresProfile.api._

    def process(
      votesData: (List[Voting.Proposal], List[(Block, List[Voting.BakerRolls])], List[(Block, List[Voting.Ballot])])
    ) = {
      val (proposals, blockRolls, blocksBallots) = votesData
      //this is a single list
      val writeProposal = TezosDb.writeVotingProposals(proposals)
      //this is a nested list, each block with many baker rolls
      val writeBakers = blockRolls.traverse {
        case (block, bakersRolls) => TezosDb.writeVotingRolls(bakersRolls, block)
      }
      //this is a nested list, each block with many ballot votes
      val writeBallots = blocksBallots.traverse {
        case (block, ballots) => TezosDb.writeVotingBallots(ballots, block)
      }

      /* combineAll reduce List[Option[Int]] => Option[Int] by summing all ints present
       * |+| is shorthand syntax to sum Option[Int] together using Int's sums
       * Any None in the operands will make the whole operation collapse in a None result
       */
      val combinedVoteWrites = (writeProposal, writeBakers, writeBallots).mapN(
        (storedProposals, storedBakers, storedBallots) =>
          storedProposals |+| storedBakers.combineAll |+| storedBallots.combineAll
      )

      runOnDb(combinedVoteWrites.transactionally)
    }

    for {
      votings <- blocks.parTraverse(node.getVotingDetails[IO])
      written <- process(votings.unzip3)
    } yield written
  }

  /* logs and essentially rethrows an unexpected critical system failure */
  private[this] def handleFatalFailures[T]: Throwable => IO[T] = fatal => {
    val errorMsg =
      "Something serioursly bad happened, probably unrecoverable. Please restart the process once possible"
    IO(logger.error(errorMsg, fatal)) >> IO.raiseError(fatal)
  }

  /* logs and wraps block handling failures */
  private[this] def toBlocksProcessingFailure[T]: Throwable => IO[T] = {
    case NonFatal(err) =>
      val errorMsg = "I failed to fetch the blocks and related data from the client and store it"
      IO(logger.error(errorMsg, err)) >> IO.raiseError(BlocksProcessingFailed(message = errorMsg, err))
    case fatal =>
      handleFatalFailures(fatal)
  }

  /* logs and wraps account handling failures */
  private[this] def toAccountsProcessingFailure[T]: Throwable => IO[T] = {
    case NonFatal(err) =>
      val errorMsg = "I failed to fetch the accounts from the client and update them"
      IO(logger.error(errorMsg, err)) >> IO.raiseError(AccountsProcessingFailed(message = errorMsg, err))
    case fatal =>
      handleFatalFailures(fatal)
  }

  /* logs and wraps delegate handling failures */
  private[this] def toDelegatesProcessingFailure[T]: Throwable => IO[T] = {
    case NonFatal(err) =>
      val errorMsg = "I failed to fetch the delegates from the client and update them"
      IO(logger.error(errorMsg, err)) >> IO.raiseError(DelegatesProcessingFailed(message = errorMsg, err))
    case fatal =>
      handleFatalFailures(fatal)
  }

  /* Fetches accounts from account-id and saves those associated with the latest operations
   * (i.e.the highest block level)
   * @return the delegates key-hashes found for the accounts passed-in, grouped by block reference
   */
  private[this] def processAccountsForBlocks(
    updates: List[BlockTagged[List[AccountId]]]
  ): IO[List[BlockTagged[List[PublicKeyHash]]]] =
    IO(logger.info("Processing latest Tezos data for updated accounts...")) >>
        processEntitiesForLatestBlocks(updates)(AccountsProcessor.process)

  /* Fetches delegates from pkh and saves those associated with the latest operations
   * (i.e.the highest block level)
   * @return the IO operation, with no relevant return value
   */
  private[this] def processDelegatesForBlocks(
    updates: List[BlockTagged[List[PublicKeyHash]]]
  ): IO[Unit] =
    IO(logger.info("Processing latest Tezos data for account delegates...")) >>
        processEntitiesForLatestBlocks(updates)(DelegatesProcessor.process)

  /* Captures the idea of reading multiple entries for different
   * block levels, and keeping only the latest for each one, and
   * send that to custom processing.
   * E.g. Used to handle accounts and delegates persistence
   * @param updates the unsorted list of keys with referring block data
   * @param processDistinct will effectfully process the distinct entries
   * @tparam EntityKey the unique identifier of each entry (e.g. account id, pkh)
   * @tparam Result the processing result value
   * @return the actual result of processing, in a IO effect
   */
  private[this] def processEntitiesForLatestBlocks[EntityKey, Result](
    updates: List[BlockTagged[List[EntityKey]]]
  )(
    processDistinct: Map[EntityKey, BlockReference] => IO[Result]
  ): IO[Result] = {
    //we sort things by lastest levels and only keep a single key entry to process
    def keepMostRecent(associations: List[(EntityKey, BlockReference)]): Map[EntityKey, BlockReference] =
      associations.foldLeft(Map.empty[EntityKey, BlockReference]) { (collected, entry) =>
        val key = entry._1
        if (collected.contains(key)) collected else collected + (key -> entry._2)
      }

    val sorted = updates.flatMap {
      case BlockTagged(hash, level, keys) =>
        keys.map(_ -> (hash, level))
    }.sortBy {
      case (key, (hash, level)) => level
    }(Ordering[Int].reverse)

    val toBeFetched = keepMostRecent(sorted)

    processDistinct(toBeFetched)
  }

  /** Fetches and stores all accounts from the latest blocks still in the checkpoint */
  private[this] def processTezosAccountsCheckpoint: IO[Unit] =
    processEntitiesFromCheckpoint(
      "account",
      runOnDb(TezosDb.getLatestAccountsFromCheckpoint)
    )(AccountsProcessor.process)

  /** Fetches and stores all delegates from the latest blocks still in the checkpoint */
  private[this] def processTezosDelegatesCheckpoint: IO[Unit] =
    processEntitiesFromCheckpoint(
      "delegate",
      runOnDb(TezosDb.getLatestDelegatesFromCheckpoint)
    )(DelegatesProcessor.process)

  /* Generic processing of checkpoint entries (e.g. accounts, delegates)
   * Will basically log the steps and verify if there's anything to process
   *
   * @param entityName the singular name used to log the steps
   * @param updates the checkpoint entries as a map
   * @param processDistinct will effectfully process the distinct entries
   * @tparam EntityKey the unique identifier of each entry (e.g. account id, pkh)
   * @tparam Result the processing result value
   * @return the IO operation, with no relevant return value
   */
  private[this] def processEntitiesFromCheckpoint[EntityKey](
    entityName: String,
    updates: IO[Map[EntityKey, BlockReference]]
  )(
    processDistinct: Map[EntityKey, BlockReference] => IO[_]
  ): IO[Unit] =
    for {
      _ <- IO(logger.info("Selecting all {}s left in the checkpoint table...", entityName))
      checkpoints <- updates
      _ <- if (checkpoints.nonEmpty) {
        IO(
          logger.info(
            "I loaded all of {} checkpointed ids from the DB and will proceed to fetch updated {}s information from the chain",
            checkpoints.size
          )
        ) >> processDistinct(checkpoints)
      } else IO(logger.info("There's no data to fetch from the {}s checkpoint.", entityName))
    } yield ()

  private object AccountsProcessor {

    type AccountsIndex = Map[AccountId, Account]
    type DelegateKeys = List[PublicKeyHash]

    /* Fetches the data from the chain node and stores accounts into the data store.
     * @param ids a pre-filtered map of account ids with latest block referring to them
     */
    def process(
      ids: Map[AccountId, BlockReference]
    ): IO[List[BlockTagged[DelegateKeys]]] = {
      import cats.instances.list._
      import cats.instances.int._
      import cats.instances.option._
      import cats.instances.tuple._

      /* Logs the available counts of written data and returns the last
       * element of the input tuple
       */
      def logAndKeepDelegateKeys[Result]: ((Int, Option[Int], Result)) => IO[Result] = {
        case (accountsRows, delegateCheckpointRows, result) =>
          IO(
            logger.info(
              "{} accounts were touched on the database. Checkpoint stored for{} delegates.",
              accountsRows,
              delegateCheckpointRows.fold("")(" " + _)
            )
          ) >> result.pure[IO]
      }

      /* Pairs delegates references extracted from the input account data,
       * with the input itself
       */
      def extractDelegatesInfo(
        taggedAccounts: Chunk[BlockTagged[AccountsIndex]]
      ): (List[BlockTagged[AccountsIndex]], List[BlockTagged[DelegateKeys]]) = {
        val taggedList = taggedAccounts.toList
        val taggedDelegatesKeys = taggedList.map {
          case BlockTagged(blockHash, blockLevel, accountsMap) =>
            import TezosTypes.Syntax._
            val delegateKeys = accountsMap.values.toList
              .mapFilter(_.delegate.value)

            delegateKeys.taggedWithBlock(blockHash, blockLevel)
        }
        (taggedList, taggedDelegatesKeys)
      }

      /* Writes data to db and tracks the rows added */
      def processAccountsPage(
        taggedAccounts: List[BlockTagged[AccountsIndex]],
        taggedDelegateKeys: List[BlockTagged[DelegateKeys]]
      ): IO[(Int, Option[Int], List[BlockTagged[DelegateKeys]])] =
        runOnDb(TezosDb.writeAccountsAndCheckpointDelegates(taggedAccounts, taggedDelegateKeys)).onError {
          case e => IO(logger.error("Could not write accounts to the database", e))
        }.map {
          case (accountWrites, delegateCheckpoints) => (accountWrites, delegateCheckpoints, taggedDelegateKeys)
        }

      /* Remove processed ids from the checkpoint.
       * Ignores the input because it uses the available ids to clean the checkpoint
       */
      def cleanup[T] = (_: T) => {
        //can fail with no real downsides
        val processed = Some(ids.keySet)
        for {
          _ <- IO(logger.info("Cleaning {} processed accounts from the checkpoint...", ids.size))
          cleaned <- runOnDb(TezosDb.cleanAccountsCheckpoint(processed))
          _ <- IO(logger.info("Done cleaning {} accounts checkpoint rows.", cleaned))
        } yield ()
      }

      /* Streams the (unevaluated) incoming data, actually fetching the results.
       * We use combinators to keep the ongoing requests' flow under control.
       * The results are grouped to optimize for database storage.
       */
      val saveAccounts = node
        .getAccounts[IO](ids)
        .prefetchN(batchingConf.accountPageSize)
        .chunkN(batchingConf.accountPageSize)
        .map(extractDelegatesInfo)
        .evalMap((processAccountsPage _).tupled)
        .compile
        .foldMonoid
        .flatMap(logAndKeepDelegateKeys)

      IO(logger.info("Ready to fetch updated accounts information from the chain")) >>
        saveAccounts
          .flatTap(cleanup)
          .handleErrorWith(toAccountsProcessingFailure)
    }
  }

  private object DelegatesProcessor {

    /* Fetches the data from the chain node and stores delegates into the data store.
     * @param ids a pre-filtered map of delegate hashes with latest block referring to them
     */
    def process(ids: Map[PublicKeyHash, BlockReference]): IO[Unit] = {
      import cats.instances.int._

      /* Writes data to db and tracks the rows added */
      def processDelegatesPage(
        taggedDelegates: Chunk[BlockTagged[Map[PublicKeyHash, Delegate]]]
      ): IO[Int] =
        runOnDb(TezosDb.writeDelegatesAndCopyContracts(taggedDelegates.toList)).onError {
          case e => IO(logger.error("Could not write delegates to the database", e))
        }

      /* Remove processed ids from the checkpoint.
       * Ignores the input because it uses the available ids to clean the checkpoint
       */
      def cleanup[T] = (_: T) => {
        //can fail with no real downsides
        val processed = Some(ids.keySet)
        for {
          _ <- IO(logger.info("Cleaning {} processed delegates from the checkpoint...", ids.size))
          cleaned <- runOnDb(TezosDb.cleanDelegatesCheckpoint(processed))
          _ <- IO(logger.info("Done cleaning {} delegates checkpoint rows.", cleaned))
        } yield ()
      }

      /* Streams the (unevaluated) incoming data, actually fetching the results.
       * We use combinators to keep the ongoing requests' flow under control.
       * The results are grouped to optimize for database storage.
       */
      val saveDelegates =
        node
          .getDelegates[IO](ids)
          .prefetchN(batchingConf.accountPageSize)
          .chunkN(batchingConf.accountPageSize)
          .evalMap(processDelegatesPage)
          .compile
          .foldMonoid
          .flatMap { rowCount =>
            IO(logger.info("{} delegates were touched on the database.", rowCount))
          }

      IO(logger.info("Ready to fetch updated delegates information from the chain")) >>
        saveDelegates
          .flatTap(cleanup)
          .handleErrorWith(toDelegatesProcessingFailure)

    }
  }

  /** Keeps track of time passed between different partial checkpoints of some entity processing
    * Designed to be partially applied to set properties of the whole process once, and then only compute partial completion
    *
    * @param entityName a string that will be logged to identify what kind of resource is being processed
    * @param totalToProcess how many entities there were in the first place
    * @param processStartNanos a nano-time from jvm monotonic time, used to identify when the whole processing operation began
    * @param processed how many entities were processed at the current checkpoint
    * @return an IO operation that will output the progress when executed
    */
  private[this] def logProcessingProgress(entityName: String, totalToProcess: Int, processStartNanos: Long)(
    processed: Int
  ): IO[Unit] =
    for {
      currentNanos <- getNanos
      elapsed = currentNanos - processStartNanos
      progress = processed.toDouble / totalToProcess
      etaMins = Duration(scala.math.ceil(elapsed / progress) - elapsed, NANOSECONDS).toMinutes
      _ <- IO.shift //log from another thread to keep processing other blocks
      _ <- IO(logger.info("Completed processing {}% of total requested {}s", "%.2f".format(progress * 100), entityName))
      _ <- IO(
        logger.info(
          "Estimated throughput is {}/min.",
          "%d".format(processed / Duration(elapsed, NANOSECONDS).toMinutes)
        )
      ).whenA(elapsed > 60e9)
      _ <- IO(
        logger.info(
          "Estimated time to finish is {} minutes.",
          etaMins
        )
      ).whenA(processed < totalToProcess && etaMins > 1)
    } yield ()
}
