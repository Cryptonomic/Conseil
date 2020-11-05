package tech.cryptonomic.conseil.indexer.tezos.processing

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.collection.immutable.SortedSet
import tech.cryptonomic.conseil.common.config.ChainEvent
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BlockLevel, TezosBlockHash}
import tech.cryptonomic.conseil.indexer.tezos.{TezosIndexedDataOperations, TezosDatabaseOperations => TezosDb}
import slick.jdbc.PostgresProfile.api._

object AccountsResetHandler {

  /** Events on which to trigger accounts updates, as ordered pairs of
    * level and a pattern to decide which accounts needs to be reloaded.
    */
  type AccountResetEvents = SortedSet[(BlockLevel, ChainEvent.AccountIdPattern)]

  /** A wrapper type to semantically identify a set of events for
    * accounts reset that still haven't been processed by the handler.
    * They will contain the events for block level yet not reached by
    * the chain indexing.
    *
    * @param events left to process yet
    */
  case class UnhandledResetEvents(events: AccountResetEvents = SortedSet.empty) extends AnyVal

  /* shortcut definition to unclutter the code */
  private lazy val NoEventsToHandle = Future.successful(UnhandledResetEvents())
}

/** We capture global chain events that requires to refresh accounts
  * data from the remote node.
  * The original event to require such out-of-band processing was ad-hoc
  * airdrop of mutez after a protocol upgrade.
  * We identify when the refresh is needed as a specific block level, and
  * restrict which accounts are affected via a pattern.
  *
  * @param db raw access to the underlying slick database
  * @param indexedData access to the operations on locally indexed data
  */
class AccountsResetHandler(
    db: Database,
    indexedData: TezosIndexedDataOperations
) extends ConseilLogSupport {
  import AccountsResetHandler._

  /** Finds unprocessed levels requiring accounts reset (i.e. when there is a need to reload data for multiple accounts from the chain) */
  private[tezos] def unprocessedResetRequestLevels(
      events: List[ChainEvent]
  )(implicit ec: ExecutionContext): Future[AccountResetEvents] =
    events.collectFirst {
      case ChainEvent.AccountsRefresh(levelsNeedingRefresh) if levelsNeedingRefresh.nonEmpty =>
        db.run(TezosDb.fetchProcessedEventsLevels(ChainEvent.accountsRefresh.render)).map { levels =>
          //used to remove processed events
          val processed = levels.toSet
          //we want individual event levels with the associated pattern, such that we can sort them by level
          val unprocessedEvents = levelsNeedingRefresh.toList.flatMap {
            case (accountPattern, levels) => levels.filterNot(processed).sorted.map(_ -> accountPattern)
          }
          SortedSet(unprocessedEvents: _*)
        }
    }.getOrElse(Future.successful(SortedSet.empty))

  /* Possibly updates all accounts if the current block level is past any of the given ones
   *
   * @param events the relevant levels, each with its own selection pattern, that calls for a refresh
   * @return the still unprocessed events, requiring to be handled later
   */
  private[tezos] def applyUnhandledAccountsResets(events: AccountResetEvents)(
      implicit ec: ExecutionContext
  ): Future[UnhandledResetEvents] =
    if (events.nonEmpty) {
      //This method is too long and messy, should be better organized
      for {
        storedHead <- indexedData.fetchMaxLevel
        unhandled <- if (events.exists(_._1 <= storedHead)) {
          val (past, toCome) = events.partition(_._1 <= storedHead)
          val (levels, selectors) = past.unzip
          val showLevels = levels.mkString(", ")
          logger.info(
            s"A block was reached that requires an update of account data as specified in the configuration file. A full refresh is now underway. Relevant block levels: $showLevels"
          )
          indexedData.fetchBlockAtLevel(levels.max).flatMap {
            case Some(referenceBlockForRefresh) =>
              val (hashRef, levelRef, timestamp, cycle) =
                (
                  TezosBlockHash(referenceBlockForRefresh.hash),
                  referenceBlockForRefresh.level,
                  referenceBlockForRefresh.timestamp.toInstant,
                  referenceBlockForRefresh.metaCycle
                )
              db.run(
                  //put all accounts in checkpoint, log the past levels to the db, keep the rest for future cycles
                  TezosDb.refillAccountsCheckpointFromExisting(hashRef, levelRef, timestamp, cycle, selectors) >>
                      TezosDb.writeProcessedEventsLevels(
                        ChainEvent.accountsRefresh.render,
                        levels.toList
                      )
                )
                .andThen {
                  case Success(accountsCount) =>
                    val showCount = accountsCount.fold("")(" " + _)
                    logger.info(s"Checkpoint stored for$showCount account updates in view of the full refresh.")
                  case Failure(err) =>
                    logger.error("I failed to store the accounts refresh updates in the checkpoint", err)
                }
                .map(_ => toCome) //keep the yet unreached levels and pass them on
            case None =>
              logger.warn(
                s"I couldn't find in Conseil the block data at level ${levels.max}, required for the general accounts update, and this is actually unexpected. I'll retry the whole operation at next cycle."
              )
              Future.successful(events)
          }
        } else Future.successful(events)
      } yield UnhandledResetEvents(unhandled)
    } else NoEventsToHandle

}
