package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import java.sql.Timestamp

import io.circe.Json
import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.common.bitcoin.Tables.BlocksRow

class BitcoinDataHelpersTest extends WordSpec with Matchers with BitcoinDataHelpers {

  "BitcoinDataHelpers" should {
      val encodeAny = anySchema.encoder
      "encode Bitcoin Blocks into json properly" in {
        encodeAny(
          BlocksRow(
            hash = "hash",
            confirmations = 1,
            size = 6,
            weight = 3,
            height = 3,
            version = 1,
            merkleRoot = "root",
            time = Timestamp.valueOf("2020-06-20 20:00:00"),
            nonce = 1,
            bits = "bits",
            difficulty = 3,
            nTx = 0
          )
        ) shouldBe Json.fromFields(
          List(
            "hash" -> Json.fromString("hash"),
            "confirmations" -> Json.fromInt(1),
            "size" -> Json.fromInt(6),
            "weight" -> Json.fromInt(3),
            "height" -> Json.fromInt(3),
            "version" -> Json.fromInt(1),
            "merkleRoot" -> Json.fromString("root"),
            "time" -> Json.fromLong(1592676000000L),
            "nonce" -> Json.fromInt(1),
            "bits" -> Json.fromString("bits"),
            "difficulty" -> Json.fromInt(3),
            "nTx" -> Json.fromInt(0)
          )
        )
      }
    }

}
