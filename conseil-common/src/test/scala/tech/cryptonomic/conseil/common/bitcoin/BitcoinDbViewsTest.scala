package tech.cryptonomic.conseil.common.bitcoin

import scala.concurrent.ExecutionContext

import cats.effect._
import org.scalatest.{Matchers, WordSpec}
import slick.jdbc.PostgresProfile.api._

import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase

class BitcoinDbViewsTest
    extends WordSpec
    with Matchers
    with InMemoryDatabase
    with BitcoinInMemoryDatabaseSetup
    with BitcoinFixtures
    with BitcoinStubs {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin db views" should {

      "return balance for the given address" in new BitcoinPersistenceStubs(dbHandler) {
        val unspentOutput = RpcFixtures.outputResult.copy(
          scriptPubKey = RpcFixtures.outputResult.scriptPubKey.copy(addresses = Some(List("address1")))
        )

        val spentOutput = RpcFixtures.outputResult.copy(
          scriptPubKey = RpcFixtures.outputResult.scriptPubKey.copy(addresses = Some(List("address2")))
        )

        val input = RpcFixtures.inputResult.copy(txid = Some("tx1"), vout = Some(0))

        (for {
          // run
          _ <- tx.transact(
            bitcoinPersistenceStub
              .createBlock(
                RpcFixtures.blockResult.copy(height = 1, hash = "hash1"),
                List(RpcFixtures.transactionResult.copy(txid = "tx1", blockhash = "hash1", vout = List(unspentOutput)))
              )
          )

          _ <- tx.transact(
            bitcoinPersistenceStub
              .createBlock(
                RpcFixtures.blockResult.copy(height = 2, hash = "hash2"),
                List(
                  RpcFixtures.transactionResult
                    .copy(txid = "tx2", blockhash = "hash2", vin = List(input), vout = List(spentOutput))
                )
              )
          )

          // create view
          _ <- tx.transact(Views.createAccountsViewSql)

          // test results
          result <- tx.transact(Views.Accounts.result)
        } yield result).unsafeRunSync() shouldBe Vector(
              Views.AccountsRow("address1", BigDecimal(0)),
              Views.AccountsRow("address2", BigDecimal(50.0))
            )
      }
    }

}
