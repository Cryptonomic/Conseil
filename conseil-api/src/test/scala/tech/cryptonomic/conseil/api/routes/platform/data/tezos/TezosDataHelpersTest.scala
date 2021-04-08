package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import java.sql.Timestamp

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import tech.cryptonomic.conseil.common.tezos.Fork
import tech.cryptonomic.conseil.common.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow, OperationsRow}
import tech.cryptonomic.conseil.common.testkit.LoggingTestSupport

class TezosDataHelpersTest extends TezosDataHelpers with AnyWordSpecLike with Matchers with LoggingTestSupport {

  "TezosDataHelpers" should {
      val encodeAny = anySchema.encoder.encode _
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
        ) shouldBe ujson.Obj(
          "level" -> ujson.Num(1),
          "proto" -> ujson.Num(0),
          "predecessor" -> ujson.Str("predecessor"),
          "timestamp" -> ujson.Num(0),
          "fitness" -> ujson.Str("fitness"),
          "protocol" -> ujson.Str("protocol"),
          "hash" -> ujson.Str("hash"),
          "utcYear" -> ujson.Num(2020),
          "utcMonth" -> ujson.Num(6),
          "utcDay" -> ujson.Num(20),
          "utcTime" -> ujson.Str("20:00:00"),
          "forkId" -> ujson.Str("leader")
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
        ) shouldBe ujson.Obj(
          "accountId" -> ujson.Str("account1"),
          "blockId" -> ujson.Str("block1"),
          "blockLevel" -> ujson.Num(1),
          "balance" -> ujson.Num(1000),
          "isBaker" -> ujson.False,
          "isActivated" -> ujson.False,
          "forkId" -> ujson.Str("leader")
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
        ) shouldBe ujson.Obj(
          "operationId" -> ujson.Num(1),
          "operationGroupHash" -> ujson.Str("operationGroupHash1"),
          "kind" -> ujson.Str("kind1"),
          "blockHash" -> ujson.Str("blockHash1"),
          "blockLevel" -> ujson.Num(1),
          "internal" -> ujson.False,
          "timestamp" -> ujson.Num(0),
          "utcYear" -> ujson.Num(2020),
          "utcMonth" -> ujson.Num(6),
          "utcDay" -> ujson.Num(20),
          "utcTime" -> ujson.Str("20:00:00"),
          "forkId" -> ujson.Str("leader")
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
        ) shouldBe ujson.Obj(
          "protocol" -> ujson.Str("protocol"),
          "hash" -> ujson.Str("hash"),
          "branch" -> ujson.Str("branch"),
          "blockId" -> ujson.Str("blockId"),
          "blockLevel" -> ujson.Num(1),
          "forkId" -> ujson.Str("leader")
        )
      }
    }

}
