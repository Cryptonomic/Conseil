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
       // Tables.Blocks                 ++= blocks.map(blockToDatabaseRow) //,
        //Tables.OperationGroups        ++= blocks.flatMap(operationGroupToDatabaseRow),
        //Tables.Operations             ++= blocks.flatMap(operationsToDatabaseRow)
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
    /*
  /**
    * Generates database rows for blocks.
    * @param block  Block
    * @return       Database rows
    */
  def blockToDatabaseRow(block: Block): Tables.BlocksRow =
    Tables.BlocksRow(
      chainId = block.metadata.chainId,
      protocol = block.metadata.protocol,
      level = block.metadata.header.level,
      proto = block.metadata.header.proto,
      predecessor = block.metadata.header.predecessor,
      validationPass = block.metadata.header.validationPass,
      operationsHash = block.metadata.header.operationsHash,
      protocolData = "", //block.metadata.header.protocol_data,
      hash = block.metadata.hash,
      timestamp = block.metadata.header.timestamp,
      fitness = block.metadata.header.fitness.mkString(",")
    )

  /**
    * Generates database rows for a block's operation groups.
    * @param block  Block
    * @return       Database rows
    */
  def operationGroupToDatabaseRow(block: Block): List[Tables.OperationGroupsRow] =
    block.operationGroups.map{ og =>
      Tables.OperationGroupsRow(
        hash = og.hash,
        branch = og.branch,
        kind = og.kind,
        block = og.block,
        level = og.level,
        slots = fixSlots(og.slots),
        signature = og.signature,
        proposals = og.proposals,
        period = og.period,
        source = og.source,
        proposal = og.proposal,
        ballot = og.ballot,
        chain = og.chain,
        counter = og.counter,
        fee = og.fee,
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
      og.operations match {
        case None =>  List[Tables.OperationsRow]()
        case Some(operations) =>
          operations.map { operation =>
            Tables.OperationsRow(
              operationId = 0, // what's the difference between operationId and Id fields?
              operationGroupHash = og.hash,
              opKind = operation.kind,
              level = operation.level,
              nonce = operation.nonce,
              id = operation.id,
              publicKey = operation.public_key,
              amount = operation.amount,
              destination = operation.destination,
              parameters = operation.parameters.flatMap(x => Some(x.toString)),
              managerpubkey = operation.managerPubKey, //change to camel case?
              balance = operation.balance,
              spendable = operation.spendable,
              delegatable = operation.delegatable,
              delegate = operation.delegate,
              script = operation.script.flatMap(x => Some(x.toString))
            )
          }
      }
    }

  */
  private def fixSlots(slots: Option[List[Int]]): Option[String] =
    slots.flatMap{s: Seq[Int] => Some(s.mkString(","))}
}
