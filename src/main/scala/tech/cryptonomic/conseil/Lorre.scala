package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import com.typesafe.scalalogging.{LazyLogging, Logger}
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.concurrent.duration.Duration
import scala.util.control.NonFatal

import cats.Foldable
import cats.syntax.all._
import cats.effect.{ContextShift, ExitCode, IO, IOApp, Resource}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.io.MainOutputs.LorreOutput
import tech.cryptonomic.conseil.config.{Custom, Depth, Everything, LorreAppConfig, Newest, Platforms}
import tech.cryptonomic.conseil.util.DatabaseUtil
import tech.cryptonomic.conseil.tezos.{
  ApiOperations,
  FeeOperations,
  TezosNodeInterface,
  TezosNodeOperator,
  TezosTypes,
  TezosDatabaseOperations => TezosDb
}
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  Account,
  AccountId,
  Block,
  BlockData,
  BlockHash,
  BlockReference,
  BlockTagged,
  Delegate,
  Protocol4Delegate,
  PublicKeyHash
}

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorrev2 extends IOApp with LazyLogging with LorreAppConfig with LorreOutput with ProcessingUtils {

  //reads all configuration upstart, will only proceed if all values are found
  override def run(args: List[String]): IO[ExitCode] =
    loadApplicationConfiguration(args)
      .flatMap(conf => setupResources(conf) use runConfigurationWithResources(conf))
      .as(ExitCode.Success)

  /* Given a valid configuration and a set of resources, executes the actual program */
  private def runConfigurationWithResources(
      conf: LorreAppConfig.CombinedConfiguration
  )(resources: (Database, ActorSystem, TezosNodeOperator, ApiOperations)): IO[Unit] = {
    lazy val ignoreProcessFailures = {
      val envConf = sys.env.get(LORRE_FAILURE_IGNORE_VAR)
      envConf.exists(ignore => ignore.toLowerCase == "true" || ignore.toLowerCase == "yes")
    }
    //shared resources available for any operation
    val (db, system, node, api) = resources

    //brings the execution context in scope
    import system.dispatcher

    //to use the actor system thread pool for concurrency, considering IOApp already provides a global one
    val contextShift: ContextShift[IO] = IO.contextShift(dispatcher)

    //bring in scope all useful operations to be combined later
    val blocksProcessor = new BlocksProcessor()(node, db, system.dispatcher, contextShift)
    val delegatesProcessor =
      new DelegatesProcessor(conf.batching.blockPageSize)(node, db, system.dispatcher, contextShift)
    val accountsProcessor =
      new AccountsProcessor(delegatesProcessor, conf.batching.blockPageSize)(node, db, system.dispatcher, contextShift)
    import BlocksProcessor.BlocksProcessingFailed
    import AccountsProcessor.AccountsProcessingFailed
    import DelegatesProcessor.DelegatesProcessingFailed
    //simplifies the calls
    import blocksProcessor.{
      extractAccountRefs,
      fetchAndStoreBakingAndEndorsingRights,
      fetchAndStoreVotesForBlocks,
      forEachBlockPage,
      storeBlocks
    }
    import accountsProcessor.{processAccountsAndGetDelegateKeys, storeAccountsCheckpoint}
    import delegatesProcessor.processDelegates
    import conf.lorre.{
      bootupConnectionCheckTimeout,
      bootupRetryInterval,
      depth => configuredDepth,
      headHash => configuredHead,
      numberOfFeesAveraged,
      feeUpdateInterval
    }

    //IO action to show up a startup log
    val showConfig = IO(displayInfo(conf.tezos)) >>
          IO(
            displayConfiguration(
              Platforms.Tezos,
              conf.tezos,
              conf.lorre,
              (LORRE_FAILURE_IGNORE_VAR, ignoreProcessFailures)
            )
          ).whenA(conf.verbose.on)

    //the main program sequence, it's just a description that is yet to be run
    val mainLoop = for {
      head <- checkTezosConnection(node, bootupConnectionCheckTimeout, bootupRetryInterval)
      conseilLevel <- fetchMaxLevelOnConseil(api)
      _ <- liftLog(_.info("Processing Tezos Blocks..."))
      start <- timer.clock.monotonic(NANOSECONDS)
      toSync <- fetchBlockPagesToSync(node, configuredDepth, configuredHead)(head, conseilLevel)
      (blockPages, total) = toSync
      blocksWritten <- forEachBlockPage(blockPages, logProcessingProgress(total, start)) { blockPage =>
        val blocks = blockPage.map { case (block, _) => block }
        val touchedAccountIds = extractAccountRefs(blockPage)

        for {
          _ <- storeAccountsCheckpoint(touchedAccountIds)
          storedBlocks <- storeBlocks(blockPage)
          _ <- fetchAndStoreVotesForBlocks(blocks)
          _ <- fetchAndStoreBakingAndEndorsingRights(blocks.map(_.data.hash))
          _ <- liftLog(_.info("Processing latest Tezos data for updated accounts..."))
          delegateKeys <- processAccountsAndGetDelegateKeys(discardOldestDuplicates(touchedAccountIds))
          _ <- liftLog(_.info("Processing latest Tezos data for account delegates..."))
          _ <- processDelegates(discardOldestDuplicates(delegateKeys))
          //retry processing leftovers in the checkpoints
          _ <- processCheckpoints(accountsProcessor, delegatesProcessor)
        } yield storedBlocks

      }

    } yield blocksWritten

    //the possibly repeated process
    val processing = configuredDepth match {
      case Newest =>
        //in this case we do the same thing over and over, after short breaks
        val repeatStep = (iteration: Int) =>
          for {
            _ <- mainLoop
            _ <- lift(FeeOperations.processTezosAverageFees(numberOfFeesAveraged))(contextShift)
              .whenA(iteration % feeUpdateInterval == 0)
            _ <- liftLog(_.info("Taking a nap."))
            _ <- IO.sleep(conf.lorre.sleepInterval)
          } yield (iteration + 1) % Int.MaxValue

        //builds a stream over the iterated step, and then runs it ignoring the result value
        fs2.Stream.iterateEval(0)(repeatStep).compile.drain
      case _ =>
        //this will run once and be done
        mainLoop <* liftLog(_.info("Synchronization is done."))
    }

    //combine the pieces, adding custom error handling if required
    showConfig >> processing.handleErrorWith {
      case f @ (AccountsProcessingFailed(_, _) | BlocksProcessingFailed(_, _) | DelegatesProcessingFailed(_, _))
          if ignoreProcessFailures =>
        liftLog(_.error("Failed processing but will keep on going next cycle", f))
    }.void
  }

  /** Tries to fetch blocks head to verify if connection with Tezos node was successfully established
    * It retries consistently upon failure
    */
  private def checkTezosConnection(
      nodeOperator: TezosNodeOperator,
      bootupConnectionCheckTimeout: FiniteDuration,
      bootupRetryInterval: FiniteDuration
  ): IO[BlockData] = {
    val tryFetchHead =
      lift(nodeOperator.getBareBlockHead())
        .timeout(bootupConnectionCheckTimeout) <* liftLog(_.info("Successfully made initial connection to Tezos"))

    tryFetchHead.handleErrorWith(
      error =>
        liftLog(_.error("Could not make initial connection to Tezos", error)) >>
            IO.sleep(bootupRetryInterval) >>
            checkTezosConnection(nodeOperator, bootupConnectionCheckTimeout, bootupRetryInterval)
    )
  }

  /* Read conseil to fetch the highest block level stored */
  private def fetchMaxLevelOnConseil(api: ApiOperations)(implicit ec: ExecutionContext): IO[Int] =
    lift(api.fetchMaxLevel())

  /* Based on input configuration, lazily fetch the required pages of blocks from the node */
  private def fetchBlockPagesToSync(
      nodeOperator: TezosNodeOperator,
      depth: Depth,
      headHash: Option[BlockHash]
  )(currentHead: BlockData, maxLevelStoredOnConseil: Int): IO[nodeOperator.PaginatedBlocksResults] = depth match {
    case Newest => nodeOperator.getBlocksNotInDatabase(currentHead, maxLevelStoredOnConseil).pure[IO]
    case Everything => lift(nodeOperator.getLatestBlocks(head = Left(currentHead)))
    case Custom(n) => lift(nodeOperator.getLatestBlocks(head = headHash.toRight(currentHead), depth = Some(n)))
  }

  /* grabs all remainig data in the checkpoints tables and processes them */
  private def processCheckpoints(
      accountsProcessor: AccountsProcessor,
      delegatesProcessor: DelegatesProcessor
  ): IO[Unit] = {
    import accountsProcessor.{getAccountsCheckpoint, processAccountsAndGetDelegateKeys}
    import delegatesProcessor.{getDelegatesCheckpoint, processDelegates}

    for {
      _ <- liftLog(_.info("Selecting all accounts left in the checkpoint table..."))
      accountsCheckpoint <- getAccountsCheckpoint
      _ <- processAccountsAndGetDelegateKeys(accountsCheckpoint).unlessA(accountsCheckpoint.isEmpty).void
      _ <- liftLog(_.info("Selecting all delegates left in the checkpoint table..."))
      delegatesCheckpoint <- getDelegatesCheckpoint
      _ <- processDelegates(delegatesCheckpoint).unlessA(delegatesCheckpoint.isEmpty)
    } yield ()
  }

  /* Starts from a valid configuration, to build external resources access
   * e.g. Database, rpc node, actor system
   * the allocated values are wrapped as Resources to allow guaranteed release-safety using the "bracket" pattern
   * i.e. providing effectful creation and release operations to be called at the right moment
   */
  private def setupResources(
      config: LorreAppConfig.CombinedConfiguration
  ): Resource[IO, (Database, ActorSystem, TezosNodeOperator, ApiOperations)] = {
    val LorreAppConfig.CombinedConfiguration(
      lorreConf,
      tezosConf,
      callsConf,
      streamingClientConf,
      batchingConf,
      verbose
    ) = config

    //database write access
    val db = Resource.fromAutoCloseable(IO(DatabaseUtil.lorreDb)).widen[Database]

    //access to higher level operations on conseil's stored data
    val apiOps = Resource.make(
      acquire = IO(new ApiOperations)
    )(
      release = api =>
        liftLog(_.info("Releasing the conseil API resources")) >>
            IO(api.release())
    )

    //both system and dispatcher are needed for all async operations in code to follow
    val actorSystem = Resource.make(
      acquire = IO(ActorSystem("lorre-system"))
    )(
      release = actorSys =>
        liftLog(_.info("Terminating the Actor System")) >>
            lift(actorSys.terminate()).void
    )

    //access to tezos node rpc
    def nodeRpc(implicit system: ActorSystem) =
      Resource.make(
        acquire = IO(new TezosNodeInterface(tezosConf, callsConf, streamingClientConf))
      )(
        release = rpc =>
          liftLog(_.info("Shutting down the blockchain node interface")) >>
              lift(rpc.shutdown()).void
      )

    //using this as a trick to wrap acquisition and release of the whole resource stack with logs
    def logging =
      Resource.make(
        acquire = liftLog(_.info("Acquiring resources based on config"))
      )(
        release = _ => liftLog(_.info("All things closed"))
      )

    //high-level api to fetch data from the tezos node
    val node = for {
      _ <- logging
      sys <- actorSystem
      api <- apiOps
      rpc <- nodeRpc(sys)
    } yield new TezosNodeOperator(rpc, tezosConf.network, batchingConf, api)(sys.dispatcher)

    (db, actorSystem, node, apiOps).tupled
  }

  /* Logs the tracked progress time of blocks processing.
   * Designed to be partially applied to set properties of the whole process once, and then only compute partial completion
   *
   * @param totalToProcess how many entities there were in the first place
   * @param processStartNanos a nano-time from jvm monotonic time, used to identify when the whole processing operation began
   * @param processed how many entities were processed at the current checkpoint
   * @param cs needed to shift logical threads of execution
   * @param timer needed to get the current nano-time as an effect
   */
  private def logProcessingProgress(totalToProcess: Int, processStartNanos: Long)(processed: Int): IO[Unit] =
    for {
      currentNanos <- timer.clock.monotonic(NANOSECONDS)
      elapsed = currentNanos - processStartNanos
      progress = processed.toDouble / totalToProcess
      eta = if (elapsed * progress > 0) Duration(scala.math.ceil(elapsed / progress) - elapsed, NANOSECONDS)
      else Duration.Inf
      _ <- IO.shift //log from another thread to keep processing other blocks
      _ <- liftLog(
        _.info("Completed processing {}% of total requested blocks", "%.2f".format(progress * 100))
      )
      _ <- liftLog(
        _.info(
          "Estimated average throughput is {}/min.",
          "%d".format(processed / Duration(elapsed, NANOSECONDS).toMinutes)
        )
      ).whenA(elapsed > 60e9)
      _ <- liftLog(
        _.info(
          "Estimated time to finish is {} hours and {} minutes.",
          eta.toHours,
          eta.toMinutes % 60
        )
      ).whenA(processed < totalToProcess && eta.isFinite && eta.toMinutes > 1)
    } yield ()

}

/* Provides common operations useful to generic processing of paginated data from the chain */
private[conseil] trait ProcessingUtils {
  self: LazyLogging =>

  /* lift side-effecting logging calls into a pure IO context */
  protected def liftLog(impureLogging: Logger => Unit): IO[Unit] =
    IO(impureLogging(logger))

  /* lift side-effecting future calls into a pure IO context */
  protected def lift[T](future: => Future[T])(implicit cs: ContextShift[IO]): IO[T] =
    IO.fromFuture(IO(future))

  /* Converts the iterator to a, combinator-rich, pure, IO-based stream
   *
   * @param pages lazy iterator of async results
   * @param adaptFetchError converts any error during processing to another, using this function
   * @param cs needed to handle concurrent handling of IO values
   * @return the stream of contents
   */
  protected def streamPages[Content](
      pages: Iterator[Future[Content]],
      adaptFetchError: Throwable => Throwable = identity
  )(implicit cs: ContextShift[IO]): fs2.Stream[IO, Content] =
    fs2.Stream
      .fromIterator[IO](pages.map(lift(_))) //converts the futures to IO values, delaying computation
      .evalMap(identity) //this will unwrap a layer of IO
      .adaptErr {
        case NonFatal(error) => adaptFetchError(error) //use a custom error for common failures
      }

  /* Converts a stream of lists such that the processing chunk
   * has the desired size
   * @param pageSize the size of the output stream chunks
   */
  protected def ensurePageSize[Elem](pageSize: Int): fs2.Pipe[IO, List[Elem], List[Elem]] = {
    import cats.instances.list._
    it =>
      it.foldMonoid.repartition { elems =>
        fs2.Chunk.iterable(elems.grouped(pageSize).toIterable)
      }
  }

  /* "Unpacks" lists of block-referenced elements and removes any duplicate by
   * keeping those at the highest level
   */
  protected def discardOldestDuplicates[T](taggedLists: List[BlockTagged[List[T]]]) = {
    /* Scans the association and puts them in a map adding only missing elements
     * which are asssumed to be sorted by highest block level first
     */
    def collectMostRecent(associations: List[(T, BlockReference)]): Map[T, BlockReference] =
      associations.foldLeft(Map.empty[T, BlockReference]) {
        case (collectedMap, pair) =>
          if (collectedMap.contains(pair._1)) collectedMap else collectedMap + pair
      }

    val sorted = taggedLists.flatMap {
      case BlockTagged(hash, level, timestamp, elements) =>
        elements.map(_ -> (hash, level, timestamp))
    }.sortBy {
      case (element, (hash, level, timestamp)) => level
    }(Ordering[Int].reverse)

    collectMostRecent(sorted)
  }

  /* Collects logging operation for storage outcomes of different entities */
  protected object ProcessLogging {
    val blocksStored = (count: Int) =>
      liftLog(
        _.info("Wrote {} blocks to the database", count)
      )

    val failedToStoreBlocks: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not write blocks to the database", t))
    }

    val votesStored = (count: Option[Int]) =>
      liftLog(
        _.info("Wrote {} block voting data records to the database", count.getOrElse("the"))
      )

    val failedToStoreVotes: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not write voting data to the database", t))
    }

    val blockRightsStored = (count: Option[Int]) =>
      liftLog(
        _.info("Wrote {} baking and endorsing rights to the database", count.getOrElse("the"))
      )

    val failedToStoreRights: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not write baking and endorsing rights to the database", t))
    }

    val accountsCheckpointRead = (checkpoints: Map[AccountId, BlockReference]) =>
      if (checkpoints.nonEmpty)
        liftLog(
          _.info(
            "I loaded all of {} checkpointed ids from the DB and will proceed to fetch updated accounts information from the chain",
            checkpoints.size
          )
        )
      else
        liftLog(_.info("No data to fetch from the accounts checkpoint"))

    val failedToReadAccountsCheckpoints: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not read accounts from the checkpoint log", t))
    }

    val accountsCheckpointed = (count: Option[Int]) =>
      liftLog(
        _.info("Wrote {} account updates to the checkpoint log", count.getOrElse("the"))
      )

    val failedToCheckpointAccounts: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not write accounts to the checkpoint log", t))
    }

    val accountsStored = (count: Int) =>
      liftLog(
        _.info("Added {} new accounts to the database", count)
      )

    val failedToStoreAccounts: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not write accounts to the database", t))
    }

    val delegatesCheckpointRead = (checkpoints: Map[PublicKeyHash, BlockReference]) =>
      if (checkpoints.nonEmpty)
        liftLog(
          _.info(
            "I loaded all of {} checkpointed ids from the DB and will proceed to fetch updated delegates information from the chain",
            checkpoints.size
          )
        )
      else liftLog(_.info("No data to fetch from the delegates checkpoint"))

    val failedToReadDelegatesCheckpoints: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not read delegates from the checkpoint log", t))
    }

    val delegatesCheckpointed = (count: Option[Int]) =>
      liftLog(
        _.info("Wrote {} delegate keys updates to the checkpoint log", count.getOrElse("the"))
      )

    val failedToCheckpointDelegates: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not write delegates to the checkpoint log", t))
    }

    val delegatesStored = (count: Int) =>
      liftLog(
        _.info("Added {} new delegates to the database", count)
      )

    val failedToStoreDelegates: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not write delegates to the database", t))
    }

    val accountsCheckpointCleanup = (count: Int) =>
      liftLog(
        _.info("Done cleaning {} checkpoint account rows", count)
      )

    val failedCheckpointAccountsCleanup: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not cleanup the accounts checkpoint log", t))
    }

    val delegatesCheckpointCleanup = (count: Int) =>
      liftLog(
        _.info("Done cleaning {} checkpoint delegate rows", count)
      )

    val failedCheckpointDelegatesCleanup: PartialFunction[Throwable, IO[Unit]] = {
      case t =>
        liftLog(_.error("Could not cleanup the delegates checkpoint log", t))
    }
  }

}

private[conseil] object BlocksProcessor {

  /** Something went wrong during handling of Blocks or related sub-data */
  case class BlocksProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException
}

/* Provides operations related to blocks */
private[conseil] class BlocksProcessor(
    implicit nodeOperator: TezosNodeOperator,
    db: Database,
    ec: ExecutionContext,
    cs: ContextShift[IO]
) extends LazyLogging
    with ProcessingUtils {
  import BlocksProcessor.BlocksProcessingFailed

  /* Write the blocks to the db */
  def storeBlocks(blockResults: nodeOperator.BlockFetchingResults): IO[Int] = {
    val blocks = blockResults.map { case (block, _) => block }
    lift(db.run(TezosDb.writeBlocks(blocks)))
      .map(_ => blocks.size)
      .flatTap(ProcessLogging.blocksStored)
      .onError(ProcessLogging.failedToStoreBlocks)

  }

  /* Fetches voting data for the blocks and stores any relevant
   * result into the appropriate database table
   */
  def fetchAndStoreVotesForBlocks(blocks: List[Block]): IO[Option[Int]] = {
    import cats.instances.list._
    import cats.instances.option._
    import cats.instances.int._
    import slickeffect.implicits._

    lift(nodeOperator.getVotingDetails(blocks)).flatMap {
      case (_, bakersBlock, ballotsBlock) =>
        //this is a nested list, each block with many baker rolls
        val writeBakers = bakersBlock.traverse {
          case (block, bakersRolls) => TezosDb.writeVotingRolls(bakersRolls, block)
        }

        val combinedVoteWrites = writeBakers.map(_.combineAll.map(_ + ballotsBlock.size))

        lift(db.run(combinedVoteWrites.transactionally))
    }.flatTap(ProcessLogging.votesStored)
      .onError(ProcessLogging.failedToStoreVotes)
  }

  def fetchAndStoreBakingAndEndorsingRights(blockHashes: List[BlockHash]): IO[Option[Int]] = {
    import slickeffect.implicits._
    import cats.implicits._
    (lift(nodeOperator.getBatchBakingRights(blockHashes)), lift(nodeOperator.getBatchEndorsingRights(blockHashes))).mapN {
      case (bakes, endorses) =>
        (TezosDb.writeBakingRights(bakes), TezosDb.writeEndorsingRights(endorses))
          .mapN(_ |+| _) //generic sum works over Option[Int], taking care of the optionality
    }.flatMap(dbOp => lift(db.run(dbOp.transactionally)))
      .flatTap(ProcessLogging.blockRightsStored)
      .onError(ProcessLogging.failedToStoreRights)
  }

  /* adapts the page processing to fetched blocks */
  def forEachBlockPage(
      pages: Iterator[Future[nodeOperator.BlockFetchingResults]],
      notifyProgress: Int => IO[Unit]
  )(handlePage: nodeOperator.BlockFetchingResults => IO[Int]) = {
    import cats.instances.int._

    val fetchErrorAdapter = (source: Throwable) =>
      BlocksProcessingFailed("Could not fetch blocks from the client", source)

    streamPages(pages, fetchErrorAdapter)
      .evalMap(handlePage) //processes each page
      .scanMonoid //accumulates results, e.g. blocks done, delegate keys in accounts
      .filter(_ != 0) //no use to print the first empty result
      .evalTap(notifyProgress)
      .compile
      .last
  }

  /** Re-organize the data, wrapping the account ids into a block tag wrapping */
  def extractAccountRefs(blockResults: nodeOperator.BlockFetchingResults): List[BlockTagged[List[AccountId]]] = {
    import TezosTypes.Syntax._
    blockResults.map {
      case (block, accounts) => accounts.taggedWithBlock(block.data)
    }
  }
}

private[this] object AccountsProcessor {
  type AccountsIndex = Map[AccountId, Account]
  type AccountFetchingResults = List[BlockTagged[AccountsIndex]]

  /** Something went wrong during handling of Accounts */
  case class AccountsProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException
}

/* dedicated to accounts processing steps */
private[conseil] class AccountsProcessor(delegateProcessor: DelegatesProcessor, batching: Int)(
    implicit nodeOperator: TezosNodeOperator,
    db: Database,
    ec: ExecutionContext,
    cs: ContextShift[IO]
) extends LazyLogging
    with ProcessingUtils {
  import AccountsProcessor.{AccountFetchingResults, AccountsIndex, AccountsProcessingFailed}
  import delegateProcessor.storeDelegatesCheckpoint
  import DelegatesProcessor.DelegateKeys

  /** Fetches the referenced accounts by ids and stores them returning the
    * referenced delegates by key, tagged with the block info
    */
  def processAccountsAndGetDelegateKeys(indices: Map[AccountId, BlockReference]) = {

    val (accounts, _) = nodeOperator.getAccountsForBlocks(indices)

    liftLog(_.info("Ready to fetch updated accounts information from the chain")) >>
      forEachAccountPage(accounts) { fetchResults =>
        val ids = fetchResults.flatMap {
          _.content.keys
        }.toSet
        val delegateReferences = extractDelegatesInfo(fetchResults)

        storeDelegatesCheckpoint(delegateReferences) >>
          storeAccounts(fetchResults) >>
          liftLog(_.info("Cleaning {} processed accounts from the checkpoint...", ids.size)) >>
          cleanAccountsCheckpoint(ids) >>
          delegateReferences.pure[IO]
      }

  }

  /** Writes accounts to the database */
  def storeAccounts(indices: List[BlockTagged[AccountsIndex]]): IO[Int] =
    lift(db.run(TezosDb.writeAccounts(indices)))
      .flatTap(ProcessLogging.accountsStored)
      .onError(ProcessLogging.failedToStoreAccounts)

  /** Reads account ids referenced by previous blocks from the checkpoint table */
  def getAccountsCheckpoint: IO[Map[AccountId, BlockReference]] =
    lift(db.run(TezosDb.getLatestAccountsFromCheckpoint))
      .flatTap(ProcessLogging.accountsCheckpointRead)
      .onError(ProcessLogging.failedToReadAccountsCheckpoints)

  /* Writes the accounts references to the checkpoint (a sort of write-ahead-log) */
  def storeAccountsCheckpoint(ids: List[BlockTagged[List[AccountId]]]): IO[Option[Int]] =
    lift(db.run(TezosDb.writeAccountsCheckpoint(ids.par.map(_.asTuple).toList)))
      .flatTap(ProcessLogging.accountsCheckpointed)
      .onError(ProcessLogging.failedToCheckpointAccounts)

  /** Removes entries from the accounts checkpoint table */
  def cleanAccountsCheckpoint(ids: Set[AccountId]): IO[Int] =
    lift(db.run(TezosDb.cleanAccountsCheckpoint(Some(ids))))
      .flatTap(ProcessLogging.accountsCheckpointCleanup)
      .onError(ProcessLogging.failedCheckpointAccountsCleanup)

  /** Reads delegation keys within the accounts information
    * @param indices the account data indexed by id and block
    * @return the delegate pkhs, paired with the relevant block
    */
  def extractDelegatesInfo(indices: List[BlockTagged[AccountsIndex]]): List[BlockTagged[DelegateKeys]] = {
    import cats.instances.list._

    def extractKey(account: Account): Option[PublicKeyHash] =
      PartialFunction.condOpt(account.delegate) {
        case Some(Right(pkh)) => pkh
        case Some(Left(Protocol4Delegate(_, Some(pkh)))) => pkh
      }

    indices.map {
      case BlockTagged(blockHash, blockLevel, timestamp, accountsMap) =>
        import TezosTypes.Syntax._
        val keys = accountsMap.values.toList.mapFilter(extractKey)
        keys.taggedWithBlock(blockHash, blockLevel, timestamp)
    }
  }

  /* adapts the page processing to fetched accounts, returning the included delegate keys */
  private def forEachAccountPage(
      pages: Iterator[Future[AccountFetchingResults]]
  )(handlePage: AccountFetchingResults => IO[List[BlockTagged[DelegateKeys]]]) = {
    import cats.instances.list._
    import cats.instances.option._

    val fetchErrorAdapter = (source: Throwable) =>
      AccountsProcessingFailed("Could not fetch accounts from the client", source)

    streamPages(pages, fetchErrorAdapter)
      .through(ensurePageSize(batching))
      .evalMap(handlePage) //processes each page
      .foldMonoid //accumulates results, i.e delegate keys in accounts
      .compile
      .last
      .map(Foldable[Option].fold(_)) //removes the outer optional layer, returning the empty list if nothing's there
  }

}

private[conseil] object DelegatesProcessor {
  type DelegatesIndex = Map[PublicKeyHash, Delegate]
  type DelegateFetchingResults = List[BlockTagged[DelegatesIndex]]
  type DelegateKeys = List[PublicKeyHash]

  /** Something went wrong during handling of Delegates */
  case class DelegatesProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException
}

/* dedicated to delegates processing steps */
private[conseil] class DelegatesProcessor(batching: Int)(
    implicit nodeOperator: TezosNodeOperator,
    db: Database,
    ec: ExecutionContext,
    cs: ContextShift[IO]
) extends LazyLogging
    with ProcessingUtils {
  import DelegatesProcessor.{DelegateFetchingResults, DelegateKeys, DelegatesIndex, DelegatesProcessingFailed}

  /** fetches the referenced delegates by keys and stores them */
  def processDelegates(keys: Map[PublicKeyHash, BlockReference]): IO[Unit] = {

    val (delegates, _) = nodeOperator.getDelegatesForBlocks(keys)

    liftLog(_.info("Ready to fetch updated delegates information from the chain")) >>
      forEachDelegatePage(delegates) { fetchResults =>
        val pagePKHs = fetchResults.flatMap {
          _.content.keys
        }.toSet

        storeDelegates(fetchResults) >>
          liftLog(_.info("Cleaning {} processed delegates from the checkpoint...", pagePKHs.size)) >>
          cleanDelegatesCheckpoint(pagePKHs)
      }.void

  }

  /** Writes delegates to the database */
  def storeDelegates(
      indices: List[BlockTagged[DelegatesIndex]]
  )(implicit ec: ExecutionContext, cs: ContextShift[IO], db: Database): IO[Int] =
    lift(db.run(TezosDb.writeDelegatesAndCopyContracts(indices)))
      .flatTap(ProcessLogging.delegatesStored)
      .onError(ProcessLogging.failedToStoreDelegates)

  /** Reads account ids referenced by previous blocks from the checkpoint table */
  def getDelegatesCheckpoint: IO[Map[PublicKeyHash, BlockReference]] =
    lift(db.run(TezosDb.getLatestDelegatesFromCheckpoint))
      .flatTap(ProcessLogging.delegatesCheckpointRead)
      .onError(ProcessLogging.failedToReadDelegatesCheckpoints)

  /** Writes delegate keys to download in the checkpoint table */
  def storeDelegatesCheckpoint(delegateKeys: List[BlockTagged[DelegateKeys]]): IO[Option[Int]] = {
    val keys = delegateKeys.map(_.asTuple)
    lift(db.run(TezosDb.writeDelegatesCheckpoint(keys)))
      .flatTap(ProcessLogging.delegatesCheckpointed)
      .onError(ProcessLogging.failedToCheckpointDelegates)
  }

  /** Removes entries from the accounts checkpoint table */
  def cleanDelegatesCheckpoint(keys: Set[PublicKeyHash]): IO[Int] =
    lift(db.run(TezosDb.cleanDelegatesCheckpoint(Some(keys))))
      .flatTap(ProcessLogging.delegatesCheckpointCleanup)
      .onError(ProcessLogging.failedCheckpointDelegatesCleanup)

  /* adapts the page processing to fetched delegates */
  private def forEachDelegatePage(
      pages: Iterator[Future[DelegateFetchingResults]]
  )(handlePage: DelegateFetchingResults => IO[Int]) = {
    import cats.instances.int._

    val fetchErrorAdapter = (source: Throwable) =>
      DelegatesProcessingFailed("Could not fetch delegates from the client", source)

    streamPages(pages, fetchErrorAdapter)
      .through(ensurePageSize(batching))
      .evalMap(handlePage) //processes each page
      .foldMonoid //accumulates results, i.e. stored delegates
      .compile
      .last

  }

}
