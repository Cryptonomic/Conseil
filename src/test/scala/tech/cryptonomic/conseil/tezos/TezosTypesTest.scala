package tech.cryptonomic.conseil.tezos

import org.scalatest.{Matchers, WordSpec}

class TezosTypesTest extends WordSpec with Matchers {

  val sut = TezosTypes

  "The Base58Check verifier" should {
    "accept an empty string" in {
      sut.isBase58Check("") shouldBe true
    }

    "accept a correctly encoded string" in {
      sut.isBase58Check("signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe true
    }

    "reject a string with forbidden chars" in {
      sut.isBase58Check("signiRfcqmbGc6UtW1WzulJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe false
      sut.isBase58Check("$signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe false
      sut.isBase58Check("signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf*") shouldBe false
    }

    "reject a string with spaces" in {
      sut.isBase58Check("signiRfcqmbGc6UtW1WzuJNGzRRs DLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe false
      sut.isBase58Check(" signiRfcqmbGc6UtW1WzuJNGzRRsDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe false
      sut.isBase58Check("signiRfcqmbGc6UtW1WzuJNGzRRsDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf ") shouldBe false
    }

  }

}