package tech.cryptonomic.conseil.common.tezos

import java.time.Instant

import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class TezosTypesTest extends ConseilSpec {

  val sut = TezosTypes

  "The Base58Check verifier" should {
      "accept an empty string" in {
        sut.isBase58Check("") shouldBe true
      }

      "accept a correctly encoded string" in {
        sut.isBase58Check(
          "signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe true
      }

      "reject a string with forbidden chars" in {
        sut.isBase58Check(
          "signiRfcqmbGc6UtW1WzulJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        sut.isBase58Check(
          "$signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        sut.isBase58Check(
          "signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf*"
        ) shouldBe false
      }

      "reject a string with spaces" in {
        sut.isBase58Check(
          "signiRfcqmbGc6UtW1WzuJNGzRRs DLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        sut.isBase58Check(
          " signiRfcqmbGc6UtW1WzuJNGzRRsDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        sut.isBase58Check(
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

        val ref = BlockReference(hash, level, someTime, None, None)

        content.taggedWithBlock(ref) shouldEqual BlockTagged(ref, content)

      }
    }

  "The BlockTagged wrapper" should {
      "convert to a tuple" in {
        val someTime = Some(Instant.ofEpochMilli(0))
        val content = "A content string"
        val (hash, level) = (TezosBlockHash("hash"), 1)

        BlockTagged(BlockReference(hash, level, someTime, None, None), content).asTuple shouldEqual (hash, level, someTime, None, None, content)
      }
    }

  "The Baking object" should {
      "compute rolls from staking balances" in {
        val stakes = PositiveDecimal(BigDecimal.exact(21126133085692L))

        Baking.computeRollsFromStakes(stakes).value shouldBe 2640
      }

      "compute no rolls if there's not enough staking balance" in {

        val stakes = PositiveDecimal((Baking.BakerRollsSize * 1e6) - 1)

        Baking.computeRollsFromStakes(stakes).value shouldBe 0
      }
    }
}
