package tech.cryptonomic.conseil.indexer.sql

import java.sql.Timestamp
import java.time.LocalDateTime

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.tezos.{Fork, Tables}
import tech.cryptonomic.conseil.common.tezos.Tables.{Fees, FeesRow}
import tech.cryptonomic.conseil.indexer.tezos.TezosInMemoryDatabaseSetup
import tech.cryptonomic.conseil.indexer.sql.DefaultDatabaseOperations._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class DefaultDatabaseOperationsTest
    extends AnyWordSpec
    with Matchers
    with InMemoryDatabase
    with TezosInMemoryDatabaseSetup
    with ScalaFutures {

  "The default database operations" should {
      val fees: List[FeesRow] = List.tabulate(5) { i =>
        FeesRow(
          1 + i,
          3 + i,
          5 + i,
          Timestamp.valueOf(LocalDateTime.of(2018, 11, 22, 12, 30)),
          s"$i-example",
          None,
          None,
          forkId = Fork.mainForkId
        )
      }

      "insert data when table is empty" in {
        dbHandler.run(insertWhenEmpty[Fees](Tables.Fees, fees)).futureValue shouldBe Some(5)
      }

      "do not insert data when table is not empty" in {
        dbHandler.run(Tables.Fees ++= fees).isReadyWithin(5 seconds) shouldBe true
        dbHandler.run(insertWhenEmpty[Fees](Tables.Fees, fees)).futureValue.value shouldBe Some(0)
      }
    }
}
