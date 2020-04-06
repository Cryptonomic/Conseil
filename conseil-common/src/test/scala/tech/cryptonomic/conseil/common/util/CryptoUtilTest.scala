package tech.cryptonomic.conseil.common.util

import org.scalatest.{FlatSpec, Matchers}

class CryptoUtilTest extends FlatSpec with Matchers {

  "CryptoUtil" should "correctly decode and encode a Tezos account ID" in {
      val accountID = "tz1Z5pFi5Sy99Kcz36XA5WtKW7Z6NVG9LdA4"
      val decoded = CryptoUtil.base58CheckDecode(accountID, "tz1").get
      val encoded = CryptoUtil.base58CheckEncode(decoded.toList, "tz1").get
      encoded should be(accountID)
    }

  "CryptoUtil" should "correctly decode and encode a Tezos operation ID" in {
      val operationID = "op26bhfiE1tVKiZHfkujRcasnghyRnvDx9gnKGiNwAW98M71EWF"
      val decoded = CryptoUtil.base58CheckDecode(operationID, "op").get
      val encoded = CryptoUtil.base58CheckEncode(decoded.toList, "op").get
      encoded should be(operationID)
    }

}
