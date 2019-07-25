package tech.cryptonomic.conseil.tezos.repositories

import tech.cryptonomic.conseil.util.Conversion.Syntax._
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  Account,
  AccountId,
  Block,
  BlockHash,
  BlockReference,
  BlockTagged,
  ContractId,
  Delegate,
  PublicKeyHash,
  Voting
}
import tech.cryptonomic.conseil.tezos.DatabaseConversions
import tech.cryptonomic.conseil.tezos.Fees.{AverageFees, OperationKind => FeesOpKind}
import tech.cryptonomic.conseil.tezos.Tables.{
  Accounts,
  AccountsCheckpoint,
  AccountsCheckpointRow,
  AccountsRow,
  BalanceUpdates,
  BalanceUpdatesRow,
  Ballots,
  BallotsRow,
  Blocks,
  BlocksRow,
  DelegatedContracts,
  DelegatedContractsRow,
  Delegates,
  DelegatesCheckpoint,
  DelegatesCheckpointRow,
  DelegatesRow,
  Fees,
  FeesRow,
  OperationGroups,
  OperationGroupsRow,
  Operations,
  OperationsRow,
  Proposals,
  ProposalsRow,
  Rolls,
  RollsRow
}
import tech.cryptonomic.conseil.generic.chain.DataTypes.{Aggregation, OutputType, Predicate, QueryOrdering}
import tech.cryptonomic.conseil.generic.chain.DataTypes.OutputType.OutputType
import tech.cryptonomic.conseil.metadata.repositories.MetadataRepository

import cats.Applicative
import slick.jdbc.PostgresProfile.api._
import slickeffect.implicits._
import scala.concurrent.ExecutionContext
import com.typesafe.scalalogging.LazyLogging

object SlickRepositories {

  /** Sanitizes predicate values so query is safe from SQL injection */
  def sanitizePredicates(predicates: List[Predicate]): List[Predicate] =
    predicates.map { predicate =>
      predicate.copy(set = predicate.set.map(field => sanitizeForSql(field.toString)))
    }

  /** Sanitizes string to be viable to paste into plain SQL */
  def sanitizeForSql(str: String): String = {
    val supportedCharacters = Set('_', '.', '+', ':', '-', ' ')
    str.filter(c => c.isLetterOrDigit || supportedCharacters.contains(c))
  }
}

/** Provides repositories implicit instances based on slick and futures */
class SlickRepositories(implicit ec: ExecutionContext) {
  import SlickRepositories._
  import DatabaseConversions._

  /**
    * Removes  data from a generic checkpoint table
    * @param selection limits the removed rows to those
    *                  concerning the selected elements, by default no selection is made.
    *                  We strictly assume those keys were previously loaded from the checkpoint table itself
    * @param tableQuery the slick table query to identify which is the table to clean up
    * @param tableTotal an action needed to compute the number of max keys in the checkpoint
    * @param applySelection used to filter the results to clean-up, using the available `selection`
    * @return the database action to run
    */
  private[this] def cleanCheckpoint[PK, Row, T <: Table[Row], CheckpointTable <: TableQuery[T]](
      selection: Option[Set[PK]],
      tableQuery: CheckpointTable,
      tableTotal: DBIO[Int],
      applySelection: (CheckpointTable, Set[PK]) => Query[T, Row, Seq]
  )(implicit ec: ExecutionContext): DBIO[Int] =
    selection match {
      case Some(pks) =>
        for {
          total <- tableTotal
          marked = if (total > pks.size) applySelection(tableQuery, pks)
          else tableQuery
          deleted <- marked.delete
        } yield deleted
      case None =>
        tableQuery.delete
    }

  implicit object metadataRepository extends MetadataRepository[DBIO, String, String, String] with LazyLogging {
    import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder._

    /** Type representing Map[String, Option[Any]] for query response */
    type QueryResponse = Map[String, Option[Any]]

    /** THIS IMPLEMENTATION IS VULNERABLE TO SQL INJECTION
      * @see [[tech.cryptonomic.conseil.tezos.repositories.MetadataRepository#countDistinct]]
      */
    override def countDistinct(table: String, column: String) =
      sql"""SELECT COUNT(*) FROM (SELECT DISTINCT #$column FROM #$table) AS temp"""
        .as[Int]
        .map(_.head)

    /** THIS IMPLEMENTATION IS VULNERABLE TO SQL INJECTION
      * @see [[tech.cryptonomic.conseil.tezos.repositories.MetadataRepository#countRows]]
      */
    override def countRows(table: String) =
      sql"""SELECT reltuples FROM pg_class WHERE relname = $table"""
        .as[Int]
        .map(_.head)

    /** THIS IMPLEMENTATION IS VULNERABLE TO SQL INJECTION
      * @see [[tech.cryptonomic.conseil.tezos.repositories.MetadataRepository#selectDistinct]]
      */
    override def selectDistinct(table: String, column: String) =
      sql"""SELECT DISTINCT #$column::VARCHAR FROM #$table WHERE #$column IS NOT NULL"""
        .as[String]
        .map(_.toList)

    /** THIS IMPLEMENTATION IS VULNERABLE TO SQL INJECTION
      * @see [[tech.cryptonomic.conseil.tezos.repositories.MetadataRepository#selectDistinctLike]]
      */
    override def selectDistinctLike(table: String, column: String, matchingString: String) = {
      val cleanMatch = sanitizeForSql(matchingString)
      sql"""SELECT DISTINCT #$column::VARCHAR FROM #$table WHERE #$column LIKE '%#$cleanMatch%' AND #$column IS NOT NULL"""
        .as[String]
        .map(_.toList)
    }

    /**
      * @see [[tech.cryptonomic.conseil.tezos.repositories.MetadataRepository#selectWithPredicates]]
      */
    override def selectWithPredicates(
        table: String,
        columns: List[String],
        predicates: List[Predicate],
        ordering: List[QueryOrdering],
        aggregation: List[Aggregation],
        outputType: OutputType,
        limit: Int
    ) = {
      val q =
        makeQuery(table, columns, aggregation)
          .addPredicates(aggregation.flatMap(_.getPredicate) ::: sanitizePredicates(predicates))
          .addGroupBy(aggregation, columns)
          .addOrdering(ordering)
          .addLimit(limit)

      if (outputType == OutputType.sql) {
        DBIO.successful(List(Map("sql" -> Some(q.queryParts.mkString("")))))
      } else {
        q.as[QueryResponse].map(_.toList)
      }

    }

  }

  implicit object blocksRepository extends BlocksRepository[DBIO] with LazyLogging {

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.BlocksRepository#anyBlockAvailable]] */
    override def anyBlockAvailable =
      Blocks.exists.result

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.BlocksRepository#blockExists]] */
    override def blockExists(hash: BlockHash) =
      Blocks.findBy(_.hash).applied(hash.value).exists.result

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.BlocksRepository#fetchMaxBlockLevel]] */
    override def fetchMaxBlockLevel =
      Blocks
        .map(_.level)
        .max
        .result

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.BlocksRepository#writeBlocks]] */
    override def writeBlocks(blocks: List[Block]) = {
      logger.info(s"""Writing ${blocks.length} block records to DB...""")
      Blocks ++= blocks.map(_.convertTo[BlocksRow])
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.BlocksRepository#blockAndOpsExists]] */
    override def blockAndOpsExists(hash: BlockHash) =
      Applicative[DBIO].map2(
        blockExists(hash),
        OperationGroups.filter(_.blockId === hash.value).exists.result
      )(_ && _)

  }

  implicit object operationsRepository extends OperationsRepository[DBIO, OperationGroupsRow, OperationsRow, Int, BalanceUpdatesRow] with LazyLogging {
      import tech.cryptonomic.conseil.util.CollectionOps._

      /** Precompiled fetch for Operations by Group */
      val operationsByGroupHash = Operations.findBy(_.operationGroupHash)

      /** @see [[tech.cryptonomic.conseil.tezos.repositories.OperationsRepository#operationsForGroup]] */
      override def operationsForGroup(groupHash: String) =
        (for {
          operation <- operationsByGroupHash(groupHash).extract
          group <- operation.operationGroupsFk
        } yield (group, operation)).result.map { pairs =>
          /*
           * we first collect all de-normalized pairs under the common group and then extract the
           * only key-value from the resulting map
           */
          val keyed = pairs.byKey()
          keyed.keys.headOption
            .map(k => (k, keyed(k)))
        }

      /** @see [[tech.cryptonomic.conseil.tezos.repositories.OperationsRepository#writeOperationWithNewId]] */
      override def writeOperationWithNewId(operation: OperationsRow) =
        Operations returning Operations.map(_.operationId) += operation

      /** @see [[tech.cryptonomic.conseil.tezos.repositories.OperationsRepository#writeOperationsGroups]] */
      override def writeOperationsGroups(groups: List[OperationGroupsRow]) =
        OperationGroups ++= groups

      /** @see [[tech.cryptonomic.conseil.tezos.repositories.OperationsRepository#writeUpdates]] */
      override def writeUpdates(updates: List[BalanceUpdatesRow], sourceOperation: Option[Int]) = {
        val updatesWithSource = updates.map(_.copy(sourceId = sourceOperation))
        BalanceUpdates ++= updatesWithSource
      }

    }

  implicit object votingRepository extends VotingRepository[DBIO] with LazyLogging {

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.VotingRepository#writeVotingBallots]] */
    override def writeVotingBallots(ballots: List[Voting.Ballot], block: Block) = {
      logger.info(
        s"""Writing ${ballots.length} ballots for block ${block.data.hash.value} at level ${block.data.header.level} to the DB..."""
      )
      Ballots ++= (block, ballots).convertToA[List, BallotsRow]
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.VotingRepository#writeVotingProposals]] */
    override def writeVotingProposals(proposals: List[Voting.Proposal]) = {
      logger.info(s"""Writing ${proposals.length} voting proposals to the DB...""")
      Proposals ++= proposals.flatMap(_.convertToA[List, ProposalsRow])
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.VotingRepository#writeVotingRolls]] */
    override def writeVotingRolls(bakers: List[Voting.BakerRolls], block: Block) = {
      logger.info(s"""Writing ${bakers.length} bakers to the DB...""")
      Rolls ++= (block, bakers).convertToA[List, RollsRow]
    }
  }

  implicit object feesRepository extends FeesRepository[DBIO] with LazyLogging {
    import scala.math.{ceil, max}
    import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.FeesRepository#calculateAverageFees]] */
    override def calculateAverageFees(kind: FeesOpKind, numberOfFeesAveraged: Int) = {
      def computeAverage(ts: java.sql.Timestamp, fees: Seq[(Option[BigDecimal], java.sql.Timestamp)]): AverageFees = {
        val values = fees.map {
          case (fee, _) => fee.map(_.toDouble).getOrElse(0.0)
        }
        val m: Int = ceil(mean(values)).toInt
        val s: Int = ceil(stdev(values)).toInt
        AverageFees(max(m - s, 0), m, m + s, ts, kind)
      }

      val opQuery =
        Operations
          .filter(_.kind === kind)
          .map(o => (o.fee, o.timestamp))
          .distinct
          .sortBy { case (_, ts) => ts.desc }
          .take(numberOfFeesAveraged)
          .result

      opQuery.map { timestampedFees =>
        timestampedFees.headOption.map {
          case (_, latest) =>
            computeAverage(latest, timestampedFees)
        }
      }
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.FeesRepository#writeFees]] */
    override def writeFees(fees: List[AverageFees]) = {
      logger.info("Writing fees to DB...")
      Fees ++= fees.map(_.convertTo[FeesRow])
    }

  }

  implicit object accountsRepository extends AccountsRepository[DBIO] with LazyLogging {

    /* computes the number of distinct accounts present in the checkpoint table */
    private val getCheckpointSize =
      AccountsCheckpoint.distinctOn(_.accountId).length.result

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.AccountsRepository#cleanAccountsCheckpoint]] */
    override def cleanAccountsCheckpoint(ids: Option[Set[AccountId]] = None) = {
      logger.info("Cleaning the accounts checkpoint table...")
      cleanCheckpoint[
        AccountId,
        AccountsCheckpointRow,
        AccountsCheckpoint,
        TableQuery[AccountsCheckpoint]
      ](
        selection = ids,
        tableQuery = AccountsCheckpoint,
        tableTotal = getCheckpointSize,
        applySelection = (checkpoint, keySet) => checkpoint.filter(_.accountId inSet keySet.map(_.id))
      )
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.AccountsRepository#getLatestAccountsFromCheckpoint]] */
    override def getLatestAccountsFromCheckpoint = {
      /* Given a sorted sequence of checkpoint rows whose reference level is decreasing,
       * collects them in a map, skipping keys already added
       * This prevents duplicate entry keys and keeps the highest level referenced, using an in-memory algorithm
       * We can think of optimizing this later, we're now optimizing on db queries
       */
      def keepLatestAccountIds(checkpoints: Seq[AccountsCheckpointRow]): Map[AccountId, BlockReference] =
        checkpoints.foldLeft(Map.empty[AccountId, BlockReference]) { (collected, row) =>
          val key = AccountId(row.accountId)
          if (collected.contains(key)) collected else collected + (key -> (BlockHash(row.blockId), row.blockLevel))
        }

      logger.info("Getting the latest accounts from checkpoints in the DB...")

      AccountsCheckpoint
        .sortBy(_.blockLevel.desc)
        .result
        .map(keepLatestAccountIds)
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.AccountsRepository#updateAccounts]] */
    override def updateAccounts(accountsInfo: List[BlockTagged[Map[AccountId, Account]]]) = {
      logger.info(s"""Writing ${accountsInfo.length} accounts to DB...""")
      DBIO
        .sequence(accountsInfo.flatMap { info =>
          info.convertToA[List, AccountsRow].map(Accounts.insertOrUpdate)
        })
        .map(_.sum)
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.AccountsRepository#writeAccountsCheckpoint]] */
    override def writeAccountsCheckpoint(accountIds: List[(BlockHash, Int, List[AccountId])]) = {
      logger.info(s"""Writing ${accountIds.flatMap(_._3).size} account checkpoints to DB...""")
      AccountsCheckpoint ++= accountIds.flatMap(_.convertToA[List, AccountsCheckpointRow])
    }
  }

  implicit object delegatesRepository extends DelegatesRepository[DBIO] with LazyLogging {

    /* computes the number of distinct accounts present in the checkpoint table */
    val getCheckpointSize =
      DelegatesCheckpoint.distinctOn(_.delegatePkh).length.result

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.DelegatesRepository#cleanDelegatesCheckpoint]] */
    override def cleanDelegatesCheckpoint(pkhs: Option[Set[PublicKeyHash]] = None) = {
      logger.info("Cleaning the delegate checkpoints table...")
      cleanCheckpoint[
        PublicKeyHash,
        DelegatesCheckpointRow,
        DelegatesCheckpoint,
        TableQuery[DelegatesCheckpoint]
      ](
        selection = pkhs,
        tableQuery = DelegatesCheckpoint,
        tableTotal = getCheckpointSize,
        applySelection = (checkpoint, keySet) => checkpoint.filter(_.delegatePkh inSet keySet.map(_.value))
      )
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.DelegatesRepository#copyAccountsAsDelegateContracts]] */
    override def copyAccountsAsDelegateContracts(contractsIds: Set[ContractId]) = {
      logger.info("Copying select accounts to delegates contracts table in DB...")
      val ids = contractsIds.map(_.id)
      val inputAccounts = Accounts
        .filter(_.accountId inSet ids)
        .result
        .map(_.map(_.convertTo[DelegatedContractsRow]))

      //we read the accounts data, then remove matching ids from contracts and re-insert the updated rows
      (for {
        accounts <- inputAccounts
        _ <- DelegatedContracts.filter(_.accountId inSet ids).delete
        updated <- DelegatedContracts.forceInsertAll(accounts)
      } yield updated).transactionally
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.DelegatesRepository#getLatestDelegatesFromCheckpoint]] */
    override def getLatestDelegatesFromCheckpoint = {
      /* Given a sorted sequence of checkpoint rows whose reference level is decreasing,
       * collects them in a map, skipping keys already added
       * This prevents duplicate entry keys and keeps the highest level referenced, using an in-memory algorithm
       * We can think of optimizing this later, we're now optimizing on db queries
       */
      def keepLatestDelegatesKeys(
          checkpoints: Seq[DelegatesCheckpointRow]
      ): Map[PublicKeyHash, BlockReference] =
        checkpoints.foldLeft(Map.empty[PublicKeyHash, BlockReference]) { (collected, row) =>
          val key = PublicKeyHash(row.delegatePkh)
          if (collected.contains(key)) collected else collected + (key -> (BlockHash(row.blockId), row.blockLevel))
        }

      logger.info("Getting the latest delegates from checkpoints in the DB...")
      DelegatesCheckpoint
        .sortBy(_.blockLevel.desc)
        .result
        .map(keepLatestDelegatesKeys)
    }

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.DelegatesRepository#updateDelegates]] */
    override def updateDelegates(delegatesInfo: List[BlockTagged[Map[PublicKeyHash, Delegate]]]) =
      DBIO
        .sequence(
          delegatesInfo.flatMap {
            case BlockTagged(blockHash, blockLevel, delegateMap) =>
              delegateMap.map {
                case (pkh, delegate) =>
                  Delegates insertOrUpdate (blockHash, blockLevel, pkh, delegate).convertTo[DelegatesRow]
              }
          }
        )
        .map(_.sum)

    /** @see [[tech.cryptonomic.conseil.tezos.repositories.DelegatesRepository#writeDelegatesCheckpoint]] */
    override def writeDelegatesCheckpoint(delegatesKeyHashes: List[(BlockHash, Int, List[PublicKeyHash])]) = {
      logger.info(s"""Writing ${delegatesKeyHashes.flatMap(_._3).size} delegate checkpoints to DB...""")
      DelegatesCheckpoint ++= delegatesKeyHashes.flatMap(_.convertToA[List, DelegatesCheckpointRow])
    }
  }

}
