package tech.cryptonomic.conseil.api.sql

import java.sql.Timestamp
import java.time.LocalDateTime

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.api.TezosInMemoryDatabaseSetup
import tech.cryptonomic.conseil.api.sql.DefaultDatabaseOperations._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.tezos.{Fork, Tables}
import tech.cryptonomic.conseil.common.tezos.Tables.FeesRow

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps

class DefaultDatabaseOperationsTest
    extends WordSpec
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

      "count distinct elements in column properly" in {
        dbHandler.run(Tables.Fees ++= fees).isReadyWithin(5 seconds) shouldBe true
        dbHandler.run(countDistinct("tezos", "fees", "timestamp")).futureValue shouldBe 1
        dbHandler.run(countDistinct("tezos", "fees", "low")).futureValue shouldBe 5
      }

      "select distinct elements from column properly" in {
        dbHandler.run(Tables.Fees ++= fees).isReadyWithin(5 seconds) shouldBe true
        dbHandler.run(selectDistinct("tezos", "fees", "timestamp")).futureValue shouldBe List(
          "2018-11-22 12:30:00"
        )
        dbHandler.run(selectDistinct("tezos", "fees", "low")).futureValue should contain theSameElementsAs List(
          "1",
          "2",
          "3",
          "4",
          "5"
        )
      }

      "select distinct elements from column with 'like' properly" in {
        dbHandler.run(Tables.Fees ++= fees).isReadyWithin(5 seconds) shouldBe true
        dbHandler.run(selectDistinctLike("tezos", "fees", "kind", "1-")).futureValue shouldBe List(
          "1-example"
        )
      }
    }
}
