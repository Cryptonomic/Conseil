package tech.cryptonomic.conseil.util

import org.scalatest.{FlatSpec, Matchers}

class CryptoUtilTest extends FlatSpec with Matchers {

  "CryptoUtilTest" should "correctly decode and encode a Tezos account ID" in {
    val accountID = "TZ1Xn4Khg8VV4ZW5Qj1AWHcidJkNdKJnBWoJ"
    val decoded = CryptoUtil.base58CheckDecode(accountID, "tz1").get
    val encoded = CryptoUtil.base58CheckEncode(decoded.toList, "tz1").get
    encoded should be (accountID)
  }

  "CryptoUtilTest" should "correctly decode and encode a Tezos operation ID" in {
    val operationID = "op26bhfiE1tVKiZHfkujRcasnghyRnvDx9gnKGiNwAW98M71EWF"
    val decoded = CryptoUtil.base58CheckDecode(operationID, "op").get
    val encoded = CryptoUtil.base58CheckEncode(decoded.toList, "op").get
    encoded should be (operationID)
  }

}
