package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import slick.jdbc.{GetResult, PositionedParameters, SQLActionBuilder}
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.QueryProtocolTypes.Predicates
import tech.cryptonomic.conseil.tezos.Tables.{OperationGroupsRow, OperationsRow}
import tech.cryptonomic.conseil.tezos.TezosTypes.{Account, AccountsWithBlockHashAndLevel, Block, BlockHash}
import tech.cryptonomic.conseil.util.CollectionOps._
import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext
import scala.math.{ceil, max}

/**
  * Functions for writing Tezos data to a database.
  */
object TezosDatabaseOperations extends LazyLogging {

  private val conf = ConfigFactory.load
  private val numberOfFeesAveraged = conf.getInt("lorre.numberOfFeesAveraged")

  /**
    * Writes computed fees averages to a database.
    *
    * @param fees List of average fees for different operation kinds.
    * @return     Database action possibly containing the number of rows written (if available from the underlying driver)
    */
  def writeFees(fees: List[AverageFees]): DBIO[Option[Int]] =
    Tables.Fees ++= fees.map(RowConversion.convertAverageFees)

  /**
    * Writes accounts from a specific block to a database.
    *
    * @param accountsInfo Accounts with their corresponding block hash.
    * @return          Database action possibly containing the number of rows written (if available from the underlying driver)
    */
  def writeAccounts(accountsInfo: AccountsWithBlockHashAndLevel): DBIO[Option[Int]] =
    Tables.Accounts ++= RowConversion.convertAccounts(accountsInfo)

  /**
    * Writes blocks and related operations to a database.
    * @param blocks   Block with operations.
    * @return         Future on database inserts.
    */
  def writeBlocks(blocks: List[Block]): DBIO[Unit] =
      DBIO.seq(
        Tables.Blocks          ++= blocks.map(RowConversion.convertBlock),
        Tables.OperationGroups ++= blocks.flatMap(RowConversion.convertBlocksOperationGroups),
        Tables.Operations      ++= blocks.flatMap(RowConversion.convertBlockOperations)
      )

  /**
    * Given the operation kind, return range of fees and timestamp for that operation.
    * @param kind  Operation kind
    * @return      The average fees for a given operation kind, if it exists
    */
  def calculateAverageFees(kind: String)(implicit ec: ExecutionContext): DBIO[Option[AverageFees]] = {
    def computeAverage(ts: java.sql.Timestamp, fees: Seq[(Option[String], java.sql.Timestamp)]): AverageFees = {
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
    * Delete all accounts in database not associated with block at maxLevel.
    * @return the number of rows removed
    */
  def purgeOldAccounts()(implicit ex: ExecutionContext): DBIO[Int] =
    fetchAccountsMaxBlockLevel.flatMap( maxLevel =>
      Tables.Accounts.filter(_.blockLevel =!= maxLevel).delete
    ).transactionally

  /**
    * Reads in all operations referring to the group
    * @param groupHash is the group identifier
    * @param ec the [[ExecutionContext]] needed to compose db operations
    * @return the operations and the collecting group, if there's one for the given hash, else [[None]]
    */
  def operationsForGroup(groupHash: String)(implicit ec: ExecutionContext): DBIO[Option[(OperationGroupsRow, Seq[OperationsRow])]] =
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

  /** conversions from domain objects to database row format */
  private object RowConversion {

    private[TezosDatabaseOperations] def convertAverageFees(in: AverageFees) =
      Tables.FeesRow(
        low = in.low,
        medium = in.medium,
        high = in.high,
        timestamp = in.timestamp,
        kind = in.kind
    )

    private[TezosDatabaseOperations] def convertAccounts(blockAccounts: AccountsWithBlockHashAndLevel) = {
      val AccountsWithBlockHashAndLevel(hash, level, accounts) = blockAccounts
      accounts.map {
        case (id, Account(manager, balance, spendable, delegate, script, counter)) =>
          Tables.AccountsRow(
            accountId = id.id,
            blockId = hash.value,
            manager = manager,
            spendable = spendable,
            delegateSetable = delegate.setable,
            delegateValue = delegate.value,
            counter = counter,
            script = script.map(_.toString),
            balance = balance,
            blockLevel = level
          )
      }.toList
    }

    private[TezosDatabaseOperations] def convertBlock(block: Block) = {
      val header = block.metadata.header
      Tables.BlocksRow(
        level = header.level,
        proto = header.proto,
        predecessor = header.predecessor.value,
        timestamp = header.timestamp,
        validationPass = header.validationPass,
        fitness = header.fitness.mkString(","),
        context = Some(header.context), //put in later
        signature = header.signature,
        protocol = block.metadata.protocol,
        chainId = block.metadata.chain_id,
        hash = block.metadata.hash.value,
        operationsHash = header.operations_hash
      )
    }

    private[TezosDatabaseOperations] def convertBlocksOperationGroups(block: Block): List[Tables.OperationGroupsRow] =
      block.operationGroups.map{ og =>
        Tables.OperationGroupsRow(
          protocol = og.protocol,
          chainId = og.chain_id,
          hash = og.hash.value,
          branch = og.branch,
          signature = og.signature,
          blockId = block.metadata.hash.value
        )
      }

    private[TezosDatabaseOperations] def convertBlockOperations(block: Block): List[Tables.OperationsRow] =
      block.operationGroups.flatMap{ og =>
        og.contents.fold(List.empty[Tables.OperationsRow]){
          operations =>
            operations.map { operation =>
              Tables.OperationsRow(
                kind = operation.kind,
                source = operation.source,
                fee = operation.fee,
                gasLimit = operation.gasLimit,
                storageLimit = operation.storageLimit,
                amount = operation.amount,
                destination = operation.destination,
                operationGroupHash = og.hash.value,
                operationId = 0,
                balance = operation.balance,
                delegate = operation.delegate,
                blockHash = block.metadata.hash.value,
                blockLevel = block.metadata.header.level,
                timestamp = block.metadata.header.timestamp,
                pkh = operation.pkh
              )
            }
        }
      }

  }

  /* use as max block level when none exists */
  private[tezos] val defaultBlockLevel: BigDecimal = -1

  /** Precompiled fetch for Operations by Group */
  val operationsByGroupHash =
    Tables.Operations.findBy(_.operationGroupHash)

  /** Precompiled fetch for groups of operations */
  val operationGroupsByHash =
    Tables.OperationGroups.findBy(_.hash).map(_.andThen(_.take(1)))

  /**
    * Computes the level of the most recent block in the accounts table or [[defaultBlockLevel]] if none is found.
    */
  private[tezos] def fetchAccountsMaxBlockLevel: DBIO[BigDecimal] =
    Tables.Accounts
      .map(_.blockLevel)
      .max
      .getOrElse(defaultBlockLevel)
      .result

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


  private implicit val getMap: GetResult[Map[String, Any]] = GetResult[Map[String, Any]](positionedResult => {
    val metadata = positionedResult.rs.getMetaData
    (1 to positionedResult.numColumns).flatMap(i => {
      val columnName = metadata.getColumnName(i).toLowerCase
      val columnValue = positionedResult.nextObjectOption
      columnValue.map(columnName -> _)
    }).toMap
  })

  /**
    * Selects elements filtered by the predicates
    * THIS METHOD IS VULNERABLE TO SQL INJECTION
    * @param table          name of the table
    * @param columns        list of column names
    * @param predicates     list of predicates for query to be filtered with
    * @return               list of map of [string, any], which represents list of rows as a map of column name to value
    */
  def selectWithPredicates(table: String, columns: List[String], predicates: List[Predicates])(implicit ec: ExecutionContext):
  DBIO[List[Map[String, Any]]] = {
    val pred = predicates.map { p =>
      concat(
        sql""" AND #${p.field} #${QueryProtocolTypes.mapOperationToSQL(p.operation, p.inverse)} """,
        List(values(p.set.map(_.toString))))
    }
    val query = sql"""SELECT #${columns.mkString(",")} FROM #$table WHERE true """

    concat(query, pred).as[Map[String, Any]].map(_.toList)
  }

  //some adjusted hacks from https://github.com/slick/slick/issues/1161 as Slick does not simple concatenation of actions
  /** concatenates SQLActionsBuilders */
  @tailrec
  private def concat(acc: SQLActionBuilder, actions: List[SQLActionBuilder]): SQLActionBuilder = {
    actions match {
      case Nil => acc
      case x :: xs =>
        concat(SQLActionBuilder(acc.queryParts ++ x.queryParts, (p: Unit, pp: PositionedParameters) => {
          acc.unitPConv.apply(p, pp)
          x.unitPConv.apply(p, pp)
        }), xs)
    }
  }

  /** inserts values into query */
  private def values[T](xs: TraversableOnce[T]): SQLActionBuilder = {
    var b = sql"("
    var first = true
    xs.foreach { x =>
      if(first) first = false
      else b = concat(b, List(sql","))
      b = concat(b, List(sql"'#$x'"))
    }
    concat(b, List(sql")"))
  }
}

