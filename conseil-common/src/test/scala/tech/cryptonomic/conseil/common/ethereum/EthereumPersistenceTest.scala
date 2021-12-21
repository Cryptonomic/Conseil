package tech.cryptonomic.conseil.common.ethereum

import scala.concurrent.ExecutionContext
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.ethereum.EthereumPersistence._
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

import cats.effect.unsafe.implicits.global

class EthereumPersistenceTest
    extends ConseilSpec
    with InMemoryDatabase
    with EthereumInMemoryDatabaseSetup
    with EthereumFixtures
    with EthereumStubs {

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
        } yield result).unsafeRunSync() shouldBe Vector(
              DbFixtures.transactionRow.copy(timestamp = None)
            )
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
        } yield result).unsafeRunSync() shouldBe Vector(
              DbFixtures.transactionReceiptRow.copy(timestamp = None)
            )
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

      "save account balances from the getBalance JSON-RPC response" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(
            ethereumPersistenceStub.createAccountBalances(
              List(RpcFixtures.accountFromResult, RpcFixtures.accountToResult, RpcFixtures.contractTokenAccountResult)
            )
          )
          result <- tx.transact(Tables.AccountsHistory.result)
        } yield result).unsafeRunSync() shouldBe Vector(
              DbFixtures.accountHistoryFromRow,
              DbFixtures.accountHistoryToRow,
              DbFixtures.contractTokenAccountHistoryRow
            )
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
        } yield result).unsafeRunSync() shouldBe Some(DbFixtures.blockRow.copy(hash = "hash2", level = 2))
      }

      "save account from transaction" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          _ <- tx.transact(
            ethereumPersistenceStub
              .upsertAccounts(List(RpcFixtures.accountFromResult, RpcFixtures.accountToResult))(ExecutionContext.global)
          )
          result <- tx.transact(Tables.Accounts.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.accountFromRow, DbFixtures.accountToRow)
      }

      "upsert account from transaction" in new EthereumPersistenceStubs(dbHandler) {
        val existingAccount = RpcFixtures.accountToResult.copy(
          blockHash = "0x0",
          blockNumber = "0x10000",
          timestamp = "0x55d21480",
          balance = BigDecimal("0.0")
        )
        (for {
          _ <- tx.transact(Tables.Accounts += existingAccount.convertTo[Tables.AccountsRow])
          _ <- tx.transact(
            ethereumPersistenceStub
              .upsertAccounts(List(RpcFixtures.accountFromResult, RpcFixtures.accountToResult))(ExecutionContext.global)
          )
          result <- tx.transact(Tables.Accounts.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.accountFromRow, DbFixtures.accountToRow)
      }
    }

  "save account from contract" in new EthereumPersistenceStubs(dbHandler) {
        (for {
          _ <- tx.transact(
            ethereumPersistenceStub
              .upsertAccounts(List(RpcFixtures.accountFromResult, RpcFixtures.accountToResult))(ExecutionContext.global)
          )
          result <- tx.transact(Tables.Accounts.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.accountFromRow, DbFixtures.accountToRow)
      }
}
