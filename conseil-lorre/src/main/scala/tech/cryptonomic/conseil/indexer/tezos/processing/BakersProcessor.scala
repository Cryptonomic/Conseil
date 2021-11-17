package tech.cryptonomic.conseil.indexer.tezos.processing

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import akka.Done
import akka.stream.scaladsl.Source
import tech.cryptonomic.conseil.indexer.config.{BakingAndEndorsingRights, BatchFetchConfiguration}
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{
  Block,
  BlockReference,
  BlockTagged,
  Delegate,
  InvalidPositiveDecimal,
  PositiveBigNumber,
  PositiveDecimal,
  PublicKeyHash
}
import tech.cryptonomic.conseil.indexer.tezos.{TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.indexer.tezos.TezosNodeOperator.LazyPages
import tech.cryptonomic.conseil.indexer.tezos.TezosErrors.BakersProcessingFailed
import slick.jdbc.PostgresProfile.api._
import akka.stream.{ActorMaterializer, Materializer}

/** Takes care of fetching and processing any bakers' related data.
  *
  * @param nodeOperator access to the remote node to read data
  * @param db raw access to the slick database
  * @param batchingConf defines values for batching of data processing
  * @param rightsConf used to access configuration for baking/endorsing rights processing
  * @param mat needed to execute akka streams
  */
class BakersProcessor(
    nodeOperator: TezosNodeOperator,
    db: Database,
    batchingConf: BatchFetchConfiguration,
    rightsConf: BakingAndEndorsingRights
)(implicit mat: ActorMaterializer)
    extends ConseilLogSupport {

  /* Fetches the data from the chain node and stores bakers into the data store.
   * @param ids a pre-filtered map of delegate hashes with latest block referring to them
   */
  private def process(ids: Map[PublicKeyHash, BlockReference], onlyProcessLatest: Boolean = false)(
      implicit ec: ExecutionContext
  ): Future[Done] = {
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

    def logOutcome: PartialFunction[Try[(Option[Int], Option[Int])], Unit] = {
      case Success((rows, historyRows)) =>
        val showRows = rows.fold("Some")(String.valueOf)
        val showHistoryRows = historyRows.fold("Some")(String.valueOf)
        logger.info(s"$showRows bakers were touched on the database.")
        logger.info(s"$showHistoryRows baker history rows were added to the database.")
    }

    def cleanup = {
      //can fail with no real downsides
      val processed = Some(ids.keySet)
      logger.info(s"Cleaning ${ids.size} processed bakers from the checkpoint...")
      db.run(TezosDb.cleanBakersCheckpoint(processed))
        .map(cleaned => logger.info(s"Done cleaning $cleaned bakers checkpoint rows."))
    }

    //if needed, we get the stored levels and only keep updates that are more recent
    def prunedUpdates(): Future[Map[PublicKeyHash, BlockReference]] =
      if (onlyProcessLatest) db.run {
        TezosDb.getLevelsForBakers(ids.keySet).map { currentlyStored =>
          ids.filterNot {
            case (PublicKeyHash(pkh), BlockReference(_, updateLevel, _, _, _)) =>
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
        .mapAsync(1)(
          taggedBakers =>
            db.run(TezosDb.writeBakers(taggedBakers.toList))
              .andThen(logWriteFailure)
        )
        .runFold((Monoid[Option[Int]].empty, Monoid[Option[Int]].empty)) {
          case ((processedRows, processedHistoryRows), (justDone, justDoneHistory)) =>
            (processedRows |+| justDone) -> (processedHistoryRows |+| justDoneHistory)
        } andThen logOutcome

    val fetchAndStore = for {
      (bakerPages, _) <- prunedUpdates().map(nodeOperator.getBakersForBlocks)
      _ <- saveBakers(bakerPages) flatTap (_ => cleanup)
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

  private[tezos] def processBakersForBlocks(
      updates: List[BlockTagged[List[PublicKeyHash]]]
  )(
      implicit ec: ExecutionContext
  ): Future[Done] = {
    logger.info("Processing latest Tezos data for account bakers...")

    def keepMostRecent(associations: List[(PublicKeyHash, BlockReference)]): Map[PublicKeyHash, BlockReference] =
      associations.foldLeft(Map.empty[PublicKeyHash, BlockReference]) { (collected, entry) =>
        val key = entry._1
        if (collected.contains(key)) collected else collected + (key -> entry._2)
      }

    val sorted = updates.flatMap {
      case BlockTagged(ref, ids) =>
        ids.map(_ -> ref)
    }.sortBy {
      case (id, ref) => ref.level
    }(Ordering[Long].reverse)

    val toBeFetched = keepMostRecent(sorted)

    process(toBeFetched)
  }

  /** Fetches and stores all bakers from the latest blocks still in the checkpoint */
  private[tezos] def processTezosBakersCheckpoint()(implicit ec: ExecutionContext): Future[Done] = {
    logger.info("Selecting all bakers left in the checkpoint table...")
    db.run(TezosDb.getLatestBakersFromCheckpoint) flatMap { checkpoints =>
      if (checkpoints.nonEmpty) {
        logger.info(
          s"I loaded all of ${checkpoints.size} checkpointed ids from the DB and will proceed to fetch updated bakers information from the chain"
        )
        process(checkpoints, onlyProcessLatest = true)
      } else {
        logger.info("No data to fetch from the bakers checkpoint")
        Future.successful(Done)
      }
    }
  }

  /** Updates bakers in the DB */
  private[tezos] def updateBakersBalances(blocks: List[Block])(
      implicit ec: ExecutionContext
  ): Future[Unit] = {
    import cats.implicits._
    logger.info("Updating Bakers table")
    blocks
      .find(_.data.header.level % rightsConf.cycleSize == 1)
      .traverse { block =>
        val bakingRights = db.run(TezosDb.getBakingRightsForLevel(block.data.header.level))
        val endorsingRights = db.run(TezosDb.getEndorsingRightsForLevel(block.data.header.level))
        val bakersFromDb = db.run(TezosDb.getBakers())
        for {
          br <- bakingRights
          er <- endorsingRights
          distinctDelegateKeys = (br.toList.map(_.delegate) ::: er.toList.map(_.delegate)).distinct.map(PublicKeyHash)
          delegates <- nodeOperator.getDelegatesForBlock(distinctDelegateKeys, block.data.hash)
          bakers <- bakersFromDb
          updatedBakers = applyUpdatesToBakers(delegates, bakers.toList)
          _ <- db.run(TezosDb.writeBakers(updatedBakers))
        } yield ()
      }
      .void
  }

  /** Helper method for updating BakerRows */
  private def applyUpdatesToBakers(
      delegates: Map[PublicKeyHash, Delegate],
      bakers: List[Tables.BakersRow]
  ): List[Tables.BakersRow] = {

    /** Extracts balance from PositiveBigNumber */
    def extractBalance(balance: PositiveBigNumber): Option[BigDecimal] = balance match {
      case PositiveDecimal(value) => Some(value)
      case InvalidPositiveDecimal(_) => None
    }

    def findUpdateDelegate(baker: Tables.BakersRow) = delegates.get(PublicKeyHash(baker.pkh))

    bakers.map(baker => baker -> findUpdateDelegate(baker)).collect {
      case (baker, Some(delegate)) =>
        baker.copy(
          balance = extractBalance(delegate.balance),
          frozenBalance = extractBalance(delegate.frozen_balance),
          stakingBalance = extractBalance(delegate.staking_balance),
          rolls = delegate.rolls.getOrElse(0),
          delegatedBalance = extractBalance(delegate.delegated_balance),
          deactivated = delegate.deactivated
        )
    }
  }

}
