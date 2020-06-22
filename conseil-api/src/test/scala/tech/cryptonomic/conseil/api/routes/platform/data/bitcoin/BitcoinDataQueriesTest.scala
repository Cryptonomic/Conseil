package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import java.sql.Timestamp

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.BitcoinInMemoryDatabaseSetup
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.bitcoin.Tables
import tech.cryptonomic.conseil.common.bitcoin.Tables._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class BitcoinDataQueriesTest
    extends WordSpec
    with Matchers
    with InMemoryDatabase
    with BitcoinInMemoryDatabaseSetup
    with ScalaFutures
    with OptionValues
    with LazyLogging
    with IntegrationPatience
    with BitcoinDataQueriesTest.Fixtures {

  "BitcoinDataQueries" should {
      val sut = new BitcoinDataQueries(new ApiDataOperations {
        override lazy val dbReadHandle = dbHandler
      })

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

        whenReady(sut.fetchBlockByHead("hash2")) { result =>
          result.value should contain key "hash"
          result.value("hash") shouldBe Some("hash2")
        }
      }

      "return proper number of transactions, while fetching all transactions" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchBlocks(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }
//TODO Uncomment once issue with inputs and outputs table will be fixed
    
//      "return proper number of inputs, while fetching all inputs" in {
//        // given
//        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
//        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
//        dbHandler.run(Tables.Inputs ++= inputs).isReadyWithin(5.seconds) shouldBe true
//
//        whenReady(sut.fetchInputs(Query.empty)) { result =>
//          result.value.size shouldBe 3
//        }
//      }
//
//      "return proper number of outputs, while fetching all outputs" in {
//        // given
//        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
//        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
//        dbHandler.run(Tables.Outputs ++= outputs).isReadyWithin(5.seconds) shouldBe true
//
//        whenReady(sut.fetchOutputs(Query.empty)) { result =>
//          result.value.size shouldBe 3
//        }
//      }
    }
}

object BitcoinDataQueriesTest {
  trait Fixtures {
    val block1: BlocksRow = BlocksRow(
      hash = "hash1",
      confirmations = 1,
      size = 6,
      weight = 3,
      height = 3,
      version = 1,
      merkleRoot = "root",
      time = Timestamp.valueOf("2020-06-20 20:00:00"),
      nonce = 1,
      bits = "bits",
      difficulty = 3,
      nTx = 0
    )
    val block2: BlocksRow = BlocksRow(
      hash = "hash2",
      confirmations = 1,
      size = 3,
      weight = 1,
      height = 1,
      version = 1,
      merkleRoot = "root",
      time = Timestamp.valueOf("2020-06-20 20:04:00"),
      nonce = 1,
      bits = "bits",
      difficulty = 2,
      nTx = 0
    )
    val block3: BlocksRow = BlocksRow(
      hash = "hash3",
      confirmations = 1,
      size = 2,
      weight = 1,
      height = 1,
      version = 1,
      merkleRoot = "root",
      time = Timestamp.valueOf("2020-06-20 20:05:40"),
      nonce = 1,
      bits = "bits",
      difficulty = 1,
      nTx = 0
    )
    val blocks: Seq[BlocksRow] = List(block1, block2, block3)

    val transaction1: TransactionsRow = TransactionsRow(
      txid = "1",
      blockhash = block1.hash,
      hash = "txhash1",
      size = block1.size,
      weight = block1.weight,
      version = 1,
      confirmations = 1,
      time = block1.time
    )
    val transaction2: TransactionsRow = TransactionsRow(
      txid = "2",
      blockhash = block2.hash,
      hash = "txhash1",
      size = block2.size,
      weight = block2.weight,
      version = 1,
      confirmations = 1,
      time = block2.time
    )
    val transaction3: TransactionsRow = TransactionsRow(
      txid = "3",
      blockhash = block3.hash,
      hash = "txhash1",
      size = block3.size,
      weight = block3.weight,
      version = 1,
      confirmations = 1,
      time = block3.time
    )
    val transactions: Seq[TransactionsRow] = List(transaction1, transaction2, transaction3)

    val input1: InputsRow = InputsRow(txid = "1", sequence = 1, vOut = Some(1))
    val input2: InputsRow = InputsRow(txid = "2", sequence = 2, vOut = Some(1))
    val input3: InputsRow = InputsRow(txid = "3", sequence = 3, vOut = Some(1))
    val inputs: Seq[InputsRow] = List(input1, input2, input3)

    val output1: OutputsRow = OutputsRow(
      txid = "1",
      value = BigDecimal.valueOf(100),
      n = 1,
      scriptPubKeyAsm = "script_pub_asm",
      scriptPubKeyHex = "script_pub_hex",
      scriptPubKeyType = "script_pub_type"
    )
    val output2: OutputsRow = OutputsRow(
      txid = "2",
      value = BigDecimal.valueOf(200),
      n = 2,
      scriptPubKeyAsm = "script_pub_asm",
      scriptPubKeyHex = "script_pub_hex",
      scriptPubKeyType = "script_pub_type"
    )
    val output3: OutputsRow = OutputsRow(
      txid = "3",
      value = BigDecimal.valueOf(300),
      n = 3,
      scriptPubKeyAsm = "script_pub_asm",
      scriptPubKeyHex = "script_pub_hex",
      scriptPubKeyType = "script_pub_type"
    )
    val outputs: Seq[OutputsRow] = List(output1, output2, output3)
  }
}
