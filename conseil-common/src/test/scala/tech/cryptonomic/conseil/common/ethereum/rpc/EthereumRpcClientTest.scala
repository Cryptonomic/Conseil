package tech.cryptonomic.conseil.common.ethereum.rpc

import scala.concurrent.ExecutionContext

import cats.effect._
import fs2.Stream

import tech.cryptonomic.conseil.common.ethereum.{EthereumFixtures, EthereumStubs}
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class EthereumRpcClientTest extends ConseilSpec with EthereumFixtures with EthereumStubs {

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
            ethereumClientStub(JsonFixtures.getTransactionByHashResponse).getTransactions(batchSize = 1)
          )
          .compile
          .toList
          .unsafeRunSync() shouldBe List(RpcFixtures.transactionResult)
      }

      "return a transaction receipt for the given transaction" in new EthereumClientStubs {
        Stream(RpcFixtures.transactionResult)
          .through(
            ethereumClientStub(JsonFixtures.getTransactionReceiptResponse).getTransactionReceipt
          )
          .compile
          .toList
          .unsafeRunSync() shouldBe List(RpcFixtures.transactionReceiptResult)
      }

      "return a contract for the given transaction receipt" in new EthereumClientStubs {
        Stream(RpcFixtures.transactionReceiptResult)
          .through(
            ethereumClientStub(JsonFixtures.getCodeResponse).getContract(batchSize = 1)
          )
          .compile
          .toList
          .unsafeRunSync() shouldBe List(RpcFixtures.contractResult)
      }

      "return a token info for the given contract" in new EthereumClientStubs {
        Stream(RpcFixtures.contractResult)
          .through(
            ethereumClientStub(JsonFixtures.callResponse).getTokenInfo
          )
          .compile
          .toList
          .unsafeRunSync() shouldBe List(RpcFixtures.tokenResult)
      }

    }

}
