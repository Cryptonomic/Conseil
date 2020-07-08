package tech.cryptonomic.conseil.common.bitcoin

import scala.concurrent.ExecutionContext

import cats.effect._
import org.scalatest.{Matchers, WordSpec}
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.bitcoin.BitcoinPersistence._

class BitcoinPersistenceTest
    extends WordSpec
    with Matchers
    with InMemoryDatabase
    with BitcoinInMemoryDatabaseSetup
    with BitcoinFixtures
    with BitcoinStubs {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin persistence" should {
      "save block from the JSON-RPC response" in new BitcoinPersistenceStubs(dbHandler) {
        (for {
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          result <- tx.transact(Tables.Blocks.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.blockRow)
      }

      "save transaction from the JSON-RPC response" in new BitcoinPersistenceStubs(dbHandler) {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(Tables.Transactions += RpcFixtures.transactionResult.convertTo[Tables.TransactionsRow])
          result <- tx.transact(Tables.Transactions.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.transactionRow)
      }

      "save transaction input from the JSON-RPC response" in new BitcoinPersistenceStubs(dbHandler) {
        (for {
          // we have to have block and transaction row to save the input (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(Tables.Transactions += RpcFixtures.transactionResult.convertTo[Tables.TransactionsRow])
          _ <- tx.transact(
            Tables.Inputs += (RpcFixtures.transactionResult, RpcFixtures.inputResult).convertTo[Tables.InputsRow]
          )
          result <- tx.transact(Tables.Inputs.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.inputRow)
      }

      "save transaction output from the JSON-RPC response" in new BitcoinPersistenceStubs(dbHandler) {
        (for {
          // we have to have block and transaction row to save the input (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(Tables.Transactions += RpcFixtures.transactionResult.convertTo[Tables.TransactionsRow])
          _ <- tx.transact(
            Tables.Outputs += (RpcFixtures.transactionResult, RpcFixtures.outputResult).convertTo[Tables.OutputsRow]
          )
          result <- tx.transact(Tables.Outputs.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.outputRow)
      }

      "save block with transactions using persistence (integration test)" in new BitcoinPersistenceStubs(dbHandler) {
        (for {
          // run
          _ <- tx.transact(
            bitcoinPersistenceStub
              .createBlock(RpcFixtures.blockResult, List(RpcFixtures.transactionResult))
          )
          // test results
          block <- tx.transact(Tables.Blocks.result)
          transactions <- tx.transact(Tables.Transactions.result)
          inputs <- tx.transact(Tables.Inputs.result)
          outputs <- tx.transact(Tables.Outputs.result)
        } yield block ++ transactions ++ inputs ++ outputs).unsafeRunSync() shouldBe Vector(
              DbFixtures.blockRow,
              DbFixtures.transactionRow,
              DbFixtures.inputRow,
              DbFixtures.outputRow
            )
      }
    }

}
