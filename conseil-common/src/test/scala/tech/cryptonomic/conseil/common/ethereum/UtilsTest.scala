package tech.cryptonomic.conseil.common.ethereum

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class UtilsTest extends AnyWordSpec with Matchers {
  "Utils" should {
      "remove 0x prefix from string" in {
        Utils.remove0x("0x0") shouldBe "0"
        Utils.remove0x("0") shouldBe "0"
      }

      "decode hex string" in {
        Utils.hexToString("0x313233") shouldBe "123"
      }

      "create sha-3 signature for string value" in {
        Utils.keccak("totalSupply()") shouldBe "18160DDD"
      }

      "convert hex string to big decimal" in {
        Utils.hexStringToBigDecimal("0x1") shouldBe 0x1
      }
    }
}
