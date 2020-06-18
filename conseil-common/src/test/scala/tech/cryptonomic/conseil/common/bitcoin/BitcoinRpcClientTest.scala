package tech.cryptonomic.conseil.common.bitcoin.rpc

import scala.concurrent.ExecutionContext

import cats.effect._
import org.http4s._
import org.http4s.client.Client
import fs2.Stream
import org.scalatest.{Matchers, WordSpec}

import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.rpc.json._

class BitcoinRpcClientTest extends WordSpec with Matchers {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin JSON-RPC client" should {

      "return a block hash for the given level" in {
        val response = """[{
        |  "result": "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
        |  "id": "requestId"
        |}]""".stripMargin

        Stream(102000)
          .through(bitcoinClient(response).getBlockHash(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List("00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58")
      }

      "return a block for the given hash" in {
        val response = """[{
        |  "result": {
        |    "hash": "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
        |    "confirmations": 533285,
        |    "strippedsize": 215,
        |    "size": 215,
        |    "weight": 860,
        |    "height": 102000,
        |    "version": 1,
        |    "versionHex": "00000001",
        |    "merkleroot": "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
        |    "tx": [
        |      "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01"
        |     ],
        |     "time": 1294691980,
        |     "mediantime": 1294691440,
        |     "nonce": 3851572992,
        |     "bits": "1b0404cb",
        |     "difficulty": 16307.42093852398,
        |     "chainwork": "00000000000000000000000000000000000000000000000008203c8509509ae0",
        |     "nTx": 1,
        |     "previousblockhash": "0000000000038b80cf5db1173e96f2290cfda12c505b0fe1bd37d6975e164a8a",
        |     "nextblockhash": "0000000000035107dce8eb675c6fa9a08c7617c109b3553ad8f208dda24065a6"
        |  },
        |  "id": "requestId"
        |}]""".stripMargin

        Stream("00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58")
          .through(bitcoinClient(response).getBlockByHash(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List(block)
      }

      "return a transactions (with imputs and outputs) for the given block" in {
        val response =
          """[{
        |  "result": {
        |    "txid": "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
        |    "hash": "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
        |    "version": 1,
        |    "size": 134,
        |    "vsize": 134,
        |    "weight": 536,
        |    "locktime": 0,
        |    "vin": [
        |      {
        |        "coinbase": "04cb04041b0166",
        |        "sequence": 4294967295
        |      }
        |    ],
        |    "vout": [
        |      {
        |        "value": 50.00000000,
        |        "n": 0,
        |        "scriptPubKey": {
        |          "asm": "0476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332 OP_CHECKSIG",
        |          "hex": "410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac",
        |          "type": "pubkey"
        |        }
        |      }
        |    ],
        |    "hex": "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0704cb04041b0166ffffffff0100f2052a0100000043410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac00000000",
        |    "blockhash": "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
        |    "confirmations": 533285,
        |    "time": 1294691980,
        |    "blocktime": 1294691980
        |  },
        |  "id": "requestId"
        |}]""".stripMargin

        Stream(block)
          .through(bitcoinClient(response).getTransactionsFromBlock(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List(transaction)

        Stream(transaction)
          .through(bitcoinClient(response).getTransactionComponents)
          .compile
          .toList
          .unsafeRunSync() shouldBe List(
          input.copy(txid = Some("d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01")),
          output.copy(txid = Some("d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01"))
        )
      }

    }

  private val block = Block(
    hash = "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
    size = 215,
    strippedsize = 215,
    weight = 860,
    height = 102000,
    version = 1,
    versionHex = "00000001",
    merkleroot = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
    nonce = 3851572992L,
    bits = "1b0404cb",
    difficulty = 16307.42093852398,
    chainwork = "00000000000000000000000000000000000000000000000008203c8509509ae0",
    nTx = 1,
    previousblockhash = Some("0000000000038b80cf5db1173e96f2290cfda12c505b0fe1bd37d6975e164a8a"),
    nextblockhash = Some("0000000000035107dce8eb675c6fa9a08c7617c109b3553ad8f208dda24065a6"),
    time = 1294691980,
    mediantime = 1294691440,
    tx = List("d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01")
  )

  private val input = TransactionInput(
    txid = None,
    vout = None,
    scriptSig = None,
    sequence = 4294967295L,
    coinbase = Some("04cb04041b0166"),
    txinwitness = None
  )

  private val output = TransactionOutput(
    txid = None,
    value = Some(50.0),
    n = 0,
    scriptPubKey = ScriptPubKey(
      asm =
        "0476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332 OP_CHECKSIG",
      hex =
        "410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac",
      reqSigs = None,
      `type` = "pubkey",
      addresses = None
    )
  )

  private val transaction = Transaction(
    txid = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
    blockhash = "00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58",
    hash = "d6118e27502a9b239c4519351c765667c86b1a4f8ff7592c367e88e4dac63e01",
    hex =
      "01000000010000000000000000000000000000000000000000000000000000000000000000ffffffff0704cb04041b0166ffffffff0100f2052a0100000043410476b42b3f2ff4ca18f71d6bd30de5e52cc055cc9f798c39d32e391ec227548fd1bd5104d07a7e1a443f01afb9781d64126fd3571f9c0d93747b30eeddd71a5332ac00000000",
    size = 134,
    vsize = 134,
    weight = 536,
    version = 1,
    time = 1294691980,
    locktime = 0,
    blocktime = 1294691980,
    vin = List(input),
    vout = List(output)
  )

  private def bitcoinClient(json: String): BitcoinClient[IO] = {
    val response = Response[IO](
      Status.Ok,
      body = Stream(json).through(fs2.text.utf8Encode)
    )
    val rpcClient =
      new RpcClient[IO]("https://api-endpoint.com", 1, Client.fromHttpApp(HttpApp.liftF(IO.pure(response))))
    new BitcoinClient(rpcClient)
  }

}
