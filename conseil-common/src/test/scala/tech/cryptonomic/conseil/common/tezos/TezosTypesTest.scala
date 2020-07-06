package tech.cryptonomic.conseil.common.tezos

import java.time.Instant

import org.scalatest.{EitherValues, Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.common.tezos.TezosTypes._

class TezosTypesTest extends WordSpec with Matchers with OptionValues with EitherValues {

  "The Base58Check verifier" should {
      "accept an empty string" in {
        TezosTypes.isBase58Check("") shouldBe true
      }

      "accept a correctly encoded string" in {
        TezosTypes.isBase58Check(
          "signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe true
      }

      "reject a string with forbidden chars" in {
        TezosTypes.isBase58Check(
          "signiRfcqmbGc6UtW1WzulJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        TezosTypes.isBase58Check(
          "$signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        TezosTypes.isBase58Check(
          "signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf*"
        ) shouldBe false
      }

      "reject a string with spaces" in {
        TezosTypes.isBase58Check(
          "signiRfcqmbGc6UtW1WzuJNGzRRs DLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        TezosTypes.isBase58Check(
          " signiRfcqmbGc6UtW1WzuJNGzRRsDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        TezosTypes.isBase58Check(
          "signiRfcqmbGc6UtW1WzuJNGzRRsDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf "
        ) shouldBe false
      }

    }

  "The Syntax import" should {
      "allow building Block-tagged generic data" in {
        import TezosTypes.Syntax._
        val someTime = Some(Instant.ofEpochMilli(0))
        val content = "A content string"
        val (hash, level) = (TezosBlockHash("hash"), 1)

        content.taggedWithBlock(hash, level, someTime, None, None) shouldEqual BlockTagged(
          hash,
          level,
          someTime,
          None,
          None,
          content
        )
      }
    }

  "The BlockTagged wrapper" should {
      "convert to a tuple" in {
        val someTime = Some(Instant.ofEpochMilli(0))
        val content = "A content string"
        val (hash, level) = (TezosBlockHash("hash"), 1)

        BlockTagged(hash, level, someTime, None, None, content).asTuple shouldEqual (hash, level, someTime, None, None, content)
      }
    }

}
