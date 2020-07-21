package tech.cryptonomic.conseil.indexer.bitcoin

import java.sql.Timestamp

import scala.concurrent.ExecutionContext
import cats.effect.{ContextShift, IO}
import fs2.Stream
import slickeffect.Transactor
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import tech.cryptonomic.conseil.common.config.Platforms.BitcoinBatchFetchConfiguration
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.{BitcoinPersistence, Tables}
import tech.cryptonomic.conseil.common.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.common.bitcoin.rpc.json.BlockchainInfo
import tech.cryptonomic.conseil.indexer.config.{Custom, Everything, Newest}

class BitcoinOperationsTest extends AnyWordSpec with MockFactory with Matchers {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin operations" should {
      "run indexer for the newest blocks" in new BitcoinOperationsStubs {
        // Bitcoin operations with mocked loadBlocksWithTransactions to test only loadBlocks.
        val bitcoinOperationsStub = new BitcoinOperationsMock {
          override def loadBlocksWithTransactions(range: Range.Inclusive) =
            bitcoinOperationsMock.loadBlocksWithTransactions(range)
        }

        // check if the method gets the latest block at the beginning
        (bitcoinPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(Some(blockRow)))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockChainInfo _) expects () returning (Stream(BlockchainInfo("mainnet", 10)).covary[IO])

        // test if method runs indexer with the correct range of the blocks
        (bitcoinOperationsMock.loadBlocksWithTransactions _) expects (6 to 10) returning (Stream.empty)

        // run
        bitcoinOperationsStub.loadBlocks(Newest).compile.drain.unsafeRunSync()
      }

      "run indexer for the all blocks" in new BitcoinOperationsStubs {
        // Bitcoin operations with mocked loadBlocksWithTransactions to test only loadBlocks.
        val bitcoinOperationsStub = new BitcoinOperationsMock {
          override def loadBlocksWithTransactions(range: Range.Inclusive) =
            bitcoinOperationsMock.loadBlocksWithTransactions(range)
        }

        // check if the method gets the latest block at the beginning
        (bitcoinPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(None))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockChainInfo _) expects () returning (Stream(BlockchainInfo("mainnet", 10)).covary[IO])

        // test if method runs indexer with the correct range of the blocks
        (bitcoinOperationsMock.loadBlocksWithTransactions _) expects (1 to 10) returning (Stream.empty)

        // run
        bitcoinOperationsStub.loadBlocks(Everything).compile.drain.unsafeRunSync()
      }

      "run indexer for the custom blocks range" in new BitcoinOperationsStubs {
        // Bitcoin operations with mocked loadBlocksWithTransactions to test only loadBlocks.
        val bitcoinOperationsStub = new BitcoinOperationsMock {
          override def loadBlocksWithTransactions(range: Range.Inclusive) =
            bitcoinOperationsMock.loadBlocksWithTransactions(range)
        }

        // check if the method gets the latest block at the beginning
        (bitcoinPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(Some(blockRow.copy(height = 3))))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockChainInfo _) expects () returning (Stream(BlockchainInfo("mainnet", 10)).covary[IO])

        // test if method runs indexer with the correct range of the blocks
        (bitcoinOperationsMock.loadBlocksWithTransactions _) expects (7 to 10) returning (Stream.empty)

        // run
        bitcoinOperationsStub.loadBlocks(Custom(3)).compile.drain.unsafeRunSync()
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

    val bitcoinOperationsMock = mock[BitcoinOperationsMock]

    val blockRow = Tables.BlocksRow(
      hash = "hash",
      size = 1,
      strippedSize = 1,
      weight = 1,
      height = 5,
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
