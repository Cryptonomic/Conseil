package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import java.sql.Timestamp

import io.circe.Json
import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.common.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow, OperationsRow}

class TezosDataHelpersTest extends WordSpec with Matchers with TezosDataHelpers {

  "TezosDataHelpers" should {
      val encodeAny = anySchema.encoder
      "encode Tezos Blocks into json properly" in {
        encodeAny(
          BlocksRow(
            level = 1,
            proto = 0,
            predecessor = "predecessor",
            timestamp = Timestamp.valueOf("2020-06-20 20:00:00"),
            validationPass = 0,
            fitness = "fitness",
            protocol = "protocol",
            hash = "hash",
            utcYear = 2020,
            utcMonth = 6,
            utcDay = 20,
            utcTime = "20:00:00"
          )
        ) shouldBe Json.fromFields(
          List(
            "level" -> Json.fromInt(1),
            "proto" -> Json.fromInt(0),
            "predecessor" -> Json.fromString("predecessor"),
            "timestamp" -> Json.fromLong(1592676000000L),
            "validationPass" -> Json.fromInt(0),
            "fitness" -> Json.fromString("fitness"),
            "protocol" -> Json.fromString("protocol"),
            "hash" -> Json.fromString("hash"),
            "utcYear" -> Json.fromInt(2020),
            "utcMonth" -> Json.fromInt(6),
            "utcDay" -> Json.fromInt(20),
            "utcTime" -> Json.fromString("20:00:00")
          )
        )

      }
      "encode Tezos Accounts into json properly" in {
        encodeAny(
          AccountsRow("account1", "block1", blockLevel = BigDecimal.valueOf(1L), balance = BigDecimal.valueOf(1000))
        ) shouldBe Json.fromFields(
          List(
            "accountId" -> Json.fromString("account1"),
            "blockId" -> Json.fromString("block1"),
            "blockLevel" -> Json.fromInt(1),
            "balance" -> Json.fromInt(1000),
            "isBaker" -> Json.False,
            "isActivated" -> Json.False
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
            timestamp = Timestamp.valueOf("2020-06-20 20:00:00"),
            utcYear = 2020,
            utcMonth = 6,
            utcDay = 20,
            utcTime = "20:00:00"
          )
        ) shouldBe Json.fromFields(
          List(
            "operationId" -> Json.fromInt(1),
            "operationGroupHash" -> Json.fromString("operationGroupHash1"),
            "kind" -> Json.fromString("kind1"),
            "blockHash" -> Json.fromString("blockHash1"),
            "blockLevel" -> Json.fromInt(1),
            "internal" -> Json.False,
            "timestamp" -> Json.fromLong(1592676000000L),
            "utcYear" -> Json.fromInt(2020),
            "utcMonth" -> Json.fromInt(6),
            "utcDay" -> Json.fromInt(20),
            "utcTime" -> Json.fromString("20:00:00")
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
            blockLevel = 1
          )
        ) shouldBe Json.fromFields(
          List(
            "protocol" -> Json.fromString("protocol"),
            "hash" -> Json.fromString("hash"),
            "branch" -> Json.fromString("branch"),
            "blockId" -> Json.fromString("blockId"),
            "blockLevel" -> Json.fromInt(1)
          )
        )
      }
    }

}
