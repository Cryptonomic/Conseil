package tech.cryptonomic.conseil

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.TezosUtil.getBlockHead
import tech.cryptonomic.conseil.tezos.Types.Block
import tech.cryptonomic.conseil.tezos.{Tables, TezosUtil}

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}

object Lorre extends App with LazyLogging {

  lazy val db = Database.forConfig("conseildb")

  def getBlocksFromClient(network: String, offset: Int): Try[List[Block]] =
    getBlockHead(network).flatMap(head => getBlocksFromClient(network, head.level - offset, head.level))

  def getBlocksFromClient(network: String, startBlock: Int, endBlock: Int): Try[List[Block]] =
    getBlockHead(network).flatMap(head => Try{processBlocks(network, head.hash, startBlock, endBlock)})

  def processBlocks(network: String, hash: String, startBlock: Int, endBlock: Int): List[Block] =
    TezosUtil.getBlock(network, hash) match {
      case Success(block) => {
        if(block.level > endBlock)
          processBlocks(network, block.predecessor, startBlock, endBlock)
        else if(block.level >= startBlock)
          block :: processBlocks(network, block.predecessor, startBlock, endBlock)
        else
          List[Block]()
      }
      case Failure(e) => throw e
    }

  def writeToDatabase(blocks: List[Block]) = {
    db.run(
      DBIO.seq(
        Tables.Blocks ++= blocks.map(block =>
          Tables.BlocksRow(
            netId = block.net_id,
            protocol = block.protocol,
            level = block.level,
            proto = block.proto,
            //predecessor = Some(block.predecessor),
            predecessor = None,
            timestamp = Some(block.timestamp),
            validationPass = block.validation_pass,
            operationsHash = block.operations_hash,
            fitness1 = block.fitness.head,
            fitness2 = block.fitness.tail.head,
            data = block.data,
            hash = block.hash
          )
        )
      )
    )
  }

  getBlocksFromClient("alphanet", 10) match {
    case Success(blocks) => {
      Try {
        val dbFut = writeToDatabase(blocks)
        dbFut onComplete {
          _ match {
            case Success(_) => logger.info(s"Wrote blocks to the database.")
            case Failure(e) => logger.error(s"Could not write blocks to the database because ${e.getMessage}")
          }
        }
        Await.result(dbFut, Duration.Inf)
      }
    }
    case Failure(e) => logger.error(s"Could not fetch blocks from client because ${e.getMessage}")
  }
  db.close()
}
