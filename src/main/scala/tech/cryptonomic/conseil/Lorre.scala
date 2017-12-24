package tech.cryptonomic.conseil

import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.Types.Block
import tech.cryptonomic.conseil.tezos.TezosUtil.getBlockHead
import tech.cryptonomic.conseil.tezos.{Tables, TezosUtil, Types}

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration
import scala.util.{Failure, Success, Try}
import scala.concurrent.ExecutionContext.Implicits.global

object Lorre extends App with LazyLogging {

  def getBlocksFromClient(network: String, offset: Int): Try[List[Block]] =
    getBlockHead(network).flatMap(head => getBlocksFromClient(network, head.level - offset, head.level))

  def getBlocksFromClient(network: String, startBlock: Int, endBlock: Int): Try[List[Block]] =
    getBlockHead(network).flatMap(head => processBlocks(network, head.hash, startBlock, endBlock))

  def processBlocks(network: String, hash: String, startBlock: Int, endBlock: Int): Try[List[Block]] =
    TezosUtil.getBlock(network, hash).flatMap{block =>
      logger.info(block.level.toString)
      if(block.level > endBlock)
        processBlocks(network, block.predecessor, startBlock, endBlock)
      else if(block.level >= startBlock)
        processBlocks(network, block.predecessor, startBlock, endBlock).flatMap(blocks => Try(block :: blocks))
      else
        Try(List[Block]())
    }

  def writeToDatabase(block: Block): Try[Unit] = Try {
    val db = Database.forConfig("conseildb")
    try {
      val blocks = TableQuery[Tables.Blocks]
      val dbOp = DBIO.seq(
        blocks += Tables.BlocksRow(
          blockId = 2,
          netId = block.net_id,
          protocol = block.protocol,
          level = block.level,
          proto = block.proto,
          predecessor = Some(block.predecessor),
          timestamp = Some(block.timestamp),
          validationPass = block.validation_pass,
          operationsHash = block.operations_hash,
          fitness1 = block.fitness.head,
          fitness2 = block.fitness.tail.head,
          data = block.data,
          hash = block.hash
        )
      )
      val dbFut = db.run(dbOp)
      dbFut onComplete {_ match
        {
        case Success(_) => println("*****Success")
        case Failure(e) => println(s"*****Failure: ${e.getMessage}")
      }
      }
      Await.result(dbFut, Duration.Inf)
    } finally db.close()
  }

  getBlocksFromClient("alphanet", 5) match {
    case Success(blocks) => blocks.foreach(println)
    case Failure(e) => println(e)
  }
}
