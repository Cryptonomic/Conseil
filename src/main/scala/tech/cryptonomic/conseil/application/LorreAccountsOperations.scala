package tech.cryptonomic.conseil.application

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.config.ChainEvent
import tech.cryptonomic.conseil.util.IOUtils.{lift, IOLogging}
import tech.cryptonomic.conseil.tezos.{ApiOperations, TezosNodeOperator, TezosTypes, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  Account,
  AccountId,
  BlockHash,
  BlockReference,
  BlockTagged,
  Protocol4Delegate,
  PublicKeyHash
}
import cats.Foldable
import cats.syntax.all._
import cats.effect.{ContextShift, IO}
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.{ExecutionContext, Future}

object AccountsOperations {
  type AccountsIndex = Map[AccountId, Account]
  type AccountFetchingResults = List[BlockTagged[AccountsIndex]]

  /** Something went wrong during handling of Accounts */
  case class AccountsProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException
}

class AccountsOperations(
    delegateOps: DelegatesOperations,
    batching: Int,
    nodeOperator: TezosNodeOperator,
    db: Database,
    api: ApiOperations
)(
    implicit ec: ExecutionContext,
    cs: ContextShift[IO]
) extends LorreProcessingUtils
    with LazyLogging
    with IOLogging {

  import AccountsOperations.{AccountFetchingResults, AccountsIndex, AccountsProcessingFailed}
  import delegateOps.storeDelegatesCheckpoint
  import DelegatesOperations.DelegateKeys
  import ChainEvent.{AccountUpdatesEvents, AccountIdPattern}

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
      .flatTap(LorreAccountsProcessingLog.accountsStored)
      .onError(LorreAccountsProcessingLog.failedToStoreAccounts)

  /** Reads account ids referenced by previous blocks from the checkpoint table */
  def getAccountsCheckpoint: IO[Map[AccountId, BlockReference]] =
    lift(db.run(TezosDb.getLatestAccountsFromCheckpoint))
      .flatTap(LorreAccountsProcessingLog.accountsCheckpointRead)
      .onError(LorreAccountsProcessingLog.failedToReadAccountsCheckpoints)

  /* Writes the accounts references to the checkpoint (a sort of write-ahead-log) */
  def storeAccountsCheckpoint(ids: List[BlockTagged[List[AccountId]]]): IO[Option[Int]] =
    lift(db.run(TezosDb.writeAccountsCheckpoint(ids.par.map(_.asTuple).toList)))
      .flatTap(LorreAccountsProcessingLog.accountsCheckpointed)
      .onError(LorreAccountsProcessingLog.failedToCheckpointAccounts)

  /** Removes entries from the accounts checkpoint table */
  def cleanAccountsCheckpoint(ids: Set[AccountId]): IO[Int] =
    lift(db.run(TezosDb.cleanAccountsCheckpoint(Some(ids))))
      .flatTap(LorreAccountsProcessingLog.accountsCheckpointCleanup)
      .onError(LorreAccountsProcessingLog.failedCheckpointAccountsCleanup)

  /* Fills the checkpoint with all existing account ids stored, based on the passed block reference */
  def refreshAllAccountsInCheckpoint(
      ref: BlockReference,
      selectors: Set[AccountIdPattern] = Set(".*")
  ): IO[Option[Int]] = {
    val (hashRef, levelRef, Some(timestamp), cycle) = ref

    lift(db.run(TezosDb.refillAccountsCheckpointFromExisting(hashRef, levelRef, timestamp, cycle, selectors)))
      .flatTap(LorreAccountsProcessingLog.accountsRefreshStored)
      .onError(LorreAccountsProcessingLog.failedToRefreshAccounts)
  }

  /* Records the fact that the passed-in levels are already processed w.r.t. refreshing accounts' data */
  def storeProcessedAccountEvents(pastLevels: List[Int]): IO[Option[Int]] =
    lift(db.run(TezosDb.writeProcessedEventsLevels(ChainEvent.accountsRefresh.render, pastLevels.map(BigDecimal(_)))))
      .flatTap(LorreAccountsProcessingLog.accountEventsProcessed)
      .onError(LorreAccountsProcessingLog.failedAccountEventsProcessed)

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
      case BlockTagged(blockHash, blockLevel, timestamp, cycle, accountsMap) =>
        import TezosTypes.Syntax._
        val keys = accountsMap.values.toList.mapFilter(extractKey)
        keys.taggedWithBlock(blockHash, blockLevel, timestamp, cycle)
    }
  }

  /* Possibly updates all accounts if the current block level is past any of the given ones
   * @param storedLevel the currently stored head level in conseil
   * @param events the relevant levels that calls for a refresh
   * @return the event levels to process left after the process
   */
  def processAccountRefreshes(storedLevel: Int, events: AccountUpdatesEvents): IO[AccountUpdatesEvents] =
    if (events.nonEmpty && events.exists(_._1 <= storedLevel)) {
      val (past, toCome) = events.partition(_._1 <= storedLevel)
      val (levels, selectors) = past.unzip
      for {
        _ <- liftLog(
          _.info(
            "A block was reached that requires an update of account data as specified in the configuration file. A full refresh is now underway. Relevant block eventLevels: {}",
            levels.mkString(", ")
          )
        )
        refreshBlock <- lift(api.fetchBlockAtLevel(levels.max))
        _ <- refreshBlock match {
          case Some(referenceBlockForRefresh) =>
            val blockRef =
              (
                BlockHash(referenceBlockForRefresh.hash),
                referenceBlockForRefresh.level,
                Some(referenceBlockForRefresh.timestamp.toInstant),
                referenceBlockForRefresh.metaCycle
              )
            refreshAllAccountsInCheckpoint(blockRef, selectors.toSet) >>
              storeProcessedAccountEvents(levels.toList)
          case None =>
            liftLog(
              _.warn(
                "I couldn't find in Conseil the block data at level {}, required for the general accounts update, and this is actually unexpected. I'll retry the whole operation at next cycle.",
                levels.max
              )
            )
        }
      } yield toCome
    } else IO.pure(events)

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

/* Collects logging operation for storage outcomes of different entities */
object LorreAccountsProcessingLog extends LazyLogging with IOLogging {

  val accountsCheckpointCleanup = (count: Int) =>
    liftLog(
      _.info("Done cleaning {} checkpoint account rows", count)
    )

  val failedCheckpointAccountsCleanup: PartialFunction[Throwable, IO[Unit]] = {
    case t =>
      liftLog(_.error("Could not cleanup the accounts checkpoint log", t))
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

  val accountsRefreshStored = (count: Option[Int]) =>
    liftLog(
      _.info(
        "Checkpoint stored for{} account updates in view of the full refresh.",
        count.fold("")(" " + _)
      )
    )

  val failedToRefreshAccounts: PartialFunction[Throwable, IO[Unit]] = {
    case t =>
      liftLog(_.error("I failed to store the accounts refresh updates in the checkpoint", t))
  }

  val accountEventsProcessed = (_: Option[Int]) =>
    liftLog(_.info("Stored processed levels for chain events relative to accounts needing refresh"))

  val failedAccountEventsProcessed: PartialFunction[Throwable, IO[Unit]] = {
    case t =>
      liftLog(_.warn("I failed to store processed levels for chain events relative to accounts needing refresh", t))
  }

}
