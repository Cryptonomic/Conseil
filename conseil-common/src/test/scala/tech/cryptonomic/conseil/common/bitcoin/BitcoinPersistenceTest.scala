package tech.cryptonomic.conseil.common.bitcoin

import scala.concurrent.ExecutionContext

import cats.effect._
import cats.arrow.FunctionK
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
        (for {
          // run
          _ <- tx.transact(
            persistance
              .createBlock(
                RpcFixtures.block,
                List(
                  RpcFixtures.transaction
                    .copy(vin = List(RpcFixtures.inputWithTxid), vout = List(RpcFixtures.outputWithTxid))
                )
              )
          )
          // test results
          block <- tx.transact(Tables.Blocks.result)
          transactions <- tx.transact(Tables.Transactions.result)
          inputs <- tx.transact(Tables.Inputs.result)
          outputs <- tx.transact(Tables.Outputs.result)
        } yield block ++ transactions ++ inputs ++ outputs).unsafeRunSync() shouldBe Vector(
          DbFixtures.block,
          DbFixtures.transaction,
          DbFixtures.input,
          DbFixtures.output
        )
      }
    }

  trait BitcoinPersistanceFixtgures {

    // prevent transactor from shutdown handler after each operation
    val tx = Transactor.liftK(new FunctionK[DBIO, IO] {
      def apply[A](dbio: DBIO[A]): IO[A] = Async.fromFuture(IO.delay(dbHandler.run(dbio)))
    })

    val persistance = new BitcoinPersistence[IO]
  }

}
