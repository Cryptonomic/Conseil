package tech.cryptonomic.conseil.util

import org.scalatest.{FlatSpec, Matchers}

class CryptoUtilTest extends FlatSpec with Matchers {

  "CryptoUtil" should "correctly decode and encode a Tezos account ID as bytes" in {
      val accountID = "tz1Z5pFi5Sy99Kcz36XA5WtKW7Z6NVG9LdA4"
      val decoded = CryptoUtil.base58CheckDecode(accountID, "tz1").get
      val encoded = CryptoUtil.base58CheckEncode(decoded.toList, "tz1").get
      encoded should be(accountID)
    }

  it should "correctly decode and encode a Tezos operation ID" in {
      val operationID = "op26bhfiE1tVKiZHfkujRcasnghyRnvDx9gnKGiNwAW98M71EWF"
      val decoded = CryptoUtil.base58CheckDecode(operationID, "op").get
      val encoded = CryptoUtil.base58CheckEncode(decoded.toList, "op").get
      encoded should be(operationID)
    }

  it should "correctly pack and unpack a Tezos account ID as hex-string" in {
      val accountID = "tz1Z5pFi5Sy99Kcz36XA5WtKW7Z6NVG9LdA4"
      val packed = CryptoUtil.packAddress(accountID).get
      //packing adds the packet length at the beginning, read doesn't care
      val unpacked = CryptoUtil.readAddress(packed.drop(12)).get
      unpacked should be(accountID)
    }

  it should "read a binary address to its b58check tezos id" in {
      val address = CryptoUtil.readAddress("0000a8d45bdc966ddaaac83188a1e1c1fde2a3e05e5c").get
      address shouldBe "tz1b2icJC4E7Y2ED1xsZXuqYpF7cxHDtduuP"
    }

}
