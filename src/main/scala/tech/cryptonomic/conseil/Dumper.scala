package tech.cryptonomic.conseil

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.TezosTypes.Block
import tech.cryptonomic.conseil.util.TezosUtil

import scala.util.{Failure, Success, Try}

object Dumper extends App with LazyLogging {

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

  takeAnotherDump("alphanet", 0,24368).map(_.foreach(println))

}
