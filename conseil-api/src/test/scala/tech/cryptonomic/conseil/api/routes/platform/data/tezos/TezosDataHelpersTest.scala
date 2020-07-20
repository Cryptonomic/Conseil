package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import java.sql.Timestamp

import io.circe.Json
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import tech.cryptonomic.conseil.common.tezos.Fork
import tech.cryptonomic.conseil.common.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow, OperationsRow}

class TezosDataHelpersTest extends TezosDataHelpers with AnyWordSpecLike with Matchers {

  "TezosDataHelpers" should {
      val encodeAny = anySchema.encoder
      "encode Tezos Blocks into json properly" in {
        encodeAny(
          BlocksRow(
            level = 1,
            proto = 0,
            predecessor = "predecessor",
            timestamp = new Timestamp(0),
            fitness = "fitness",
            protocol = "protocol",
            hash = "hash",
            utcYear = 2020,
            utcMonth = 6,
            utcDay = 20,
            utcTime = "20:00:00",
            forkId = Fork.mainForkId
          )
        ) shouldBe Json.fromFields(
          List(
            "level" -> Json.fromInt(1),
            "proto" -> Json.fromInt(0),
            "predecessor" -> Json.fromString("predecessor"),
            "timestamp" -> Json.fromLong(0),
            "fitness" -> Json.fromString("fitness"),
            "protocol" -> Json.fromString("protocol"),
            "hash" -> Json.fromString("hash"),
            "utcYear" -> Json.fromInt(2020),
            "utcMonth" -> Json.fromInt(6),
            "utcDay" -> Json.fromInt(20),
            "utcTime" -> Json.fromString("20:00:00"),
            "forkId" -> Json.fromString("leader")
          )
        )

      }
      "encode Tezos Accounts into json properly" in {
        encodeAny(
          AccountsRow(
            "account1",
            "block1",
            blockLevel = 1L,
            balance = BigDecimal.valueOf(1000),
            forkId = Fork.mainForkId
          )
        ) shouldBe Json.fromFields(
          List(
            "accountId" -> Json.fromString("account1"),
            "blockId" -> Json.fromString("block1"),
            "blockLevel" -> Json.fromInt(1),
            "balance" -> Json.fromInt(1000),
            "isBaker" -> Json.False,
            "isActivated" -> Json.False,
            "forkId" -> Json.fromString("leader")
          )
        )
      }
      "encode Tezos Operations into json properly" in {
        encodeAny(
          OperationsRow(
            operationId = 1,
            operationGroupHash = "operationGroupHash1",
            kind = "kind1",
            blockHash = "blockHash1",
            blockLevel = 1,
            internal = false,
            timestamp = new Timestamp(0),
            utcYear = 2020,
            utcMonth = 6,
            utcDay = 20,
            utcTime = "20:00:00",
            forkId = Fork.mainForkId
          )
        ) shouldBe Json.fromFields(
          List(
            "operationId" -> Json.fromInt(1),
            "operationGroupHash" -> Json.fromString("operationGroupHash1"),
            "kind" -> Json.fromString("kind1"),
            "blockHash" -> Json.fromString("blockHash1"),
            "blockLevel" -> Json.fromInt(1),
            "internal" -> Json.False,
            "timestamp" -> Json.fromLong(0),
            "utcYear" -> Json.fromInt(2020),
            "utcMonth" -> Json.fromInt(6),
            "utcDay" -> Json.fromInt(20),
            "utcTime" -> Json.fromString("20:00:00"),
            "forkId" -> Json.fromString("leader")
          )
        )
      }
      "encode Tezos Operation Groups into json properly" in {
        encodeAny(
          OperationGroupsRow(
            protocol = "protocol",
            hash = "hash",
            branch = "branch",
            blockId = "blockId",
            blockLevel = 1,
            forkId = Fork.mainForkId
          )
        ) shouldBe Json.fromFields(
          List(
            "protocol" -> Json.fromString("protocol"),
            "hash" -> Json.fromString("hash"),
            "branch" -> Json.fromString("branch"),
            "blockId" -> Json.fromString("blockId"),
            "blockLevel" -> Json.fromInt(1),
            "forkId" -> Json.fromString("leader")
          )
        )
      }
    }

}
