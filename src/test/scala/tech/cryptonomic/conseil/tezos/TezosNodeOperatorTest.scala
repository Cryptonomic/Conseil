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

        case "blocks/BKiiqiGu758Q76DLiqvN2ocwowjnR3aRrXguRYVq2xw61chzQoZ/proto/context/contracts" =>
          getStoredAccounts("BKiiqiGu758Q76DLiqvN2ocwowjnR3aRrXguRYVq2xw61chzQoZ")
        case "blocks/BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38/proto/context/contracts" =>
          getStoredAccounts("BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38")

        case "blocks/BKiiqiGu758Q76DLiqvN2ocwowjnR3aRrXguRYVq2xw61chzQoZ/proto/context/contracts/tz1btz5Av9BdpoTPnS9zGyPvpgAovmaZ23iN" =>
          getStoredAccount("BKiiqiGu758Q76DLiqvN2ocwowjnR3aRrXguRYVq2xw61chzQoZ", "tz1btz5Av9BdpoTPnS9zGyPvpgAovmaZ23iN")
        case "blocks/BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38/proto/context/contracts/tz1btz5Av9BdpoTPnS9zGyPvpgAovmaZ23iN" =>
          getStoredAccount("BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38", "tz1btz5Av9BdpoTPnS9zGyPvpgAovmaZ23iN")

        case "blocks/head" =>
          getStoredBlock("BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38")
        case "blocks/head/proto/operations" =>
          getStoredOperations("BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38")

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
        case "blocks/BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38" =>
          throw new Exception("A block request failed due to an alien invasion.")
        case _ => MockTezosNode.runQuery(network, command).get
      }
    }

  }

  val keyStore: KeyStore = KeyStore(
    publicKey = "edpktua1v774adQc4nn88sTCWBqjCQGcjq9ZoxTaBhTdmDu6EKLKWw",
    privateKey = "edskS18kSm7DLHs9eBtdWRiruqCbxPtt2RiTjfbU1sJxDsyPqVUo5TncUqJFsHbFA5uKUojHgvjtEB7Mt9VxZnSJVNAGa1iAYq",
    publicKeyHash = "tz1QdZSY2nEYyTEoKYrryhAQxaiUfG9VkZ23"
  )

  "getBlock" should "should correctly fetch the genesis block" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val block: Try[TezosTypes.Block] = nodeOp.getBlock("zeronet", "BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe")
    block.get.metadata.hash should be ("BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe")
  }

  //skip block at level 1 because zeronet doesn't give proper response, also, hint because not the head
  "getBlocks" should "fetch the correct number of blocks" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 0, 2, None, followFork = false)
    blocks.get.length should be (3)
  }

  "getBlocks" should "handle a failed RPC request" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNodeWithErrors)
    val blocks = nodeOp.getBlocks("zeronet", 0, 5, None, followFork = false)
    blocks.isFailure should be (true)
  }

  "getBlocks" should "work correctly with a hint whose level is too low" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 1, 2, Some("BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe"), followFork = false)
    blocks.get.length should be (0)
  }

  "getBlocks" should "handle an invalid block payload" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 0, 2, Some("fakeblock"), followFork = false)
    blocks.isFailure should be (true)
  }

  //java.util.NoSuchElementException: head of empty list, doesn't work correctly?
  "getBlocks" should "work correctly with an offset and hint" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 3, Some("BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38"), followFork = false)
    blocks.get.head.metadata.hash should be ("BLockGenesisGenesisGenesisGenesisGenesis385e5hNnQTe")
  }


  "getBlocks" should "work correctly with an offset" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 1, None, followFork = false)
    blocks.get.size should be (1)
  }


  // Once we can mock the datbase, we should test whether getBlocks() works on a forked chain.

  "getBlocks" should "work correctly with a hint whose level is too high" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val blocks = nodeOp.getBlocks("zeronet", 0, 1, Some("BKiiqiGu758Q76DLiqvN2ocwowjnR3aRrXguRYVq2xw61chzQoZ"), followFork = false)
    blocks.get.length should be (2)
  }


  "getAccounts" should "correctly fetch all accounts for a block" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val accounts = nodeOp.getAccounts("zeronet", "BKiRLq7c2QVr6X428RRvp6JLTJEnWPE4bc4cAQHoo9GuZz9GH38")
    accounts.get.accounts.size should be (1) //actually 5 in tezos, 1 for testing purposes.
    val account = accounts.get.accounts.get("tz1btz5Av9BdpoTPnS9zGyPvpgAovmaZ23iN")
    account.get.balance should be (12000000000000.0)
  }

  "getAccounts" should "handle an invalid accounts payload" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val accounts = nodeOp.getAccounts("zeronet", "dummy")
    accounts.isFailure should be (true)
  }

  "getAccounts" should "handle a badly-formed account payload" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(MockTezosNode)
    val accounts = nodeOp.getAccounts("zeronet", "BMMYEBsahXhnCdb7RqGTPnt9a8kdpMApjVV5iXzxr9MFdS4MHuP")
    accounts.isFailure should be (true)
  }

  "sendTransaction" should "correctly send a transaction" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)
    val result = nodeOp.sendTransactionOperation(
      "zeronet",
      keyStore,
      "TZ1akACBXUC3vSAjXdNsEGjGJLWHNdXGgcok",
      100f,
      1f
    )
    result.isSuccess should be (true)
  }

  "sendDelegationOperation" should "correctly delegate to a given account" in {
    val delegatedKeyStore = keyStore.copy(publicKeyHash = "TZ1eC3DHVJeWkodQjtDAPx4UobEXeHzmhJnA")
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)
    val result = nodeOp.sendDelegationOperation(
      "zeronet",
      delegatedKeyStore,
      "tz1QdZSY2nEYyTEoKYrryhAQxaiUfG9VkZ23",
      1f
    )
    result.isSuccess should be (true)
  }

  "sendOriginationOperation" should "originate an account" in {
    val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)
    val result = nodeOp.sendOriginationOperation(
      "zeronet",
      keyStore,
      100f,
      "tz1QdZSY2nEYyTEoKYrryhAQxaiUfG9VkZ23",
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
  }
}
