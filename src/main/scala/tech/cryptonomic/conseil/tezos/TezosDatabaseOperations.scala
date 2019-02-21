package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.generic.chain.DataTypes.{Predicate, QueryOrdering, QueryResponse}
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.util.CollectionOps._
import tech.cryptonomic.conseil.util.Conversion.Syntax._
import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder._
import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

import scala.concurrent.ExecutionContext
import scala.math.{ceil, max}

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
  def writeFees(fees: List[AverageFees]): DBIO[Option[Int]] =
    Tables.Fees ++= fees.map(_.convertTo[Tables.FeesRow])

  /**
    * Writes accounts with block data to a database.
    *
    * @param accountsInfo List data on the accounts and the corresponding blocks that operated on those
    * @return     Database action possibly containing the number of rows written (if available from the underlying driver)
    */
  def writeAccounts(accountsInfo: List[BlockAccounts])(implicit ec: ExecutionContext): DBIO[Int] =
    DBIO.sequence(accountsInfo.flatMap {
      info =>
        info.convertToA[List, Tables.AccountsRow].map(Tables.Accounts.insertOrUpdate)
    }).map(_.sum)
      .transactionally

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
    import BlockBalances._
    import SymbolSourceDescriptor.Show._
    import DatabaseConversions.OperationTablesData
    import Tables.{BalanceUpdatesRow, BlocksRow, OperationGroupsRow, OperationsRow}

    //straightforward Database IO Actions waiting to be just run
    val saveBlocksAction = Tables.Blocks ++= blocks.map(_.convertTo[BlocksRow])
    val saveBlocksBalanceUpdatesAction = Tables.BalanceUpdates ++= blocks.flatMap(_.data.convertToA[List, BalanceUpdatesRow])
    val saveGroupsAction = Tables.OperationGroups ++= blocks.flatMap(_.convertToA[List, OperationGroupsRow])

    //a function that takes a row to save and creates an action to do that, returning the new id
    val saveOperationGetNewId = Kleisli[DBIO, OperationsRow, Int] {
      Tables.Operations returning Tables.Operations.map(_.operationId) += _
    }
    //a function that takes rows to save with an operation id, and creates an action to do that
    val saveBalanceUpdatesForOperationId = Kleisli[DBIO, (Int, List[BalanceUpdatesRow]), Option[Int]]{
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
    * Writes association of account ids and block data to define accounts that needs update
    * @param accountIds will have blocks, paired with corresponding account ids to store
    * @return Database action possibly returning the rows written (if available form the underlying driver)
    */
  def writeAccountsCheckpoint(accountIds: List[(BlockHash, Int, List[AccountId])]): DBIO[Option[Int]] =
    Tables.AccountsCheckpoint ++= accountIds.flatMap(_.convertToA[List, Tables.AccountsCheckpointRow])

  /**
    * Removes  data on the accounts checkpoint table
    * @param selection limits the removed rows to those
    *                  concerning the selected elements, by default no selection is made.
    *                  We strictly assume those Ids were previously loaded from the checkpoint table itself
    * @return the database action to run
    */
  def cleanAccountsCheckpoint(selection: Option[Set[AccountId]] = None)(implicit ec: ExecutionContext): DBIO[Int] =
    selection match {
      case Some(ids) =>
        for {
          total <- getAccountsCheckpointSize()
          marked =
            if (total > ids.size) Tables.AccountsCheckpoint.filter(_.accountId inSet ids.map(_.id))
            else Tables.AccountsCheckpoint
          deleted <- marked.delete
        } yield deleted
      case None =>
        Tables.AccountsCheckpoint.delete
    }

  /**
    * @return the number of distinct accounts present in the checkpoint table
    */
  def getAccountsCheckpointSize(): DBIO[Int] =
    Tables.AccountsCheckpoint.distinctOn(_.accountId).length.result

  /**
    * Reads the account ids in the checkpoint table, considering
    * only those that at the latest block level (highest value)
    * @return a database action that loads the list of relevant rows
    */
  def getLatestAccountsFromCheckpoint(implicit ec: ExecutionContext): DBIO[Map[AccountId, BlockReference]] =
    for {
      ids <- Tables.AccountsCheckpoint.map(_.accountId).distinct.result
      rows <- DBIO.sequence(ids.map {
                id => Tables.AccountsCheckpoint.filter(_.accountId === id).sortBy(_.blockLevel.desc).take(1).result.head
              })
    } yield rows.map {
      case Tables.AccountsCheckpointRow(id, blockId, level) => AccountId(id) -> (BlockHash(blockId), level)
    }.toMap

/*     Tables.AccountsCheckpoint.result.map(
      _.groupBy(_.accountId) //rows by accounts
        .values //only use the collection of values, ignoring the group key
        .map {
          idRows =>
            //keep only the latest and group by block reference, and rewrap it as map entries
            val Tables.AccountsCheckpointRow(id, latestBlockId, latestLevel) = idRows.maxBy(_.blockLevel)
            AccountId(id) -> (BlockHash(latestBlockId), latestLevel)
        }.toMap
    )
 */
  /**
    * Writes the blocks data to the database
    * at the same time saving enough information about updated accounts to later fetch those accounts
    * @param blocksWithAccounts a map with new blocks as keys, and updated account ids as the values
    */
  def writeBlocksAndCheckpointAccounts(blocksWithAccounts: Map[Block, List[AccountId]])(implicit ec: ExecutionContext): DBIO[Option[Int]] = {
    //ignore the account ids for storage, and prepare the checkpoint account data
    //we do this on a single sweep over the list, pairing the results and then unzipping the outcome
    val (blocks, accountUpdates) =
      blocksWithAccounts.map {
        case (block, accountIds) =>
          block -> (block.data.hash, block.data.header.level, accountIds)
      }.toList.unzip

    //sequence both operations in a single transaction
    (writeBlocks(blocks) andThen writeAccountsCheckpoint(accountUpdates)).transactionally
  }

  /**
    * Given the operation kind, return range of fees and timestamp for that operation.
    * @param kind                 Operation kind
    * @param numberOfFeesAveraged How many values to use for statistics computations
    * @return                     The average fees for a given operation kind, if it exists
    */
  def calculateAverageFees(kind: String, numberOfFeesAveraged: Int)(implicit ec: ExecutionContext): DBIO[Option[AverageFees]] = {
    def computeAverage(ts: java.sql.Timestamp, fees: Seq[(Option[BigDecimal], java.sql.Timestamp)]): AverageFees = {
      val values = fees.map {
        case (fee, _) => fee.map(_.toDouble).getOrElse(0.0)
      }
      val m: Int = ceil(mean(values)).toInt
      val s: Int = ceil(stdev(values)).toInt
      AverageFees(max(m - s, 0), m, m + s, ts, kind)
    }

    val opQuery =
      Tables.Operations
        .filter(_.kind === kind)
        .map(o => (o.fee, o.timestamp))
        .distinct
        .sortBy { case (_, ts) => ts.desc }
        .take(numberOfFeesAveraged)
        .result

    opQuery.map {
      timestampedFees =>
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
  def operationsForGroup(groupHash: String)(implicit ec: ExecutionContext): DBIO[Option[(Tables.OperationGroupsRow, Seq[Tables.OperationsRow])]] =
    (for {
      operation <- operationsByGroupHash(groupHash).extract
      group <- operation.operationGroupsFk
    } yield (group, operation)
    ).result
    .map {
      pairs =>
        /*
         * we first collect all de-normalized pairs under the common group and then extract the
         * only key-value from the resulting map
         */
        val keyed = pairs.byKey()
        keyed.keys
          .headOption
          .map( k => (k, keyed(k)))
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

  /** Precompiled fetch for groups of operations */
  val operationGroupsByHash =
    Tables.OperationGroups.findBy(_.hash).map(_.andThen(_.take(1)))

  /** Computes the max level of blocks or [[defaultBlockLevel]] if no block exists */
  private[tezos] def fetchMaxBlockLevel: DBIO[Int] =
    Tables.Blocks
      .map(_.level)
      .max
      .getOrElse(defaultBlockLevel.toInt)
      .result

  /** is there any block stored? */
  def doBlocksExist(): DBIO[Boolean] =
    Tables.Blocks.exists.result

  /**
    * Counts number of rows in the given table
    * @param table  slick table
    * @return       amount of rows in the table
    */
  def countRows(table: TableQuery[_]): DBIO[Int] =
    table.length.result

  // Slick does not allow count operations on arbitrary column names
  /**
    * Counts number of distinct elements by given table and column
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table  name of the table
    * @param column name of the column
    * @return       amount of distinct elements in given column
    */
  def countDistinct(table: String, column: String)(implicit ec: ExecutionContext): DBIO[Int] =
    sql"""SELECT COUNT(DISTINCT #$column) FROM #$table""".as[Int].map(_.head)

  /**
    * Selects distinct elements by given table and column
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table  name of the table
    * @param column name of the column
    * @return       distinct elements in given column as a list
    */
  def selectDistinct(table: String, column: String)(implicit ec: ExecutionContext): DBIO[List[String]] = {
    sql"""SELECT DISTINCT #$column FROM #$table""".as[String].map(_.toList)
  }

  /**
    * Selects distinct elements by given table and column with filter
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table          name of the table
    * @param column         name of the column
    * @param matchingString string which is being matched
    * @return               distinct elements in given column as a list
    */
  def selectDistinctLike(table: String, column: String, matchingString: String)(implicit ec: ExecutionContext): DBIO[List[String]] = {
    sql"""SELECT DISTINCT #$column FROM #$table WHERE #$column LIKE '%#$matchingString%'""".as[String].map(_.toList)
  }

  /**
    * Selects elements filtered by the predicates
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table          name of the table
    * @param columns        list of column names
    * @param predicates     list of predicates for query to be filtered with
    * @param ordering       list of ordering conditions for the query
    * @param limit          max number of rows fetched
    * @return               list of map of [string, any], which represents list of rows as a map of column name to value
    */
  def selectWithPredicates(
    table: String,
    columns: List[String],
    predicates: List[Predicate],
    ordering: List[QueryOrdering],
    limit: Int)
    (implicit ec: ExecutionContext): DBIO[List[QueryResponse]] = {
     makeQuery(table, columns)
       .addPredicates(predicates)
       .addOrdering(ordering)
       .addLimit(limit)
       .as[QueryResponse]
       .map(_.toList)
  }

}

