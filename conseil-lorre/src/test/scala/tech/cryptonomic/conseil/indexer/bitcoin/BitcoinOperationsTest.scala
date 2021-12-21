package tech.cryptonomic.conseil.indexer.bitcoin

import java.sql.Timestamp

import cats.effect.IO
import fs2.Stream
import slickeffect.Transactor
import org.scalamock.scalatest.MockFactory
import tech.cryptonomic.conseil.common.config.Platforms.BitcoinBatchFetchConfiguration
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.{BitcoinPersistence, Tables}
import tech.cryptonomic.conseil.common.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.common.bitcoin.rpc.json.BlockchainInfo
import tech.cryptonomic.conseil.indexer.config.{Custom, Everything, Newest}
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

import cats.effect.unsafe.implicits.global

class BitcoinOperationsTest extends ConseilSpec with MockFactory {

  /* We can't currently mock the BitcoinOperations right now, because mixing-in ConseilLogSupport brings in
   * a `logger` attribute that is incompatible with scalamock capabilities.
   * We remedy by implementing it via mock dependencies and overrides of methods that needs to be predictable.
   */

  "Bitcoin operations" should {
      "run indexer for the newest blocks" in new BitcoinOperationsStubs {
        // test if method runs indexer with the correct range of the blocks
        val bitcoinOperationsStub = new BitcoinOperationsMock {
          override def loadBlocksWithTransactions(range: Range.Inclusive) = {
            range shouldBe (6 to 10)
            Stream.empty
          }
        }
        // check if the method gets the latest block at the beginning
        (bitcoinPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(Some(blockRow)))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockChainInfo _) expects () returning (Stream(BlockchainInfo("mainnet", 10)).covary[IO])

        // run
        bitcoinOperationsStub.loadBlocks(Newest, None).compile.drain.unsafeRunSync()
      }

      "run indexer for the all blocks" in new BitcoinOperationsStubs {
        // test if method runs indexer with the correct range of the blocks
        val bitcoinOperationsStub = new BitcoinOperationsMock {
          override def loadBlocksWithTransactions(range: Range.Inclusive) = {
            range shouldBe (1 to 10)
            Stream.empty
          }
        }
        // check if the method gets the latest block at the beginning
        (bitcoinPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(None))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockChainInfo _) expects () returning (Stream(BlockchainInfo("mainnet", 10)).covary[IO])

        // run
        bitcoinOperationsStub.loadBlocks(Everything, None).compile.drain.unsafeRunSync()
      }

      "run indexer for the custom blocks range" in new BitcoinOperationsStubs {
        // test if method runs indexer with the correct range of the blocks
        val bitcoinOperationsStub = new BitcoinOperationsMock {
          override def loadBlocksWithTransactions(range: Range.Inclusive) = {
            range shouldBe (7 to 10)
            Stream.empty
          }
        }

        // check if the method gets the latest block at the beginning
        (bitcoinPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(Some(blockRow.copy(level = 3))))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockChainInfo _) expects () returning (Stream(BlockchainInfo("mainnet", 10)).covary[IO])

        // run
        bitcoinOperationsStub.loadBlocks(Custom(3), None).compile.drain.unsafeRunSync()
      }
    }

  /**
    * Stubs that can help to provide tests for the [[BitcoinOperations]].
    *
    * Usage example:
    *
    * {{{
    *   "test name" in new BitcoinOperationsStubs {
    *     // bitcoinOperationsStub is available in the current scope
    *   }
    * }}}
    */
  trait BitcoinOperationsStubs {

    val batchConfig = BitcoinBatchFetchConfiguration(
      indexerThreadsCount = 1,
      httpFetchThreadsCount = 1,
      hashBatchSize = 1,
      blocksBatchSize = 1,
      transactionsBatchSize = 1
    )

    class BitcoinClientMock(rpcClient: RpcClient[IO]) extends BitcoinClient[IO](rpcClient)
    class BitcoinPersistenceMock extends BitcoinPersistence[IO]

    val bitcoinClientMock = mock[BitcoinClientMock]
    val bitcoinPersistenceMock = mock[BitcoinPersistenceMock]
    val txMock = mock[Transactor[IO]]

    class BitcoinOperationsMock
        extends BitcoinOperations[IO](bitcoinClientMock, bitcoinPersistenceMock, txMock, batchConfig)

    val blockRow = Tables.BlocksRow(
      hash = "hash",
      size = 1,
      strippedSize = 1,
      weight = 1,
      level = 5,
      version = 1,
      versionHex = "00000001",
      merkleRoot = "merkleRoot",
      nonce = 1L,
      bits = "1",
      difficulty = 1.0,
      chainWork = "0",
      nTx = 0,
      previousBlockHash = None,
      nextBlockHash = None,
      time = Timestamp.valueOf("2011-01-10 20:39:40"),
      medianTime = Timestamp.valueOf("2011-01-10 20:30:40")
    )
  }

}
