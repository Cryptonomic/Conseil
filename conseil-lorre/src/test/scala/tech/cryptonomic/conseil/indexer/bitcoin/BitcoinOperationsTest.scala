package tech.cryptonomic.conseil.indexer.bitcoin

import org.scalatest.{Matchers, WordSpec}
import org.scalatest.Matchers
import org.scalamock.scalatest.MockFactory

class BitcoinOperationsTest extends WordSpec with MockFactory with Matchers {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Bitcoin persistence" should {
      "save block from the JSON-RPC response" in new BitcoinPersistenceStubs {
        (for {
          _ <- tx.transact(Tables.Blocks += RpcFixtures.blockResult.convertTo[Tables.BlocksRow])
          result <- tx.transact(Tables.Blocks.result)
        } yield result).unsafeRunSync() shouldBe Vector(DbFixtures.blockRow)
      }
    }

}
