package tech.cryptonomic.conseil.common.bitcoin

import scala.concurrent.ExecutionContext

import cats.effect._
import cats.arrow.FunctionK
import org.scalatest.{Matchers, WordSpec}
import slickeffect.Transactor
import slick.jdbc.PostgresProfile.api._
import slick.dbio.DBIO

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
import tech.cryptonomic.conseil.common.bitcoin.BitcoinPersistence._

class BitcoinPersistenceTest
    extends WordSpec
    with Matchers
    with InMemoryDatabase
    with BitcoinInMemoryDatabaseSetup
    with BitcoinFixtures {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin persistence" should {
      "save block from the JSON-RPC response" in new BitcoinPersistanceStubs {
        (for {
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          result <- tx.transact(Tables.Blocks.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.blockRow)
      }

      "save transaction from the JSON-RPC response" in new BitcoinPersistanceStubs {
        (for {
          // we have to have block row to save the transaction (due to the foreign key)
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          _ <- tx.transact(Tables.Transactions += RpcFixtures.transactionResult.convertTo[Tables.TransactionsRow])
          result <- tx.transact(Tables.Transactions.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.transactionRow)
      }

      "save transaction input from the JSON-RPC response" in new BitcoinPersistanceStubs {
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

      "save transaction output from the JSON-RPC response" in new BitcoinPersistanceStubs {
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

      "save block with transactions using persistance (integration test)" in new BitcoinPersistanceStubs {
        (for {
          // run
          _ <- tx.transact(
            bitcoinPersistanceStub
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

  /**
    * Stubs that can help to provide tests for the [[BitcoinPersistence]].
    *
    * Usage example:
    *
    * {{{
    *   "test name" in new BitcoinPersistanceStubs {
    *     // bitcoinPersistanceStub is available in the current scope
    *   }
    * }}}
    */
  trait BitcoinPersistanceStubs {

    /**
      * This transactor object will actually execute any slick database action (DBIO) and convert
      * the result into a lazy IO value. When the IO effect is run to obtain the value, the transactor
      * automatically guarantees to properly release the underlying database resources.
      *
      * The default implementation of [[slickeffect.Transactor]] wraps Slick db into the resource,
      * to handle proper shutdown at the end of the execution. I the test mode we want to encapsulate
      * every single test, so we have to prevent `Transactor` from shutdown with providing own method
      * to run the Slick query. The [[slickeffect.Transactor]] uses `FunctionK` to do the execution, so we
      * need to `liftK` own effectful function with the `dbHandler.run`.
      * More info about [[cats.arrow.FunctionK]]: https://github.com/typelevel/cats/blob/master/docs/src/main/tut/datatypes/functionk.md
      */
    val tx = Transactor.liftK(new FunctionK[DBIO, IO] {
      def apply[A](dbio: DBIO[A]): IO[A] = Async.fromFuture(IO.delay(dbHandler.run(dbio)))
    })

    val bitcoinPersistanceStub = new BitcoinPersistence[IO]
  }

}
