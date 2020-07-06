package tech.cryptonomic.conseil.indexer.bitcoin

import scala.concurrent.ExecutionContext

import cats.effect.{ContextShift, IO}
import slick.jdbc.PostgresProfile.api._
import slick.dbio.DBIO
import org.scalatest.{Matchers, WordSpec}
import org.scalatest.Matchers
import org.scalamock.scalatest.MockFactory

import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.bitcoin.{BitcoinFixtures, BitcoinInMemoryDatabaseSetup, BitcoinStubs}
import tech.cryptonomic.conseil.common.bitcoin.Tables
import tech.cryptonomic.conseil.common.bitcoin.BitcoinPersistence._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.common.bitcoin.BitcoinPersistence
import slickeffect.Transactor
import tech.cryptonomic.conseil.common.config.Platforms.BitcoinBatchFetchConfiguration
import tech.cryptonomic.conseil.common.rpc.RpcClient

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
      // "..." in new BitcoinPersistenceStubs(dbHandler) with BitcoinOperationsStubs { 
      // }
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

    class BitcoinClientMock(rpcClient: RpcClient[IO]) extends BitcoinClient[IO](rpcClient)
    class BitcoinPersistenceMock extends BitcoinPersistence[IO]

    val bitcoinClientMock = mock[BitcoinClientMock]
    val bitcoinPersistenceMock = mock[BitcoinPersistenceMock]
    val txMock = mock[Transactor[IO]]

    def bitcoinOperationsStub: BitcoinOperations[IO] =
      new BitcoinOperations(
        bitcoinClient = bitcoinClientMock,
        persistence = bitcoinPersistenceMock,
        tx = txMock,
        batchConf = BitcoinBatchFetchConfiguration(
          indexerThreadsCount = 1,
          httpFetchThreadsCount = 1,
          hashBatchSize = 1,
          blocksBatchSize = 1,
          transactionsBatchSize = 1
        )
      )
  }

}
