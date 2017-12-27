package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes.{Account, AccountsWithBlockHash, Block, OperationGroup}
import tech.cryptonomic.conseil.util.JsonUtil.fromJson

import scala.util.{Failure, Success, Try}
import scalaj.http.{HttpOptions, HttpResponse}

object TezosNodeOperations extends LazyLogging{

  val conf = ConfigFactory.load

  /**
    * Runs an RPC call against the configured Tezos node.
    * @param network  Which Tezos network to go against
    * @param path     RPC path to invoke
    * @return
    */
  def runQuery(network: String, path: String): Try[String] = {
    Try{
      val hostname = conf.getString(s"platforms.tezos.${network}.node.hostname")
      val port = conf.getInt(s"platforms.tezos.${network}.node.port")
      val pathPrefix = conf.getString(s"platforms.tezos.${network}.node.pathPrefix")
      val url = s"http://${hostname}:${port}/${pathPrefix}${path}"
      logger.info(s"Querying URL ${url} for platform Tezos and network ${network}")
      val response: HttpResponse[String] = scalaj.http.Http(url).postData("""{}""")
        .header("Content-Type", "application/json")
        .header("Charset", "UTF-8")
        .option(HttpOptions.readTimeout(100000))
        .option(HttpOptions.connTimeout(100000)).asString
      response.body
    }
  }

  def getAccountForBlock(network: String, blockHash: String, accountID: String): Try[TezosTypes.Account] =
    runQuery(network, s"blocks/${blockHash}/proto/context/contracts/${accountID}").flatMap { jsonEncodedAccount =>
      Try(fromJson[TezosTypes.AccountContainer](jsonEncodedAccount)).flatMap(acctContainer => Try(acctContainer.ok))
    }

  def getAllAccountsForBlock(network: String, blockHash: String): Try[Map[String, TezosTypes.Account]] = Try {
    runQuery(network, s"blocks/${blockHash}/proto/context/contracts") match {
      case Success(jsonEncodedAccounts) =>
        val accountIDs = fromJson[TezosTypes.AccountsContainer](jsonEncodedAccounts)
        val listedAccounts: List[String] = accountIDs.ok
        val accounts = listedAccounts.map(acctID => getAccountForBlock(network, blockHash, acctID))
        accounts.filter(_.isFailure).length match {
          case 0 =>
            val justTheAccounts = accounts.map(_.get)
            (listedAccounts zip justTheAccounts).toMap
          case _ => throw new Exception(s"Could not decode one of the accounts for block ${blockHash}")
        }
      case Failure(e) =>
        logger.error(s"Could not get a list of accounts for block ${blockHash}")
        throw e
    }
  }

  def getAllOperationsForBlock(network: String, blockHash: String) =
    runQuery(network, s"blocks/${blockHash}/proto/operations").flatMap { jsonEncodedOperationsContainer =>
      Try(fromJson[TezosTypes.OperationGroupContainer](jsonEncodedOperationsContainer)).flatMap{ operationsContainer =>
        Try(operationsContainer.ok)
      }
    }

  def getBlock(network: String, hash: String): Try[TezosTypes.Block] =
    runQuery(network, s"blocks/${hash}").flatMap { jsonEncodedBlock =>
      Try(fromJson[TezosTypes.BlockMetadata](jsonEncodedBlock)).flatMap { theBlock =>
        if(theBlock.level==0) Try(Block(theBlock, List[OperationGroup](), Map[String, Account]()))    //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
        else {
          getAllAccountsForBlock(network, hash).flatMap{ itsAccounts =>
            getAllOperationsForBlock(network, hash).flatMap{ itsOperations =>
              itsOperations.length match {
                case 0 => Try(Block(theBlock, List[OperationGroup](), Map[String, Account]()))
                case _ => Try(Block(theBlock, itsOperations.head, itsAccounts))
              }
            }
          }
        }
      }
    }

  def getBlockHead(network: String): Try[TezosTypes.Block]= {
    getBlock(network, "head")
  }

  def getBlocks(network: String, offset: Int, startBlockHash: Option[String]): Try[List[Block]] =
    startBlockHash match {
      case None =>
        getBlockHead(network).flatMap(head => getBlocks(network, head.metadata.level - offset, head.metadata.level, startBlockHash))
      case Some(hash) =>
        getBlock(network, hash).flatMap(block => getBlocks(network, block.metadata.level - offset, block.metadata.level, startBlockHash))
    }

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

  private def processBlocks(network: String, hash: String, minLevel: Int, maxLevel: Int, blockSoFar: List[Block] = List[Block]()): List[Block] =
    TezosNodeOperations.getBlock(network, hash) match {
      case Success(block) => {
        logger.info(s"Current block height: ${block.metadata.level}")
        if(block.metadata.level == 0 || block.metadata.level == minLevel)
          block :: blockSoFar
        else if(block.metadata.level > maxLevel)
          processBlocks(network, block.metadata.predecessor, minLevel, maxLevel, blockSoFar)
        else if(block.metadata.level > minLevel)
          processBlocks(network, block.metadata.predecessor, minLevel, maxLevel, block :: blockSoFar)
        else
          List[Block]()
      }
      case Failure(e) => throw e
    }

  def getAccounts(network: String, blockHash: String) =
    getAllAccountsForBlock(network, blockHash).flatMap{ accounts =>
      Try(AccountsWithBlockHash(blockHash, accounts))
    }

  def getLatestAccounts(network: String): Try[AccountsWithBlockHash]=
    getBlockHead(network).flatMap{ blockHead => getAccounts(network, blockHead.metadata.hash)}

}
