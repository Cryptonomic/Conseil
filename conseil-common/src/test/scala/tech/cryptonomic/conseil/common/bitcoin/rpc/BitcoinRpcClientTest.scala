package tech.cryptonomic.conseil.common.bitcoin.rpc

import fs2.Stream
import tech.cryptonomic.conseil.common.bitcoin.{BitcoinFixtures, BitcoinStubs}
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

import cats.effect.unsafe.implicits.global

class BitcoinRpcClientTest extends ConseilSpec with BitcoinFixtures with BitcoinStubs {

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
          .unsafeRunSync() shouldBe List((RpcFixtures.blockResult, List(RpcFixtures.transactionResult)))
      }

    }

}
