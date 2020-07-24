package tech.cryptonomic.conseil.common.ethereum.rpc

import scala.concurrent.ExecutionContext

import cats.effect._
import fs2.Stream
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

import tech.cryptonomic.conseil.common.ethereum.{EthereumFixtures, EthereumStubs}

class EthereumRpcClientTest extends AnyWordSpec with Matchers with EthereumFixtures with EthereumStubs {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Ethereum JSON-RPC client" should {

      "return a number of most recent block" in new EthereumClientStubs {
        ethereumClientStub(JsonFixtures.ethBlockNumberResponse).getMostRecentBlockNumber.compile.toList
          .unsafeRunSync() shouldBe List(
              "0x4b7"
            )
      }

      "return a block for the given number" in new EthereumClientStubs {
        Stream("1")
          .through(ethereumClientStub(JsonFixtures.getBlockResponse).getBlockByNumber(batchSize = 1))
          .compile
          .toList
          .unsafeRunSync() shouldBe List(RpcFixtures.blockResult)
      }

      "return a transactions for the given block" in new EthereumClientStubs {
        Stream(RpcFixtures.blockResult)
          .through(
            ethereumClientStub(JsonFixtures.getTransactionByHashResponse).getBlockWithTransactions(batchSize = 1)
          )
          .compile
          .toList
          .unsafeRunSync() shouldBe List((RpcFixtures.blockResult, List(RpcFixtures.transactionResult)))
      }

    }

}
