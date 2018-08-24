package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.ApiOperations.{awaitTimeInSeconds, dbHandle}
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountsWithBlockHashAndLevel, Block}
import tech.cryptonomic.conseil.util.MathUtil.{mean, stdev}

import scala.concurrent.duration.{Duration, SECONDS}
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.math.{ceil, max}
import scala.util.Try

/**
  * Functions for writing Tezos data to a database.
  */
object TezosDatabaseOperations {

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
    dbHandle.run(
      DBIO.seq(
        Tables.Blocks                 ++= blocks.map(blockToDatabaseRow),
        Tables.OperationGroups        ++= blocks.flatMap(operationGroupToDatabaseRow),
        Tables.Operations             ++= blocks.flatMap(operationsToDatabaseRow)
      )
    )

  /**
    * Writes accounts from a specific blocks to a database.
    * @param accountsInfo Accounts with their corresponding block hash.
    * @param dbHandle     Handle to a database.
    * @return             Future on database inserts.
    */
  def writeAccountsToDatabase(accountsInfo: AccountsWithBlockHashAndLevel, dbHandle: Database): Future[Unit] =
    dbHandle.run(
      DBIO.seq(
        Tables.Accounts               ++= accountsToDatabaseRows(accountsInfo)
      )
    )

  /**
    *
    * @param fees      List of possible average fees for different operation kinds.
    * @param dbHandle  Handle to a database
    * @return          Future on database inserts.
    */
  def writeFeesToDatabase(fees: List[Option[AverageFees]], dbHandle: Database): Future[Unit] =
    dbHandle.run(
      DBIO.seq(
        Tables.Fees                   ++= feesToDatabaseRows(fees)
      )
    )

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
    * Generates database rows from a list of averageFees calculated for some operation kinds.
    * @param maybeFees  List of possible new fees that may have been calculated for different
    *                   operation kinds.
    * @return           Database rows
    */
  def feesToDatabaseRows(maybeFees: List[Option[AverageFees]]): List[Tables.FeesRow] = {
    maybeFees
      .filter(_.isDefined)
      .map{ someFee =>
        val fee = someFee.get
        Tables.FeesRow(
          low = fee.low,
          medium = fee.medium,
          high = fee.high,
          timestamp = fee.timestamp,
          kind = fee.kind
        )
      }
  }

  /**
    * Given the operation kind, return range of fees and timestamp for that operation.
    * @param kind  Operation kind
    * @return      The average fees for a given operation kind, if it exists
    */
  def calculateAverageFees(kind: String): Option[AverageFees] = {
    val operationKinds = Set[String]{kind}
    val action = for {
      o <- Tables.Operations
      if o.kind.inSet(operationKinds)
    } yield (o.fee, o.timestamp)
    val op = dbHandle.run(action.distinct.sortBy(_._2.desc).take(numberOfFeesAveraged).result)
    val results = Await.result(op, Duration.apply(awaitTimeInSeconds, SECONDS))
    val resultNumbers = results.map(x => x._1.getOrElse("0").toDouble)
    val m: Int = ceil(mean(resultNumbers)).toInt
    val s: Int = ceil(stdev(resultNumbers)).toInt
    results.length match {
      case 0 => None
      case _ =>
        val timestamp = results.head._2
        Some(AverageFees(max(m - s, 0), m, m + s, timestamp, kind))
    }
  }


  /**
    * Delete all accounts in database not associated with block at maxLevel.
    * @return No value, database update
    */
  def purgeOldAccounts(): Try[Int] = {
    ApiOperations.fetchMaxBlockLevelForAccounts().flatMap{ blockLevel =>
      Try {
        val query = Tables.Accounts.filter(row => !(row.blockLevel === blockLevel))
        val action = query.delete
        val op = dbHandle.run(action)
        Await.result(op, Duration.apply(awaitTimeInSeconds, SECONDS))
      }
    }
  }

  /**
    * Checks if a block for this hash and related operations are stored on db
    * @param hash Identifies the block
    * @param ec   Needed to compose the operations
    * @return     true if block and operations exists
    */
  def blockExists(hash: String)(implicit ec: ExecutionContext): Future[Boolean] =
    dbHandle.run(for {
      blockThere <- Tables.Blocks.findBy(_.hash).applied(hash).exists.result
      opsThere <- Tables.OperationGroups.filter(_.blockId === hash).exists.result
    } yield blockThere && opsThere)



}
