package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.ApiOperations.dbHandle
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.Tables.{OperationGroupsRow, OperationsRow}
import tech.cryptonomic.conseil.tezos.TezosTypes.{Account, AccountsWithBlockHashAndLevel, Block}
import tech.cryptonomic.conseil.util.CollectionOps._
import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

import scala.concurrent.{ExecutionContext, Future}
import scala.math.{ceil, max}
import scala.util.{Failure, Success}

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
  def writeFeesIO(fees: List[AverageFees]): DBIO[Option[Int]] =
    Tables.Fees ++= fees.map(RowConversion.ofAverageFees)

  /**
    * Writes accounts from a specific block to a database.
    *
    * @param accountsInfo Accounts with their corresponding block hash.
    * @return          Database action possibly containing the number of rows written (if available from the underlying driver)
    */
  def writeAccountsIO(accountsInfo: AccountsWithBlockHashAndLevel): DBIO[Option[Int]] =
    Tables.Accounts ++= RowConversion.ofAccounts(accountsInfo)

  /**
    * Writes blocks and related operations to a database.
    * @param blocks   Block with operations.
    * @return         Future on database inserts.
    */
  def writeBlocksIO(blocks: List[Block]): DBIO[Unit] =
      DBIO.seq(
        Tables.Blocks          ++= blocks.map(RowConversion.ofBlock),
        Tables.OperationGroups ++= blocks.flatMap(RowConversion.ofBlocksOperationGroups),
        Tables.Operations      ++= blocks.flatMap(RowConversion.ofBlockOperations)
      )

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

  def operationsForGroupIO(groupHash: String)(implicit ec: ExecutionContext): DBIO[Option[(OperationGroupsRow, Seq[OperationsRow])]] =
    (for {
      operation <- Tables.operationsByGroupHash(groupHash).extract
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

  /** Computes the max level of blocks or [[defaultBlockLevel]] if no block exists */
  private[tezos] def maxBlockLevel: DBIO[Int] =
  Tables.Blocks
      .map(_.level)
      .max
      .getOrElse(defaultBlockLevel.toInt)
      .result

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

    private[TezosDatabaseOperations] def ofAccounts(blockAccounts: AccountsWithBlockHashAndLevel) = {
      val AccountsWithBlockHashAndLevel(hash, level, accounts) = blockAccounts
      accounts.map {
        case (id, Account(manager, balance, spendable, delegate, script, counter)) =>
          Tables.AccountsRow(
            accountId = id,
            blockId = hash,
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

    private[TezosDatabaseOperations] def ofBlock(block: Block) = {
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

    private[TezosDatabaseOperations] def ofBlocksOperationGroups(block: Block): List[Tables.OperationGroupsRow] =
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

    private[TezosDatabaseOperations] def ofBlockOperations(block: Block): List[Tables.OperationsRow] =
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


  }

}
