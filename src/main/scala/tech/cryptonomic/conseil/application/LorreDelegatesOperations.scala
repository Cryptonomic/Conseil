package tech.cryptonomic.conseil.application

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.util.IOUtils.{lift, IOLogging}
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.tezos.TezosTypes.{BlockReference, BlockTagged, Delegate, PublicKeyHash}
import cats.effect.IO
import cats.syntax.all._
import tech.cryptonomic.conseil.tezos.TezosNodeOperator
import scala.concurrent.{ExecutionContext, Future}
import cats.effect.ContextShift
import slick.jdbc.PostgresProfile.api._

object DelegatesOperations {
  type DelegatesIndex = Map[PublicKeyHash, Delegate]
  type DelegateFetchingResults = List[BlockTagged[DelegatesIndex]]
  type DelegateKeys = List[PublicKeyHash]

  /** Something went wrong during handling of Delegates */
  case class DelegatesProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException
}

/* dedicated to delegates processing steps */
class DelegatesOperations(batching: Int, nodeOperator: TezosNodeOperator, db: Database)(
    implicit ec: ExecutionContext,
    cs: ContextShift[IO]
) extends LorreProcessingUtils
    with LazyLogging
    with IOLogging {
  import DelegatesOperations.{DelegateFetchingResults, DelegateKeys, DelegatesIndex, DelegatesProcessingFailed}
  import TezosNodeOperator.LazyPages

  /** Fetches the referenced delegates by keys and stores them
    * @param keys a pre-filtered map of delegate hashes with latest block referring to them
    * @param onlyProcessLatest verify that no recent update was made to the delegate before processing each key
    *                          (default = false)
    */
  def processDelegates(keys: Map[PublicKeyHash, BlockReference], onlyProcessLatest: Boolean = false): IO[Unit] = {

    //we get accounts data, only considering more recent updates than stored, if so requested
    val pages: IO[LazyPages[DelegateFetchingResults]] = {
      if (onlyProcessLatest) prunedUpdates(keys)
      else IO.pure(keys)
    }.map(nodeOperator.getDelegatesForBlocks(_)._1)

    liftLog(_.info("Ready to fetch updated delegates information from the chain")) >>
      pages.flatMap(
        delegates =>
          forEachDelegatePage(delegates) { fetchResults =>
            val pagePKHs = fetchResults.flatMap {
              _.content.keys
            }.toSet

            storeDelegates(fetchResults) >>
              liftLog(_.info("Cleaning {} processed delegates from the checkpoint...", pagePKHs.size)) >>
              cleanDelegatesCheckpoint(pagePKHs)
          }.void
      )

  }

  /** Gets the stored levels and only keep updates that are more recent */
  private def prunedUpdates(keys: Map[PublicKeyHash, BlockReference]): IO[Map[PublicKeyHash, BlockReference]] =
    lift(db.run(TezosDb.getLevelsForDelegates(keys.keySet))).map { currentlyStored =>
      keys.filterNot {
        case (PublicKeyHash(pkh), (_, updateLevel, _, _)) =>
          currentlyStored.exists { case (storedPkh, storedLevel) => storedPkh == pkh && storedLevel > updateLevel }
      }
    }

  /** Writes delegates to the database */
  def storeDelegates(
      indices: List[BlockTagged[DelegatesIndex]]
  )(implicit ec: ExecutionContext, cs: ContextShift[IO]): IO[Int] =
    lift(db.run(TezosDb.writeDelegatesAndCopyContracts(indices)))
      .flatTap(LorreDelegatesProcessingLog.delegatesStored)
      .onError(LorreDelegatesProcessingLog.failedToStoreDelegates)

  /** Reads account ids referenced by previous blocks from the checkpoint table */
  def getDelegatesCheckpoint: IO[Map[PublicKeyHash, BlockReference]] =
    lift(db.run(TezosDb.getLatestDelegatesFromCheckpoint))
      .flatTap(LorreDelegatesProcessingLog.delegatesCheckpointRead)
      .onError(LorreDelegatesProcessingLog.failedToReadDelegatesCheckpoints)

  /** Writes delegate keys to download in the checkpoint table */
  def storeDelegatesCheckpoint(delegateKeys: List[BlockTagged[DelegateKeys]]): IO[Option[Int]] = {
    val keys = delegateKeys.map(_.asTuple)
    lift(db.run(TezosDb.writeDelegatesCheckpoint(keys)))
      .flatTap(LorreDelegatesProcessingLog.delegatesCheckpointed)
      .onError(LorreDelegatesProcessingLog.failedToCheckpointDelegates)
  }

  /** Removes entries from the accounts checkpoint table */
  def cleanDelegatesCheckpoint(keys: Set[PublicKeyHash]): IO[Int] =
    lift(db.run(TezosDb.cleanDelegatesCheckpoint(Some(keys))))
      .flatTap(LorreDelegatesProcessingLog.delegatesCheckpointCleanup)
      .onError(LorreDelegatesProcessingLog.failedCheckpointDelegatesCleanup)

  /* adapts the page processing to fetched delegates */
  private def forEachDelegatePage(
      pages: LazyPages[DelegateFetchingResults]
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

/* Collects logging operation for storage outcomes of different entities */
object LorreDelegatesProcessingLog extends LazyLogging with IOLogging {

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

  val delegatesCheckpointCleanup = (count: Int) =>
    liftLog(
      _.info("Done cleaning {} checkpoint delegate rows", count)
    )

  val failedCheckpointDelegatesCleanup: PartialFunction[Throwable, IO[Unit]] = {
    case t =>
      liftLog(_.error("Could not cleanup the delegates checkpoint log", t))
  }
}
