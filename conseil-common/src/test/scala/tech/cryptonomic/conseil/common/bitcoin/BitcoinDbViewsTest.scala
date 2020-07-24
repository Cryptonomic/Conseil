package tech.cryptonomic.conseil.common.bitcoin

import scala.concurrent.ExecutionContext

import cats.effect._
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase

class BitcoinDbViewsTest
    extends AnyWordSpec
    with Matchers
    with InMemoryDatabase
    with BitcoinInMemoryDatabaseSetup
    with BitcoinFixtures
    with BitcoinStubs {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin db views" should {

      /**
        * The logic behind the Bitcoin accounts is explained in [[Views.AccountsViewSql]]
        * In the example below, we create two blocks with two transactions, 
        * one of which is spent and the other is not.
        */
      "return balance for the given address" in new BitcoinPersistenceStubs(dbHandler) {
        val unspentOutput = RpcFixtures.outputResult.copy(
          scriptPubKey = RpcFixtures.outputResult.scriptPubKey.copy(addresses = Some(List("address1")))
        )

        val spentOutput = RpcFixtures.outputResult.copy(
          scriptPubKey = RpcFixtures.outputResult.scriptPubKey.copy(addresses = Some(List("address2")))
        )

        val block1 = RpcFixtures.blockResult.copy(height = 1, hash = "hash1")
        val block2 = RpcFixtures.blockResult.copy(height = 2, hash = "hash2")

        val tx1 = RpcFixtures.transactionResult.copy(txid = "tx1", blockhash = block1.hash, vout = List(unspentOutput))

        val input = RpcFixtures.inputResult.copy(txid = Some(tx1.txid), vout = Some(0))

        val tx2 = RpcFixtures.transactionResult.copy(
          txid = "tx2",
          blockhash = block2.hash,
          vin = List(input),
          vout = List(spentOutput)
        )

        (for {
          // run
          _ <- tx.transact(bitcoinPersistenceStub.createBlock(block1, List(tx1)))
          _ <- tx.transact(bitcoinPersistenceStub.createBlock(block2, List(tx2)))

          // create view
          _ <- tx.transact(Views.AccountsViewSql)

          // test results
          result <- tx.transact(Views.Accounts.result)
        } yield result).unsafeRunSync() shouldBe Vector(
              Views.AccountsRow("address1", BigDecimal(0)),
              Views.AccountsRow("address2", BigDecimal(50.0))
            )
      }
    }

}
