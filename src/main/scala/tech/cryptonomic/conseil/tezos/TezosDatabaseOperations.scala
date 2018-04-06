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
        Tables.OperationGroups        ++= blocks.flatMap(operationGroupToDatabaseRow)
        //REMOVED FOR ZERONET COMPATIBILTY
        /*Tables.Transactions           ++= blocks.flatMap(transactionsToDatabaseRows),
        Tables.Endorsements           ++= blocks.flatMap(endorsementsToDatabaseRows),
        Tables.Originations           ++= blocks.flatMap(originationsToDatabaseRows),
        Tables.Delegations            ++= blocks.flatMap(delegationsToDatabaseRows),
        Tables.Proposals              ++= blocks.flatMap(proposalsToDatabaseRows),
        Tables.Ballots                ++= blocks.flatMap(ballotsToDatabaseRows),
        Tables.SeedNonceRevealations  ++= blocks.flatMap(seedNonceRevelationsToDatabaseRows),
        Tables.FaucetTransactions     ++= blocks.flatMap(faucetTransactionsToDatabaseRows)*/
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
        account._1, accountsInfo.block_hash, account._2.manager, account._2.spendable,
        account._2.delegate.setable, account._2.delegate.value, account._2.balance,
        account._2.counter
      )
    }
    //WON'T RECOGNIZE FIELD NAMES IN INSTANTIATION
    /*accountsInfo.accounts.map { account =>
      Tables.AccountsRow(
        accountId = account._1,
        blockId = accountsInfo.block_hash,
        manager = account._2.manager,
        spendable = account._2.spendable,
        delegateSetable = account._2.delegate.setable,
        delegateValue = account._2.delegate.value,
        balance = account._2.balance,
        counter = account._2.counter
      )
    }.toList*/

  /**
    * Generates database rows for blocks.
    * @param block  Block
    * @return       Database rows
    */
  def blockToDatabaseRow(block: Block): Tables.BlocksRow =
    Tables.BlocksRow(
      netId = block.metadata.chain_id,
      protocol = block.metadata.protocol,
      level = block.metadata.level,
      proto = block.metadata.proto,
      predecessor = block.metadata.predecessor,
      timestamp = block.metadata.timestamp,
      validationPass = block.metadata.validation_pass,
      operationsHash = block.metadata.operations_hash,
      data = block.metadata.protocol_data,
      hash = block.metadata.hash,
      fitness = block.metadata.fitness.mkString(",")
    )

  /**
    * Generates database rows for a block's operation groups.
    * @param block  Block
    * @return       Database rows
    */
  def operationGroupToDatabaseRow(_block: Block): List[Tables.OperationGroupsRow] =
    _block.operationGroups.map{ og =>
      Tables.OperationGroupsRow(
        hash = og.hash,
        block = og.block,
        branch = og.branch,
        signature = og.signature,
        slots = og.slots.mkString,
        level = og.level,
        kind = og.kind
      )
      Tables.OperationGroupsRow(hash = og.hash, block = og.block, br)
    }
  /* REMOVED FOR ZERONET COMPATIBILITY
  /**
    * Generates database rows for a block's transactions.
    * @param block  Block
    * @return       Database row
    */
  def transactionsToDatabaseRows(block: Block): List[Tables.TransactionsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="transaction").map{operation =>
        Tables.TransactionsRow(
          transactionId = 0,
          operationGroupHash = og.hash,
          amount = operation.amount.get,
          destination = operation.destination,
          parameters = None
        )
      }
    }

  /**
    * Generates database rows for a block's endorsements.
    * @param block  Block
    * @return       Database rows
    */
  def endorsementsToDatabaseRows(block: Block): List[Tables.EndorsementsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="endorsement").map{operation =>
        Tables.EndorsementsRow(
          endorsementId = 0,
          operationGroupHash = og.hash,
          blockId = operation.block.get,
          slot = operation.slot.get
        )
      }
    }

  /**
    * Generates database rows for a block's originations.
    * @param block  Block
    * @return       Database rows
    */
  def originationsToDatabaseRows(block: Block): List[Tables.OriginationsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="origination").map{operation =>
        Tables.OriginationsRow(
          originationId = 0,
          operationGroupHash = og.hash,
          managerpubkey = operation.managerPubKey,
          balance = operation.balance,
          spendable = operation.spendable,
          delegatable = operation.delegatable,
          delegate = operation.delegate,
          script = None
        )
      }
    }

  /**
    * Generates database rows for a block's delegations.
    * @param block  Block
    * @return       Database rows
    */
  def delegationsToDatabaseRows(block: Block): List[Tables.DelegationsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="delegation").map{operation =>
        Tables.DelegationsRow(
          delegationId = 0,
          operationGroupHash = og.hash,
          delegate = operation.delegate.get
        )
      }
    }

  /**
    * Generates database rows for a block's proposals.
    * @param block  Block
    * @return       Database rows
    */
  def proposalsToDatabaseRows(block: Block): List[Tables.ProposalsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="proposal").map{operation =>
        Tables.ProposalsRow(
          proposalId = 0,
          operationGroupHash = og.hash,
          period = operation.period.get,
          proposal = operation.proposal.get
        )
      }
    }

  /**
    * Generates database rows for a block's ballots.
    * @param block  Block
    * @return       Database rows
    */
  def ballotsToDatabaseRows(block: Block): List[Tables.BallotsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="ballot").map{operation =>
        Tables.BallotsRow(
          ballotId = 0,
          operationGroupHash = og.hash,
          period = operation.period.get,
          proposal = operation.proposal.get,
          ballot = operation.ballot.get
        )
      }
    }

  /**
    * Generates database rows for a block's seed nonce revelations.
    * @param block  Block
    * @return       Database rows
    */
  def seedNonceRevelationsToDatabaseRows(block: Block): List[Tables.SeedNonceRevealationsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="seed_nonce_revelation").map{operation =>
        Tables.SeedNonceRevealationsRow(
          seedNonnceRevealationId = 0,
          operationGroupHash = og.hash,
          level = operation.level.get,
          nonce = operation.nonce.get
        )
      }
    }

  /**
    * Generates database rows for a block's faucet transactions.
    * @param block  Block
    * @return       Database rows
    */
  def faucetTransactionsToDatabaseRows(block: Block): List[Tables.FaucetTransactionsRow] =
    block.operationGroups.flatMap{ og =>
      og.operations.filter(_.kind.get=="faucet").map{operation =>
        Tables.FaucetTransactionsRow(
          faucetTransactionId = 0,
          operationGroupHash = og.hash,
          id = operation.id.get,
          nonce = operation.nonce.get
        )
      }
    }
    */
}
