package tech.cryptonomic.conseil.indexer.bitcoin

import scala.concurrent.ExecutionContext

import cats.effect.{ContextShift, IO}
import fs2.Stream
import slickeffect.Transactor
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.Matchers
import org.scalamock.scalatest.MockFactory

import tech.cryptonomic.conseil.indexer.config.{Custom, Everything, Newest}
import tech.cryptonomic.conseil.common.config.Platforms.BitcoinBatchFetchConfiguration
import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.Tables
import tech.cryptonomic.conseil.common.bitcoin.{BitcoinFixtures, BitcoinInMemoryDatabaseSetup, BitcoinStubs}
import tech.cryptonomic.conseil.common.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.common.bitcoin.rpc.json.BlockchainInfo
import tech.cryptonomic.conseil.common.bitcoin.BitcoinPersistence
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase

class BitcoinOperationsTest
    extends WordSpec
    with MockFactory
    with Matchers
    with InMemoryDatabase
    with BitcoinInMemoryDatabaseSetup
    with BitcoinFixtures
    with BitcoinStubs {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin operations" should {
      "run indexer for the newest blocks" in new BitcoinOperationsStubs {
        // check if the method gets the latest block at the beginning
        (bitcoinPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(
              Some(DbFixtures.blockRow.copy(height = 5))
            ))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockChainInfo _) expects () returning (Stream(BlockchainInfo("mainnet", 10)).covary[IO])

        // test if method runs indexer with the correct range of the blocks
        (dummyBitcoinOperationsMock.loadBlocksWithTransactions _) expects (6 to 10) returning (Stream.empty)

        dummyBitcoinOperationsStub.loadBlocks(Newest).compile.drain.unsafeRunSync() shouldBe (())
      }

      "run indexer for the all blocks" in new BitcoinOperationsStubs {
        // check if the method gets the latest block at the beginning
        (bitcoinPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(None))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockChainInfo _) expects () returning (Stream(BlockchainInfo("mainnet", 10)).covary[IO])

        // test if method runs indexer with the correct range of the blocks
        (dummyBitcoinOperationsMock.loadBlocksWithTransactions _) expects (1 to 10) returning (Stream.empty)

        dummyBitcoinOperationsStub.loadBlocks(Everything).compile.drain.unsafeRunSync() shouldBe (())
      }

      "run indexer for the custom blocks range" in new BitcoinOperationsStubs {
        // check if the method gets the latest block at the beginning
        (bitcoinPersistenceMock.getLatestIndexedBlock _) expects ()
        (txMock.transact[Option[Tables.BlocksRow]] _) expects (*) returning (IO(
              Some(DbFixtures.blockRow.copy(height = 3))
            ))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockChainInfo _) expects () returning (Stream(BlockchainInfo("mainnet", 10)).covary[IO])

        // test if method runs indexer with the correct range of the blocks
        (dummyBitcoinOperationsMock.loadBlocksWithTransactions _) expects (7 to 10) returning (Stream.empty)

        dummyBitcoinOperationsStub.loadBlocks(Custom(3)).compile.drain.unsafeRunSync() shouldBe (())
      }

      "index blocks with transactions with the given range" in new BitcoinOperationsStubs {
        // check if the method gets existing blocks at the beginning
        (bitcoinPersistenceMock.getExistingBlocks _) expects (1 to 10)
        (txMock.transact[Seq[Int]] _) expects (*) returning (IO((1 to 10).toVector))

        // mock Bitcoin client calls
        (bitcoinClientMock.getBlockHash _) expects (1) returning (_ => Stream("hash").covary[IO])
        (bitcoinClientMock.getBlockByHash _) expects (1) returning (_ => Stream(RpcFixtures.blockResult).covary[IO])
        (bitcoinClientMock.getBlockWithTransactions _) expects (1) returning (
                _ => Stream((RpcFixtures.blockResult, List(RpcFixtures.transactionResult))).covary[IO]
            )

        // check if the method saves data to the database
        (bitcoinPersistenceMock.createBlock _) expects (*, *)
        (txMock.transact[Unit] _) expects (*) returning (IO.unit)

        bitcoinOperationsStub.loadBlocksWithTransactions(1 to 10).compile.drain.unsafeRunSync() shouldBe (())
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

    class DummyBitcoinOperationsMock
        extends BitcoinOperations[IO](bitcoinClientMock, bitcoinPersistenceMock, txMock, batchConfig)

    val dummyBitcoinOperationsMock = mock[DummyBitcoinOperationsMock]

    def bitcoinOperationsStub: BitcoinOperations[IO] =
      new BitcoinOperations(
        bitcoinClient = bitcoinClientMock,
        persistence = bitcoinPersistenceMock,
        tx = txMock,
        batchConf = batchConfig
      )

    // Bitcoin Operations with mocked `loadBlocksWithTransactions`
    def dummyBitcoinOperationsStub: BitcoinOperations[IO] =
      new BitcoinOperations(
        bitcoinClient = bitcoinClientMock,
        persistence = bitcoinPersistenceMock,
        tx = txMock,
        batchConf = batchConfig
      ) {
        override def loadBlocksWithTransactions(range: Range.Inclusive) =
          dummyBitcoinOperationsMock.loadBlocksWithTransactions(range)
      }
  }

}
