package tech.cryptonomic.conseil.indexer.tezos.processing

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import akka.Done
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import cats._
import cats.implicits._
import tech.cryptonomic.conseil.indexer.config.{BakingAndEndorsingRights, BatchFetchConfiguration}
import tech.cryptonomic.conseil.indexer.tezos.{TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.indexer.tezos.TezosNodeOperator.LazyPages
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{
  Account,
  AccountId,
  BlockLevel,
  BlockReference,
  BlockTagged,
  Protocol4Delegate,
  PublicKeyHash,
  TezosBlockHash,
  Voting
}
import tech.cryptonomic.conseil.indexer.tezos.TezosErrors.AccountsProcessingFailed
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Syntax._
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._

/** Collects operations related to handling accounts from
  * the tezos node.
  *
  * @param nodeOperator connects to tezos
  * @param db raw access to the slick database
  * @param batchingConf used to access configuration on batch fetching
  * @param rightsConf used to access configuration for baking/endorsing rights processing
  * @param mat implicitly required materializer of akka streams, used by internal streaming processing
  */
class AccountsProcessor(
    nodeOperator: TezosNodeOperator,
    db: Database,
    batchingConf: BatchFetchConfiguration,
    rightsConf: BakingAndEndorsingRights
)(
    implicit mat: ActorMaterializer
) extends LazyLogging {

  /** accounts, indexed by id */
  private type AccountsIndex = Map[AccountId, Account]
  /* an alias to denote pkhs that identify delegates */
  private type DelegateKeys = List[PublicKeyHash]

  /* Fetches the data from the chain node and stores accounts into the data store.
   *
   * @param ids a pre-filtered map of account ids with latest block referring to them
   * @param votingData rolls associated to each block, we assume data is available for all block associated in the `ids`
   * @param onlyProcessLatest verify that no recent update was made to the account before processing each id
   *        (default = false)
   */
  private def process(
      ids: Map[AccountId, BlockReference],
      votingData: Map[TezosBlockHash, List[Voting.BakerRolls]],
      onlyProcessLatest: Boolean = false
  )(
      implicit ec: ExecutionContext
  ): Future[List[BlockTagged[DelegateKeys]]] = {

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

    /* Traverse the accounts and extract any available key for a delegate,
     * producing the latter alongside information on the block of reference
     * The return value is a pair of the input accounts and the baker keys,
     * both "tagged" with block-identifying data
     */
    def extractBakersInfo(
        taggedAccounts: Seq[BlockTagged[AccountsIndex]]
    ): (List[BlockTagged[AccountsIndex]], List[BlockTagged[DelegateKeys]]) = {
      val taggedList = taggedAccounts.toList

      def extractBakerKey(account: Account): Option[PublicKeyHash] =
        PartialFunction.condOpt(account.delegate) {
          case Some(Right(pkh)) => pkh
          case Some(Left(Protocol4Delegate(_, Some(pkh)))) => pkh
        }

      val taggedBakersKeys = taggedList.map(
        taggedIndex =>
          taggedIndex.map(
            accountsIndex => accountsIndex.values.toList.mapFilter(extractBakerKey)
          )
      )
      (taggedList, taggedBakersKeys)
    }

    /** Starting from accounts grouped by block, and the correspoding delegates,
      * we compute extra information and store everything in the database.
      * All of this is done on a "page" of those values, which represents a chunk
      * of block levels in the chain, being processed together.
      *
      * @param taggedAccounts for a batch of blocks, the corresponding accounts, indexed by id
      * @param taggedBakerKeys for the same batch of blocks, all delegates pkh extracted from the accounts' data
      * @param ec used for concurrent operations
      * @return the number of accounts written, checkpoint rows for delegate keys, the delegate/baker keys to be processed
      */
    def processAccountsPage(
        taggedAccounts: List[BlockTagged[AccountsIndex]],
        taggedBakerKeys: List[BlockTagged[DelegateKeys]]
    )(
        implicit ec: ExecutionContext
    ): Future[(Option[Int], Option[Int], List[BlockTagged[DelegateKeys]])] = {
      // we fetch active delegates per block so we can filter out current active bakers
      // at this point in time and put the information about it in the separate row
      // (there is no operation like bakers deactivation)
      val accountsWithHistoryFut = for {
        activatedOperations <- fetchActivationOperationsByLevel(taggedAccounts.map(_.ref.level).distinct)
        activatedAccounts <- db.run(TezosDb.findActivatedAccountIds)
        updatedTaggedAccounts = updateTaggedAccountsWithIsActivated(
          taggedAccounts,
          activatedOperations.mapValues(_.toSet),
          activatedAccounts.map(PublicKeyHash(_)).toSet
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

    /** Pairs every block accounts map in the input with active bakers for that same block level */
    def getInactiveBakersWithTaggedAccounts(
        taggedAccounts: List[BlockTagged[AccountsIndex]]
    ): Future[List[(BlockTagged[AccountsIndex], List[Tables.AccountsRow])]] =
      Future.traverse(taggedAccounts) { accountsPerLevel =>
        if (accountsPerLevel.ref.level % rightsConf.cycleSize == 1) {
          nodeOperator.fetchActiveBakers(accountsPerLevel.ref.hash).flatMap { activeBakers =>
            //the returned ids also reference the input hash, but we can discard it
            //we only want the active ids to fetch the inactive by difference from the database
            val activeIds = activeBakers.map(_._2.toSet).getOrElse(Set.empty)
            db.run {
              TezosDb
                .getFilteredBakerAccounts(exclude = activeIds)
                .map(accountsPerLevel -> _)
            }
          }
        } else {
          Future.successful(accountsPerLevel -> List.empty)
        }
      }

    /** Marks all the input accounts as "activated",
      * based on the data from the other inputs.
      * Assumptions: the activated operations and ids will
      * contain the information necessary to check all the input accounts.
      *
      * @param taggedAccounts the accounts to mark, with additional block references
      * @param activatedViaOperationsPerLevel defines the pkh of accounts activated in operations for a given level
      * @param activatedViaAccountIds defines known activated account ids
      * @return
      */
    def updateTaggedAccountsWithIsActivated(
        taggedAccounts: List[BlockTagged[AccountsIndex]],
        activatedViaOperationsPerLevel: Map[BlockLevel, Set[PublicKeyHash]],
        activatedViaAccountIds: Set[PublicKeyHash]
    ): List[BlockTagged[AccountsIndex]] =
      taggedAccounts.map { taggedAccount =>
        val activatedViaOperations = activatedViaOperationsPerLevel.getOrElse(taggedAccount.ref.level, Set.empty)
        taggedAccount.copy(
          content = taggedAccount.content.mapValues { account =>
            if (account.manager.exists(activatedViaAccountIds | activatedViaOperations)) {
              account.copy(isActivated = Some(true))
            } else account
          }
        )
      }

    /** Get all pkh for each level in input, belonging to any activate-account operation */
    def fetchActivationOperationsByLevel(levels: List[BlockLevel]): Future[Map[BlockLevel, List[PublicKeyHash]]] = {
      import slick.jdbc.PostgresProfile.api._
      db.run {
        DBIO.sequence {
          levels.map { level =>
            TezosDb
              .fetchRecentOperationsHashByKind(Set("activate_account"), level)
              .map(
                optionalOperationHashes => level -> optionalOperationHashes.toList.map(PublicKeyHash(_))
              )
          }
        }.map(_.toMap)
      }
    }

    /** remove from the checkpoints any processed id */
    def cleanup = {
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
            case (AccountId(id), BlockReference(_, updateLevel, _, _, _)) =>
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
            .get(taggedAccounts.ref.hash)
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
      (stored, checkpoints, delegateKeys) <- saveAccounts(updatedPages) flatTap (_ => cleanup)
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

  /* Fetches accounts from account-id and saves those associated with the latest operations
   * (i.e.the highest block level)
   * @return the bakers key-hashes found for the accounts passed-in, grouped by block reference
   */
  private[tezos] def processAccountsForBlocks(
      updates: List[BlockTagged[List[AccountId]]],
      votingData: Map[TezosBlockHash, List[Voting.BakerRolls]]
  )(
      implicit ec: ExecutionContext
  ): Future[List[BlockTagged[List[PublicKeyHash]]]] = {
    logger.info("Processing latest Tezos data for updated accounts...")

    def keepMostRecent(associations: List[(AccountId, BlockReference)]): Map[AccountId, BlockReference] =
      associations.foldLeft(Map.empty[AccountId, BlockReference]) { (collected, entry) =>
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

    process(toBeFetched, votingData)
  }

  /** Fetches and stores all accounts from the latest blocks still in the checkpoint */
  private[tezos] def processTezosAccountsCheckpoint()(implicit ec: ExecutionContext): Future[Done] = {

    logger.info("Selecting all accounts left in the checkpoint table...")
    db.run(TezosDb.getLatestAccountsFromCheckpoint) flatMap { checkpoints =>
      if (checkpoints.nonEmpty) {
        logger.info(
          "I loaded all of {} checkpointed ids from the DB and will proceed to fetch updated accounts information from the chain",
          checkpoints.size
        )
        // here we need to get missing bakers for the given block
        db.run(TezosDb.getBakersForBlocks(checkpoints.values.map(_.hash).toList)).flatMap { bakers =>
          process(checkpoints, bakers.toMap, onlyProcessLatest = true).map(_ => Done)
        }

      } else {
        logger.info("No data to fetch from the accounts checkpoint")
        Future.successful(Done)
      }
    }
  }

}
