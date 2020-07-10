package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import java.sql.Timestamp

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.BitcoinInMemoryDatabaseSetup
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataOperations
import tech.cryptonomic.conseil.common.bitcoin.BitcoinTypes.BitcoinBlockHash
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

        whenReady(sut.fetchBlockByHash(BitcoinBlockHash("hash2"))) { result =>
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

      "return proper transaction by id" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchTransactionById("1")) { result =>
          result.value should contain key "hash"
          result.value("txid") shouldBe Some("1")
        }
      }

      "return proper number of inputs, while fetching all inputs" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Inputs ++= inputs).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchInputs(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper number of outputs, while fetching all outputs" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Outputs ++= outputs).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchOutputs(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper number of accounts, while fetching all of accounts" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Inputs ++= inputs).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Outputs ++= outputs).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchAccounts(Query.empty)) { result =>
          result.value.size shouldBe 3
        }
      }

      "return proper account by address" in {
        // given
        dbHandler.run(Tables.Blocks ++= blocks).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Transactions ++= transactions).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Inputs ++= inputs).isReadyWithin(5.seconds) shouldBe true
        dbHandler.run(Tables.Outputs ++= outputs).isReadyWithin(5.seconds) shouldBe true

        whenReady(sut.fetchAccountByAddress("script_pub_key_address_2")) { result =>
          result.value should (contain key "address" and contain value output2.scriptPubKeyAddresses)
          //TODO Once we will replace BigDecimal into something better, we should compare 'value' field here as well
        }
      }
    }
}

object BitcoinDataQueriesTest {
  trait Fixtures {
    val block1: BlocksRow = BlocksRow(
      hash = "hash1",
      size = 6,
      strippedSize = 6,
      weight = 3,
      height = 3,
      version = 1,
      versionHex = "vhex1",
      merkleRoot = "root",
      nonce = 1,
      bits = "bits",
      difficulty = 3,
      chainWork = "cw1",
      nTx = 0,
      medianTime = Timestamp.valueOf("2020-06-20 20:00:00"),
      time = Timestamp.valueOf("2020-06-20 20:00:00")
    )
    val block2: BlocksRow = BlocksRow(
      hash = "hash2",
      size = 3,
      strippedSize = 6,
      weight = 1,
      height = 1,
      version = 1,
      versionHex = "vhex2",
      merkleRoot = "root",
      nonce = 1,
      bits = "bits",
      difficulty = 2,
      chainWork = "cw2",
      nTx = 0,
      medianTime = Timestamp.valueOf("2020-06-20 20:04:00"),
      time = Timestamp.valueOf("2020-06-20 20:04:00")
    )
    val block3: BlocksRow = BlocksRow(
      hash = "hash3",
      size = 2,
      strippedSize = 6,
      weight = 1,
      height = 1,
      version = 1,
      versionHex = "vhex3",
      merkleRoot = "root",
      nonce = 1,
      bits = "bits",
      difficulty = 1,
      chainWork = "cw3",
      nTx = 0,
      medianTime = Timestamp.valueOf("2020-06-20 20:05:40"),
      time = Timestamp.valueOf("2020-06-20 20:05:40")
    )
    val blocks: Seq[BlocksRow] = List(block1, block2, block3)

    val transaction1: TransactionsRow = TransactionsRow(
      txid = "1",
      blockhash = block1.hash,
      hash = "txhash1",
      hex = "hex1",
      size = block1.size,
      weight = block1.weight,
      vsize = 1,
      version = 1,
      lockTime = block1.time,
      blockTime = block1.time,
      time = block1.time
    )
    val transaction2: TransactionsRow = TransactionsRow(
      txid = "2",
      blockhash = block2.hash,
      hash = "txhash1",
      hex = "hex2",
      size = block2.size,
      weight = block2.weight,
      vsize = 1,
      version = 1,
      lockTime = block2.time,
      blockTime = block2.time,
      time = block2.time
    )
    val transaction3: TransactionsRow = TransactionsRow(
      txid = "3",
      blockhash = block3.hash,
      hash = "txhash1",
      hex = "hex3",
      size = block3.size,
      weight = block3.weight,
      vsize = 1,
      version = 1,
      lockTime = block3.time,
      blockTime = block3.time,
      time = block3.time
    )
    val transactions: Seq[TransactionsRow] = List(transaction1, transaction2, transaction3)

    val input1: InputsRow = InputsRow(txid = "1", sequence = 1, vOut = Some(1), outputTxid = Some("1"))
    val input2: InputsRow = InputsRow(txid = "2", sequence = 2, vOut = Some(2))
    val input3: InputsRow = InputsRow(txid = "3", sequence = 3, vOut = Some(3), outputTxid = Some("3"))
    val inputs: Seq[InputsRow] = List(input1, input2, input3)

    val output1: OutputsRow = OutputsRow(
      txid = "1",
      value = Some(1L),
      n = 1,
      scriptPubKeyAsm = "script_pub_asm_1",
      scriptPubKeyHex = "script_pub_hex_1",
      scriptPubKeyType = "script_pub_type_1",
      scriptPubKeyAddresses = Some("script_pub_key_address_1")
    )
    val output2: OutputsRow = OutputsRow(
      txid = "2",
      value = Some(BigDecimal.valueOf(10.0)),
      n = 2,
      scriptPubKeyAsm = "script_pub_asm_2",
      scriptPubKeyHex = "script_pub_hex_2",
      scriptPubKeyType = "script_pub_type_2",
      scriptPubKeyAddresses = Some("script_pub_key_address_2")
    )
    val output3: OutputsRow = OutputsRow(
      txid = "3",
      value = Some(100L),
      n = 3,
      scriptPubKeyAsm = "script_pub_asm_3",
      scriptPubKeyHex = "script_pub_hex_3",
      scriptPubKeyType = "script_pub_type_3",
      scriptPubKeyAddresses = Some("script_pub_key_address_3")
    )
    val outputs: Seq[OutputsRow] = List(output1, output2, output3)
  }
}
