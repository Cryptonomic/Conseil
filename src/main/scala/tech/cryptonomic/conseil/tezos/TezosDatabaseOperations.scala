package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.GetResult
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.Tables.{OperationGroupsRow, OperationsRow}
import tech.cryptonomic.conseil.tezos.TezosTypes.{Account, AccountsWithBlockHashAndLevel, Block, BlockHash}
import tech.cryptonomic.conseil.util.CollectionOps._
import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

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

  /** count number of the elements in the table */
  def countRows(table: TableQuery[_]): DBIO[Int] =
    table.length.result

  // Slick did not let me to this any other way
  /** count number of the distinct elements in the table
    * WARNING!
    * THIS METHOD IS VULNERABLE TO SQL INJECTION.
    * USE IT WISELY.
    */
  def countDistinct(table: String, column: String)(implicit ec: ExecutionContext): DBIO[Int] =
    sql"""SELECT COUNT(DISTINCT #$column) FROM #$table""".as[Int].map(_.head)

  def selectDistinct(table: String, column: String)(implicit ec: ExecutionContext): DBIO[List[String]] = {
    sql"""SELECT DISTINCT #$column FROM #$table""".as[String].map(_.toList)
  }

  def selectDistinctLike(table: String, column: String, matchingString: String)(implicit ec: ExecutionContext): DBIO[List[String]] = {
    sql"""SELECT DISTINCT #$column FROM #$table WHERE #$column LIKE '%#$matchingString%'""".as[String].map(_.toList)
  }
}
