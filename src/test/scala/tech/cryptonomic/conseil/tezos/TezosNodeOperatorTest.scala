package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers}
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore

import scala.util.Try

class TezosNodeOperatorTest extends FlatSpec with MockFactory with Matchers with LazyLogging {

  object MockTezosNode extends TezosRPCInterface {

    def runQuery(network: String, command: String, payload: Option[String] = None): Try[String] = Try{
      logger.info(s"Ran Tezos Query: Network = $network, Command = $command, Payload = $payload")
      command match {
        case "blocks/BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe" =>
          getStoredBlock("BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe")
        case "blocks/BKiiqiGu758Q76DLiqvN2ocwowjnR3aRrXguRYVq2xw61chzQoZ" =>
          getStoredBlock("BKiiqiGu758Q76DLiqvN2ocwowjnR3aRrXguRYVq2xw61chzQoZ")
        case "blocks/BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38" =>
          getStoredBlock("BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38")

        case "blocks/BKiiqiGu758Q76DLiqvN2ocwowjnR3aRrXguRYVq2xw61chzQoZ/proto/operations" =>
          getStoredOperations("BKiiqiGu758Q76DLiqvN2ocwowjnR3aRrXguRYVq2xw61chzQoZ")
        case "blocks/BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38/proto/operations" =>
          getStoredOperations("BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38")
        /*
      case "blocks/BLockGenesisGenesisGenesisGenesisGenesisFFFFFgtaC8G" =>
        getStoredBlock("BLockGenesisGenesisGenesisGenesisGenesisFFFFFgtaC8G")
      case "blocks/BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh" =>
        getStoredBlock("BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh")
      case "blocks/BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP" =>
        getStoredBlock("BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP")
      case "blocks/BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF" =>
        getStoredBlock("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")

      case "blocks/BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh/proto/operations" =>
        getStoredOperations("BM7uW88Qavxu5Z3jWQ11xuF79n4F4iNPnjoyA3tG3USZMKo2FKh")
      case "blocks/BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP/proto/operations" =>
        getStoredOperations("BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP")
      case "blocks/BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF/proto/operations" =>
        getStoredOperations("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")

      case "blocks/BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP/proto/context/contracts" =>
        getStoredAccounts("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
      case "blocks/BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF/proto/context/contracts" =>
        getStoredAccounts("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")

      case "blocks/BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP/proto/context/contracts/Abadaccount" =>
        getStoredAccount("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF", "Abadaccount")
      case "blocks/BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF/proto/context/contracts/tz1ey28xfyVvtPRPN9d43Wbf1vkPs868CGXM" =>
        getStoredAccount("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF", "tz1ey28xfyVvtPRPN9d43Wbf1vkPs868CGXM")

      case "blocks/head" =>
        getStoredBlock("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
      case "blocks/head/proto/operations" =>
        getStoredOperations("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
        */

        case _ => throw new Exception("You are silly bear.")

      }
    }

    private def getStoredBlock(hash: String): String =
      scala.io.Source.fromFile(s"src/test/resources/tezos_blocks/$hash.json").mkString
    private def getStoredOperations(hash: String): String =
      scala.io.Source.fromFile(s"src/test/resources/tezos_blocks/$hash.operations.json").mkString
    private def getStoredAccounts(hash: String): String =
      scala.io.Source.fromFile(s"src/test/resources/tezos_blocks/$hash.accounts.json").mkString
    private def getStoredAccount(hash: String, accountID: String): String =
      scala.io.Source.fromFile(s"src/test/resources/tezos_blocks/$hash.account.$accountID.json").mkString
  }

  object MockTezosNodeWithErrors extends TezosRPCInterface {

    def runQuery(network: String, command: String, payload: Option[String] = None): Try[String] = Try {
      command match {
        case "blocks/BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP" =>
          throw new Exception("A block request failed due to an alien invasion.")
        case _ => MockTezosNode.runQuery(network, command).get
      }
    }

  }

  val keyStore: KeyStore = KeyStore(
    publicKey = "edpku8PouyaJa2GPyXNsqsFiqjc3PZ2bc2ujzNUJQJ87N1jwTckp7n",
    privateKey = "edskRjp9WGL5QDFBcjHJGdxh6wfNtAYZY7CFmjSXbXSHWDydw8S9VVbaPJNFaDpHC2UscMTJWVryEQCUewQ8SNEChG2Vnb6GXA",
    publicKeyHash = "tz1eyrhHFQ6McdKkTeZtBKoRYUsgRgARN7QE"
  )

  /*val keyStore: KeyStore = KeyStore(
    publicKey = "edpku5ViG6Pc3uYooHuWhLr3eb2x86xNettKRm5SXBg9AfoYqrWdZc",
    privateKey = "edskRtLP6MGr3Y4taNfC19f4TjU3KYYHpfLQzxxovzX5aS4TztpbpajTVUzruNj53iLvymkwTKAnfE72dvPx7BPBan5tvdTrAg",
    publicKeyHash = "tz1R7cAdCTtFAWmVkju1cVUceyrR1vHvhu2Z"
  )*/
  "getBlock" should "should correctly fetch the genesis block" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val block: Try[TezosTypes.Block] = nodeOp.getBlock("zeronet", "BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe")
    block.get.metadata.hash should be ("BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe")
  }

  //skip block at level 1 because zeronet doesn't give proper response, also, hint because not the head
  "getBlocks" should "fetch the correct number of blocks" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 0, 3, Some("BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38"), followFork = false)
    blocks.get.length should be (3)
  }

  "getBlocks" should "handle a failed RPC request" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNodeWithErrors)
    val blocks = nodeOp.getBlocks("zeronet", 0, 3, None, followFork = false)
    blocks.isFailure should be (true)
  }

  "getBlocks" should "work correctly with a hint whose level is too low" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 2, 3, Some("BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe"), followFork = false)
    blocks.get.length should be (0)
  }

  "getBlocks" should "handle an invalid block payload" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 0, 3, Some("fakeblock"), followFork = false)
    blocks.isFailure should be (true)
  }
  /*
  //java.util.NoSuchElementException: head of empty list, doesn't work correctly?
  "getBlocks" should "work correctly with an offset and hint" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 3, Some("BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38"), followFork = false)
    blocks.get.head.metadata.hash should be ("BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe")
  }
  */
  //head isn't in mock chain, how to fix this?
  /*
  "getBlocks" should "work correctly with an offset" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 3, None, followFork = false)
    blocks.get.size should be (3)
  }*/

/*
  // Once we can mock the datbase, we should test whether getBlocks() works on a forked chain.

  "getBlocks" should "work correctly with a hint whose level is too high" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("alphanet", 0, 2, Some("BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF"), followFork = false)
    blocks.get.length should be (3)
  }


  "getAccounts" should "correctly fetch all accounts for a block" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val accounts = nodeOp.getAccounts("alphanet", "BMXXHxiX1zsCAMvvSUMR3jhNEBVWAp2CdviAgYrJ5NYYUJbL1zF")
    accounts.get.accounts.size should be (1)
    val account = accounts.get.accounts.get("tz1ey28xfyVvtPRPN9d43Wbf1vkPs868CGXM")
    account.get.balance should be (399800000)
  }

  "getAccounts" should "handle an invalid accounts payload" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val accounts = nodeOp.getAccounts("alphanet", "dummy")
    accounts.isFailure should be (true)
  }

  "getAccounts" should "handle a badly-formed account payload" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val accounts = nodeOp.getAccounts("alphanet", "BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP")
    accounts.isFailure should be (true)
  }

  "sendTransaction" should "correctly send a transaction" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)
    val result = nodeOp.sendTransactionOperation(
      "alphanet",
      keyStore,
      "tz1R7cAdCTtFAWmVkju1cVUceyrR1vHvhu2Z",
      100f,
      1f
    )
    result.isSuccess should be (true)
  }
*/
  //The following tests are commented out until a full mock can be implemented.
  /*
  "sendFaucetRequest" should "correctly create a free account" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)
    val result = nodeOp.sendFaucetOperation(
      "alphanet",
      keyStore
    )
    result.isSuccess should be (true)
  }


  "fundAccountWithFaucet" should "fund a given account using the faucet" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)
    val result = nodeOp.fundAccountWithFaucet(
      "alphanet",
      keyStore
    )
    result.isSuccess should be (true)
  }

  "sendDelegationOperation" should "correctly delegate to a given account" in {
    val delegatedKeyStore = keyStore.copy(publicKeyHash = "TZ1eC3DHVJeWkodQjtDAPx4UobEXeHzmhJnA")
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)
    val result = nodeOp.sendDelegationOperation(
      "alphanet",
      delegatedKeyStore,
      "tz1R7cAdCTtFAWmVkju1cVUceyrR1vHvhu2Z",
      1f
    )
    result.isSuccess should be (true)
  }

  "sendOriginationOperation" should "originate an account" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)
    val result = nodeOp.sendOriginationOperation(
      "alphanet",
      keyStore,
      100f,
      "tz1R7cAdCTtFAWmVkju1cVUceyrR1vHvhu2Z",
      spendable = true,
      delegatable = true,
      1f
    )
    result.isSuccess should be (true)
  }

  "createAccount" should "generate a new Tezos key pair" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)
    val result = nodeOp.createIdentity()
    result.isSuccess should be (true)
  }*/
}
