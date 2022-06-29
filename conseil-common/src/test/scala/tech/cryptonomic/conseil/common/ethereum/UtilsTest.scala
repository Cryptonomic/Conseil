package tech.cryptonomic.conseil.common.ethereum

import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class UtilsTest extends ConseilSpec {
  "Utils" should {
    "decode hex string" in {
      Utils.hexToString("0x313233") shouldBe "123"
    }

    "create sha-3 signature for string value" in {
      Utils.keccak("totalSupply()") shouldBe "18160DDD"
    }

    "convert hex string to big decimal" in {
      Utils.hexStringToBigDecimal("0x1") shouldBe 0x1
    }

    "convert empty hex to big decimal" in {
      Utils.hexStringToBigDecimal("0x") shouldBe 0
    }

    "convert hex to Option[Int]" in {
      Utils.hexToInt("0x1") shouldBe Some(1)
    }

    "convert empty hex to None" in {
      Utils.hexToInt("0x") shouldBe None
    }

    "truncate empty hex string" in {
      Utils.truncateEmptyHexString("0x") shouldBe ""
    }

    "not truncate valid hex string" in {
      Utils.truncateEmptyHexString("0x1") shouldBe "0x1"
    }

    "convert hex to string containing Unicode characters" in {
      Utils.hexToString(
        "0x00000000000000000000000000000000000000000000000000000000000000200000000000000000000000000000000000000000000000000000000000000004f09fa68400000000000000000000000000000000000000000000000000000000"
      ) shouldBe "\uD83E\uDD84"
    }
  }
}
