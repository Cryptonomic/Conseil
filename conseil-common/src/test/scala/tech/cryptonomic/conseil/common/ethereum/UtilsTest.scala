package tech.cryptonomic.conseil.common.ethereum

import org.scalatest.wordspec.AnyWordSpec
import org.scalatest.matchers.should.Matchers

class UtilsTest extends AnyWordSpec with Matchers {
  "Utils" should {
      "convert function signature to 4 byte hex selector" in {
        Utils.functionSignatureTo4byteHexSelector("totalSupply()") shouldBe 0x18160ddd
        Utils.functionSignatureTo4byteHexSelector("totalSupply ()") shouldBe 0x18160ddd
      }
    }
}
