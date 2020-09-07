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
      to = Some("to1"),
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
      to = Some("to1"),
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
      to = Some("to1"),
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
  }
}
