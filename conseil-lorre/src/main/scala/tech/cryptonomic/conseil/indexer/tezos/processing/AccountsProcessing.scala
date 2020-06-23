package tech.cryptonomic.conseil.indexer.tezos.processing

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}
import akka.Done
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import tech.cryptonomic.conseil.indexer.config.{BakingAndEndorsingRights, BatchFetchConfiguration}
import tech.cryptonomic.conseil.indexer.tezos.{TezosNodeOperator, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.indexer.tezos.TezosNodeOperator.LazyPages
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{
  Account,
  AccountId,
  BlockHash,
  BlockReference,
  BlockTagged,
  Protocol4Delegate,
  PublicKeyHash,
  Voting
}
import tech.cryptonomic.conseil.indexer.tezos.TezosErrors.AccountsProcessingFailed
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Syntax._

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._

class AccountsProcessing(
    nodeOperator: TezosNodeOperator,
    db: Database,
    batchingConf: BatchFetchConfiguration,
    rightsConf: BakingAndEndorsingRights
)(
    implicit mat: ActorMaterializer
) extends LazyLogging {

  type AccountsIndex = Map[AccountId, Account]
  type DelegateKeys = List[PublicKeyHash]

  /* Fetches the data from the chain node and stores accounts into the data store.
   * @param ids a pre-filtered map of account ids with latest block referring to them
   * @param onlyProcessLatest verify that no recent update was made to the account before processing each id
   *        (default = false)
   */
  private def process(
      ids: Map[AccountId, BlockReference],
      votingData: Map[BlockHash, List[Voting.BakerRolls]],
      onlyProcessLatest: Boolean = false
  )(
      implicit ec: ExecutionContext
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
          val bakerKeys = accountsMap.values.toList
            .mapFilter(extractBakerKey)

          bakerKeys.taggedWithBlock(blockHash, blockLevel, timestamp, cycle, period)
      }
      (taggedList, taggedBakersKeys)
    }

    def processAccountsPage(
        taggedAccounts: List[BlockTagged[Map[AccountId, Account]]],
        taggedBakerKeys: List[BlockTagged[DelegateKeys]]
    )(
        implicit ec: ExecutionContext
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
        if (blockTaggedAccounts.blockLevel % rightsConf.cycleSize == 1) {
          nodeOperator.fetchActiveBakers(taggedAccounts.map(x => (x.blockLevel, x.blockHash))).flatMap { activeBakers =>
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

  /* Fetches accounts from account-id and saves those associated with the latest operations
   * (i.e.the highest block level)
   * @return the bakers key-hashes found for the accounts passed-in, grouped by block reference
   */
  private[tezos] def processAccountsForBlocks(
      updates: List[BlockTagged[List[AccountId]]],
      votingData: Map[BlockHash, List[Voting.BakerRolls]]
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
      case BlockTagged(hash, level, timestamp, cycle, period, ids) =>
        ids.map(_ -> (hash, level, timestamp, cycle, period))
    }.sortBy {
      case (id, (hash, level, timestamp, cycle, period)) => level
    }(Ordering[Int].reverse)

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
        db.run(TezosDb.getBakersForBlocks(checkpoints.values.map(_._1).toList)).flatMap { bakers =>
          process(checkpoints, bakers.toMap, onlyProcessLatest = true).map(_ => Done)
        }

      } else {
        logger.info("No data to fetch from the accounts checkpoint")
        Future.successful(Done)
      }
    }
  }

}
