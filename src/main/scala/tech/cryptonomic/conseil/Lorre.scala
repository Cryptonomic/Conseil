package tech.cryptonomic.conseil

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes.Block

import scala.util.{Failure, Success, Try}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.TezosUtil.getBlockHead
import tech.cryptonomic.conseil.tezos.{TezosTypes, TezosUtil}

object Lorre extends App with LazyLogging {

  def takeAnotherDump(network: String, startBlock: Int, endBlock: Int): List[Try[Block]] = {
    val head: Try[TezosTypes.Block] = TezosUtil.getBlockHead(network)
    head match {
      case Success(block) =>
        process(network, block.hash, startBlock, endBlock)
      case Failure(e) =>
        logger.warn(e.getMessage)
        List[Try[Block]]()
    }
  }

  def takeAnotherDump(network: String, offset: Int): List[Try[Block]] = {
    getBlockHead(network) match {
      case Success(s) => takeAnotherDump(network, s.level - offset, s.level)
      case Failure(e) => List[Try[Block]]()
    }

  }

  def process(network: String, hash: String, startBlock: Int, endBlock: Int): List[Try[Block]] = {
    val result: Try[Block] = TezosUtil.getBlock(network, hash)
    result match {
      case Success(block) =>
        logger.info(block.level.toString)
        if(block.level<startBlock) List[Try[Block]]()
        else if(block.level > endBlock) process(network, block.predecessor, startBlock, endBlock)
        else result :: process(network, block.predecessor, startBlock, endBlock)
      case Failure(e) =>
        logger.warn(e.getMessage)
        List[Try[Block]]()
    }
  }

  def writeToDatabase(blocks: List[Try[Block]]) = {
    val db = Database.forConfig("conseildb")
    try{
      println(sql"select * from users".toString)
    } finally db.close()
  }

  takeAnotherDump("alphanet", 5).map(_.foreach(println))
}
