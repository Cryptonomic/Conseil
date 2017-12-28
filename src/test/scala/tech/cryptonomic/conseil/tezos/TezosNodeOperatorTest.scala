package tech.cryptonomic.conseil.tezos

import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}

import scala.util.Try

class TezosNodeOperatorTest extends FlatSpec with MockFactory with Matchers {

  object MockTezosNode extends TezosRPCInterface {
    def runQuery(network: String, command: String): Try[String] = Try{
      command match {
        case "blocks/BLockGenesisGenesisGenesisGenesisGenesisFFFFFgtaC8G" => getStoredBlock("BLockGenesisGenesisGenesisGenesisGenesisFFFFFgtaC8G")
        case "blocks/BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh" => getStoredBlock("BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh")
        case "blocks/BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP" => getStoredBlock("BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP")
        case "blocks/BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF" => getStoredBlock("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
        case "blocks/BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh/proto/operations" => getStoredOperations("BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh")
        case "blocks/BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP/proto/operations" => getStoredOperations("BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP")
        case "blocks/BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF/proto/operations" => getStoredOperations("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
        case "blocks/head" => getStoredBlock("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
        case "blocks/head/proto/operations" => getStoredOperations("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
      }
    }

    private def getStoredBlock(hash: String): String = scala.io.Source.fromFile(s"src/test/resources/tezos_blocks/$hash.json").mkString
    private def getStoredOperations(hash: String): String = scala.io.Source.fromFile(s"src/test/resources/tezos_blocks/$hash.operations.json").mkString
  }

  /*val tezosNodeMock = mock[TezosRPCInterface]
  inAnyOrder {
    (tezosNodeMock.runQuery _).
      expects("alphanet", "blocks/BLockGenesisGenesisGenesisGenesisGenesisFFFFFgtaC8G").
      returning(Try {
        getStoredBlock("BLockGenesisGenesisGenesisGenesisGenesisFFFFFgtaC8G")
      })
    (tezosNodeMock.runQuery _).
      expects("alphanet", "blocks/BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh").
      returning(Try {
        getStoredBlock("BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh")
      })
    (tezosNodeMock.runQuery _).
      expects("alphanet", "blocks/BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP").
      returning(Try {
        getStoredBlock("BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP")
      })
    (tezosNodeMock.runQuery _).
      expects("alphanet", "blocks/BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF").
      returning(Try {
        getStoredBlock("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
      })
    (tezosNodeMock.runQuery _).
      expects("alphanet", "blocks/head").
      returning(Try {
        getStoredBlock("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
      })
  }*/

  "getBlock" should "should correctly fetch the genesis block" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val block: Try[TezosTypes.Block] = nodeOp.getBlock("alphanet", "BLockGenesisGenesisGenesisGenesisGenesisFFFFFgtaC8G")
    block.get.metadata.hash should be ("BLockGenesisGenesisGenesisGenesisGenesisFFFFFgtaC8G")
  }

  "getBlocks" should "fetch the correct number of blocks" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("alphanet", 0, 3, None)
    blocks.get.length should be (4)
  }


}
