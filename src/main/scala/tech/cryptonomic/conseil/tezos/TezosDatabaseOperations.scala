package tech.cryptonomic.conseil.tezos

import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountsWithBlockHash, Block}

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
      context = None, //put in later
      signature = header.signature,
      protocol = block.metadata.protocol,
      chainId = block.metadata.chainId,
      hash = block.metadata.hash,
      operationsHash = header.operationsHash
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
        chainId = og.chainId,
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
              block = operation.block,
              level = operation.level,
              slots = fixSlots(operation.slots),
              nonce = operation.nonce,
              pkh = operation.pkh,
              secret = operation.secret,
              source = operation.source,
              fee = operation.fee,
              counter = operation.counter,
              gasLimit = operation.gasLimit,
              storageLimit = operation.storageLimit,
              publicKey = operation.publicKey,
              amount = operation.amount,
              destination = operation.destination,
              managerPubKey = operation.managerPubKey,
              balance = operation.balance,
              spendable = operation.spendable,
              delegatable = operation.delegatable,
              delegate = operation.delegate,
              operationGroupHash = og.hash,
              operationId = 0
            )
          }
      }
    }


  private def fixSlots(slots: Option[List[Int]]): Option[String] =
    slots.flatMap{s: Seq[Int] => Some(s.mkString(","))}

  private def fixProposals(slots: Option[List[String]]): Option[String] =
    slots.flatMap{s: Seq[String] => Some(s.mkString(","))}
}
