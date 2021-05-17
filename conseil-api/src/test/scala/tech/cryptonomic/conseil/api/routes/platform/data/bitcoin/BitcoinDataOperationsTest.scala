package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import java.sql.Timestamp

import org.scalatest.concurrent.IntegrationPatience
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.BitcoinInMemoryDatabaseSetup
import tech.cryptonomic.conseil.common.bitcoin.BitcoinTypes.BitcoinBlockHash
import tech.cryptonomic.conseil.common.bitcoin.Tables
import tech.cryptonomic.conseil.common.bitcoin.Tables._
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.common.testkit.{ConseilSpec, InMemoryDatabase}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._

class BitcoinDataOperationsTest
    extends ConseilSpec
    with InMemoryDatabase
    with BitcoinInMemoryDatabaseSetup
    with ConseilLogSupport
    with IntegrationPatience
    with BitcoinDataOperationsTest.Fixtures {

  "BitcoinDataOperations" should {
      val sut = new BitcoinDataOperations {
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

        whenReady(sut.fetchBlockByHash(BitcoinBlockHash("hash2"))) { result =>
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
          result.value should (contain key "value" and contain value output2.value.map(convertAndScale(_, 2)))

          // Since we are getting data for QueryResponse in generic way,
          // we are accessing Java API which returns java data types.
          // We need to convert and adjust scale for what we are receiving from Slick,
          // because ScalaTest uses `==`, instead od `compareTo` for this type.
          def convertAndScale(v: BigDecimal, s: Int): java.math.BigDecimal = v.bigDecimal.setScale(s)
        }
      }
    }
}

object BitcoinDataOperationsTest {
  trait Fixtures {
    private val defaultBlock = BlocksRow(
      hash = "hash",
      size = 0,
      strippedSize = 0,
      weight = 0,
      height = 0,
      version = 1,
      versionHex = "vhex",
      merkleRoot = "root",
      nonce = 0,
      bits = "bits",
      difficulty = 0,
      chainWork = "cw",
      nTx = 0,
      medianTime = Timestamp.valueOf("2020-01-01 00:00:00"),
      time = Timestamp.valueOf("2020-01-01 00:00:00")
    )
    val block1: BlocksRow = defaultBlock.copy(
      hash = "hash1",
      medianTime = Timestamp.valueOf("2020-06-20 20:00:00"),
      time = Timestamp.valueOf("2020-06-20 20:00:00")
    )
    val block2: BlocksRow = defaultBlock.copy(
      hash = "hash2",
      medianTime = Timestamp.valueOf("2020-06-20 20:04:00"),
      time = Timestamp.valueOf("2020-06-20 20:04:00")
    )
    val block3: BlocksRow = defaultBlock.copy(
      hash = "hash3",
      medianTime = Timestamp.valueOf("2020-06-20 20:05:40"),
      time = Timestamp.valueOf("2020-06-20 20:05:40")
    )
    val blocks: Seq[BlocksRow] = List(block1, block2, block3)

    private val defaultTransaction: BlocksRow => TransactionsRow =
      block =>
        TransactionsRow(
          txid = "0",
          blockhash = block.hash,
          blockHeight = block.height,
          hash = "txhash0",
          hex = "hex",
          size = block.size,
          weight = block.weight,
          vsize = 1,
          version = 1,
          lockTime = block.time,
          blockTime = block.time,
          time = block.time
        )
    val transaction1: TransactionsRow = defaultTransaction(block1).copy(txid = "1", hash = "txhash1")
    val transaction2: TransactionsRow = defaultTransaction(block2).copy(txid = "2", hash = "txhash1")
    val transaction3: TransactionsRow = defaultTransaction(block3).copy(txid = "3", hash = "txhash1")
    val transactions: Seq[TransactionsRow] = List(transaction1, transaction2, transaction3)

    private val defaultInput: BlocksRow => InputsRow =
      block =>
        InputsRow(
          txid = "0",
          blockhash = block.hash,
          blockHeight = block.height,
          blockTime = block.time,
          sequence = 0,
          vOut = Some(0),
          outputTxid = Some("0")
        )
    val input1: InputsRow = defaultInput(block1).copy(txid = "1", sequence = 1, vOut = Some(1), outputTxid = Some("1"))
    val input2: InputsRow = defaultInput(block1).copy(txid = "2", sequence = 2, vOut = Some(2))
    val input3: InputsRow = defaultInput(block1).copy(txid = "3", sequence = 3, vOut = Some(3), outputTxid = Some("3"))
    val inputs: Seq[InputsRow] = List(input1, input2, input3)

    private val defaultOutput: BlocksRow => OutputsRow =
      block =>
        OutputsRow(
          txid = "0",
          blockhash = block.hash,
          blockHeight = block.height,
          blockTime = block.time,
          value = Some(0L),
          n = 0,
          scriptPubKeyAsm = "script_pub_asm_0",
          scriptPubKeyHex = "script_pub_hex_0",
          scriptPubKeyType = "script_pub_type_0",
          scriptPubKeyAddresses = Some("script_pub_key_address_0")
        )

    val output1: OutputsRow = defaultOutput(block1).copy(
      txid = "1",
      value = Some(BigDecimal.valueOf(1.0)),
      n = 1,
      scriptPubKeyAsm = "script_pub_asm_1",
      scriptPubKeyHex = "script_pub_hex_1",
      scriptPubKeyType = "script_pub_type_1",
      scriptPubKeyAddresses = Some("script_pub_key_address_1")
    )
    val output2: OutputsRow = defaultOutput(block1).copy(
      txid = "2",
      value = Some(BigDecimal.valueOf(10.0)),
      n = 2,
      scriptPubKeyAsm = "script_pub_asm_2",
      scriptPubKeyHex = "script_pub_hex_2",
      scriptPubKeyType = "script_pub_type_2",
      scriptPubKeyAddresses = Some("script_pub_key_address_2")
    )
    val output3: OutputsRow = defaultOutput(block1).copy(
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
