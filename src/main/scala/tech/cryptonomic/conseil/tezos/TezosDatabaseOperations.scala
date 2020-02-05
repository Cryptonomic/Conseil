package tech.cryptonomic.conseil.tezos

import java.time.Instant

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.config.ChainEvent.AccountIdPattern
import tech.cryptonomic.conseil.generic.chain.DataTypes.{Query => _, _}
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.util.CollectionOps._
import tech.cryptonomic.conseil.util.Conversion.Syntax._
import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder._
import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

import scala.concurrent.ExecutionContext
import scala.math.{ceil, max}
import cats.effect.Async
import com.github.tminglei.slickpg.ExPostgresProfile
import org.slf4j.LoggerFactory
import slick.basic.Capability
import tech.cryptonomic.conseil.generic.chain.DataTypes.OutputType.OutputType
import slick.jdbc.JdbcCapabilities
import tech.cryptonomic.conseil.tezos.TezosNodeOperator.FetchRights
import tech.cryptonomic.conseil.tezos.TezosTypes.Voting.BakerRolls

import scala.collection.immutable.Queue

/**
  * Functions for writing Tezos data to a database.
  */
object TezosDatabaseOperations extends LazyLogging {

  import DatabaseConversions._

  /**
    *  Writes computed vote aggregates to a database.
    *
    *  @param votes List of vote aggregates for some range of block levels with ballots.
    *  @return      Database action possibly containing the number of rows written
    */
  def writeVotes(votes: List[VoteAggregates]): DBIO[Option[Int]] = {
    logger.info("Writing votes to DB...")
    Tables.Votes ++= votes.map(_.convertTo[Tables.VotesRow])
  }

  /**
    * Writes computed fees averages to a database.
    *
    * @param fees List of average fees for different operation kinds.
    * @return     Database action possibly containing the number of rows written (if available from the underlying driver)
    */
  def writeFees(fees: List[AverageFees]): DBIO[Option[Int]] = {
    logger.info("Writing fees to DB...")
    Tables.Fees ++= fees.map(_.convertTo[Tables.FeesRow])
  }

  /**
    * Writes accounts with block data to a database.
    *
    * @param accountsInfo List data on the accounts and the corresponding blocks that operated on those
    * @return     Database action possibly containing the number of rows written (if available from the underlying driver)
    */
  def writeAccounts(
      accountsInfo: List[BlockTagged[Map[AccountId, Account]]]
  ): DBIO[Option[Int]] = {
    import CustomPostgresProfile.api._

    val keepMostRecent = (rows: List[Tables.AccountsRow]) =>
      rows
        .sortBy(_.blockLevel)(Ordering[BigDecimal].reverse)
        .foldLeft((Queue.empty[Tables.AccountsRow], Set.empty[String])) { (accumulator, row) =>
          val (queued, index) = accumulator
          if (index(row.accountId)) accumulator else (queued :+ row, index + row.accountId)
        }
        ._1

    logger.info(s"""Writing ${accountsInfo.length} accounts to DB...""")
    val rows = accountsInfo.flatMap(_.convertToA[List, Tables.AccountsRow])
    (keepMostRecent andThen Tables.Accounts.insertOrUpdateAll)(rows)
  }

  /**
    * Writes accounts history with block data to a database.
    *
    * @param accountsInfo List data on the accounts and the corresponding blocks that operated on those
    * @return     Database action possibly containing the number of rows written (if available from the underlying driver)
    */
  def writeAccountsHistory(
      accountsInfo: List[BlockTagged[Map[AccountId, Account]]]
  ): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${accountsInfo.length} accounts_history to DB...""")
    Tables.AccountsHistory ++= accountsInfo.flatMap(_.convertToA[List, Tables.AccountsHistoryRow])
  }

  /**
    * Writes blocks and related operations to a database.
    * @param blocks   Block with operations.
    * @return         Database action to execute.
    */
  def writeBlocks(blocks: List[Block])(implicit ec: ExecutionContext): DBIO[Unit] = {
    // Kleisli is a Function with effects, Kleisli[F, A, B] ~= A => F[B]
    import cats.data.Kleisli
    import cats.instances.list._
    import slickeffect.implicits._
    import DatabaseConversions.OperationTablesData
    import SymbolSourceLabels.Show._
    import tech.cryptonomic.conseil.tezos.BlockBalances._
    import Tables.{BalanceUpdatesRow, BlocksRow, OperationGroupsRow, OperationsRow}

    logger.info(s"""Writing ${blocks.length} block records to DB...""")

    //straightforward Database IO Actions waiting to be just run
    val saveBlocksAction = Tables.Blocks ++= blocks.map(_.convertTo[BlocksRow])
    val saveBlocksBalanceUpdatesAction = Tables.BalanceUpdates ++= blocks.flatMap { block =>
            block.data.convertToA[List, BalanceUpdatesRow]
          }

    val saveGroupsAction = Tables.OperationGroups ++= blocks.flatMap(_.convertToA[List, OperationGroupsRow])

    //a function that takes a row to save and creates an action to do that, returning the new id
    val saveOperationGetNewId = Kleisli[DBIO, OperationsRow, (String, Int)] {
      Tables.Operations returning Tables.Operations.map(o => (o.operationGroupHash, o.operationId)) += _
    }
    //a function that takes rows to save with an operation id, and creates an action to do that
    val saveBalanceUpdatesForOperationId = Kleisli[DBIO, ((String, Int), List[BalanceUpdatesRow]), Option[Int]] {
      case ((operationGroupHash, operationRowId), balanceRows) =>
        Tables.BalanceUpdates ++= balanceRows.map(
              _.copy(operationGroupHash = Some(operationGroupHash), sourceId = Some(operationRowId))
            )
    }

    /* Compose the kleisli functions to get a single "action function"
     * Calling `.first` will make the kleisli take a tuple and only apply the function to the first element
     * leaving the second untouched.
     * We do this to align the output with the input of the second step
     */
    val saveOperationsAndBalances: Kleisli[DBIO, (OperationsRow, List[BalanceUpdatesRow]), Option[Int]] =
      saveOperationGetNewId.first andThen saveBalanceUpdatesForOperationId

    //Sequence the save actions, some of which are being applied to a whole collection of operations and balances
    DBIO.seq(
      saveBlocksAction,
      saveBlocksBalanceUpdatesAction,
      saveGroupsAction,
      saveOperationsAndBalances.traverse(blocks.flatMap(_.convertToA[List, OperationTablesData])),
      saveBigMaps(blocks)
    )

  }

  /**
    * Writes big map information from relevant operations occurring in the blocks.
    * This means creating new rows on originations' allocations, or creating/updating
    * map contents on transactions' update/copy, or removing all data on
    * transactions' remove.
    *
    * @param blocks the blocks containing the operations
    * @param ec the context to run async operations
    */
  def saveBigMaps(blocks: List[Block])(implicit ec: ExecutionContext): DBIO[Unit] = {
    import Tables.{BigMapContentsRow, BigMapsRow, OriginatedAccountMapsRow}
    import CustomPostgresProfile.api._

    logger.info("Writing big map differences to DB...")
    //TODO add log entries at each step

    val copyDiffs = blocks.flatMap(TezosOptics.Blocks.readBigMapDiffCopy.getAll)
    val removalDiffs = blocks.flatMap(TezosOptics.Blocks.readBigMapDiffRemove.getAll)

    //need to load the sources and copy them with a new destination id
    val contentCopies = copyDiffs.collect {
      case Contract.BigMapCopy(_, Decimal(sourceId), Decimal(destinationId)) =>
        Tables.BigMapContents
          .filter(_.bigMapId === sourceId)
          .map(it => (destinationId, it.key, it.keyHash, it.value))
          .result
          .map(rows => rows.map(BigMapContentsRow.tupled).toList)
    }

    val copyContent = DBIO
      .sequence(contentCopies)
      .flatMap { updateRows =>
        val copies = updateRows.flatten
        logger.info("{} big maps will be copied.", copies.size)
        Tables.BigMapContents.insertOrUpdateAll(copies)
      }
      .map(_.sum)

    val saveMaps = {
      val maps = blocks.flatMap(_.convertToA[List, BigMapsRow])
      logger.info("{} big maps will be added.", maps.size)
      Tables.BigMaps ++= maps
    }

    val saveContent = {
      val content = blocks.flatMap(_.convertToA[List, BigMapContentsRow])
      logger.info("{} big map content entries will be added.", content.size)
      Tables.BigMapContents ++= content
    }

    val saveContractOrigin = {
      val refs = blocks.flatMap(_.convertToA[List, OriginatedAccountMapsRow])
      logger.info("{} big map accounts references will be made.", refs.size)
      Tables.OriginatedAccountMaps ++= refs
    }

    val removeAnyNeeded: DBIO[Unit] = {
      val idsToRemove = removalDiffs.collect {
        case Contract.BigMapRemove(_, Decimal(bigMapId)) =>
          bigMapId
      }.toSet

      logger.info("{} big maps will be removed.", idsToRemove.size)
      DBIO.seq(
        Tables.BigMapContents.filter(_.bigMapId inSet idsToRemove).delete,
        Tables.BigMaps.filter(_.bigMapId inSet idsToRemove).delete,
        Tables.OriginatedAccountMaps.filter(_.bigMapId inSet idsToRemove).delete
      )
    }

    /* The interleaving of these operations would actually need more complexity to handle properly,
     * since we're processing collections of blocks, therefore some operations handled "after" might be
     * referring to something "happening before" or viceversa.
     * E.g. you could find that a new origination creates a map with the same identifier of another previously
     * removed (is this allowed?).
     * Therefore the insert might fail on finding the old record id being there already, whereas the real sequence
     * of events would have removed the old first.
     * We might consider improving the situation by doing a pre-check that the altered sequencing of the operations
     * doesn't interfere with the "causality" of the chain events.
     */
    DBIO.seq(saveMaps, saveContent, saveContractOrigin, copyContent, removeAnyNeeded)
  }

  /**
    * Writes association of account ids and block data to define accounts that needs update
    * @param accountIds will have block information, paired with corresponding account ids to store
    * @return Database action possibly returning the rows written (if available form the underlying driver)
    */
  def writeAccountsCheckpoint(
      accountIds: List[(BlockHash, Int, Option[Instant], Option[Int], List[AccountId])]
  ): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${accountIds.map(_._5).map(_.length).sum} account checkpoints to DB...""")
    Tables.AccountsCheckpoint ++= accountIds.flatMap(_.convertToA[List, Tables.AccountsCheckpointRow])
  }

  /**
    * Writes association of delegate key-hashes and block data to define delegates that needs to be written
    * @param delegatesKeyHashes will have block information, paired with corresponding hashes to store
    * @return Database action possibly returning the rows written (if available form the underlying driver)
    */
  def writeDelegatesCheckpoint(
      delegatesKeyHashes: List[(BlockHash, Int, Option[Instant], Option[Int], List[PublicKeyHash])]
  ): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${delegatesKeyHashes.map(_._5).map(_.length).sum} delegate checkpoints to DB...""")
    Tables.DelegatesCheckpoint ++= delegatesKeyHashes.flatMap(_.convertToA[List, Tables.DelegatesCheckpointRow])
  }

  /** Removes data from a accounts checkpoint table */
  def cleanAccountsCheckpoint(
      ids: Option[Set[AccountId]] = None
  )(implicit ec: ExecutionContext): DBIO[Int] = {
    logger.info("""Cleaning the accounts checkpoint table..""")
    cleanCheckpoint[
      AccountId,
      Tables.AccountsCheckpointRow,
      Tables.AccountsCheckpoint,
      TableQuery[Tables.AccountsCheckpoint]
    ](
      selection = ids,
      tableQuery = Tables.AccountsCheckpoint,
      tableTotal = getAccountsCheckpointSize(),
      applySelection = (checkpoint, keySet) => checkpoint.filter(_.accountId inSet keySet.map(_.id))
    )
  }

  /** Removes data from a delegates checkpoint table */
  def cleanDelegatesCheckpoint(
      pkhs: Option[Set[PublicKeyHash]] = None
  )(implicit ec: ExecutionContext): DBIO[Int] = {
    logger.info("""Cleaning the delegate checkpoints table..""")
    cleanCheckpoint[
      PublicKeyHash,
      Tables.DelegatesCheckpointRow,
      Tables.DelegatesCheckpoint,
      TableQuery[Tables.DelegatesCheckpoint]
    ](
      selection = pkhs,
      tableQuery = Tables.DelegatesCheckpoint,
      tableTotal = getDelegatesCheckpointSize(),
      applySelection = (checkpoint, keySet) => checkpoint.filter(_.delegatePkh inSet keySet.map(_.value))
    )
  }

  /**
    * Removes  data from a generic checkpoint table
    * @param selection limits the removed rows to those
    *                  concerning the selected elements, by default no selection is made.
    *                  We can't assume those keys were previously loaded from the checkpoint table itself
    * @param tableQuery the slick table query to identify which is the table to clean up
    * @param tableTotal an action needed to compute the number of max keys in the checkpoint
    * @param applySelection used to filter the results to clean-up, using the available `selection`
    * @return the database action to run
    */
  def cleanCheckpoint[PK, Row, T <: Table[Row], CheckpointTable <: TableQuery[T]](
      selection: Option[Set[PK]] = None,
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

  /**
    * @return the number of distinct accounts present in the checkpoint table
    */
  def getAccountsCheckpointSize(): DBIO[Int] =
    Tables.AccountsCheckpoint.distinctOn(_.accountId).length.result

  /**
    * Takes all existing account ids and puts them in the
    * checkpoint to be later reloaded, based on the passed block reference
    */
  def refillAccountsCheckpointFromExisting(
      hash: BlockHash,
      level: Int,
      timestamp: Instant,
      cycle: Option[Int],
      selectors: Set[AccountIdPattern] = Set(".*")
  )(
      implicit ec: ExecutionContext
  ): DBIO[Option[Int]] = {

    /* as taken almost literally from this S.O. suggestion
     * https://stackoverflow.com/questions/46218122/slick-is-there-a-way-to-create-a-where-clause-with-a-regex
     * will add the postgres '~' operator to slick filters, to do regular expression matching
     */
    implicit class postgresPosixRegexMatch(value: Rep[String]) {
      def ~(pattern: Rep[String]): Rep[Boolean] = {
        val expr = SimpleExpression.binary[String, String, Boolean] { (v, pat, builder) =>
          builder.expr(v)
          builder.sqlBuilder += " ~ "
          builder.expr(pat)
        }
        expr(value, pattern)
      }
    }

    logger.info(
      "Fetching all ids for existing accounts matching {} and adding them to checkpoint with block hash {}, level {}, cycle {} and time {}",
      selectors.mkString(", "),
      hash.value,
      level,
      cycle,
      timestamp
    )

    //for each pattern, create a query and then union them all
    val regexQueries = selectors
      .map(
        sel =>
          Tables.Accounts
            .filter(_.accountId ~ sel)
            .map(_.accountId)
            .distinct
      )
      .reduce(_ union _)

    regexQueries.distinct.result
      .flatMap(
        ids =>
          writeAccountsCheckpoint(
            List(
              (hash, level, Some(timestamp), cycle, ids.map(AccountId(_)).toList)
            )
          )
      )
  }

  /**
    * @return the number of distinct accounts present in the checkpoint table
    */
  def getDelegatesCheckpointSize(): DBIO[Int] =
    Tables.DelegatesCheckpoint.distinctOn(_.delegatePkh).length.result

  /**
    * Reads the account ids in the checkpoint table,
    * sorted by decreasing block-level
    * @return a database action that loads the list of relevant rows
    */
  def getLatestAccountsFromCheckpoint(implicit ec: ExecutionContext): DBIO[Map[AccountId, BlockReference]] = {
    /* Given a sorted sequence of checkpoint rows whose reference level is decreasing,
     * collects them in a map, skipping keys already added
     * This prevents duplicate entry keys and keeps the highest level referenced, using an in-memory algorithm
     * We can think of optimizing this later, we're now optimizing on db queries
     */
    def keepLatestAccountIds(checkpoints: Seq[Tables.AccountsCheckpointRow]): Map[AccountId, BlockReference] =
      checkpoints.foldLeft(Map.empty[AccountId, BlockReference]) { (collected, row) =>
        val key = AccountId(row.accountId)
        val time = row.asof.toInstant
        if (collected.contains(key)) collected
        else collected + (key -> (BlockHash(row.blockId), row.blockLevel, Some(time), row.cycle))
      }

    logger.info("Getting the latest accounts from checkpoints in the DB...")

    Tables.AccountsCheckpoint
      .sortBy(_.blockLevel.desc)
      .result
      .map(keepLatestAccountIds)
  }

  /**
    * Reads the delegate key hashes in the checkpoint table,
    * sorted by decreasing block-level
    * @return a database action that loads the list of relevant rows
    */
  def getLatestDelegatesFromCheckpoint(implicit ex: ExecutionContext): DBIO[Map[PublicKeyHash, BlockReference]] = {
    /* Given a sorted sequence of checkpoint rows whose reference level is decreasing,
     * collects them in a map, skipping keys already added
     * This prevents duplicate entry keys and keeps the highest level referenced, using an in-memory algorithm
     * We can think of optimizing this later, we're now optimizing on db queries
     */
    def keepLatestDelegatesKeys(
        checkpoints: Seq[Tables.DelegatesCheckpointRow]
    ): Map[PublicKeyHash, BlockReference] =
      checkpoints.foldLeft(Map.empty[PublicKeyHash, BlockReference]) { (collected, row) =>
        val key = PublicKeyHash(row.delegatePkh)
        if (collected.contains(key)) collected
        else collected + (key -> (BlockHash(row.blockId), row.blockLevel, None, None))
      }

    logger.info("Getting the latest delegates from checkpoints in the DB...")
    Tables.DelegatesCheckpoint
      .sortBy(_.blockLevel.desc)
      .result
      .map(keepLatestDelegatesKeys)
  }

  /**
    * Writes the blocks data to the database
    * at the same time saving enough information about updated accounts to later fetch those accounts
    * @param blocks the blocks to save
    * @param accountUpdates all the ids for accounts involved in some block operation
    */
  def writeBlocksAndCheckpointAccounts(
      blocks: List[Block],
      accountUpdates: List[BlockTagged[List[AccountId]]]
  )(implicit ec: ExecutionContext): DBIO[Option[Int]] = {
    logger.info("Writing blocks and account checkpoints to the DB...")
    //sequence both operations in a single transaction
    (writeBlocks(blocks) andThen writeAccountsCheckpoint(accountUpdates.map(_.asTuple))).transactionally
  }

  /** Custom postgres profile for enabling `insertOrUpdateAll` */
  trait CustomPostgresProfile extends ExPostgresProfile {
    // Add back `capabilities.insertOrUpdate` to enable native `upsert` support; for postgres 9.5+
    override protected def computeCapabilities: Set[Capability] =
      super.computeCapabilities + JdbcCapabilities.insertOrUpdate
  }

  object CustomPostgresProfile extends CustomPostgresProfile

  /**
    * Upserts baking rights to the database
    * @param bakingRightsMap mapping of hash to bakingRights list
    */
  def upsertBakingRights(
      bakingRightsMap: Map[FetchRights, List[BakingRights]]
  ): DBIO[Option[Int]] = {
    import CustomPostgresProfile.api._
    logger.info("Writing baking rights to the DB...")
    val conversionResult = for {
      (blockHashWithCycleAndGovernancePeriod, bakingRightsList) <- bakingRightsMap
      bakingRights <- bakingRightsList
    } yield (blockHashWithCycleAndGovernancePeriod, bakingRights).convertTo[Tables.BakingRightsRow]

    Tables.BakingRights.insertOrUpdateAll(conversionResult)
  }

  /**
    * Upserts endorsing rights to the database
    * @param endorsingRightsMap mapping of hash to endorsingRights list
    */
  def upsertEndorsingRights(
      endorsingRightsMap: Map[FetchRights, List[EndorsingRights]]
  ): DBIO[Option[Int]] = {
    import CustomPostgresProfile.api._
    logger.info("Writing endorsing rights to the DB...")
    val transformationResult = for {
      (blockHashWithCycleAndGovernancePeriod, endorsingRightsList) <- endorsingRightsMap
      endorsingRights <- endorsingRightsList
    } yield (blockHashWithCycleAndGovernancePeriod, endorsingRights).convertToA[List, Tables.EndorsingRightsRow]

    Tables.EndorsingRights.insertOrUpdateAll(transformationResult.flatten)
  }
  val berLogger = LoggerFactory.getLogger("RightsFetcher")

  /**
    * Writes baking rights to the database
    * @param bakingRights mapping of hash to endorsingRights list
    */
  def insertBakingRights(bakingRights: List[BakingRights]): DBIO[Option[Int]] = {
    berLogger.info("Inserting baking rights to the DB...")
    Tables.BakingRights ++= bakingRights.map(_.convertTo[Tables.BakingRightsRow])
  }

  /**
    * Writes endorsing rights to the database
    * @param endorsingRights mapping of hash to endorsingRights list
    */
  def insertEndorsingRights(endorsingRights: List[EndorsingRights]): DBIO[Option[Int]] = {
    berLogger.info("Inserting endorsing rights to the DB...")
    Tables.EndorsingRights ++= endorsingRights.flatMap(_.convertToA[List, Tables.EndorsingRightsRow])
  }

  /**
    * Writes accounts to the database and record the keys (hashes) to later save complete delegates information relative to each block
    * @param accounts the full accounts' data
    * @param delegatesKeyHashes for each block reference a list of pkh of delegates that were involved with the block
    * @return a database action that stores both arguments and return a tuple of the row counts inserted
    */
  def writeAccountsAndCheckpointDelegates(
      accounts: List[BlockTagged[Map[AccountId, Account]]],
      delegatesKeyHashes: List[BlockTagged[List[PublicKeyHash]]]
  )(implicit ec: ExecutionContext): DBIO[(Option[Int], Option[Int], Option[Int])] = {
    import slickeffect.implicits._

    logger.info("Writing accounts and delegate checkpoints to the DB...")

    //we tuple because we want transactionality guarantees and we need all insert-counts to get returned
    Async[DBIO]
      .tuple3(
        writeAccounts(accounts),
        writeAccountsHistory(accounts),
        writeDelegatesCheckpoint(delegatesKeyHashes.map(_.asTuple))
      )
      .transactionally
  }

  /**
    * Writes delegates to the database and gets the delegated accounts' keys to copy the accounts data
    * as delegated contracts on the db, as a secondary copy
    * @param delegates the full delegates' data
    * @return a database action that stores delegates and returns the number of saved rows
    */
  def writeDelegatesAndCopyContracts(
      delegates: List[BlockTagged[Map[PublicKeyHash, Delegate]]]
  ): DBIO[Option[Int]] = {
    import CustomPostgresProfile.api._

    val keepMostRecent = (rows: List[Tables.DelegatesRow]) =>
      rows
        .sortBy(_.blockLevel)(Ordering[Int].reverse)
        .foldLeft((Queue.empty[Tables.DelegatesRow], Set.empty[String])) { (accumulator, row) =>
          val (queued, index) = accumulator
          if (index(row.pkh)) accumulator else (queued :+ row, index + row.pkh)
        }
        ._1

    logger.info("Writing delegates to DB and copying contracts to delegates contracts table...")

    val rows = delegates.flatMap {
      case BlockTagged(blockHash, blockLevel, timestamp, cycle, delegateMap) =>
        delegateMap.map {
          case (pkh, delegate) =>
            (blockHash, blockLevel, pkh, delegate).convertTo[Tables.DelegatesRow]
        }
    }
    (keepMostRecent andThen Tables.Delegates.insertOrUpdateAll)(rows)
  }

  /** Fetch the latest block level available for each account id stored */
  def getLevelsForAccounts(ids: Set[AccountId]): DBIO[Seq[(String, BigDecimal)]] =
    Tables.Accounts
      .map(table => (table.accountId, table.blockLevel))
      .filter(_._1 inSet ids.map(_.id))
      .result

  /** Fetch the latest block level available for each delegate pkh stored */
  def getLevelsForDelegates(ids: Set[PublicKeyHash]): DBIO[Seq[(String, Int)]] =
    Tables.Delegates
      .map(table => (table.pkh, table.blockLevel))
      .filter(_._1 inSet ids.map(_.value))
      .result

  /** Updates accounts history with bakers */
  def updateAccountsHistoryWithBakers(bakers: List[Voting.BakerRolls], block: Block): DBIO[Int] = {
    logger.info(s"""Writing ${bakers.length} accounts history updates to the DB...""")
    Tables.AccountsHistory
      .filter(
        ahs =>
          ahs.isBaker === false && ahs.blockId === block.data.hash.value && ahs.accountId.inSet(bakers.map(_.pkh.value))
      )
      .map(_.isBaker)
      .update(true)
  }

  /** Updates accounts with bakers */
  def updateAccountsWithBakers(bakers: List[Voting.BakerRolls], block: Block): DBIO[Int] = {
    logger.info(s"""Writing ${bakers.length} accounts updates to the DB...""")
    Tables.Accounts
      .filter(
        account =>
          account.isBaker === false && account.blockId === block.data.hash.value && account.accountId
              .inSet(bakers.map(_.pkh.value))
      )
      .map(_.isBaker)
      .update(true)
  }

  def getBakersForBlocks(
      hashes: List[BlockHash]
  )(implicit ec: ExecutionContext): DBIO[List[(BlockHash, List[BakerRolls])]] =
    DBIO.sequence {
      hashes.map { hash =>
        Tables.Delegates
          .filter(_.pkh === hash.value)
          .result
          .map(hash -> _.map(delegate => BakerRolls(PublicKeyHash(delegate.pkh), delegate.rolls)).toList)
      }
    }

  /**
    * Given the operation kind, return range of fees and timestamp for that operation.
    * @param kind                 Operation kind
    * @param numberOfFeesAveraged How many values to use for statistics computations
    * @return                     The average fees for a given operation kind, if it exists
    */
  def calculateAverageFees(kind: String, numberOfFeesAveraged: Int)(
      implicit ec: ExecutionContext
  ): DBIO[Option[AverageFees]] = {
    def computeAverage(
        ts: java.sql.Timestamp,
        cycle: Option[Int],
        level: Option[Int],
        fees: Seq[(Option[BigDecimal], java.sql.Timestamp, Option[Int], Option[Int])]
    ): AverageFees = {
      val values = fees.map {
        case (fee, _, _, _) => fee.map(_.toDouble).getOrElse(0.0)
      }
      val m: Int = ceil(mean(values)).toInt
      val s: Int = ceil(stdev(values)).toInt

      AverageFees(max(m - s, 0), m, m + s, ts, kind, cycle, level)
    }

    val opQuery =
      Tables.Operations
        .filter(_.kind === kind)
        .map(o => (o.fee, o.timestamp, o.cycle, o.level))
        .distinct
        .sortBy { case (_, ts, _, _) => ts.desc }
        .take(numberOfFeesAveraged)
        .result

    opQuery.map { timestampedFees =>
      timestampedFees.headOption.map {
        case (_, latest, cycle, level) =>
          computeAverage(latest, cycle, level, timestampedFees)
      }
    }
  }

  /** Finds activated accounts - useful when updating accounts history
    * @return sequence of activated account ids
    */
  def findActivatedAccountIds: DBIO[Seq[String]] =
    Tables.Accounts
      .filter(_.isActivated)
      .map(_.accountId)
      .result

  /** Load all operations referenced from a block level and higher, that are of a specific kind.
    * @param ofKind a set of kinds to filter operations, if empty there will be no result
    * @param fromLevel the lowest block-level to start from, zero by default
    * @return the matching operations pkh, sorted by ascending block-level
    */
  def fetchRecentOperationsHashByKind(
      ofKind: Set[String],
      fromLevel: Int = 0
  ): DBIO[Seq[Option[String]]] =
    Tables.Operations
      .filter(
        row => (row.kind inSet ofKind) && (row.blockLevel >= fromLevel)
      )
      .sortBy(_.blockLevel.asc)
      .map(_.pkh)
      .result

  /**
    * Reads in all operations referring to the group
    * @param groupHash is the group identifier
    * @param ec the `ExecutionContext` needed to compose db operations
    * @return the operations and the collecting group, if there's one for the given hash, else `None`
    */
  def operationsForGroup(
      groupHash: String
  )(implicit ec: ExecutionContext): DBIO[Option[(Tables.OperationGroupsRow, Seq[Tables.OperationsRow])]] =
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

  /**
    * Checks if a block for this hash and related operations are stored on db
    * @param hash Identifies the block
    * @param ec   Needed to compose the operations
    * @return     true if block and operations exists
    */
  def blockExists(hash: BlockHash)(implicit ec: ExecutionContext): DBIO[Boolean] =
    for {
      blockThere <- Tables.Blocks.findBy(_.hash).applied(hash.value).exists.result
      opsThere <- Tables.OperationGroups.filter(_.blockId === hash.value).exists.result
    } yield blockThere && opsThere

  /* use as max block level when none exists */
  private[tezos] val defaultBlockLevel: BigDecimal = -1

  /** Precompiled fetch for Operations by Group */
  val operationsByGroupHash =
    Tables.Operations.findBy(_.operationGroupHash)

  /** Computes the max level of blocks or [[defaultBlockLevel]] if no block exists */
  private[tezos] def fetchMaxBlockLevel: DBIO[Int] =
    Tables.Blocks
      .map(_.level)
      .max
      .getOrElse(defaultBlockLevel.toInt)
      .result

  /** Get next batch of voting levels */
  def fetchVotingBlockLevels(implicit ec: ExecutionContext): DBIO[Set[Int]] = {

    def fetchVotesMaxLevel: DBIO[Int] =
      Tables.Votes
        .map(_.level)
        .max
        .getOrElse(defaultBlockLevel.toInt)
        .result

    for {
      headBlockLevel <- fetchMaxBlockLevel
      voteMaxLevel <- fetchVotesMaxLevel
    } yield (voteMaxLevel + 1 to headBlockLevel).toSet
  }

  /** Voting data representation */
  case class VotingData(
      proposalHash: Option[String],
      level: Int,
      hash: String,
      cycle: Option[Int],
      timestamp: java.sql.Timestamp,
      ballot: Option[String],
      address: Option[String]
  )

  /** Get necessary fields to populate votes table from ballot operations */
  def fetchVotingData(levels: Set[Int])(implicit ec: ExecutionContext): DBIO[Seq[VotingData]] =
    Tables.Operations
      .filter(_.kind === "ballot")
      .filter(_.blockLevel inSet levels)
      .map { o =>
        (o.proposal, o.blockLevel, o.blockHash, o.cycle, o.timestamp, o.ballot, o.source)
      }
      .result
      .map(_.map(VotingData.tupled))

  /** is there any block stored? */
  def doBlocksExist(): DBIO[Boolean] =
    Tables.Blocks.exists.result

  /** Returns all levels that have seen a custom event processing, e.g.
    * - auto-refresh of all accounts after the babylon protocol amendment
    *
    * @param eventType the type of event levels to fetch
    * @return a list of values marking specific levels that needs not be processed anymore
    */
  def fetchProcessedEventsLevels(eventType: String): DBIO[Seq[BigDecimal]] =
    Tables.ProcessedChainEvents.filter(_.eventType === eventType).map(_.eventLevel).result

  /** Adds any new level for which a custom event processing has been executed
    *
    * @param eventType the type of event to record
    * @param levels the levels to write to db, currently there must be no collision with existing entries
    * @return the number of entries saved to the checkpoint
    */
  def writeProcessedEventsLevels(eventType: String, levels: List[BigDecimal]): DBIO[Option[Int]] =
    Tables.ProcessedChainEvents ++= levels.map(Tables.ProcessedChainEventsRow(_, eventType))

  /** Prefix for the table queries */
  private val tablePrefix = "tezos"

  /**
    * Counts number of rows in the given table
    * @param table  slick table
    * @return       amount of rows in the table
    */
  def countRows(table: String)(implicit ec: ExecutionContext): DBIO[Int] =
    sql"""SELECT reltuples FROM pg_class WHERE relname = $table""".as[Int].map(_.head)

  /**
    * Counts number of distinct elements by given table and column
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table  name of the table
    * @param column name of the column
    * @return       amount of distinct elements in given column
    */
  def countDistinct(table: String, column: String)(implicit ec: ExecutionContext): DBIO[Int] =
    sql"""SELECT COUNT(*) FROM (SELECT DISTINCT #$column FROM #${tablePrefix + "." + table}) AS temp"""
      .as[Int]
      .map(_.head)

  /**
    * Selects distinct elements by given table and column
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table  name of the table
    * @param column name of the column
    * @return       distinct elements in given column as a list
    */
  def selectDistinct(table: String, column: String)(implicit ec: ExecutionContext): DBIO[List[String]] =
    sql"""SELECT DISTINCT #$column::VARCHAR FROM #${tablePrefix + "." + table} WHERE #$column IS NOT NULL"""
      .as[String]
      .map(_.toList)

  /**
    * Selects distinct elements by given table and column with filter
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table          name of the table
    * @param column         name of the column
    * @param matchingString string which is being matched
    * @return               distinct elements in given column as a list
    */
  def selectDistinctLike(table: String, column: String, matchingString: String)(
      implicit ec: ExecutionContext
  ): DBIO[List[String]] =
    sql"""SELECT DISTINCT #$column::VARCHAR FROM #${tablePrefix + "." + table} WHERE #$column LIKE '%#$matchingString%' AND #$column IS NOT NULL"""
      .as[String]
      .map(_.toList)

  /**
    * Selects elements filtered by the predicates
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table          name of the table
    * @param columns        list of column names
    * @param predicates     list of predicates for query to be filtered with
    * @param ordering       list of ordering conditions for the query
    * @param aggregation    optional aggregation
    * @param limit          max number of rows fetched
    * @return               list of map of [string, any], which represents list of rows as a map of column name to value
    */
  def selectWithPredicates(
      table: String,
      columns: List[Field],
      predicates: List[Predicate],
      ordering: List[QueryOrdering],
      aggregation: List[Aggregation],
      temporalPartition: Option[String],
      snapshot: Option[Snapshot],
      outputType: OutputType,
      limit: Int
  )(implicit ec: ExecutionContext): DBIO[List[QueryResponse]] = {
    val tableWithPrefix = tablePrefix + "." + table
    val q = (temporalPartition, snapshot) match {
      case (Some(tempPartition), Some(snap)) =>
        makeTemporalQuery(tableWithPrefix, columns, predicates, aggregation, ordering, tempPartition, snap, limit)
      case _ =>
        makeQuery(tableWithPrefix, columns, aggregation)
          .addPredicates(predicates)
          .addGroupBy(aggregation, columns)
          .addHaving(aggregation)
          .addOrdering(ordering)
          .addLimit(limit)
    }

    if (outputType == OutputType.sql) {
      DBIO.successful(List(Map("sql" -> Some(q.queryParts.mkString("")))))
    } else {
      q.as[QueryResponse].map(_.toList)
    }
  }

}
