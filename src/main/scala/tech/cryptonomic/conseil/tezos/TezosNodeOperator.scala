package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountsWithBlockHash, Block, OperationGroup}
import tech.cryptonomic.conseil.util.JsonUtil.fromJson

import scala.util.{Failure, Success, Try}


/**
  * Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database.
  */
class TezosNodeOperator(node: TezosRPCInterface) extends LazyLogging {

  /**
    * Fetches a specific account for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @param accountID  Account ID
    * @return           The account
    */
  def getAccountForBlock(network: String, blockHash: String, accountID: String): Try[TezosTypes.Account] =
    node.runQuery(network, s"blocks/$blockHash/proto/context/contracts/$accountID").flatMap { jsonEncodedAccount =>
      Try(fromJson[TezosTypes.AccountContainer](jsonEncodedAccount)).flatMap(acctContainer => Try(acctContainer.ok))
    }

  /**
    * Fetches all accounts for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block.
    * @return           Accounts
    */
  def getAllAccountsForBlock(network: String, blockHash: String): Try[Map[String, TezosTypes.Account]] = Try {
    node.runQuery(network, s"blocks/$blockHash/proto/context/contracts") match {
      case Success(jsonEncodedAccounts) =>
        val accountIDs = fromJson[TezosTypes.AccountsContainer](jsonEncodedAccounts)
        val listedAccounts: List[String] = accountIDs.ok
        val accounts = listedAccounts.map(acctID => getAccountForBlock(network, blockHash, acctID))
        accounts.count(_.isFailure) match {
          case 0 =>
            val justTheAccounts = accounts.map(_.get)
            (listedAccounts zip justTheAccounts).toMap
          case _ => throw new Exception(s"Could not decode one of the accounts for block $blockHash")
        }
      case Failure(e) =>
        logger.error(s"Could not get a list of accounts for block $blockHash")
        throw e
    }
  }

  /**
    * Returns all operation groups for a given block.
    * The list of lists return type is intentional as it corresponds to the return value of the Tezos client.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of the given block
    * @return           Operation groups
    */
  def getAllOperationsForBlock(network: String, blockHash: String): Try[List[List[OperationGroup]]] =
    node.runQuery(network, s"blocks/$blockHash/proto/operations").flatMap { jsonEncodedOperationsContainer =>
      Try(fromJson[TezosTypes.OperationGroupContainer](jsonEncodedOperationsContainer)).flatMap{ operationsContainer =>
        Try(operationsContainer.ok)
      }
    }

  /**
    * Gets a given block.
    * @param network  Which Tezos network to go against
    * @param hash     Hash of the given block
    * @return         Block
    */
  def getBlock(network: String, hash: String): Try[TezosTypes.Block] =
    node.runQuery(network, s"blocks/$hash").flatMap { jsonEncodedBlock =>
      Try(fromJson[TezosTypes.BlockMetadata](jsonEncodedBlock)).flatMap { theBlock =>
        if(theBlock.level==0)
          Try(Block(theBlock, List[OperationGroup]()))    //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
        else {
          getAllOperationsForBlock(network, hash).flatMap{ itsOperations =>
            itsOperations.length match {
              case 0 => Try(Block(theBlock, List[OperationGroup]()))
              case _ => Try(Block(theBlock, itsOperations.head))
            }
          }
        }
      }
    }

  /**
    * Gets the block head.
    * @param network  Which Tezos network to go against
    * @return         Block head
    */
  def getBlockHead(network: String): Try[TezosTypes.Block]= {
    getBlock(network, "head")
  }

  /**
    * Gets all blocks from the head down to the oldest block not already in the database.
    * @param network  Which Tezos network to go against
    * @return         Blocks
    */
  def getBlocksNotInDatabase(network: String): Try[List[Block]] =
    ApiOperations.fetchMaxLevel().orElse{
      logger.warn("There were apparently no rows in the database. Dumping the whole chain.")
      Try(-1)
    }.flatMap{ maxLevel =>
      getBlockHead(network).flatMap { blockHead =>
        val headLevel = blockHead.metadata.level
        val headHash  = blockHead.metadata.hash
        if(headLevel <= maxLevel)
          Try(List[Block]())
        else
          getBlocks(network, maxLevel+1, headLevel, Some(headHash))
      }
    }

  /**
    * Gets the latest blocks from the database using an offset.
    * @param network        Which Tezos network to go against
    * @param offset         How many previous blocks to get from the start
    * @param startBlockHash The block from which to offset, using the hash provided or the head if none is provided.
    * @return               Blocks
    */
  def getBlocks(network: String, offset: Int, startBlockHash: Option[String]): Try[List[Block]] =
    startBlockHash match {
      case None =>
        getBlockHead(network).flatMap(head => getBlocks(network, head.metadata.level - offset + 1, head.metadata.level, startBlockHash))
      case Some(hash) =>
        getBlock(network, hash).flatMap(block => getBlocks(network, block.metadata.level - offset + 1, block.metadata.level, startBlockHash))
    }

  /**
    * Gets the blocks using a specified rage
    * @param network        Which Tezos network to go against
    * @param minLevel       Minimum block level
    * @param maxLevel       Maximum block level
    * @param startBlockHash If specified, start from the supplied block hash.
    * @return               Blocks
    */
  def getBlocks(network: String, minLevel: Int, maxLevel: Int, startBlockHash: Option[String]): Try[List[Block]] =
    startBlockHash match {
      case None =>
        getBlockHead(network).flatMap(head => Try {
          processBlocks(network, head.metadata.hash, minLevel, maxLevel)
        })
      case Some(hash) =>
        getBlock(network, hash).flatMap(block => Try {
          processBlocks(network, block.metadata.hash, minLevel, maxLevel)
        })
    }

  /**
    * Traverses Tezos blockchain as parameterized.
    * @param network    Which Tezos network to go against
    * @param hash       Current block to fetch
    * @param minLevel   Minimum level at which to stop
    * @param maxLevel   Level at which to start collecting blocks
    * @param blockSoFar Blocks collected so far
    * @return           All collected block
    */
  private def processBlocks(network: String, hash: String, minLevel: Int, maxLevel: Int, blockSoFar: List[Block] = List[Block]()): List[Block] =
    getBlock(network, hash) match {
      case Success(block) =>
        logger.info(s"Current block height: ${block.metadata.level}")
        if((block.metadata.level == 0 && minLevel <= 0) || block.metadata.level == minLevel)
          block :: blockSoFar
        else if(block.metadata.level > maxLevel)
          processBlocks(network, block.metadata.predecessor, minLevel, maxLevel, blockSoFar)
        else if(block.metadata.level > minLevel)
          processBlocks(network, block.metadata.predecessor, minLevel, maxLevel, block :: blockSoFar)
        else
          List[Block]()
      case Failure(e) => throw e
    }

  /**
    * Get all accounts for a given block
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @return           Accounts with their corresponding block hash
    */
  def getAccounts(network: String, blockHash: String): Try[AccountsWithBlockHash] =
    getAllAccountsForBlock(network, blockHash).flatMap{ accounts =>
      Try(AccountsWithBlockHash(blockHash, accounts))
    }

  /**
    * Get accounts for the latest block in the database.
    * @param network  Which Tezos network to go against
    * @return         Accounts with their corresponding block hash
    */
  def getLatestAccounts(network: String): Try[AccountsWithBlockHash]=
    ApiOperations.fetchLatestBlock().flatMap(dbBlockHead => getAccounts(network, dbBlockHead.hash))

}
