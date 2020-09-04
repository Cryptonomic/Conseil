package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import java.sql.Timestamp

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.OptionValues
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.EthereumInMemoryDatabaseSetup
import tech.cryptonomic.conseil.common.ethereum.EthereumTypes.EthereumBlockHash
import tech.cryptonomic.conseil.common.ethereum.Tables
import tech.cryptonomic.conseil.common.ethereum.Tables._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class EthereumDataOperationsTest
    extends AnyWordSpec
    with Matchers
    with InMemoryDatabase
    with EthereumInMemoryDatabaseSetup
    with ScalaFutures
    with OptionValues
    with LazyLogging
    with IntegrationPatience
    with EthereumDataOperationsTest.Fixtures {

  "EthereumDataOperations" should {
      val sut = new EthereumDataOperations("ethereum") {
        override lazy val dbReadHandle = dbHandler
      }

      "return proper number of blocks, while fetching all blocks" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchBlocks(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper head block" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchBlocksHead()) { result =>
          result.value should contain key "hash"
          result.value("hash") shouldBe Some("hash3")
        }
      }

      "return proper block by hash" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchBlockByHash(EthereumBlockHash("hash2"))) { result =>
          result.value should contain key "hash"
          result.value("hash") shouldBe Some("hash2")
        }
      }

      "return proper number of transactions, while fetching all transactions" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchTransactions(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper transaction by hash" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchTransactionByHash("hash1")) { result =>
          result.value should contain key "hash"
          result.value("hash") shouldBe Some("hash1")
        }
      }

      "return proper logs, while fetching all logs" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Logs ++= logs).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchLogs(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper receipts, while fetching all receipts" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Receipts ++= receipts).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchReceipts(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper contracts, while fetching all contracts" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Contracts ++= contracts).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchContracts(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper tokens, while fetching all tokens" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Tokens ++= tokens).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchTokens(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper token transfers, while fetching all token transfers" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Tokens ++= tokens).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.TokenTransfers ++= tokenTransfers).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchTokenTransfers(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper number of accounts, while fetching all of accounts" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchAccounts(Query.empty)) { result =>
          result.value.size shouldBe 2
        }
      }

      "return proper account by address" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchAccountByAddress("to")) { result =>
          result.value should (contain key "address" and contain value transaction1.to)
          //TODO Fix test for 'value' field in AccountRow
          //Issue with the current approach is that type inside case class is from 'scala' while slick is mapping data inside 'QueryResponse' to 'java'.
          //Additionally BigDecimals are not easy comparable with 'scalatest'.
          //Once type for 'value' field inside AccountRow will be changed from 'BigDecimal' into something else or
          //the type returned by 'fetchAccountByAddress' will be different than 'QueryResponse',
          //assert below should be either uncommented (in case of replacing 'BigDecimal')
          //or entire test should be updated (in case of changing returned type by 'fetchAccountByAddress').
          //result.value should (contain key "value" and contain value output2.value)
        }
      }
    }
}

object EthereumDataOperationsTest {
  trait Fixtures {

    val block1: BlocksRow = BlocksRow(
      hash = "hash1",
      number = 1,
      difficulty = "1",
      extraData = "extra1",
      gasLimit = "1",
      gasUsed = "1",
      logsBloom = "bloom",
      miner = "a",
      mixHash = "m1",
      nonce = "n",
      parentHash = None,
      receiptsRoot = "r",
      sha3Uncles = "sha3",
      size = "3",
      stateRoot = "sr",
      totalDifficulty = "3",
      transactionsRoot = "tr",
      uncles = None,
      timestamp = Timestamp.valueOf("2020-06-20 20:05:40")
    )
    val block2: BlocksRow = BlocksRow(
      hash = "hash2",
      number = 2,
      difficulty = "3",
      extraData = "extra1",
      gasLimit = "3",
      gasUsed = "2",
      logsBloom = "bloom",
      miner = "a",
      mixHash = "m1",
      nonce = "n",
      parentHash = Some(block1.hash),
      receiptsRoot = "r",
      sha3Uncles = "sha3",
      size = "3",
      stateRoot = "sr",
      totalDifficulty = "5",
      transactionsRoot = "tr",
      uncles = None,
      timestamp = Timestamp.valueOf("2020-06-20 20:06:10")
    )
    val block3: BlocksRow = BlocksRow(
      hash = "hash3",
      number = 3,
      difficulty = "5",
      extraData = "extra1",
      gasLimit = "3",
      gasUsed = "2",
      logsBloom = "bloom",
      miner = "a",
      mixHash = "m1",
      nonce = "n",
      parentHash = None,
      receiptsRoot = "r",
      sha3Uncles = "sha3",
      size = "3",
      stateRoot = "sr",
      totalDifficulty = "5",
      transactionsRoot = "tr",
      uncles = None,
      timestamp = Timestamp.valueOf("2020-06-20 20:08:00")
    )
    val blocks: Seq[BlocksRow] = List(block1, block2, block3)

    val transaction1: TransactionsRow = TransactionsRow(
      hash = "hash1",
      blockHash = block1.hash,
      blockNumber = block1.number,
      from = "from1",
      gas = "1",
      gasPrice = "1",
      input = "i1",
      nonce = "1",
      to = Some("to"),
      transactionIndex = "1",
      value = BigDecimal("100"),
      v = "v",
      r = "r",
      s = "s"
    )
    val transaction2: TransactionsRow = TransactionsRow(
      hash = "hash2",
      blockHash = block2.hash,
      blockNumber = block3.number,
      from = "from1",
      gas = "1",
      gasPrice = "1",
      input = "i1",
      nonce = "1",
      to = Some("to"),
      transactionIndex = "2",
      value = BigDecimal("150"),
      v = "v",
      r = "r",
      s = "s"
    )
    val transaction3: TransactionsRow = TransactionsRow(
      hash = "hash3",
      blockHash = block3.hash,
      blockNumber = block2.number,
      from = "from2",
      gas = "1",
      gasPrice = "1",
      input = "i1",
      nonce = "1",
      to = Some("to3"),
      transactionIndex = "3",
      value = BigDecimal("100"),
      v = "v",
      r = "r",
      s = "s"
    )
    val transactions: Seq[TransactionsRow] = List(transaction1, transaction2, transaction3)

    val log1: LogsRow = LogsRow(
      address = "address1",
      blockHash = block1.hash,
      blockNumber = block1.number,
      data = "data1",
      logIndex = "1",
      removed = false,
      topics = "t1",
      transactionHash = transaction1.hash,
      transactionIndex = transaction1.transactionIndex
    )
    val log2: LogsRow = LogsRow(
      address = "address2",
      blockHash = block2.hash,
      blockNumber = block2.number,
      data = "data2",
      logIndex = "1",
      removed = false,
      topics = "t2",
      transactionHash = transaction2.hash,
      transactionIndex = transaction2.transactionIndex
    )
    val log3: LogsRow = LogsRow(
      address = "address3",
      blockHash = block3.hash,
      blockNumber = block3.number,
      data = "data3",
      logIndex = "1",
      removed = false,
      topics = "t3",
      transactionHash = transaction3.hash,
      transactionIndex = transaction3.transactionIndex
    )
    val logs: Seq[LogsRow] = List(log1, log2, log3)

    val receipt1: ReceiptsRow = ReceiptsRow(
      transactionHash = transaction1.hash,
      transactionIndex = transaction1.transactionIndex,
      blockHash = block1.hash,
      blockNumber = block1.number,
      contractAddress = Some("0x1"),
      cumulativeGasUsed = "0x1",
      gasUsed = "0x1",
      logsBloom = "0x0",
      status = None,
      root = Some("0x1")
    )
    val receipt2: ReceiptsRow = ReceiptsRow(
      transactionHash = transaction2.hash,
      transactionIndex = transaction2.transactionIndex,
      blockHash = block2.hash,
      blockNumber = block2.number,
      contractAddress = Some("0x2"),
      cumulativeGasUsed = "0x2",
      gasUsed = "0x2",
      logsBloom = "0x0",
      status = None,
      root = Some("0x2")
    )
    val receipt3: ReceiptsRow = ReceiptsRow(
      transactionHash = transaction3.hash,
      transactionIndex = transaction3.transactionIndex,
      blockHash = block3.hash,
      blockNumber = block3.number,
      contractAddress = Some("0x2"),
      cumulativeGasUsed = "0x2",
      gasUsed = "0x2",
      logsBloom = "0x0",
      status = None,
      root = Some("0x2")
    )
    val receipts: Seq[ReceiptsRow] = List(receipt1, receipt2, receipt3)

    val contract1: ContractsRow = ContractsRow(
      address = "0x1",
      blockHash = block1.hash,
      blockNumber = block1.number,
      bytecode = "0x0",
      isErc20 = false,
      isErc721 = false
    )
    val contract2: ContractsRow = ContractsRow(
      address = "0x2",
      blockHash = block2.hash,
      blockNumber = block2.number,
      bytecode = "0x0",
      isErc20 = false,
      isErc721 = false
    )
    val contract3: ContractsRow = ContractsRow(
      address = "0x3",
      blockHash = block3.hash,
      blockNumber = block3.number,
      bytecode = "0x0",
      isErc20 = false,
      isErc721 = false
    )
    val contracts: Seq[ContractsRow] = List(contract1, contract2, contract3)

    val token1: TokensRow = TokensRow(
      address = "0x1",
      blockHash = block1.hash,
      blockNumber = block1.number,
      name = "token 1",
      symbol = "symbol 1",
      decimals = "0x0",
      totalSupply = "0x0"
    )
    val token2: TokensRow = TokensRow(
      address = "0x2",
      blockHash = block2.hash,
      blockNumber = block2.number,
      name = "token 1",
      symbol = "symbol 1",
      decimals = "0x0",
      totalSupply = "0x0"
    )
    val token3: TokensRow = TokensRow(
      address = "0x3",
      blockHash = block3.hash,
      blockNumber = block3.number,
      name = "token 1",
      symbol = "symbol 1",
      decimals = "0x0",
      totalSupply = "0x0"
    )
    val tokens: Seq[TokensRow] = List(token1, token2, token3)

    val tokenTransfer1: TokenTransfersRow = TokenTransfersRow(
      blockNumber = block1.number,
      transactionHash = transaction1.hash,
      fromAddress = "0x1",
      toAddress = "0x2",
      value = 1.0
    )
    val tokenTransfer2: TokenTransfersRow = TokenTransfersRow(
      blockNumber = block2.number,
      transactionHash = transaction2.hash,
      fromAddress = "0x3",
      toAddress = "0x4",
      value = 1.0
    )
    val tokenTransfer3: TokenTransfersRow = TokenTransfersRow(
      blockNumber = block3.number,
      transactionHash = transaction3.hash,
      fromAddress = "0x5",
      toAddress = "0x6",
      value = 1.0
    )
    val tokenTransfers: Seq[TokenTransfersRow] = List(tokenTransfer1, tokenTransfer2, tokenTransfer3)
  }
}
