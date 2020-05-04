package tech.cryptonomic.conseil.tezos.michelson.contracts

import org.scalatest.{Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  AccountId,
  Contract,
  ContractId,
  Decimal,
  Micheline,
  Parameters,
  ScriptId
}

class StakerDaoTest extends WordSpec with Matchers with OptionValues {

  "The Token Contracts operations for the StakerDao contract" should {

      "read a balance update from big map diff" in {
        //given

        //values taken from mainnet operations
        val ledgerId = ContractId("KT1EctCuorV2NfVb1XTQgvzJ88MQtWP8cMMv")
        val mapId = 20
        val params = """
             |{
             |  "prim": "Right",
             |  "args": [
             |    {
             |      "prim": "Left",
             |      "args": [
             |        {
             |          "prim": "Left",
             |          "args": [
             |            {
             |              "prim": "Right",
             |              "args": [
             |                {
             |                  "prim": "Pair",
             |                  "args": [
             |                    {
             |                      "bytes": "00007374616b65722d64616f2f7265736572766f6972"
             |                    },
             |                    {
             |                      "prim": "Pair",
             |                      "args": [
             |                        {
             |                          "bytes": "0000c528aa23546060e4459c5b37df752eea5bf5edc3"
             |                        },
             |                        {
             |                          "int": "1"
             |                        }
             |                      ]
             |                    }
             |                  ]
             |                }
             |              ]
             |            }
             |          ]
             |        }
             |      ]
             |    }
             |  ]
             |}
          """.stripMargin

        val senderMapUpdate = Contract.BigMapUpdate(
          action = "update",
          key = Micheline("""{"bytes": "00007374616b65722d64616f2f7265736572766f6972"}"""),
          key_hash = ScriptId("exprucaLf6G5Robew77nwXRNR7gAbUJ3da2yAwqJdhyZCXZdEkLz8t"),
          big_map = Decimal(mapId),
          value = Some(Micheline("""{"int": "1499998"}"""))
        )

        val receiverMapUpdate = Contract.BigMapUpdate(
          action = "update",
          key = Micheline("""{"bytes": "0000c528aa23546060e4459c5b37df752eea5bf5edc3"}"""),
          key_hash = ScriptId("exprtv5jtq14XnqMvskagVojNgSd8bXjxTZtDYY58MtAek5gbLKA4C"),
          big_map = Decimal(mapId),
          value = Some(Micheline("""{"int": "1"}"""))
        )

        //register the token info
        val sut = TokenContracts.fromConfig(List(ledgerId -> "FA1.2-StakerDao"))
        //set the map id for the contract
        sut.setMapId(ledgerId, mapId)

        //when
        val balanceUpdates = List(senderMapUpdate, receiverMapUpdate).map(
          mapUpdate =>
            sut
              .readBalance(ledgerId)(
                diff = mapUpdate,
                params = Some(Left(Parameters(Micheline(params))))
              )
              .value
        )

        //then
        balanceUpdates should contain theSameElementsAs List(
          AccountId("tz1dcWXLS1UBeGc7EazGvoNE6D8YSzVkAsSa") -> BigInt(1),
          AccountId("tz1WAVpSaCFtLQKSJkrdVApCQC1TNK8iNxq9") -> BigInt(1499998)
        )

      }
    }

}
