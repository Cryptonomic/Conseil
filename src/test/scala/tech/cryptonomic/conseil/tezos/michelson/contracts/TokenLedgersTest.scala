package tech.cryptonomic.conseil.tezos.michelson.contracts

import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, Contract, ContractId, Decimal, Micheline, ScriptId}
import org.scalatest.OptionValues
import scala.util.Success

class TokenLedgersTest extends WordSpec with Matchers with OptionValues {

  val sut = TokenLedgers

  "The Token Ledgers operations for a known FA1.2 compliant contract" should {

      "read a balance update from big maps diff for a registered contract" in {
        //given

        //values sampled from a real babylon use-case
        val ledgerId = ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux")
        val updateValue = """
                    |{
                    |  "prim": "Pair",
                    |  "args": [
                    |    {
                    |      "int": "10000"
                    |    },
                    |    []
                    |  ]
                    |}
        """.stripMargin
        val mapUpdate = Contract.BigMapUpdate(
          action = "update",
          key = Micheline("""{"bytes" : "0000c4ce21c7a7ac69810bb3425a043def752fddc817"}"""),
          key_hash = ScriptId("exprvMHy4mAi1igbigE5BeEbEAE5ayx82ne5BA7UUXmfGpAiiVF3vx"),
          big_map = Decimal(1718),
          value = Some(Micheline(updateValue))
        )

        //when
        val balanceUpdate = sut.readBalance(ledgerId)(mapUpdate)

        //then
        val (keyHash, balance) = balanceUpdate.value
        keyHash shouldEqual mapUpdate.key_hash
        balance shouldEqual BigInt(10000)

      }

      "read no balance update from big maps diff for a non-matching map id" in {
        //given

        //some values sampled from a real babylon use-case
        val ledgerId = ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux")
        val updateValue = """
                    |{
                    |  "prim": "Pair",
                    |  "args": [
                    |    {
                    |      "int": "10000"
                    |    },
                    |    []
                    |  ]
                    |}
        """.stripMargin
        val mapUpdate = Contract.BigMapUpdate(
          action = "update",
          key = Micheline("""{"bytes" : "0000c4ce21c7a7ac69810bb3425a043def752fddc817"}"""),
          key_hash = ScriptId("exprvMHy4mAi1igbigE5BeEbEAE5ayx82ne5BA7UUXmfGpAiiVF3vx"),
          big_map = Decimal(100),
          value = Some(Micheline(updateValue))
        )

        //when
        val balanceUpdate = sut.readBalance(ledgerId)(mapUpdate)

        //then
        balanceUpdate should not be defined

      }

      "read no balance update from big maps diff for an unecpected value format of the call params" in {
        //given

        //some values sampled from a real babylon use-case
        val ledgerId = ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux")
        val updateValue = """
                    |{
                    |  "prim": "Pair",
                    |  "args": [
                    |    {
                    |      "string": "one thousand tezies"
                    |    },
                    |    []
                    |  ]
                    |}
        """.stripMargin
        val mapUpdate = Contract.BigMapUpdate(
          action = "update",
          key = Micheline("""{"bytes" : "0000c4ce21c7a7ac69810bb3425a043def752fddc817"}"""),
          key_hash = ScriptId("exprvMHy4mAi1igbigE5BeEbEAE5ayx82ne5BA7UUXmfGpAiiVF3vx"),
          big_map = Decimal(1718),
          value = Some(Micheline(updateValue))
        )

        //when
        val balanceUpdate = sut.readBalance(ledgerId)(mapUpdate)

        //then
        balanceUpdate should not be defined

      }

      "allow computation of the correct key hash for an account address" in {
        //example taken from relevant conseil.js tests
        val hash = sut.Codecs.computeKeyHash(AccountId("tz1eEnQhbwf6trb8Q8mPb2RaPkNk2rN7BKi8"))
        hash shouldBe Success("exprv7U7pkJHbeUGhs7Wj8GTUnvfZfJRUcSCRo2EYqRSnUx1xWKrY9")
      }

      "compute the correct key hash for the sample babylon account address" in {
        //some values sampled from a real babylon use-case
        val hash = sut.Codecs.computeKeyHash(AccountId("tz1dae51wqhBwC7YdGiJAAU5JYwEvVH3Usf2"))
        hash shouldBe Success("exprvMHy4mAi1igbigE5BeEbEAE5ayx82ne5BA7UUXmfGpAiiVF3vx")
      }

      "allow computation of the correct hash from a binary string" in {
        //example taken from relevant conseil.js tests
        val bytes = sut.Codecs.readHex("050a000000160000cc04e65d3e38e4e8059041f27a649c76630f95e2")
        bytes shouldBe a[Success[_]]
        val hash = sut.Codecs.encodeBigMapKey(bytes.get)
        hash shouldBe Success("exprv7U7pkJHbeUGhs7Wj8GTUnvfZfJRUcSCRo2EYqRSnUx1xWKrY9")
      }

      "compute the correct hash from the sample babylon key bytes" in {
        //some values sampled from a real babylon use-case
        val bytes = sut.Codecs.readHex("050a000000160000cc04e65d3e38e4e8059041f27a649c76630f95e2")
        bytes shouldBe a[Success[_]]
        val hash = sut.Codecs.encodeBigMapKey(bytes.get)
        hash shouldBe Success("exprv7U7pkJHbeUGhs7Wj8GTUnvfZfJRUcSCRo2EYqRSnUx1xWKrY9")
      }

    }

}
