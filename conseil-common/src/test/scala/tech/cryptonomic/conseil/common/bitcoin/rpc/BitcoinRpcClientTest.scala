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

      "return a block hash for the given height" in new BitcoinClientFixtures {
        Stream(102000)
          .through(bitcoinClient(JsonFixtures.getBlockHash).getBlockHash(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List("00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58")
      }

      "return a block for the given hash" in new BitcoinClientFixtures {
        Stream("00000000000335c47dd6ae953912d172a4d9839355f2083165043bb6f43c2f58")
          .through(bitcoinClient(JsonFixtures.getBlock).getBlockByHash(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List(RpcFixtures.block)
      }

      "return a transactions (with imputs and outputs) for the given block" in new BitcoinClientFixtures {
        Stream(RpcFixtures.block)
          .through(bitcoinClient(JsonFixtures.getRawTransaction).getTransactionsFromBlock(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List(RpcFixtures.transaction)

        Stream(RpcFixtures.transaction)
          .through(bitcoinClient(JsonFixtures.getRawTransaction).getTransactionComponents)
          .compile
          .toList
          .unsafeRunSync() shouldBe List(
              RpcFixtures.inputWithTxid,
              RpcFixtures.outputWithTxid
            )
      }

    }

  trait BitcoinClientFixtures {

    def bitcoinClient(json: String): BitcoinClient[IO] = {
      val response = Response[IO](
        Status.Ok,
        body = Stream(json).through(fs2.text.utf8Encode)
      )
      val rpcClient =
        new RpcClient[IO]("https://api-endpoint.com", 1, Client.fromHttpApp(HttpApp.liftF(IO.pure(response))))
      new BitcoinClient(rpcClient)
    }
  }

}
