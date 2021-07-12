package tech.cryptonomic.conseil.indexer.tezos.michelson.contracts

import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.testkit.ConseilSpec
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts.Tzip16
import tech.cryptonomic.conseil.indexer.tezos.michelson.dto.{MichelsonBytesConstant, MichelsonInstruction}
import tech.cryptonomic.conseil.indexer.tezos.michelson.parser.JsonParser

class TokenContractsTest extends ConseilSpec {

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
        val (PublicKeyHash(id), balance) = balanceUpdate.value
        id shouldEqual "tz1dae51wqhBwC7YdGiJAAU5JYwEvVH3Usf2"
        balance shouldEqual BigInt(10000)

      }

      "read a balance update from big map diffs for a registered contract with bytes-representation of accounts" in {
        //given

        //values sampled from tzBTC use-case
        val ledgerId = ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux")
        val mapId = 1718
        val updateValue = """{ "bytes": "0507070086bb230200000000"}"""
        val mapUpdate = Contract.BigMapUpdate(
          action = "update",
          key = Micheline(
            """{ "bytes": "05070701000000066c65646765720a0000001600008d34410a9ccfa23728e02ca58cfaeb67b69d99fb" }"""
          ),
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
        val (PublicKeyHash(id), balance) = balanceUpdate.value
        id shouldEqual "tz1YWeZqt67XGHUvPFBmfMfWAoZtXELTWtKh"
        balance shouldEqual BigInt(290502)

      }

      "read no balance update from big maps diff for a non-registered map id" in {
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

        //when
        val balanceUpdate = sut.readBalance(ledgerId)(mapUpdate)
        balanceUpdate.isDefined shouldBe false
      }

      "read no balance update from big maps diff for a non-matching map id" in {
        //given

        //some values sampled from a real babylon use-case
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
          big_map = Decimal(100),
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

      "ignore an update referring to the total supply for tzBTC-like tokens" in {
        //given

        //some values sampled from a real babylon use-case
        val ledgerId = ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux")
        val mapId = 1718
        val mapUpdate = Contract.BigMapUpdate(
          action = "update",
          key = Micheline("""{ "bytes" : "0000c4ce21c7a7ac69810bb3425a043def752fddc817" }"""),
          key_hash = ScriptId("exprunzteC5uyXRHbKnqJd3hUMGTWE9Gv5EtovDZHnuqu6SaGViV3N"),
          big_map = Decimal(mapId),
          value = Some(Micheline("""{ "bytes": "050098e1e8d78a02" } """))
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

      "Parse and extract micheline from location " in {
        val micheline = """{"prim":"Pair","args":[{"prim":"Pair","args":[{"string":"tz1UBZUkXpKGhYsP5KtzDNqLLchwF4uHrGjw"},{"prim":"Pair","args":[{"int":"0"},[]]}]},{"prim":"Pair","args":[{"prim":"Pair","args":[[{"prim":"Elt","args":[{"string":""},{"bytes":"697066733a2f2f516d534263385175796e55376241725547746a774352685a55624a795a51417272637a4b6e714d37685a50746656"}]}],[]]},{"prim":"Pair","args":[{"prim":"False"},[]]}]}]}"""

        val res = JsonParser.parse[MichelsonInstruction](micheline)

        val path = "MichelsonSingleInstruction:Pair::1;MichelsonType:Pair::0;MichelsonType:Pair::0;MichelsonInstructionSequence:0;MichelsonSingleInstruction:Elt::1"

        res.toOption.get.getAtPath(path)
        val metadataAddress = Tzip16.extractTzip16MetadataLocationFromParameters(Micheline(micheline), Some(path))

        metadataAddress shouldBe Some("""ipfs://QmSBc8QuynU7bArUGtjwCRhZUbJyZQArrczKnqM7hZPtfV""")

      }

      "Search for instruction and extract value from found path" in {
        val micheline = """{"prim":"Pair","args":[{"prim":"Pair","args":[[],{"prim":"Pair","args":[[],{"string":"tz1Y1j7FK1X9Rrv2VdPz5bXoU7SszF8W1RnK"}]}]},{"prim":"Pair","args":[{"prim":"Pair","args":[[{"prim":"Elt","args":[{"string":""},{"bytes":"697066733a2f2f516d5946375a6438624b506b7062783378434d3975693848796839574e67653133747a6f5644526d4c44526b364e"}]}],[]]},{"prim":"Pair","args":[[],[]]}]}]}"""

        val res = JsonParser.parse[MichelsonInstruction](micheline)
        //MichelsonSingleInstruction:Pair::1;MichelsonType:Pair::1;MichelsonInstructionSequence:0;MichelsonSingleInstruction:Elt::1
        val searchResult = res.toOption.get.findInstruction(MichelsonBytesConstant(""), startsWith = Some("6970"))

        val xd = "MichelsonSingleInstruction:Pair::1;MichelsonType:Pair::0;MichelsonType:Pair::0;MichelsonInstructionSequence:0;MichelsonSingleInstruction:Elt::1"

        res.toOption.get.getAtPath(xd)
        val metadataAddress = Tzip16.extractTzip16MetadataLocationFromParameters(Micheline(micheline), Some(searchResult.head))

        metadataAddress shouldBe Some("ipfs://QmYF7Zd8bKPkpbx3xCM9ui8Hyh9WNge13tzoVDRmLDRk6N")

      }
    }

}
