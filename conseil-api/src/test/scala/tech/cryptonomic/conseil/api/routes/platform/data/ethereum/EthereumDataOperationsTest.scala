package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import java.sql.Timestamp
import org.scalatest.concurrent.IntegrationPatience
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.EthereumInMemoryDatabaseSetup
import tech.cryptonomic.conseil.common.ethereum.EthereumTypes.EthereumBlockHash
import tech.cryptonomic.conseil.common.ethereum.Tables
import tech.cryptonomic.conseil.common.ethereum.Tables._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class EthereumDataOperationsTest
    extends ConseilSpec
    with InMemoryDatabase
    with EthereumInMemoryDatabaseSetup
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

      "return proper token balances, while fetching all token balances" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Tokens ++= tokens).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.TokenTransfers ++= tokenTransfers).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.TokensHistory ++= tokenBalances).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchTokensHistory(Query.empty)) { result =>
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
          result.value should (contain key "value" and contain value Some(
            convertAndScale(transaction2.value + transaction3.value, 2)
          ))

          // Since we are getting data for QueryResponse in generic way,
          // we are accessing Java API which returns java data types.
          // We need to convert and adjust scale for what we are receiving from Slick,
          // because ScalaTest uses `==`, instead od `compareTo` for this type.
          def convertAndScale(v: BigDecimal, s: Int): java.math.BigDecimal = v.bigDecimal.setScale(s)
        }
      }
    }
}

object EthereumDataOperationsTest {
  trait Fixtures {

    private val defaultBlock = BlocksRow(
      hash = "hash",
      number = 0,
      difficulty = "0",
      extraData = "extra",
      gasLimit = "0",
      gasUsed = "0",
      logsBloom = "bloom",
      miner = "a",
      mixHash = "m",
      nonce = "n",
      parentHash = None,
      receiptsRoot = "r",
      sha3Uncles = "sha3",
      size = "0",
      stateRoot = "sr",
      totalDifficulty = "0",
      transactionsRoot = "tr",
      uncles = None,
      timestamp = Timestamp.valueOf("2020-01-01 00:00:00")
    )
    val block1: BlocksRow =
      defaultBlock.copy(hash = "hash1", number = 1, timestamp = Timestamp.valueOf("2020-06-20 20:05:40"))
    val block2: BlocksRow =
      defaultBlock.copy(
        hash = "hash2",
        number = 2,
        parentHash = Some(block1.hash),
        timestamp = Timestamp.valueOf("2020-06-20 20:06:10")
      )
    val block3: BlocksRow =
      defaultBlock.copy(
        hash = "hash3",
        number = 3,
        parentHash = None,
        timestamp = Timestamp.valueOf("2020-06-20 20:08:00")
      )
    val blocks: Seq[BlocksRow] = List(block1, block2, block3)

    private val defaultTransaction = TransactionsRow(
      hash = "hash",
      blockHash = "blockHash",
      blockNumber = 0,
      timestamp = Timestamp.valueOf("2020-01-01 00:00:00"),
      from = "from",
      gas = "1",
      gasPrice = "1",
      input = "i",
      nonce = "1",
      to = None,
      transactionIndex = "0",
      value = BigDecimal("0"),
      v = "v",
      r = "r",
      s = "s"
    )
    val transaction1: TransactionsRow = defaultTransaction.copy(
      hash = "hash1",
      blockHash = block1.hash,
      blockNumber = block1.number,
      timestamp = block1.timestamp,
      from = "from1",
      to = Some("to"),
      transactionIndex = "1",
      value = BigDecimal("100")
    )
    val transaction2: TransactionsRow = defaultTransaction.copy(
      hash = "hash2",
      blockHash = block2.hash,
      blockNumber = block2.number,
      timestamp = block2.timestamp,
      from = "from1",
      to = Some("to"),
      transactionIndex = "2",
      value = BigDecimal("150")
    )
    val transaction3: TransactionsRow = defaultTransaction.copy(
      hash = "hash3",
      blockHash = block3.hash,
      blockNumber = block3.number,
      timestamp = block3.timestamp,
      from = "from2",
      to = Some("to3"),
      transactionIndex = "3",
      value = BigDecimal("100")
    )
    val transactions: Seq[TransactionsRow] = List(transaction1, transaction2, transaction3)

    private val defaultLogs =
      (block: BlocksRow, transaction: TransactionsRow) =>
        LogsRow(
          address = "address",
          blockHash = block.hash,
          blockNumber = block.number,
          timestamp = block.timestamp,
          data = "data",
          logIndex = "0",
          removed = false,
          topics = "t0",
          transactionHash = transaction.hash,
          transactionIndex = transaction.transactionIndex
        )
    val log1: LogsRow = defaultLogs(block1, transaction1).copy(address = "address1", topics = "t1")
    val log2: LogsRow = defaultLogs(block2, transaction2).copy(address = "address2", topics = "t2")
    val log3: LogsRow = defaultLogs(block3, transaction3).copy(address = "address3", topics = "t3")
    val logs: Seq[LogsRow] = List(log1, log2, log3)

    private val defaultReceipt =
      (block: BlocksRow, transaction: TransactionsRow) =>
        ReceiptsRow(
          transactionHash = transaction.hash,
          transactionIndex = transaction.transactionIndex,
          blockHash = block.hash,
          blockNumber = block.number,
          timestamp = block.timestamp,
          contractAddress = Some("0x1"),
          cumulativeGasUsed = "0x1",
          gasUsed = "0x1",
          logsBloom = "0x0",
          status = None,
          root = Some("0x1")
        )
    val receipt1: ReceiptsRow = defaultReceipt(block1, transaction1)
    val receipt2: ReceiptsRow = defaultReceipt(block2, transaction2)
    val receipt3: ReceiptsRow = defaultReceipt(block3, transaction3)
    val receipts: Seq[ReceiptsRow] = List(receipt1, receipt2, receipt3)

    private val defaultContract = (block: BlocksRow) =>
      ContractsRow(
        address = "0x0",
        blockHash = block.hash,
        blockNumber = block.number,
        bytecode = "0x0"
      )
    val contract1: ContractsRow = defaultContract(block1).copy(address = "0x1")
    val contract2: ContractsRow = defaultContract(block2).copy(address = "0x2")
    val contract3: ContractsRow = defaultContract(block3).copy(address = "0x3")
    val contracts: Seq[ContractsRow] = List(contract1, contract2, contract3)

    private val defaultToken = (block: BlocksRow) =>
      TokensRow(
        address = "0x1",
        blockHash = block.hash,
        blockNumber = block.number,
        name = "token",
        symbol = "symbol",
        decimals = "0x0",
        totalSupply = "0x0"
      )
    val token1: TokensRow = defaultToken(block1).copy(address = "0x1")
    val token2: TokensRow = defaultToken(block2).copy(address = "0x2")
    val token3: TokensRow = defaultToken(block3).copy(address = "0x3")
    val tokens: Seq[TokensRow] = List(token1, token2, token3)

    private val defaultTokenTransfer =
      (block: BlocksRow, transaction: TransactionsRow) =>
        TokenTransfersRow(
          tokenAddress = "0x1",
          blockHash = block.hash,
          blockNumber = block.number,
          timestamp = block.timestamp,
          transactionHash = transaction.hash,
          logIndex = "0x1",
          fromAddress = "0x0",
          toAddress = "0x0",
          value = 1.0
        )
    val tokenTransfer1: TokenTransfersRow =
      defaultTokenTransfer(block1, transaction1).copy(fromAddress = "0x1", toAddress = "0x2")
    val tokenTransfer2: TokenTransfersRow =
      defaultTokenTransfer(block2, transaction2).copy(fromAddress = "0x3", toAddress = "0x4")
    val tokenTransfer3: TokenTransfersRow =
      defaultTokenTransfer(block3, transaction3).copy(fromAddress = "0x5", toAddress = "0x6")
    val tokenTransfers: Seq[TokenTransfersRow] = List(tokenTransfer1, tokenTransfer2, tokenTransfer3)

    private val defaultTokenBalance =
      (block: BlocksRow, transaction: TransactionsRow) =>
        TokensHistoryRow(
          tokenAddress = "0x1",
          blockHash = block.hash,
          blockNumber = block.number,
          transactionHash = transaction.hash,
          accountAddress = "0x0",
          value = BigDecimal("1.0"),
          asof = block.timestamp
        )
    val tokenBalance1: TokensHistoryRow =
      defaultTokenBalance(block1, transaction1).copy(accountAddress = "0x1")
    val tokenBalance2: TokensHistoryRow =
      defaultTokenBalance(block2, transaction2).copy(accountAddress = "0x3")
    val tokenBalance3: TokensHistoryRow =
      defaultTokenBalance(block3, transaction3).copy(accountAddress = "0x5")
    val tokenBalances: Seq[TokensHistoryRow] = List(tokenBalance1, tokenBalance2, tokenBalance3)
  }
}
