package tech.cryptonomic.conseil.indexer.ethereum

import java.sql.Timestamp

import scala.concurrent.ExecutionContext
import cats.effect.{ContextShift, IO}
import fs2.Stream
import slickeffect.Transactor
import org.scalamock.scalatest.MockFactory
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import tech.cryptonomic.conseil.common.config.Platforms.EthereumBatchFetchConfiguration
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.ethereum.{EthereumPersistence, Tables}
import tech.cryptonomic.conseil.common.ethereum.rpc.EthereumClient
import tech.cryptonomic.conseil.indexer.config.{Custom, Everything, Newest}

class EthereumOperationsTest extends AnyWordSpec with MockFactory with Matchers {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Ethereum operations" should {
      "run indexer for the newest blocks" in new EthereumOperationsStubs {
        // Ethereum operations with mocked loadBlocksWithTransactions to test only loadBlocks.
        val ethereumOperationsStub = new EthereumOperationsMock {
          override def loadBlocksWithTransactions(range: Range.Inclusive) =
            ethereumOperationsMock.loadBlocksWithTransactions(range)
        }

        // check if the method gets the latest block at the beginning
        (ethereumPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(Some(blockRow)))

        // mock Bitcoin client calls
        (ethereumClientMock.getMostRecentBlockNumber _) expects () returning (Stream("0xA").covary[IO])

        // test if method runs indexer with the correct range of the blocks
        (ethereumOperationsMock.loadBlocksWithTransactions _) expects (6 to 10) returning (Stream.empty)

        // run
        ethereumOperationsStub.loadBlocksAndLogs(Newest).compile.drain.unsafeRunSync()
      }

      "run indexer for the all blocks" in new EthereumOperationsStubs {
        // Bitcoin operations with mocked loadBlocksWithTransactions to test only loadBlocks.
        val ethereumOperationsStub = new EthereumOperationsMock {
          override def loadBlocksWithTransactions(range: Range.Inclusive) =
            ethereumOperationsMock.loadBlocksWithTransactions(range)
        }

        // check if the method gets the latest block at the beginning
        (ethereumPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(None))

        // mock Bitcoin client calls
        (ethereumClientMock.getMostRecentBlockNumber _) expects () returning (Stream("0xA").covary[IO])

        // test if method runs indexer with the correct range of the blocks
        (ethereumOperationsMock.loadBlocksWithTransactions _) expects (1 to 10) returning (Stream.empty)

        // run
        ethereumOperationsStub.loadBlocksAndLogs(Everything).compile.drain.unsafeRunSync()
      }

      "run indexer for the custom blocks range" in new EthereumOperationsStubs {
        // Bitcoin operations with mocked loadBlocksWithTransactions to test only loadBlocks.
        val ethereumOperationsStub = new EthereumOperationsMock {
          override def loadBlocksWithTransactions(range: Range.Inclusive) =
            ethereumOperationsMock.loadBlocksWithTransactions(range)
        }

        // check if the method gets the latest block at the beginning
        (ethereumPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(Some(blockRow.copy(number = 3))))

        // mock Bitcoin client calls
        (ethereumClientMock.getMostRecentBlockNumber _) expects () returning (Stream("0xA").covary[IO])

        // test if method runs indexer with the correct range of the blocks
        (ethereumOperationsMock.loadBlocksWithTransactions _) expects (7 to 10) returning (Stream.empty)

        // run
        ethereumOperationsStub.loadBlocksAndLogs(Custom(3)).compile.drain.unsafeRunSync()
      }
    }

  /**
    * Stubs that can help to provide tests for the [[EthereumOperations]].
    *
    * Usage example:
    *
    * {{{
    *   "test name" in new EthereumOperationsStubs {
    *     // ethereumOperationsStub is available in the current scope
    *   }
    * }}}
    */
  trait EthereumOperationsStubs {

    val batchConfig = EthereumBatchFetchConfiguration(
      indexerThreadsCount = 1,
      httpFetchThreadsCount = 1,
      blocksBatchSize = 1,
      transactionsBatchSize = 1,
      logsBatchSize = 1
    )

    class EthereumClientMock(rpcClient: RpcClient[IO]) extends EthereumClient[IO](rpcClient)
    class EthereumPersistenceMock extends EthereumPersistence[IO]

    val ethereumClientMock = mock[EthereumClientMock]
    val ethereumPersistenceMock = mock[EthereumPersistenceMock]
    val txMock = mock[Transactor[IO]]

    class EthereumOperationsMock
        extends EthereumOperations[IO](ethereumClientMock, ethereumPersistenceMock, txMock, batchConfig)

    val ethereumOperationsMock = mock[EthereumOperationsMock]

    val blockRow = Tables.BlocksRow(
      hash = "hash",
      number = 5,
      difficulty = "0x1",
      extraData = "0x1",
      gasLimit = "0x1",
      gasUsed = "0x1",
      logsBloom = "0x0",
      miner = "0x1",
      mixHash = "0x1",
      nonce = "0x1",
      parentHash = Some("0x1"),
      receiptsRoot = "0x1",
      sha3Uncles = "0x1",
      size = "0x1",
      stateRoot = "0x1",
      totalDifficulty = "0x1",
      transactionsRoot = "0x1",
      uncles = None,
      timestamp = Timestamp.valueOf("2011-01-10 20:30:40")
    )
  }

}
