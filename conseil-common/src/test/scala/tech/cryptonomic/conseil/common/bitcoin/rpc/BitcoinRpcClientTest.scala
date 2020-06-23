package tech.cryptonomic.conseil.common.bitcoin.rpc

import scala.concurrent.ExecutionContext

import cats.effect._
import org.http4s._
import org.http4s.client.Client
import fs2.Stream
import org.scalatest.{Matchers, WordSpec}

import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.BitcoinFixtures

class BitcoinRpcClientTest extends WordSpec with Matchers with BitcoinFixtures {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin JSON-RPC client" should {

      "return a blockchain info" in new BitcoinClientStubs {
        bitcoinClientStub(JsonFixtures.getBlockchainInfoResponse).getBlockChainInfo.compile.toList
          .unsafeRunSync() shouldBe List(
              RpcFixtures.blockchainInfoResult
            )
      }

      "return a block hash for the given height" in new BitcoinClientStubs {
        Stream(102000)
          .through(bitcoinClientStub(JsonFixtures.getBlockHashResponse).getBlockHash(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List("00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58")
      }

      "return a block for the given hash" in new BitcoinClientStubs {
        Stream("00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58")
          .through(bitcoinClientStub(JsonFixtures.getBlockResponse).getBlockByHash(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List(RpcFixtures.blockResult)
      }

      "return a transactions (with imputs and outputs) for the given block" in new BitcoinClientStubs {
        Stream(RpcFixtures.blockResult)
          .through(bitcoinClientStub(JsonFixtures.getRawTransactionResponse).getBlockWithTransactions(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List(
              (
                RpcFixtures.blockResult,
                List(
                  RpcFixtures.transactionResult.copy(
                    vin = List(RpcFixtures.inputWithTxidResult),
                    vout = List(RpcFixtures.outputWithTxidResult)
                  )
                )
              )
            )
      }

    }

  /**
    * Stubs that can help to provide tests for the [[BitcoinClient]].
    * 
    * Usage example:
    * 
    * {{{
    *   "test name" in new BitcoinClientStubs {
    *     // bitcoinClientStub is available in the current scope
    *   }
    * }}}
    */
  trait BitcoinClientStubs {

    def bitcoinClientStub(jsonResponse: String): BitcoinClient[IO] = {
      val response = Response[IO](
        Status.Ok,
        body = Stream(jsonResponse).through(fs2.text.utf8Encode)
      )
      val rpcClient =
        new RpcClient[IO]("https://api-endpoint.com", 1, Client.fromHttpApp(HttpApp.liftF(IO.pure(response))))
      new BitcoinClient(rpcClient)
    }
  }

}
