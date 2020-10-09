package tech.cryptonomic.conseil.indexer.tezos

import java.sql.Timestamp
import java.time.{Instant, ZoneOffset}

import cats.effect.Async
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{AbstractTable, TableQuery}
import tech.cryptonomic.conseil.common.config.ChainEvent.AccountIdPattern
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{Query => _}
import tech.cryptonomic.conseil.common.sql.CustomProfileExtension
import tech.cryptonomic.conseil.common.tezos.Tables.{GovernanceRow, OriginatedAccountMapsRow}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Fee.AverageFees
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.indexer.tezos.bigmaps.BigMapsOperations
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.{TNSContract, TokenContracts}
import tech.cryptonomic.conseil.common.tezos.Tables
import tech.cryptonomic.conseil.common.util.ConfigUtil
import tech.cryptonomic.conseil.common.util.CollectionOps._
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.util.MathUtil.{mean, stdev}
import tech.cryptonomic.conseil.indexer.sql.DefaultDatabaseOperations._

import scala.collection.immutable.Queue
import scala.concurrent.{ExecutionContext, Future}
import scala.math
import scala.util.{Failure, Success}
import tech.cryptonomic.conseil.indexer.tezos.TezosGovernanceOperations.GovernanceAggregate
import java.{util => ju}
import slick.dbio.DBIOAction

/**
  * Functions for writing Tezos data to a database.
  */
object TezosDatabaseOperations extends LazyLogging {
  import TezosDatabaseConversions._

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
        .sortBy(_.blockLevel)(Ordering[Long].reverse)
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
    import TezosDatabaseConversions.OperationTablesData
    import SymbolSourceLabels.Show._
    import Tables.{BalanceUpdatesRow, BlocksRow, OperationGroupsRow, OperationsRow}
    import cats.data.Kleisli
    import cats.instances.list._
    import slickeffect.implicits._
    import BlockBalances._

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
     * referring to something "happening before" or viceversa. It's some form of dependency-graph problem.
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
      accountIds: List[(TezosBlockHash, BlockLevel, Option[Instant], Option[Int], Option[Int], List[AccountId])]
  ): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${accountIds.map(_._6).map(_.length).sum} account checkpoints to DB...""")
    Tables.AccountsCheckpoint ++= accountIds.flatMap(_.convertToA[List, Tables.AccountsCheckpointRow])
  }

  /**
    * Writes association of delegate key-hashes and block data to define delegates that needs to be written
    * @param delegatesKeyHashes will have block information, paired with corresponding hashes to store
    * @return Database action possibly returning the rows written (if available form the underlying driver)
    */
  def writeBakersCheckpoint(
      delegatesKeyHashes: List[
        (TezosBlockHash, BlockLevel, Option[Instant], Option[Int], Option[Int], List[PublicKeyHash])
      ]
  ): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${delegatesKeyHashes.map(_._6).map(_.length).sum} delegate checkpoints to DB...""")
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
      applySelection = (checkpoint, keySet) => checkpoint.filter(_.accountId inSet keySet.map(_.value))
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
      hash: TezosBlockHash,
      level: BlockLevel,
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
              (hash, level, Some(timestamp), cycle, None, ids.map(makeAccountId).toList)
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
        else
          collected + (key -> BlockReference(TezosBlockHash(row.blockId), row.blockLevel, None, row.cycle, row.period))
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
      bakingRightsMap: Map[RightsFetchKey, List[BakingRights]]
  ): DBIO[Option[Int]] = {
    import CustomProfileExtension.api._
    logger.info("Writing baking rights to the DB...")
    val conversionResult = for {
      (fetchKey, bakingRightsList) <- bakingRightsMap
      bakingRights <- bakingRightsList
    } yield (fetchKey, bakingRights).convertTo[Tables.BakingRightsRow]

    Tables.BakingRights.insertOrUpdateAll(conversionResult)
  }

  /**
    * Upserts endorsing rights to the database
    * @param endorsingRightsMap mapping of hash to endorsingRights list
    */
  def upsertEndorsingRights(
      endorsingRightsMap: Map[RightsFetchKey, List[EndorsingRights]]
  ): DBIO[Option[Int]] = {
    import CustomProfileExtension.api._
    logger.info("Writing endorsing rights to the DB...")
    val transformationResult = for {
      (fetchKey, endorsingRightsList) <- endorsingRightsMap
      endorsingRights <- endorsingRightsList
    } yield (fetchKey, endorsingRights).convertToA[List, Tables.EndorsingRightsRow]

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
          .filter(er => er.delegate === upd.delegate && er.blockLevel === upd.level)
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
          .filter(er => er.delegate === upd.delegate && er.blockLevel === upd.level)
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

  /** Fetches baking rights for given block level
    *  @param blockLevel block level
    *  @return list of baking rights rows
    */
  def getBakingRightsForLevel(blockLevel: BlockLevel): DBIO[Seq[Tables.BakingRightsRow]] =
    Tables.BakingRights.filter(rights => rights.blockLevel === blockLevel && rights.invalidatedAsof.isEmpty).result

  /** Fetches endorsing rights for given block level
    *  @param blockLevel block level
    *  @return list of endorsing rights rows
    */
  def getEndorsingRightsForLevel(blockLevel: BlockLevel): DBIO[Seq[Tables.EndorsingRightsRow]] =
    Tables.EndorsingRights.filter(rights => rights.blockLevel === blockLevel && rights.invalidatedAsof.isEmpty).result

  /**
    * Fetches all governance entries for a given block level
    * @param level to identify the relevant block
    * @return the governance data
    */
  def getGovernanceForLevel(level: BlockLevel): DBIO[Seq[GovernanceRow]] =
    Tables.Governance.filter(gov => gov.level === level && gov.invalidatedAsof.isEmpty).result

  /**
    * Stores the governance statistic aggregates in the database
    * @param governance aggregates
    * @return the number of rows added, if available from the driver
    */
  def insertGovernance(governance: List[GovernanceAggregate]): DBIO[Option[Int]] = {
    logger.info("Writing {} governance rows into database...", governance.size)
    Tables.Governance ++= governance.map(_.convertTo[Tables.GovernanceRow])
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
  def writeBakers(
      bakers: List[BlockTagged[Map[PublicKeyHash, Delegate]]]
  )(implicit ec: ExecutionContext): DBIO[(Option[Int], Option[Int])] = {
    import CustomProfileExtension.api._
    import tech.cryptonomic.conseil.common.tezos.TezosTypes.Syntax._

    val keepMostRecent = (rows: List[Tables.BakersRow]) =>
      rows
        .sortBy(_.blockLevel)(Ordering[Long].reverse)
        .foldLeft((Queue.empty[Tables.BakersRow], Set.empty[String])) { (accumulator, row) =>
          val (queued, index) = accumulator
          if (index(row.pkh)) accumulator else (queued :+ row, index + row.pkh)
        }
        ._1

    logger.info("Writing bakers to DB and copying contracts to bakers table...")

    val (rows, historyRows) = bakers.flatMap {
      case BlockTagged(blockReference, bakersMap) =>
        bakersMap
          .map(_.taggedWithBlock(blockReference).convertTo[Tables.BakersRow])
          .map { row =>
            val history = (row, blockReference.timestamp).convertTo[Tables.BakersHistoryRow]
            row -> history
          }
    }.unzip

    (keepMostRecent andThen Tables.Bakers.insertOrUpdateAll)(rows).flatMap { res =>
      (Tables.BakersHistory ++= historyRows).map(res -> _)
    }
  }

  /** Gets ballot operations for given cycle
    * then sums together all distinct votes (yay, nay, pass) in
    * a single result object.
    */
  def getBallotOperationsForCycle(cycle: Int)(implicit ec: ExecutionContext): DBIO[Voting.BallotCounts] =
    Tables.Operations
      .filter(op => op.kind === "ballot" && op.cycle === cycle && op.invalidatedAsof.isEmpty)
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

  /** Gets all ballot operations for given level
    * then sums together all distinct votes (yay, nay, pass) in
    * a single result object.
    */
  def getBallotOperationsForLevel(level: BlockLevel)(implicit ec: ExecutionContext): DBIO[Voting.BallotCounts] =
    Tables.Operations
      .filter(op => op.kind === "ballot" && op.blockLevel === level && op.invalidatedAsof.isEmpty)
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

  /** Gets proposal hashes from operations table for given cycle */
  def getProposalOperationHashesByCycle(cycle: Int)(implicit ec: ExecutionContext): DBIO[Map[ProtocolId, Int]] =
    Tables.Operations
      .filter(op => op.kind === "proposals" && op.cycle === cycle && op.proposal.nonEmpty && op.invalidatedAsof.isEmpty)
      .map(_.proposal.getOrElse("[]"))
      .result
      .map {
        _.map(expandStringArray).flatten.countValues
      }

  /** Helper method for expanding array storred as a string to list */
  private def expandStringArray(arr: String): List[ProtocolId] =
    arr.trim.stripPrefix("[").stripSuffix("]").split(',').map(ProtocolId).toList

  /** Fetch the latest block level available for each delegate pkh stored */
  def getLevelsForBakers(ids: Set[PublicKeyHash]): DBIO[Seq[(String, BlockLevel)]] =
    Tables.Bakers
      .filter(_.invalidatedAsof.isEmpty)
      .map(table => (table.pkh, table.blockLevel))
      .filter(_._1 inSet ids.map(_.value))
      .result

  /** Stores updated baking information on any account, including the history bitemporal table. */
  def updateAnyBakerAccountStored(blockHashes: Set[TezosBlockHash]) =
    DBIOAction
      .sequence(
        updateAccountsWithBakers(blockHashes) ::
            updateAccountsHistoryWithBakers(blockHashes) ::
            Nil
      )
      .transactionally

  /** Updates accounts history entries as bakers where applicable */
  def updateAccountsHistoryWithBakers(blockHashes: Set[TezosBlockHash]): DBIO[Int] = {
    logger.info("Writing any baker accounts history updates to the DB...")

    val bakersIds =
      Tables.AccountsHistory
        .filter(account => account.invalidatedAsof.isEmpty && account.isBaker === false)
        .join(
          Tables.Bakers
            .filter(baker => baker.invalidatedAsof.isEmpty && (baker.blockId inSet blockHashes.map(_.value)))
        )
        .on(_.accountId === _.pkh)
        .map { case (accounts, bakers) => accounts.accountId }

    Tables.AccountsHistory
      .filter(
        account => (account.accountId in bakersIds) && (account.blockId inSet blockHashes.map(_.value))
      )
      .map(account => (account.isBaker, account.isActiveBaker))
      .update((true, Some(true)))
  }

  /** Updates accounts as bakers where applicable */
  def updateAccountsWithBakers(blockHashes: Set[TezosBlockHash]): DBIO[Int] = {
    logger.info("Writing any baker accounts updates to the DB...")

    val bakersIds =
      Tables.Accounts
        .filter(account => account.invalidatedAsof.isEmpty && account.isBaker === false)
        .join(
          Tables.Bakers
            .filter(baker => baker.invalidatedAsof.isEmpty && (baker.blockId inSet blockHashes.map(_.value)))
        )
        .on(_.accountId === _.pkh)
        .map { case (accounts, bakers) => accounts.accountId }

    Tables.Accounts.filter(_.accountId in bakersIds).map(_.isBaker).update(true)
  }

  /**
    * Gets all bakers from the DB
    * @return
    */
  def getBakers(): DBIO[Seq[Tables.BakersRow]] =
    Tables.Bakers.filter(_.invalidatedAsof.isEmpty).result

  /**
    * Updates bakers table.
    * @param bakers list of the baker rows to be updated
    * @return
    */
  def writeBakers(bakers: List[Tables.BakersRow]): DBIO[Option[Int]] = {
    import CustomProfileExtension.api._
    logger.info(s"Updating ${bakers.size} Baker rows")
    Tables.Bakers.insertOrUpdateAll(bakers)
  }

  /** Given the operation kind, return range of fees and timestamp for that operation.
    *
    * @param kind     operation kind
    * @param daysPast how many values to use for statistics computations, as a time-window
    * @param asOf     when the computation is to be considered, by default uses the time of invocation
    * @return         the average fees for a given operation kind, if it exists
    */
  def calculateAverageFees(
      kind: String,
      daysPast: Int,
      asOf: Instant = Instant.now()
  )(
      implicit ec: ExecutionContext
  ): DBIO[Option[AverageFees]] = {
    /* We need to limit the past timestamps for this computation to a reasonable value.
     * Otherwise the query optimizer won't be able to efficiently use the indexing and
     * will do a full table scan.
     */
    val secsPerDay = 60L * 60L * 24L //secs * mins * hours
    val secsToPastHorizon = daysPast * secsPerDay

    logger.info(
      s"Computing fees starting from $daysPast days before $asOf, averaging over all values in the range"
    )

    type Cycle = Int
    type Fee = BigDecimal
    type FeeDetails = (Option[Fee], Timestamp, Option[Cycle], BlockLevel)

    def computeAverage(
        ts: Timestamp,
        cycle: Option[Cycle],
        level: BlockLevel,
        fees: Seq[FeeDetails]
    ): AverageFees = {
      val values = fees.map {
        case (fee, _, _, _) => fee.map(_.toDouble).getOrElse(0.0)
      }
      val m: Int = math.ceil(mean(values)).toInt
      val s: Int = math.ceil(stdev(values)).toInt

      AverageFees(math.max(m - s, 0), m, m + s, ts, kind, cycle, level)
    }

    val timestampLowerBound =
      Timestamp.from(asOf.atOffset(ZoneOffset.UTC).minusSeconds(secsToPastHorizon).toInstant())

    val opQuery =
      Tables.Operations
        .filter(op => op.kind === kind && op.invalidatedAsof.isEmpty)
        .filter(_.timestamp >= timestampLowerBound)
        .map(o => (o.fee, o.timestamp, o.cycle, o.blockLevel))
        .sortBy { case (_, ts, _, _) => ts.desc }

    opQuery.result.statements.foreach(stmt => println(s"raising fees as $stmt"))

    opQuery.result.map { timestampedFees =>
      timestampedFees.headOption.map {
        case (_, latest, cycle, level) =>
          computeAverage(latest, cycle, level, timestampedFees)
      }
    }
  }

  object FeesStatistics {
    import CustomProfileExtension.api._
    import CustomProfileExtension.generalAggregations.{avg, max}
    import CustomProfileExtension.statisticsAggregations.{stdDevPop}
    private val zeroBD = BigDecimal.exact(0)

    def stats(kind: Rep[String], lowBound: Rep[Timestamp]) = {
      val baseQuery = Tables.Operations
        .filter(op => op.kind === kind && op.invalidatedAsof.isEmpty && op.timestamp >= lowBound)

      baseQuery
        .map(
          it =>
            (
              avg(it.fee.getOrElse(zeroBD).?),
              stdDevPop(it.fee.getOrElse(zeroBD).?),
              max(it.timestamp.?),
              max(it.cycle).?,
              max(it.blockLevel.?)
            )
        )

    }
    val feesStatsQuery = Compiled(stats _)

    def calculateAverage(kind: String, daysPast: Int, asOf: Instant = Instant.now())(
        implicit ec: ExecutionContext
    ): DBIO[Option[AverageFees]] = {

      /* We need to limit the past timestamps for this computation to a reasonable value.
       * Otherwise the query optimizer won't be able to efficiently use the indexing and
       * will do a full table scan.
       */

      logger.info(
        s"Computing fees starting from $daysPast days before $asOf, averaging over all values in the range"
      )

      val timestampLowerBound =
        Timestamp.from(
          asOf
            .atOffset(ZoneOffset.UTC)
            .minusDays(daysPast)
            .toInstant()
        )

      stats(kind, timestampLowerBound).result.headOption.map { rows =>
        rows.map {
          case (mean, stddev, ts, cycle, level) =>
            val mu = math.ceil(mean.toDouble).toInt
            val sigma = math.ceil(stddev.toDouble).toInt
            AverageFees(
              low = math.max(mu - sigma, 0),
              medium = mu,
              high = mu + sigma,
              timestamp = ts,
              kind = kind,
              cycle = cycle,
              level = level
            )
        }
      }

    }

  }

  /** Returns all levels that have seen a custom event processing, e.g.
    * - auto-refresh of all accounts after the babylon protocol amendment
    *
    * @param eventType the type of event levels to fetch
    * @return a list of values marking specific levels that needs not be processed anymore
    */
  def fetchProcessedEventsLevels(eventType: String): DBIO[Seq[BlockLevel]] =
    Tables.ProcessedChainEvents.filter(_.eventType === eventType).map(_.eventLevel).result

  /** Adds any new level for which a custom event processing has been executed
    *
    * @param eventType the type of event to record
    * @param levels the levels to write to db, currently there must be no collision with existing entries
    * @return the number of entries saved to the checkpoint
    */
  def writeProcessedEventsLevels(eventType: String, levels: List[BlockLevel]): DBIO[Option[Int]] =
    Tables.ProcessedChainEvents ++= levels.map(Tables.ProcessedChainEventsRow(_, eventType))

  import kantan.csv._
  import shapeless._
  import shapeless.ops.hlist._

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

  /** Write an audit log entry of a detected fork and some
    * reference data useful to analyse the event.
    *
    * @param forkLevel the point where data was actually different, the fork point
    * @param forkBlockHash the first block hash differing from that on the current chain
    * @param indexedHeadLevel how far has the indexer gone ahead in the fork
    * @param detectionTime when the indexer identified the fork having happened
    * @return a unique identifier for the newly recorded fork, used as `forkId` in the system
    */
  def writeForkEntry(
      forkLevel: BlockLevel,
      forkBlockHash: TezosBlockHash,
      indexedHeadLevel: BlockLevel,
      detectionTime: Instant
  ): DBIO[String] = {
    val forkId = ju.UUID.randomUUID().toString
    val ts = new Timestamp(detectionTime.getEpochSecond())
    Tables.Forks.returning(
      Tables.Forks.map(_.forkId)
    ) += Tables.ForksRow(forkId, forkLevel, forkBlockHash.value, indexedHeadLevel, ts)
  }

  /** Temporarily lift statement constraints on foreign keys
    * We need to defer any such constraints until the transaction commits
    * when we want to update fork references in blocks and all related
    * db entities, lifting the constraint checks until everything is updated
    * and consistent.
    */
  def deferConstraints(): DBIO[Int] =
    sqlu"SET CONSTRAINTS ALL DEFERRED;"

  /** Operations related to data invalidation due to forks on the chain */
  object ForkInvalidation {
    import Tables._

    /** Collects custom data to identify how to invalidate data
      * on a specific table when a fork is detected
      *
      * @param query a query that reads the entity rows
      * @param levelColumn reads the column where the referencing block level is stored
      * @param invalidationTimeColumn reads the column that tracks invalidation time
      * @param forkIdColumn reads the column that references the current fork
      * @tparam E the slick specific table type
      */
    case class EntityTableInvalidator[E <: AbstractTable[_]](query: TableQuery[E])(
        levelColumn: E => Rep[BlockLevel],
        invalidationTimeColumn: E => Rep[Option[Timestamp]],
        forkIdColumn: E => Rep[String]
    ) {

      /** Marks all relevant entities as invalidated (i.e. by a forking event), by
        * specifying the block level at which the chain showed divergence from the local data.
        *
        * Such invalidated entries should not appear anymore as results from queries against the
        * main fork of the chain. They will thereafter need to be specifically requested.
        *
        * @param fromLevel the lower level, included, for which data doesn't match the node
        * @param asOf a time-stamp of the invalidation operation
        * @param forkId a unique identifier of the fork on which the entities were found
        * @return the number of impacted rows
        */
      def invalidate(fromLevel: BlockLevel, asOf: Instant, forkId: String): DBIO[Int] = {
        assert(fromLevel > 0, message = "Invalidation due to fork can be performed from positive block level only")
        val asOfTimestamp = Timestamp.from(asOf)
        query
          .filter(levelColumn(_) >= fromLevel)
          .map(e => (invalidationTimeColumn(e), forkIdColumn(e)))
          .update(asOfTimestamp.some, forkId)
      }

    }

    lazy val blocks = EntityTableInvalidator(Blocks)(_.level, _.invalidatedAsof, _.forkId)
    lazy val operationGroups = EntityTableInvalidator(OperationGroups)(_.blockLevel, _.invalidatedAsof, _.forkId)
    lazy val operations = EntityTableInvalidator(Operations)(_.blockLevel, _.invalidatedAsof, _.forkId)
    lazy val accounts = EntityTableInvalidator(Accounts)(_.blockLevel, _.invalidatedAsof, _.forkId)
    lazy val accountsHistory = EntityTableInvalidator(AccountsHistory)(_.blockLevel, _.invalidatedAsof, _.forkId)
    lazy val bakers = EntityTableInvalidator(Bakers)(_.blockLevel, _.invalidatedAsof, _.forkId)
    lazy val bakersHistory = EntityTableInvalidator(BakersHistory)(_.blockLevel, _.invalidatedAsof, _.forkId)
    lazy val bakingRights = EntityTableInvalidator(BakingRights)(_.blockLevel, _.invalidatedAsof, _.forkId)
    lazy val endorsingRights = EntityTableInvalidator(EndorsingRights)(_.blockLevel, _.invalidatedAsof, _.forkId)
    lazy val tokenBalances = EntityTableInvalidator(TokenBalances)(_.blockLevel, _.invalidatedAsof, _.forkId)
    lazy val governance = EntityTableInvalidator(Governance)(_.level.ifNull(-1L), _.invalidatedAsof, _.forkId)
    lazy val fees = EntityTableInvalidator(Fees)(_.level.ifNull(-1L), _.invalidatedAsof, _.forkId)

    /** Deletes entries for the registry of processed chain events.
      * Due to a fork, those events will need be processed again over the new fork
      * data.
      * Notice that we have to be careful here and make sure that events' processing is
      * an operation that can be executed mutliple times with no downsides
      * (a.k.a. idempotent)
      *
      * @param fromLevel the lower level, included, for which data doesn't match the node
      * @return the number of impacted rows (deleted)
      */
    def deleteProcessedEvents(fromLevel: BlockLevel): DBIO[Int] = {
      assert(fromLevel > 0, message = "Invalidation due to fork can be performed from positive block level only")
      Tables.ProcessedChainEvents.filter(_.eventLevel >= fromLevel).delete
    }

  }

}
