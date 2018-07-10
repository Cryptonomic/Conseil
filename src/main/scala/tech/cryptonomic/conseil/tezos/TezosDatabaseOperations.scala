package tech.cryptonomic.conseil.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountsWithBlockHash, Block, Fees}

import scala.concurrent.Future

/**
  * Functions for writing Tezos data to a database.
  */
object TezosDatabaseOperations {

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
  def writeAccountsToDatabase(accountsInfo: AccountsWithBlockHash, dbHandle: Database): Future[Unit] =
    dbHandle.run(
      DBIO.seq(
        Tables.Accounts               ++= accountsToDatabaseRows(accountsInfo)
      )
    )


  def writeFeesToDatabase(fee: Fees, dbHandle: Database): Future[Unit] =
    dbHandle.run(
      DBIO.seq(
        Tables.Fees                   ++= feesToDatabaseRows(fee)
      )
    )
  /**
    * Generates database rows for accounts.
    * @param accountsInfo Accounts
    * @return             Database rows
    */
  def accountsToDatabaseRows(accountsInfo: AccountsWithBlockHash): List[Tables.AccountsRow] =
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
        balance = account._2.balance
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
              timestamp = block.metadata.header.timestamp
            )
          }
      }
    }


  def feesToDatabaseRows(fees: Fees): Tables.FeesRow =
    Tables.FeesRow(
      low = fees.low,
      medium = fees.medium,
      high = fees.high,
      timestamp = fees.timestamp,
      kind = fees.kind
    )
}
