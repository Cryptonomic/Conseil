package tech.cryptonomic.conseil.tezos.repositories

import tech.cryptonomic.conseil.util.Conversion.Syntax._
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  Account,
  AccountId,
  Block,
  BlockHash,
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

import cats.Applicative
import slick.jdbc.PostgresProfile.api._
import slickeffect.implicits._
import scala.concurrent.ExecutionContext

class SlickRepositories(implicit ec: ExecutionContext) {
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

  implicit val genericQuerying = new GenericQuerying[DBIO, String, String, String] {
    import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder._

    /** Type representing Map[String, Option[Any]] for query response */
    type QueryResponse = Map[String, Option[Any]]

    /* THIS IMPLEMENTATION IS VULNERABLE TO SQL INJECTION */
    override def countDistinct(table: String, column: String) =
      sql"""SELECT COUNT(*) FROM (SELECT DISTINCT #$column FROM #$table) AS temp"""
        .as[Int]
        .map(_.head)

    /* THIS IMPLEMENTATION IS VULNERABLE TO SQL INJECTION */
    override def countRows(table: String) =
      sql"""SELECT reltuples FROM pg_class WHERE relname = $table"""
        .as[Int]
        .map(_.head)

    /* THIS IMPLEMENTATION IS VULNERABLE TO SQL INJECTION */
    override def selectDistinct(table: String, column: String) =
      sql"""SELECT DISTINCT #$column FROM #$table WHERE #$column IS NOT NULL"""
        .as[String]
        .map(_.toList)

    /* THIS IMPLEMENTATION IS VULNERABLE TO SQL INJECTION */
    override def selectDistinctLike(table: String, column: String, matchingString: String) =
      sql"""SELECT DISTINCT #$column FROM #$table WHERE #$column LIKE '%#$matchingString%' AND #$column IS NOT NULL"""
        .as[String]
        .map(_.toList)

    override def selectWithPredicates(
        table: String,
        columns: List[String],
        predicates: List[Predicate],
        ordering: List[QueryOrdering],
        aggregation: List[Aggregation],
        outputType: OutputType.type,
        limit: Int
    ) = {
      val q =
        makeQuery(table, columns, aggregation)
          .addPredicates(aggregation.flatMap(_.getPredicate) ::: predicates)
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

  implicit val blocksRepository = new BlocksRepository[DBIO] {

    override def anyBlockAvailable =
      Blocks.exists.result

    override def blockExists(hash: BlockHash) =
      Blocks.findBy(_.hash).applied(hash.value).exists.result

    override def fetchMaxBlockLevel =
      Blocks
        .map(_.level)
        .max
        .result

    override def writeBlocks(blocks: List[Block]) =
      Blocks ++= blocks.map(_.convertTo[BlocksRow])

    override def blockAndOpsExists(hash: BlockHash) =
      Applicative[DBIO].map2(
        blockExists(hash),
        OperationGroups.filter(_.blockId === hash.value).exists.result
      )(_ && _)

  }

  implicit val operationsRepository =
    new OperationsRepository[DBIO, OperationGroupsRow, OperationsRow, Int, BalanceUpdatesRow] {
      import tech.cryptonomic.conseil.util.CollectionOps._

      /** Precompiled fetch for Operations by Group */
      val operationsByGroupHash = Operations.findBy(_.operationGroupHash)

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

      override def writeOperationWithNewId(operation: OperationsRow) =
        Operations returning Operations.map(_.operationId) += operation

      override def writeOperationsGroups(groups: List[OperationGroupsRow]) =
        OperationGroups ++= groups

      override def writeUpdates(updates: List[BalanceUpdatesRow], sourceOperation: Option[Int]) = {
        val updatesWithSource = updates.map(_.copy(sourceId = sourceOperation))
        BalanceUpdates ++= updatesWithSource
      }

    }

  implicit val votingRepository = new VotingRepository[DBIO] {

    override def writeVotingBallots(ballots: List[Voting.Ballot], block: Block) =
      Ballots ++= (block, ballots).convertToA[List, BallotsRow]

    override def writeVotingProposals(proposals: List[Voting.Proposal]) =
      Proposals ++= proposals.flatMap(_.convertToA[List, ProposalsRow])

    override def writeVotingRolls(bakers: List[Voting.BakerRolls], block: Block) =
      Rolls ++= (block, bakers).convertToA[List, RollsRow]

  }

  implicit val feesRepository = new FeesRepository[DBIO] {
    import scala.math.{ceil, max}
    import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

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

    override def writeFees(fees: List[AverageFees]) =
      Fees ++= fees.map(_.convertTo[FeesRow])

  }

  implicit val accountsRepository = new AccountsRepository[DBIO] {

    /* computes the number of distinct accounts present in the checkpoint table */
    private val getCheckpointSize =
      AccountsCheckpoint.distinctOn(_.accountId).length.result

    override def cleanAccountsCheckpoint(ids: Option[Set[AccountId]] = None) =
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

    override def getLatestAccountsFromCheckpoint =
      for {
        ids <- AccountsCheckpoint.map(_.accountId).distinct.result
        rows <- DBIO.sequence(ids.map { id =>
          AccountsCheckpoint.filter(_.accountId === id).sortBy(_.blockLevel.desc).take(1).result.head
        })
      } yield
        rows.map {
          case AccountsCheckpointRow(id, blockId, level) => AccountId(id) -> (BlockHash(blockId), level)
        }.toMap

    override def updateAccounts(accountsInfo: List[BlockTagged[Map[AccountId, Account]]]) =
      DBIO
        .sequence(accountsInfo.flatMap { info =>
          info.convertToA[List, AccountsRow].map(Accounts.insertOrUpdate)
        })
        .map(_.sum)

    override def writeAccountsCheckpoint(accountIds: List[(BlockHash, Int, List[AccountId])]) =
      AccountsCheckpoint ++= accountIds.flatMap(_.convertToA[List, AccountsCheckpointRow])

  }

  implicit val delegatesRepository = new DelegatesRepository[DBIO] {

    /* computes the number of distinct accounts present in the checkpoint table */
    val getCheckpointSize =
      DelegatesCheckpoint.distinctOn(_.delegatePkh).length.result

    override def cleanDelegatesCheckpoint(pkhs: Option[Set[PublicKeyHash]] = None) =
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

    override def copyAccountsAsDelegateContracts(contractsIds: Set[ContractId]) = {
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

    override def getLatestDelegatesFromCheckpoint =
      for {
        keys <- DelegatesCheckpoint.map(_.delegatePkh).distinct.result
        rows <- DBIO.sequence(keys.map { pkh =>
          DelegatesCheckpoint.filter(_.delegatePkh === pkh).sortBy(_.blockLevel.desc).take(1).result.head
        })
      } yield
        rows.map {
          case DelegatesCheckpointRow(pkh, blockId, level) => PublicKeyHash(pkh) -> (BlockHash(blockId), level)
        }.toMap

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

    override def writeDelegatesCheckpoint(delegatesKeyHashes: List[(BlockHash, Int, List[PublicKeyHash])]) =
      DelegatesCheckpoint ++= delegatesKeyHashes.flatMap(_.convertToA[List, DelegatesCheckpointRow])

  }

}
