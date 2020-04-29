package tech.cryptonomic.conseil.common.tezos

import java.sql.Timestamp
import java.time.Instant

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.config.ChainEvent.AccountIdPattern
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query => _}
import tech.cryptonomic.conseil.common.tezos.FeeOperations._
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.tezos.bigmaps.BigMapsOperations
import tech.cryptonomic.conseil.common.util.CollectionOps._
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.util.MathUtil.{mean, stdev}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.math.{ceil, max}
import cats.effect.Async
import org.slf4j.LoggerFactory
import tech.cryptonomic.conseil.common.tezos.Tables.GovernanceRow
import tech.cryptonomic.conseil.common.tezos.TezosTypes.FetchRights
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Voting.BakerRolls
import slick.lifted.{AbstractTable, TableQuery}
import tech.cryptonomic.conseil.common.sql.{CustomProfileExtension, DefaultDatabaseOperations}

import scala.collection.immutable.Queue
import tech.cryptonomic.conseil.common.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.common.util.ConfigUtil
import tech.cryptonomic.conseil.common.tezos.Tables.OriginatedAccountMapsRow
import tech.cryptonomic.conseil.common.tezos.michelson.contracts.TNSContract

/**
  * Functions for writing Tezos data to a database.
  */
object TezosDatabaseOperations extends DefaultDatabaseOperations("tezos") with LazyLogging {
  import DatabaseConversions._

  private val bigMapOps = BigMapsOperations(CustomProfileExtension)

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
    import CustomProfileExtension.api._

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
    * @param accountsInfo List data on the accounts and the corresponding blocks that operated on those with accounts that became inactive
    * @return     Database action possibly containing the number of rows written (if available from the underlying driver)
    */
  def writeAccountsHistory(
      accountsInfo: List[(BlockTagged[Map[AccountId, Account]], List[Tables.AccountsRow])]
  ): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${accountsInfo.length} accounts_history to DB...""")
    Tables.AccountsHistory ++= accountsInfo.flatMap(_.convertToA[List, Tables.AccountsHistoryRow])
  }

  /**
    * Writes blocks and related operations to a database.
    * @param blocks   Block with operations.
    * @return         Database action to execute.
    */
  def writeBlocks(
      blocks: List[Block]
  )(implicit ec: ExecutionContext, tokenContracts: TokenContracts, tnsContracts: TNSContract): DBIO[Unit] = {
    // Kleisli is a Function with effects, Kleisli[F, A, B] ~= A => F[B]
    import cats.data.Kleisli
    import cats.instances.list._
    import slickeffect.implicits._
    import DatabaseConversions.OperationTablesData
    import SymbolSourceLabels.Show._
    import tech.cryptonomic.conseil.common.tezos.BlockBalances._
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
  def saveBigMaps(
      blocks: List[Block]
  )(implicit ec: ExecutionContext, tokenContracts: TokenContracts, tnsContracts: TNSContract): DBIO[Unit] = {
    import cats.implicits._
    import slickeffect.implicits._

    /* We might have new information to store for originated smart contracts, like tokens and tns
     * thus we use any newly found contract origination reference to maps and pass
     * them to the proper contracts initialization code.
     */
    def performSmartContractsInitialization(insertAction: DBIO[List[OriginatedAccountMapsRow]]): DBIO[Unit] =
      insertAction
        .flatTap(bigMapOps.initTNSMaps)
        .map(bigMapOps.initTokenMaps)

    logger.info("Writing big map differences to DB...")

    /* The interleaving of these operations would actually need a more sophisticated handling to be robust:
     * we're processing collections of blocks, therefore some operations handled "after" might be
     * referring to something "happening before" or viceversa.
     * E.g. you could find that a new origination creates a map with the same identifier of another previously
     * removed (is this allowed?).
     * Therefore the insert might fail on finding the old record id being there already, whereas the real sequence
     * of events would have removed the old first.
     * We might consider improving the situation by doing a pre-check that the altered sequencing of the operations
     * doesn't interfere with the "causality" of the chain events.
     */
    val operationSequence: List[List[Block] => DBIO[Unit]] = List(
      (bigMapOps.saveMaps _).rmap(_.void),
      (bigMapOps.upsertContent _).rmap(_.void),
      (bigMapOps.saveContractOrigin _).rmap(performSmartContractsInitialization),
      (bigMapOps.copyContent _).rmap(_.void),
      (bigMapOps.removeMaps _),
      (bigMapOps.updateTokenBalances _).rmap(_.void)
    )

    operationSequence
      .traverse[DBIO, Unit](
        op => op(blocks)
      )
      .void
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
  def writeBakersCheckpoint(
      delegatesKeyHashes: List[(BlockHash, Int, Option[Instant], Option[Int], List[PublicKeyHash])]
  ): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${delegatesKeyHashes.map(_._5).map(_.length).sum} delegate checkpoints to DB...""")
    Tables.BakersCheckpoint ++= delegatesKeyHashes.flatMap(_.convertToA[List, Tables.BakersCheckpointRow])
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
  def cleanBakersCheckpoint(
      pkhs: Option[Set[PublicKeyHash]] = None
  )(implicit ec: ExecutionContext): DBIO[Int] = {
    logger.info("""Cleaning the delegate checkpoints table..""")
    cleanCheckpoint[
      PublicKeyHash,
      Tables.BakersCheckpointRow,
      Tables.BakersCheckpoint,
      TableQuery[Tables.BakersCheckpoint]
    ](
      selection = pkhs,
      tableQuery = Tables.BakersCheckpoint,
      tableTotal = getBakersCheckpointSize(),
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
    * @return the number of distinct bakers present in the checkpoint table
    */
  def getBakersCheckpointSize(): DBIO[Int] =
    Tables.BakersCheckpoint.distinctOn(_.delegatePkh).length.result

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
        else collected + (key -> (BlockHash(row.blockId), row.blockLevel, Some(time), row.cycle, None))
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
  def getLatestBakersFromCheckpoint(implicit ex: ExecutionContext): DBIO[Map[PublicKeyHash, BlockReference]] = {
    /* Given a sorted sequence of checkpoint rows whose reference level is decreasing,
     * collects them in a map, skipping keys already added
     * This prevents duplicate entry keys and keeps the highest level referenced, using an in-memory algorithm
     * We can think of optimizing this later, we're now optimizing on db queries
     */
    def keepLatestDelegatesKeys(
        checkpoints: Seq[Tables.BakersCheckpointRow]
    ): Map[PublicKeyHash, BlockReference] =
      checkpoints.foldLeft(Map.empty[PublicKeyHash, BlockReference]) { (collected, row) =>
        val key = PublicKeyHash(row.delegatePkh)
        if (collected.contains(key)) collected
        else collected + (key -> (BlockHash(row.blockId), row.blockLevel, None, row.cycle, row.period))
      }

    logger.info("Getting the latest bakers from checkpoints in the DB...")
    Tables.BakersCheckpoint
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
  )(implicit ec: ExecutionContext, tokenContracts: TokenContracts, tnsContracts: TNSContract): DBIO[Option[Int]] = {
    logger.info("Writing blocks and account checkpoints to the DB...")
    //sequence both operations in a single transaction
    (writeBlocks(blocks) andThen writeAccountsCheckpoint(accountUpdates.map(_.asTuple))).transactionally
  }

  /**
    * Upserts baking rights to the database
    * @param bakingRightsMap mapping of hash to bakingRights list
    */
  def upsertBakingRights(
      bakingRightsMap: Map[FetchRights, List[BakingRights]]
  ): DBIO[Option[Int]] = {
    import CustomProfileExtension.api._
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
    import CustomProfileExtension.api._
    logger.info("Writing endorsing rights to the DB...")
    val transformationResult = for {
      (blockHashWithCycleAndGovernancePeriod, endorsingRightsList) <- endorsingRightsMap
      endorsingRights <- endorsingRightsList
    } yield (blockHashWithCycleAndGovernancePeriod, endorsingRights).convertToA[List, Tables.EndorsingRightsRow]

    Tables.EndorsingRights.insertOrUpdateAll(transformationResult.flatten)
  }
  val berLogger = LoggerFactory.getLogger("RightsFetcher")

  /**
    * Updates timestamps in the baking_rights table
    * @param bakingRights baking rights to be updated
    */
  def updateBakingRightsTimestamp(bakingRights: List[BakingRights]): DBIO[List[Int]] =
    DBIO.sequence {
      bakingRights.map { upd =>
        Tables.BakingRights
          .filter(er => er.delegate === upd.delegate && er.level === upd.level)
          .map(_.estimatedTime)
          .update(upd.estimated_time.map(datetime => Timestamp.from(datetime.toInstant)))
      }
    }

  /**
    * Updates timestamps in the endorsing_rights table
    * @param endorsingRights endorsing rights to be updated
    */
  def updateEndorsingRightsTimestamp(endorsingRights: List[EndorsingRights]): DBIO[List[Int]] =
    DBIO.sequence {
      endorsingRights.map { upd =>
        Tables.EndorsingRights
          .filter(er => er.delegate === upd.delegate && er.level === upd.level)
          .map(_.estimatedTime)
          .update(upd.estimated_time.map(datetime => Timestamp.from(datetime.toInstant)))
      }
    }

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

  def insertGovernance(governance: List[GovernanceRow]): DBIO[Option[Int]] = {
    logger.info("Writing {} governance rows into database...", governance.size)
    Tables.Governance ++= governance
  }

  def upsertTezosNames(names: List[TNSContract.NameRecord]): DBIO[Option[Int]] = {
    import CustomProfileExtension.api._
    logger.info("Upserting {} tezos names rows into the database...", names.size)
    Tables.TezosNames.insertOrUpdateAll(names.map(_.convertTo[Tables.TezosNamesRow]))
  }

  /**
    * Writes accounts to the database and record the keys (hashes) to later save complete bakers information relative to each block
    * @param accounts the full accounts' data with account rows of inactive bakers
    * @param bakersKeyHashes for each block reference a list of pkh of bakers that were involved with the block
    * @return a database action that stores both arguments and return a tuple of the row counts inserted
    */
  def writeAccountsAndCheckpointBakers(
      accounts: List[(BlockTagged[Map[AccountId, Account]], List[Tables.AccountsRow])],
      bakersKeyHashes: List[BlockTagged[List[PublicKeyHash]]]
  )(implicit ec: ExecutionContext): DBIO[(Option[Int], Option[Int], Option[Int])] = {
    import slickeffect.implicits._

    logger.info("Writing accounts and delegate checkpoints to the DB...")

    //we tuple because we want transactionality guarantees and we need all insert-counts to get returned
    Async[DBIO]
      .tuple3(
        writeAccounts(accounts.map(_._1)),
        writeAccountsHistory(accounts),
        writeBakersCheckpoint(bakersKeyHashes.map(_.asTuple))
      )
      .transactionally
  }

  /**
    * Writes bakers to the database and gets the delegated accounts' keys to copy the accounts data
    * as delegated contracts on the db, as a secondary copy
    * @param bakers the full delegates' data
    * @return a database action that stores delegates and returns the number of saved rows
    */
  def writeBakersAndCopyContracts(
      bakers: List[BlockTagged[Map[PublicKeyHash, Delegate]]]
  ): DBIO[Option[Int]] = {
    import CustomProfileExtension.api._

    val keepMostRecent = (rows: List[Tables.BakersRow]) =>
      rows
        .sortBy(_.blockLevel)(Ordering[Int].reverse)
        .foldLeft((Queue.empty[Tables.BakersRow], Set.empty[String])) { (accumulator, row) =>
          val (queued, index) = accumulator
          if (index(row.pkh)) accumulator else (queued :+ row, index + row.pkh)
        }
        ._1

    logger.info("Writing bakers to DB and copying contracts to bakers table...")

    val rows = bakers.flatMap {
      case BlockTagged(blockHash, blockLevel, timestamp, cycle, period, delegateMap) =>
        delegateMap.map {
          case (pkh, delegate) =>
            (blockHash, blockLevel, pkh, delegate, cycle, period).convertTo[Tables.BakersRow]
        }
    }
    (keepMostRecent andThen Tables.Bakers.insertOrUpdateAll)(rows)
  }

  /** Gets ballot operations for given cycle */
  def getBallotOperationsForCycle(cycle: Int)(implicit ec: ExecutionContext): DBIO[Voting.BallotCounts] =
    Tables.Operations
      .filter(op => op.kind === "ballot" && op.cycle === cycle)
      .groupBy(_.ballot)
      .map {
        case (vote, ops) => vote -> ops.length
      }
      .result
      .map { res =>
        val (yaysCount, naysCount, passesCount) = res.foldLeft(0, 0, 0) {
          case ((_, nays, passes), (Some("yay"), count)) => (count, nays, passes)
          case ((yays, _, passes), (Some("nay"), count)) => (yays, count, passes)
          case ((yays, nays, _), (Some("pass"), count)) => (yays, nays, count)
          case (acc, _) => acc
        }
        Voting.BallotCounts(yaysCount, naysCount, passesCount)
      }

  /** Gets ballot operations for given level */
  def getBallotOperationsForLevel(level: Int)(implicit ec: ExecutionContext): DBIO[Voting.BallotCounts] =
    Tables.Operations
      .filter(op => op.kind === "ballot" && op.level === level)
      .groupBy(_.ballot)
      .map {
        case (vote, ops) => vote -> ops.length
      }
      .result
      .map { res =>
        val (yaysCount, naysCount, passesCount) = res.foldLeft(0, 0, 0) {
          case ((_, nays, passes), (Some("yay"), count)) => (count, nays, passes)
          case ((yays, _, passes), (Some("nay"), count)) => (yays, count, passes)
          case ((yays, nays, _), (Some("pass"), count)) => (yays, nays, count)
          case (acc, _) => acc
        }
        Voting.BallotCounts(yaysCount, naysCount, passesCount)
      }

  /** Fetch the latest block level available for each account id stored */
  def getLevelsForAccounts(ids: Set[AccountId]): DBIO[Seq[(String, BigDecimal)]] =
    Tables.Accounts
      .map(table => (table.accountId, table.blockLevel))
      .filter(_._1 inSet ids.map(_.id))
      .result

  /** Fetch the latest block level available for each delegate pkh stored */
  def getLevelsForDelegates(ids: Set[PublicKeyHash]): DBIO[Seq[(String, Int)]] =
    Tables.Bakers
      .map(table => (table.pkh, table.blockLevel))
      .filter(_._1 inSet ids.map(_.value))
      .result

  /**
    * Gets inactive bakers from accounts
    * @param activeBakers accountIds needed to filter out them from all of the bakers
    * @return inactive baker accounts
    * */
  def getInactiveBakersFromAccounts(
      activeBakers: List[String]
  )(implicit ec: ExecutionContext): DBIO[List[Tables.AccountsRow]] =
    Tables.Accounts
      .filter(_.isBaker === true)
      .filterNot(_.accountId inSet activeBakers)
      .result
      .map(_.toList)

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
        Tables.Bakers
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
  def fetchMaxBlockLevel: DBIO[Int] =
    Tables.Blocks
      .map(_.level)
      .max
      .getOrElse(defaultBlockLevel.toInt)
      .result

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

  import shapeless._
  import shapeless.ops.hlist._

  import kantan.csv._

  /** Reads and inserts CSV file to the database for the given table */
  def initTableFromCsv[A <: AbstractTable[_], H <: HList](
      db: Database,
      table: TableQuery[A],
      network: String,
      separator: Char = ','
  )(
      implicit hd: HeaderDecoder[A#TableElementType],
      g: Generic.Aux[A#TableElementType, H],
      m: Mapper.Aux[ConfigUtil.Csv.Trimmer.type, H, H],
      ec: ExecutionContext
  ): Future[(List[A#TableElementType], Option[Int])] =
    ConfigUtil.Csv.readTableRowsFromCsv(table, network, separator) match {
      case Some(rows) =>
        db.run(insertWhenEmpty(table, rows))
          .andThen {
            case Success(_) => logger.info("Written {} {} rows", rows.size, table.baseTableRow.tableName)
            case Failure(e) => logger.error(s"Could not fill ${table.baseTableRow.tableName} table", e)
          }
          .map(rows -> _)
      case None =>
        logger.warn("No csv configuration found to initialize table {} for {}.", table.baseTableRow.tableName, network)
        Future.successful(List.empty -> None)
    }

}
