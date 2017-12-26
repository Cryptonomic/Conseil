package tech.cryptonomic.conseil.tezos

import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes.{Block, OperationGroup}
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
      val tezos_hostname = conf.getString(s"platforms.tezos.${network}.node.hostname")
      val tezos_port = conf.getInt(s"platforms.tezos.${network}.node.port")
      val url = s"http://${tezos_hostname}:${tezos_port}/tezos/${network}/${path}"
      logger.info(s"Querying URL ${url} for platform Tezos and network ${network}")
      val response: HttpResponse[String] = scalaj.http.Http(url).postData("""{}""")
        .header("Content-Type", "application/json")
        .header("Charset", "UTF-8")
        .option(HttpOptions.readTimeout(100000))
        .option(HttpOptions.connTimeout(100000)).asString
      response.body
    }
  }

  def getBlock(network: String, hash: String): Try[TezosTypes.Block] =
    runQuery(network, s"blocks/${hash}").flatMap { jsonEncodedBlock =>
      Try(fromJson[TezosTypes.BlockMetadata](jsonEncodedBlock)).flatMap { theBlock =>
        if(theBlock.level==0) Try(Block(theBlock, List[OperationGroup]()))    //This is a workaround for the Tezos node returning a 404 error when asked for the operations of the genesis blog, which seems like a bug.
        else {
          runQuery(network, s"blocks/${hash}/proto/operations").flatMap { jsonEncodedOperations =>
            Try(fromJson[TezosTypes.OperationGroupContainer](jsonEncodedOperations)).flatMap { itsOperations =>
              itsOperations.ok.length match {
                case 0 => Try(Block(theBlock, List[OperationGroup]()))
                case _ => Try(Block(theBlock, itsOperations.ok.head))
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

}
