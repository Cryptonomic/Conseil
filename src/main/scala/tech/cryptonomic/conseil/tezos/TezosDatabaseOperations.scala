package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.SQLActionBuilder
import tech.cryptonomic.conseil.generic.chain.DataTypes.{Query => _, _}
import tech.cryptonomic.conseil.generic.chain.DataTypes.OutputType.OutputType
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.util.CollectionOps._
import tech.cryptonomic.conseil.util.Conversion.Syntax._
import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder._
import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

import scala.concurrent.ExecutionContext
import scala.math.{ceil, max}
import cats._
import cats.implicits._
import cats.data.Kleisli
import cats.effect.Async
import slickeffect.implicits._
import mouse.any._
import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder
import ch.qos.logback.classic.db.names.TableName

/**
  * Functions for writing Tezos data to a database.
  */
object TezosDatabaseOperations extends LazyLogging {

  import DatabaseConversions._

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
  )(implicit ec: ExecutionContext): DBIO[Int] = {
    logger.info(s"""Writing ${accountsInfo.length} accounts to DB...""")
    DBIO
      .sequence(accountsInfo.flatMap { info =>
        info.convertToA[List, Tables.AccountsRow].map(Tables.Accounts.insertOrUpdate)
      })
      .map(_.sum)
  }

  /**
    * Writes blocks and related operations to a database.
    * @param blocks   Block with operations.
    * @return         Database action to execute.
    */
  def writeBlocks(blocks: List[Block])(implicit ec: ExecutionContext): DBIO[Unit] = {
    // Kleisli is a Function with effects, Kleisli[F, A, B] ~= A => F[B]
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
    val saveOperationGetNewId = Kleisli[DBIO, OperationsRow, Int] {
      Tables.Operations returning Tables.Operations.map(_.operationId) += _
    }
    //a function that takes rows to save with an operation id, and creates an action to do that
    val saveBalanceUpdatesForOperationId = Kleisli[DBIO, (Int, List[BalanceUpdatesRow]), Option[Int]] {
      case (operationRowId, balanceRows) =>
        Tables.BalanceUpdates ++= balanceRows.map(_.copy(sourceId = Some(operationRowId)))
    }

    /* Compose the kleisli functions to get a single "action function"
     * Calling `.first` will make the kleisli take a tuple and only apply the function to the first element
     * leaving the second untouched.
     * We do this to align the output with the input of the second step
     */
    val saveOperationsAndBalances: Kleisli[DBIO, (OperationsRow, List[BalanceUpdatesRow]), Option[Int]] =
      saveOperationGetNewId.first andThen saveBalanceUpdatesForOperationId

    //Sequence the save actions, the third being applied to a whole collection of operations and balances
    DBIO.seq(
      saveBlocksAction,
      saveBlocksBalanceUpdatesAction,
      saveGroupsAction,
      saveOperationsAndBalances.traverse(blocks.flatMap(_.convertToA[List, OperationTablesData]))
    )

  }

  /**
    * Writes blocks to a database and adds the corrisponding invalidated blocks into the appropriate invalidated table.
    *
    * @param blocks Blocks which are being invalidated
    * @return       Database action that will execute the writes as a side effect
    */
  def writeAndValidateBlocks(blocks: List[Block])(implicit ec: ExecutionContext): DBIO[Unit] =
    DBIO
      .seq(
        writeBlocks(blocks),
        invalidateBlocks(blocks),
        revalidateBlocks(blocks)
      )
      .transactionally

  /* find blocks at the same level as those passed-in and invalidates them */
  private def invalidateBlocks(validBlocks: List[Block])(implicit ec: ExecutionContext) =
    DBIO.sequence(validBlocks.map(valid => markInvalids(valid.data.header.level, valid.data.hash)))

  /* find corresponding blocks that needs to be invalidated and adds them to the table */
  private def markInvalids(level: Int, validHash: BlockHash)(implicit ec: ExecutionContext): DBIO[Int] =
    Tables.Blocks
      .filter(block => block.level === level && block.hash =!= validHash.value)
      .result
      .map(
        rows => rows.map(_.convertTo[Tables.InvalidatedBlocksRow])
      )
      .flatMap(
        invalid => DBIO.sequence(invalid.map(Tables.InvalidatedBlocks.insertOrUpdate)).map(_.sum)
      )

  /**
    * Update invalidated blocks in the database table so that current block is revalidated, and all other blocks
    * at same level are invalidated.
    *
    * @param block Block to be revalidated
    * @return      A database action that will give back a tuple with the number of rows updated in the form (revalidated, invalidated)
    */
  def revalidateBlock(block: Block): DBIO[(Int, Int)] = {
    val hash = block.data.hash
    val level = block.data.header.level
    val invalidatedAction = Tables.InvalidatedBlocks
      .filter(block => block.level === level && block.hash =!= hash.value)
      .map(block => block.isInvalidated)
      .update(true)
    val revalidatedAction =
      Tables.InvalidatedBlocks.filter(_.hash === hash.value).map(block => block.isInvalidated).update(false)
    invalidatedAction zip revalidatedAction
  }.transactionally

  /**
    * Updates blocks in the database, revalidating those in the passed-in list and
    * invaidating all the other stored in the database at the same chain level
    *
    * @param blocks the blocks to set as valid
    * @return       A database action that will return two numbers, the total revalidated rows, and the corresponding invalidated total
    */
  def revalidateBlocks(blocks: List[Block])(implicit ex: ExecutionContext): DBIO[(Int, Int)] =
    DBIO.sequence(blocks.map(revalidateBlock)).map { tuples =>
      val (revalidated, invalidated) = tuples.unzip
      (revalidated.sum, invalidated.sum)
    }

  /**
    * Writes association of account ids and block data to define accounts that needs update
    * @param accountIds will have block information, paired with corresponding account ids to store
    * @return Database action possibly returning the rows written (if available form the underlying driver)
    */
  def writeAccountsCheckpoint(accountIds: List[(BlockHash, Int, List[AccountId])]): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${accountIds.map(_._3).map(_.length).sum} account checkpoints to DB...""")
    Tables.AccountsCheckpoint ++= accountIds.flatMap(_.convertToA[List, Tables.AccountsCheckpointRow])
  }

  /**
    * Writes association of delegate key-hashes and block data to define delegates that needs to be written
    * @param delegatesKeyHashes will have block information, paired with corresponding hashes to store
    * @return Database action possibly returning the rows written (if available form the underlying driver)
    */
  def writeDelegatesCheckpoint(delegatesKeyHashes: List[(BlockHash, Int, List[PublicKeyHash])]): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${delegatesKeyHashes.map(_._3).map(_.length).sum} delegate checkpoints to DB...""")
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
    *                  We strictly assume those keys were previously loaded from the checkpoint table itself
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
        if (collected.contains(key)) collected else collected + (key -> (BlockHash(row.blockId), row.blockLevel))
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
        if (collected.contains(key)) collected else collected + (key -> (BlockHash(row.blockId), row.blockLevel))
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

  /**
    * Writes accounts to the database and record the keys (hashes) to later save complete delegates information relative to each block
    * @param accounts the full accounts' data
    * @param delegatesKeyHashes for each block reference a list of pkh of delegates that were involved with the block
    * @return a database action that stores both arguments and return a tuple of the row counts inserted
    */
  def writeAccountsAndCheckpointDelegates(
      accounts: List[BlockTagged[Map[AccountId, Account]]],
      delegatesKeyHashes: List[BlockTagged[List[PublicKeyHash]]]
  )(implicit ec: ExecutionContext): DBIO[(Int, Option[Int])] = {
    logger.info("Writing accounts and delegate checkpoints to the DB...")

    //we tuple because we want transactionality guarantees and we need both insert-counts to get returned
    Async[DBIO]
      .tuple2(writeAccounts(accounts), writeDelegatesCheckpoint(delegatesKeyHashes.map(_.asTuple)))
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
  )(implicit ec: ExecutionContext): DBIO[Int] = {
    logger.info("Writing delegates to DB and copying contracts to delegates contracts table...")
    val delegatesUpdateAction = DBIO.sequence(
      delegates.flatMap {
        case BlockTagged(blockHash, blockLevel, delegateMap) =>
          delegateMap.map {
            case (pkh, delegate) =>
              Tables.Delegates insertOrUpdate (blockHash, blockLevel, pkh, delegate).convertTo[Tables.DelegatesRow]
          }
      }
    )
    val contractsUpdateAction =
      copyAccountsToDelegateContracts(
        delegates.flatMap(_.content.values.flatMap(_.delegated_contracts)).toSet
      )

    (for {
      updated <- delegatesUpdateAction.map(_.sum)
      _ <- contractsUpdateAction
    } yield updated).transactionally

  }

  /* Selects accounts corresponding to the given ids and copy the rows
   * into the delegated contracts tables, whose schema should match exactly
   */
  private def copyAccountsToDelegateContracts(
      contractIds: Set[ContractId]
  )(implicit ec: ExecutionContext): DBIO[Option[Int]] = {
    logger.info("Copying select accounts to delegates contracts table in DB...")
    val ids = contractIds.map(_.id)
    val inputAccounts = Tables.Accounts
      .filter(_.accountId inSet ids)
      .result
      .map(_.map(_.convertTo[Tables.DelegatedContractsRow]))

    //we read the accounts data, then remove matching ids from contracts and re-insert the updated rows
    (for {
      accounts <- inputAccounts
      _ <- Tables.DelegatedContracts.filter(_.accountId inSet ids).delete
      updated <- Tables.DelegatedContracts.forceInsertAll(accounts)
    } yield updated).transactionally
  }

  /** Writes proposals to the database */
  def writeVotingProposals(proposals: List[Voting.Proposal]): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${proposals.length} voting proposals to the DB...""")
    Tables.Proposals ++= proposals.flatMap(_.convertToA[List, Tables.ProposalsRow])
  }

  /** Writes bakers to the database */
  def writeVotingRolls(bakers: List[Voting.BakerRolls], block: Block): DBIO[Option[Int]] = {
    logger.info(s"""Writing ${bakers.length} bakers to the DB...""")
    Tables.Rolls ++= (block, bakers).convertToA[List, Tables.RollsRow]
  }

  /** Writes ballots to the database */
  def writeVotingBallots(ballots: List[Voting.Ballot], block: Block): DBIO[Option[Int]] = {
    logger.info(
      s"""Writing ${ballots.length} ballots for block ${block.data.hash.value} at level ${block.data.header.level} to the DB..."""
    )
    Tables.Ballots ++= (block, ballots).convertToA[List, Tables.BallotsRow]
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

    def computeAverage(ts: java.sql.Timestamp, fees: Seq[(Option[BigDecimal], java.sql.Timestamp)]): AverageFees = {
      val values = fees.map {
        case (fee, _) => fee.map(_.toDouble).getOrElse(0.0)
      }
      val m: Int = ceil(mean(values)).toInt
      val s: Int = ceil(stdev(values)).toInt
      AverageFees(max(m - s, 0), m, m + s, ts, kind)
    }

    /* Joins operations with groups to get the block reference and
     * then with invalidated blocks to check if the block reference is valid.
     * It only uses the fees and timestamps if selected rows to compute the
     * stats, sorting by timestamp and limiting the results
     */
    Tables.Operations
      .map(op => (op.operationGroupHash, op.kind, op.fee, op.timestamp))
      .filter(_._2 === kind)
      .join(Tables.OperationGroups.map(gr => (gr.hash, gr.blockId)))
      .on(_._1 === _._1)
      .joinLeft(Tables.InvalidatedBlocks)
      .on {
        case ((_, group), invalid) => group._2 === invalid.hash
      }
      .filter {
        case ((_, _), invalid) => invalid.fold(true.bind)(entry => !entry.isInvalidated)
      }
      .map {
        case (((opGroup, opKind, opFee, opTime), _), _) => (opFee, opTime)
      }
      .distinct
      .sortBy {
        case (_, ts) => ts.desc
      }
      .take(numberOfFeesAveraged)
      .result
      .map { timestampedFees =>
        timestampedFees.headOption.map {
          case (_, latest) =>
            computeAverage(latest, timestampedFees)
        }
      }
  }

  /**
    * Reads in all operations referring to the group
    * @param groupHash is the group identifier
    * @param ec the `ExecutionContext` needed to compose db operations
    * @return the operations and the collecting group, if there's one for the given hash, else `None`
    */
  def operationsForGroup(
      groupHash: String
  )(implicit ec: ExecutionContext): DBIO[Option[(Tables.OperationGroupsRow, Seq[Tables.OperationsRow])]] = {
    import slickeffect.implicits._

    val query = for {
      operation <- operationsByGroupHash(groupHash).extract
      group <- operation.operationGroupsFk
    } yield (group, operation)

    val queryResult = query.result.map { pairs =>
      /*
       * we first collect all de-normalized pairs under the common group and then extract the
       * only key-value from the resulting map
       */
      val keyed = pairs.byKey()
      keyed.keys.headOption
        .map(k => (k, keyed(k)))
    }

    //now we add a check to see if the reference block for the group is yet valid
    val invalidationCheck: DBIO[Boolean] = queryResult.flatMap {
      case Some((group, ops)) => blockIsInInvalidatedState(BlockHash(group.blockId)).map(!_)
      case _ => false.pure[DBIO]
    }

    invalidationCheck.ifA(
      ifTrue = queryResult,
      ifFalse = Option.empty.pure[DBIO]
    )

  }

  /**
    * Checks if a block for this hash and related operations are stored on db
    * @param hash Identifies the block
    * @param ec   Neede to compose operatoins
    * @return     true if block and operations exist
    */
  def blockAndOpsExists(hash: BlockHash)(implicit ec: ExecutionContext): DBIO[Boolean] =
    Applicative[DBIO]
      .map2(blockExists(hash), Tables.OperationGroups.filter(_.blockId === hash.value).exists.result)(_ && _)

  /**
    * Checks if a block for this hash is stored on db
    * @param hash Identifies the block
    * @return     true if block exists
    */
  def blockExists(hash: BlockHash): DBIO[Boolean] =
    Tables.Blocks.findBy(_.hash).applied(hash.value).exists.result

  /**
    * Checks if a block for this hash has ever been invalidated
    * @param hash Identifies the block
    * @return     true if the block is in the invalidated table, regardless of its invalidation state
    */
  def blockExistsInInvalidatedBlocks(hash: BlockHash): DBIO[Boolean] =
    Tables.InvalidatedBlocks.findBy(_.hash).applied(hash.value).exists.result

  /**
    * Checks if a block for this hash has ever been invalidated
    * @param hash Identifies the block
    * @return     true if the block is in the invalidated table with and invalidated state
    */
  def blockIsInInvalidatedState(hash: BlockHash): DBIO[Boolean] =
    Tables.InvalidatedBlocks
      .filter(invalidated => invalidated.hash === hash.value && invalidated.isInvalidated === true)
      .exists
      .result

  /* use as max block level when none exists */
  private[tezos] val defaultBlockLevel: BigDecimal = -1

  /** Precompiled fetch for Operations by Group */
  val operationsByGroupHash =
    Tables.Operations.findBy(_.operationGroupHash)

  /** Precompiled fetch for invalidated Blocks */
  val invalidatedBlockByHash =
    Tables.InvalidatedBlocks.findBy(_.hash)

  /** Computes the max level of blocks or [[defaultBlockLevel]] if no block exists */
  private[tezos] def fetchMaxBlockLevel: DBIO[Int] =
    Tables.Blocks
      .map(_.level)
      .max
      .getOrElse(defaultBlockLevel.toInt)
      .result

  /** Extracts all blocks for a given level, as long as they're not currently invalidated.
    * On a consistency note, there should be at most one result available from the db
    */
  def validBlockForLevel(level: Int): DBIO[Seq[Tables.BlocksRow]] =
    (for {
      (block, invalidEntry) <- Tables.Blocks.filter(_.level === level) joinLeft Tables.InvalidatedBlocks on (_.hash === _.hash)
      if invalidEntry.fold(true.bind)(entry => !entry.isInvalidated)
    } yield block).result

  /** is there any block stored? */
  def doBlocksExist(): DBIO[Boolean] =
    Tables.Blocks.exists.result

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
    sql"""SELECT COUNT(*) FROM (SELECT DISTINCT #$column FROM #$table) AS temp""".as[Int].map(_.head)

  /**
    * Selects distinct elements by given table and column
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table  name of the table
    * @param column name of the column
    * @return       distinct elements in given column as a list
    */
  def selectDistinct(table: String, column: String)(implicit ec: ExecutionContext): DBIO[List[String]] =
    sql"""SELECT DISTINCT #$column::VARCHAR FROM #$table WHERE #$column IS NOT NULL""".as[String].map(_.toList)

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
    sql"""SELECT DISTINCT #$column::VARCHAR FROM #$table WHERE #$column LIKE '%#$matchingString%' AND #$column IS NOT NULL"""
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
      columns: List[String],
      predicates: List[Predicate],
      ordering: List[QueryOrdering],
      aggregation: List[Aggregation],
      outputType: OutputType,
      limit: Int
  )(implicit ec: ExecutionContext): DBIO[List[QueryResponse]] = {
    /* this will get into scope the conversion needed from TableRef to a full TableQuery
     * so that we can ask for it using only the table name string
     */
    import tech.cryptonomic.conseil.tezos.DatabaseQueries._

    val allPredicates = aggregation.flatMap(_.getPredicate) ::: predicates
    val aggregationFields = aggregation.flatMap(_.getPredicate.map(_.field))
    val toSqlString = (builder: SQLActionBuilder) => {
      import builder._
      if (queryParts.length == 1 && queryParts(0).isInstanceOf[String]) queryParts(0).asInstanceOf[String]
      else queryParts.iterator.map(String.valueOf).mkString
    }
    val logGeneratedQuery = (builder: SQLActionBuilder) =>
      logger.debug("Query with predicates generated for {} is:\n {}", table, toSqlString(builder))

    val q = makeQuery(table, columns, aggregation)(TableRef(table))
        .addPredicates(table, allPredicates)
        .addGroupBy(table, aggregation, columns)
        .addOrdering(table, ordering, aggregationFields.toSet)
        .addLimit(limit) <| logGeneratedQuery

    if (outputType == OutputType.sql) {
      DBIO.successful(List(Map("sql" -> Some(toSqlString(q)))))
    } else {
      q.as[QueryResponse].map(_.toList)
    }
  }

}
