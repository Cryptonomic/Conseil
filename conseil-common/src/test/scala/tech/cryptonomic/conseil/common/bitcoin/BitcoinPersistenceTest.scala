package tech.cryptonomic.conseil.common.bitcoin

import scala.concurrent.ExecutionContext

import cats.effect._
import cats.arrow.FunctionK
import fs2.Stream
import org.scalatest.{Matchers, WordSpec}
import slickeffect.Transactor
import slick.jdbc.PostgresProfile.api._
import slick.dbio.DBIO

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase

class BitcoinPersistenceTest
    extends WordSpec
    with Matchers
    with InMemoryDatabase
    with BitcoinInMemoryDatabaseSetup
    with BitcoinFixtures {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin persistence" should {

      "write blocks" in new BitcoinPersistanceFixtgures {
        Stream(RpcFixtures.block)
          .through(persistance.saveBlocks(batchSize = 1))
          .compile
          .drain
          .flatMap(_ => tx.transact(Tables.Blocks.result))
          .unsafeRunSync() shouldBe Vector(DbFixtures.block)
      }

      "write transactions" in new BitcoinPersistanceFixtgures {
        (for {
          // we can't save transaction without corresponding block
          _ <- Stream(RpcFixtures.block).through(persistance.saveBlocks(batchSize = 1)).compile.drain
          _ <- Stream(RpcFixtures.transaction).through(persistance.saveTransactions(batchSize = 1)).compile.drain
          result <- tx.transact(Tables.Transactions.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.transaction)
      }

      "write transaction components" in new BitcoinPersistanceFixtgures {
        (for {
          // we can't save transaction components without corresponding block and transaction
          _ <- Stream(RpcFixtures.block).through(persistance.saveBlocks(batchSize = 1)).compile.drain
          _ <- Stream(RpcFixtures.transaction).through(persistance.saveTransactions(batchSize = 1)).compile.drain
          _ <- Stream(
            RpcFixtures.inputWithTxid,
            RpcFixtures.outputWithTxid
          ).through(persistance.saveTransactionComponents(batchSize = 1)).compile.drain
          inputs <- tx.transact(Tables.Inputs.result)
          outputs <- tx.transact(Tables.Outputs.result)
        } yield inputs ++ outputs).unsafeRunSync() shouldBe Vector(DbFixtures.input, DbFixtures.output)
      }

    }

  trait BitcoinPersistanceFixtgures {

    val tx = Transactor.liftK(new FunctionK[DBIO, IO] {
      def apply[A](dbio: DBIO[A]): IO[A] = Async.fromFuture(IO.delay(dbHandler.run(dbio)))
    })

    val persistance = new BitcoinPersistence(tx)
  }

}
