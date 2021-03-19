package tech.cryptonomic.conseil.common.ethereum

import scala.concurrent.ExecutionContext

import cats.effect._
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.ethereum.EthereumPersistence._
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class EthereumPersistenceTest
    extends ConseilSpec
    with InMemoryDatabase
    with EthereumInMemoryDatabaseSetup
    with EthereumFixtures
    with EthereumStubs {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Ethereum persistence" should {
      "save block from the JSON-RPC response" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          result <- tx.transact(Tables.Blocks.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.blockRow)
      }

      "save transaction from the JSON-RPC response" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(Tables.Transactions += RpcFixtures.transactionResult.convertTo[Tables.TransactionsRow])
          result <- tx.transact(Tables.Transactions.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.transactionRow)
      }

      "save log from the JSON-RPC response" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(Tables.Logs += RpcFixtures.logResult.convertTo[Tables.LogsRow])
          result <- tx.transact(Tables.Logs.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.logRow)
      }

      "save transaction receipt from the JSON-RPC response" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(Tables.Receipts += RpcFixtures.transactionReceiptResult.convertTo[Tables.ReceiptsRow])
          result <- tx.transact(Tables.Receipts.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.transactionReceiptRow)
      }

      "save token transfer from the log JSON-RPC response" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(ethereumPersistenceStub.createTokenTransfers(List(RpcFixtures.tokenTransferResult)))
          result <- tx.transact(Tables.TokenTransfers.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.tokenTransferRow)
      }

      "save token balances from the log JSON-RPC response" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(ethereumPersistenceStub.createTokenBalances(List(RpcFixtures.tokenBalanceFromResult)))
          result <- tx.transact(Tables.TokensHistory.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.tokenBalanceFromRow)
      }
      "save contract from the JSON-RPC response" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(ethereumPersistenceStub.createContracts(List(RpcFixtures.contractResult)))
          result <- tx.transact(Tables.Contracts.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.contractRow)
      }

      "save token from the JSON-RPC response" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(ethereumPersistenceStub.createTokens(List(RpcFixtures.tokenResult)))
          result <- tx.transact(Tables.Tokens.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.tokenRow)
      }

      "save block with transactions using persistence (integration test)" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // run
          _ <- tx.transact(
            ethereumPersistenceStub
              .createBlock(
                RpcFixtures.blockResult,
                List(RpcFixtures.transactionResult),
                List(RpcFixtures.transactionReceiptResult)
              )
          )
          // test results
          block <- tx.transact(Tables.Blocks.result)
          transactions <- tx.transact(Tables.Transactions.result)
          receipts <- tx.transact(Tables.Receipts.result)
        } yield block ++ transactions ++ receipts).unsafeRunSync() shouldBe Vector(
              DbFixtures.blockRow,
              DbFixtures.transactionRow,
              DbFixtures.transactionReceiptRow
            )
      }

      "return existing blocks" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // create blocks
          _ <- tx.transact(
            Tables.Blocks ++= List(
                  RpcFixtures.blockResult.copy(hash = "hash1", number = "0x1"),
                  RpcFixtures.blockResult.copy(hash = "hash2", number = "0x2"),
                  RpcFixtures.blockResult.copy(hash = "hash3", number = "0x3")
                ).map(_.convertTo[Tables.BlocksRow])
          )
          // test results
          result <- tx.transact(ethereumPersistenceStub.getIndexedBlockHeights(1 to 2))
        } yield result).unsafeRunSync() shouldBe Vector(1, 2)
      }

      "return the latest block in a height range" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // create blocks
          _ <- tx.transact(
            Tables.Blocks ++= List(
                  RpcFixtures.blockResult.copy(hash = "hash1", number = "0x1"),
                  RpcFixtures.blockResult.copy(hash = "hash2", number = "0x2")
                ).map(_.convertTo[Tables.BlocksRow])
          )
          // test results
          result <- tx.transact(ethereumPersistenceStub.getLatestIndexedBlock)
        } yield result).unsafeRunSync() shouldBe Some(DbFixtures.blockRow.copy(hash = "hash2", number = 2))
      }
    }

}
