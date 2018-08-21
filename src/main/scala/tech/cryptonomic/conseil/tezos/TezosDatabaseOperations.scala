package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.ApiOperations.dbHandle
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountsWithBlockHashAndLevel, Block}
import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

import scala.concurrent.{ExecutionContext, Future}
import scala.math.{ceil, max}
import scala.util.{Failure, Success}

/**
  * Functions for writing Tezos data to a database.
  */
object TezosDatabaseOperations extends LazyLogging {

  private val conf = ConfigFactory.load
  private val awaitTimeInSeconds = conf.getInt("dbAwaitTimeInSeconds")
  private val numberOfFeesAveraged = conf.getInt("lorre.numberOfFeesAveraged")

  /**
    * Writes blocks and operations to a database.
    * @param blocks   Block with operations.
    * @param dbHandle Handle to database.
    * @return         Future on database inserts.
    */
  def writeBlocksToDatabase(blocks: List[Block], dbHandle: Database): Future[Unit] =
    dbHandle.run(DBIO.seq(
      Tables.Blocks           ++= blocks.map(blockToDatabaseRow),
      Tables.OperationGroups  ++= blocks.flatMap(operationGroupToDatabaseRow),
      Tables.Operations       ++= blocks.flatMap(operationsToDatabaseRow)
    ))

  /**
    * Writes accounts from a specific blocks to a database.
    * @param accountsInfo Accounts with their corresponding block hash.
    * @param dbHandle     Handle to a database.
    * @return             Future on database inserts.
    */
  def writeAccountsToDatabase(accountsInfo: AccountsWithBlockHashAndLevel, dbHandle: Database): Future[Option[Int]] =
    dbHandle.run(Tables.Accounts ++= accountsToDatabaseRows(accountsInfo))


  /**
    * Generates database rows for accounts.
    * @param accountsInfo Accounts
    * @return             Database rows
    */
  def accountsToDatabaseRows(accountsInfo: AccountsWithBlockHashAndLevel): List[Tables.AccountsRow] =
    accountsInfo.accounts.map { account =>
      Tables.AccountsRow(
        accountId = account._1,
        blockId = accountsInfo.blockHash,
        manager = account._2.manager,
        spendable = account._2.spendable,
        delegateSetable = account._2.delegate.setable,
        delegateValue = account._2.delegate.value,
        counter = account._2.counter,
        script = account._2.script.flatMap(x => Some(x.toString)),
        balance = account._2.balance,
        blockLevel = accountsInfo.blockLevel
      )
    }.toList

  /**
    * Generates database rows for blocks.
    * @param block  Block
    * @return       Database rows
    */
  def blockToDatabaseRow(block: Block): Tables.BlocksRow = {
    val header = block.metadata.header
    Tables.BlocksRow(
      level = header.level,
      proto = header.proto,
      predecessor = header.predecessor,
      timestamp = header.timestamp,
      validationPass = header.validationPass,
      fitness = header.fitness.mkString(","),
      context = Some(header.context), //put in later
      signature = header.signature,
      protocol = block.metadata.protocol,
      chainId = block.metadata.chain_id,
      hash = block.metadata.hash,
      operationsHash = header.operations_hash
    )
  }

  /**
    * Generates database rows for a block's operation groups.
    * @param block  Block
    * @return       Database rows
    */
  def operationGroupToDatabaseRow(block: Block): List[Tables.OperationGroupsRow] =
    block.operationGroups.map{ og =>
      Tables.OperationGroupsRow(
        protocol = og.protocol,
        chainId = og.chain_id,
        hash = og.hash,
        branch = og.branch,
        signature = og.signature,
        blockId = block.metadata.hash
      )
    }

  /**
    * Generates database rows for a block's operations.
    * @param block  Block
    * @return       Database rows
    */
  def operationsToDatabaseRow(block: Block): List[Tables.OperationsRow] =
    block.operationGroups.flatMap{ og =>
      og.contents match {
        case None =>  List[Tables.OperationsRow]()
        case Some(operations) =>
          operations.map { operation =>
            Tables.OperationsRow(
              kind = operation.kind,
              source = operation.source,
              fee = operation.fee,
              gasLimit = operation.gasLimit,
              storageLimit = operation.storageLimit,
              amount = operation.amount,
              destination = operation.destination,
              operationGroupHash = og.hash,
              operationId = 0,
              balance = operation.balance,
              delegate = operation.delegate,
              blockHash = block.metadata.hash,
              blockLevel = block.metadata.header.level,
              timestamp = block.metadata.header.timestamp,
              pkh = operation.pkh
            )
          }
      }
    }

  /**
    *
    * @param fees      List of average fees for different operation kinds.
    * @return          Database action possibly containing the number of rows written (if available from the underlying driver)
    */
  def writeFeesIO(fees: List[AverageFees]): DBIO[Option[Int]] =
    Tables.Fees ++= fees.map(RowConversion.ofAverageFees)

  /**
    * Given the operation kind, return range of fees and timestamp for that operation.
    * @param kind  Operation kind
    * @return      The average fees for a given operation kind, if it exists
    */
  def calculateAverageFeesIO(kind: String)(implicit ec: ExecutionContext): DBIO[Option[AverageFees]] = {
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
  def purgeOldAccounts()(implicit ex: ExecutionContext): Future[Int] = {
    val purged = dbHandle.run {
      accountsMaxBlockLevel.flatMap( maxLevel =>
        Tables.Accounts.filter(_.blockLevel =!= maxLevel).delete
      ).transactionally
    }
    purged.andThen {
      case Success(howMany) => logger.info("{} accounts where purged from old block levels.", howMany)
      case Failure(e) => logger.error("Could not purge old block-levels accounts", e)
    }
  }

  /** conversions from domain objects to database row format */
  object RowConversion {

    private[TezosDatabaseOperations] def ofAverageFees(in: AverageFees) =
      Tables.FeesRow(
        low = in.low,
        medium = in.medium,
        high = in.high,
        timestamp = in.timestamp,
        kind = in.kind
      )

  }

  /* use as max block level when none exists */
  private[tezos] val defaultBlockLevel: BigDecimal = -1

  /**
    * Computes the level of the most recent block in the accounts table or [[defaultBlockLevel]] if none is found.
    */
  private[tezos] def accountsMaxBlockLevel: DBIO[BigDecimal] =
    Tables.Accounts
      .map(_.blockLevel)
      .max
      .getOrElse(defaultBlockLevel)
      .result

}
