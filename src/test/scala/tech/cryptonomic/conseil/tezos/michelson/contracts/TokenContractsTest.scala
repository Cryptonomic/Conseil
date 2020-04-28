package tech.cryptonomic.conseil.tezos.michelson.contracts

import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, Contract, ContractId, Decimal, Micheline, ScriptId}
import org.scalatest.OptionValues
import scala.util.Success

class TokenContractsTest extends WordSpec with Matchers with OptionValues {

  "The Token Contracts operations for a known FA1.2 compliant contract" should {

      "read a balance update from big maps diff for a registered contract" in {
        //given

        //values sampled from a real babylon use-case
        val ledgerId = ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux")
        val mapId = 1718
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
          big_map = Decimal(mapId),
          value = Some(Micheline(updateValue))
        )

        //register the token info
        val sut = TokenContracts.fromConfig(List(ledgerId -> "FA1.2"))
        //set the map id for the contract
        sut.setMapId(ledgerId, BigDecimal(mapId))

        //when
        val balanceUpdate = sut.readBalance(ledgerId)(mapUpdate)

        //then
        val (AccountId(id), balance) = balanceUpdate.value
        id shouldEqual "tz1dae51wqhBwC7YdGiJAAU5JYwEvVH3Usf2"
        balance shouldEqual BigInt(10000)

      }

      "read no balance update from big maps diff for a non-registered map id" in {
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

        //register the token info
        val sut = TokenContracts.fromConfig(List(ledgerId -> "FA1.2"))

        //when
        val balanceUpdate = sut.readBalance(ledgerId)(mapUpdate)
        balanceUpdate.isDefined shouldBe false
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

        //register the token info
        val sut = TokenContracts.fromConfig(List(ledgerId -> "FA1.2"))
        //set the map id for the contract
        sut.setMapId(ledgerId, BigDecimal(1718))

        //when
        val balanceUpdate = sut.readBalance(ledgerId)(mapUpdate)

        //then
        balanceUpdate should not be defined

      }

      "read no balance update from big maps diff for an unexpected value format of the call params" in {
        //given

        //some values sampled from a real babylon use-case
        val ledgerId = ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux")
        val mapId = 1718
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
          big_map = Decimal(mapId),
          value = Some(Micheline(updateValue))
        )

        //register the token info
        val sut = TokenContracts.fromConfig(List(ledgerId -> "FA1.2"))
        //set the map id for the contract
        sut.setMapId(ledgerId, BigDecimal(mapId))

        //when
        val balanceUpdate = sut.readBalance(ledgerId)(mapUpdate)

        //then
        balanceUpdate should not be defined

      }

      "allow computation of the correct key hash for an account address" in {
        //example taken from relevant conseil.js tests
        val hash = TokenContracts.Codecs.computeKeyHash(AccountId("tz1eEnQhbwf6trb8Q8mPb2RaPkNk2rN7BKi8"))
        hash shouldBe Success("exprv7U7pkJHbeUGhs7Wj8GTUnvfZfJRUcSCRo2EYqRSnUx1xWKrY9")
      }

      "compute the correct key hash for the sample babylon account address" in {
        //some values sampled from a real babylon use-case
        val hash = TokenContracts.Codecs.computeKeyHash(AccountId("tz1dae51wqhBwC7YdGiJAAU5JYwEvVH3Usf2"))
        hash shouldBe Success("exprvMHy4mAi1igbigE5BeEbEAE5ayx82ne5BA7UUXmfGpAiiVF3vx")
      }

      "allow computation of the correct hash from a binary string" in {
        //example taken from relevant conseil.js tests
        val bytes = scorex.util.encode.Base16.decode("050a000000160000cc04e65d3e38e4e8059041f27a649c76630f95e2")
        bytes shouldBe a[Success[_]]
        val hash = TokenContracts.Codecs.encodeBigMapKey(bytes.get)
        hash shouldBe Success("exprv7U7pkJHbeUGhs7Wj8GTUnvfZfJRUcSCRo2EYqRSnUx1xWKrY9")
      }

      "compute the correct hash from the sample babylon key bytes" in {
        //some values sampled from a real babylon use-case
        val bytes = scorex.util.encode.Base16.decode("050a000000160000cc04e65d3e38e4e8059041f27a649c76630f95e2")
        bytes shouldBe a[Success[_]]
        val hash = TokenContracts.Codecs.encodeBigMapKey(bytes.get)
        hash shouldBe Success("exprv7U7pkJHbeUGhs7Wj8GTUnvfZfJRUcSCRo2EYqRSnUx1xWKrY9")
      }

    }

}
