package tech.cryptonomic.conseil.indexer.tezos.michelson.contracts

import com.softwaremill.diffx.scalatest.DiffShouldMatcher._
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Contract.CompatBigMapDiff
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TNSContract.LookupMapReference
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class TNSContractsTest extends ConseilSpec {

  import com.softwaremill.diffx.generic.auto._

  "The TNS Contract operations for a known configured contract" should {

      "read a valid lookup map reference if a transaction is passed with valid data" in {
        //given

        //register the tns
        val sut = new TNSContract.ConfiguredContract(tnsContractId)
        //set the proper map ids
        sut.setMapIds(tnsContractId, lookupId, reverseLookupId)

        //when
        val lookupReferenceResults = sut.readLookupMapReference(Left(ValidTransactionData.transaction)).value

        lookupReferenceResults shouldMatchTo (
          LookupMapReference(
            contractId = tnsContractId,
            lookupName = TNSContract.Name("me want tacos"),
            resolver = makeAccountId("tz2TSvNTh2epDMhZHrw73nV9piBX7kLZ9K9m"),
            mapId = TNSContract.BigMapId(lookupId),
            mapKeyHash = ScriptId("exprvT4zX5M2nUKv2msyEeRTQx6zYoMh6rpnpWeDJhEranRRt8Hea9")
          )
        )
      }

      "fail to read a valid lookup map reference if a transaction is passed with invalid map ids" in {
        //given

        //register the tns
        val sut = new TNSContract.ConfiguredContract(tnsContractId)
        //set the differing map ids
        sut.setMapIds(tnsContractId, lookupId + 1, reverseLookupId)

        //when
        val lookupReferenceResults = sut.readLookupMapReference(Left(ValidTransactionData.transaction))

        lookupReferenceResults shouldBe empty

      }

      "fail to read a valid lookup map reference if a transaction is passed with the wrong destination contract" in {
        //given

        val transaction = ValidTransactionData.transaction.copy(destination = ContractId("not me"))

        //register the tns
        val sut = new TNSContract.ConfiguredContract(tnsContractId)
        //set the proper map ids
        sut.setMapIds(tnsContractId, lookupId, reverseLookupId)

        //when
        val lookupReferenceResults = sut.readLookupMapReference(Left(transaction))

        lookupReferenceResults shouldBe empty

      }

      "fail to read a valid lookup map reference if a transaction is passed with non-compliant parameters" in {
        //given
        val parameters = Parameters(
          Micheline("""Pair 6000 "me want chili""""),
          entrypoint = Some("registerName")
        )

        val transaction = ValidTransactionData.transaction.copy(parameters = Some(Left(parameters)))

        //register the tns
        val sut = new TNSContract.ConfiguredContract(tnsContractId)
        //set the proper map ids
        sut.setMapIds(tnsContractId, lookupId, reverseLookupId)

        //when
        val lookupReferenceResults = sut.readLookupMapReference(Left(transaction))

        lookupReferenceResults shouldBe empty

      }

      "fail to read a valid lookup map reference if a transaction is passed with no entrypoint for parameters" in {
        //given
        val parameters = ValidTransactionData.parameters.copy(entrypoint = None)

        val transaction = ValidTransactionData.transaction.copy(parameters = Some(Left(parameters)))

        //register the tns
        val sut = new TNSContract.ConfiguredContract(tnsContractId)
        //set the proper map ids
        sut.setMapIds(tnsContractId, lookupId, reverseLookupId)

        //when
        val lookupReferenceResults = sut.readLookupMapReference(Left(transaction))

        lookupReferenceResults shouldBe empty

      }

      "read a valid lookup map content if called with valid data" in {
        //given

        //register the tns, no check on the registered map ids is necessary
        val sut = new TNSContract.ConfiguredContract(tnsContractId)

        //when
        val lookupContentResult =
          sut.readLookupMapContent(tnsContractId, ValidTransactionData.reverseLookupMapContent).value

        lookupContentResult shouldMatchTo(
          TNSContract.NameRecord(
            name = "me want tacos",
            updated = "False",
            resolver = "tz2TSvNTh2epDMhZHrw73nV9piBX7kLZ9K9m",
            registeredAt = "2020-03-31T03:37:11Z",
            registrationPeriod = "6000",
            owner = "tz1aTPZXhAmKmisY2iRe6dEhxwe7Db3cPoVc"
          )
        )

      }

      "fail to read a valid lookup map content if called with the wrong contract reference" in {
        //given

        //register the tns, no check on the registered map ids is necessary
        val sut = new TNSContract.ConfiguredContract(tnsContractId)

        //when
        val lookupContentResult =
          sut.readLookupMapContent(ContractId("not here"), ValidTransactionData.reverseLookupMapContent)

        lookupContentResult shouldBe empty

      }

      "fail to read a valid lookup map content if called with a non compliant micheline content" in {
        //given
        val content =
          """{
            |  "prim": "Pair",
            |  "args": [
            |    {
            |      "int": "6000"
            |    },
            |    {
            |      "string": "me want tacos"
            |    }
            |  ]
            |}""".stripMargin

        //register the tns, no check on the registered map ids is necessary
        val sut = new TNSContract.ConfiguredContract(tnsContractId)

        //when
        val lookupContentResult =
          sut.readLookupMapContent(tnsContractId, content)

        lookupContentResult shouldBe empty

      }

      "return negative results if using the fallback contract" in {
        val sut = TNSContract.noContract

        sut.isKnownRegistrar(tnsContractId) shouldBe false

        val referenceResult = sut.readLookupMapReference(Left(ValidTransactionData.transaction))
        referenceResult shouldBe empty

        val contentResult = sut.readLookupMapContent(tnsContractId, ValidTransactionData.reverseLookupMapContent)
        contentResult shouldBe empty
      }
    }

  //values sampled from a real carthage use-case
  private val tnsContractId = ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux")
  private val (lookupId, reverseLookupId) = (692, 691)
  private object ValidTransactionData {

    private val mapDiffs: List[CompatBigMapDiff] = List(
      createUpdate(
        mapId = lookupId,
        key = """{"string":"me want tacos"}"""",
        keyHash = "exprvT4zX5M2nUKv2msyEeRTQx6zYoMh6rpnpWeDJhEranRRt8Hea9"
      ),
      createUpdate(
        mapId = reverseLookupId,
        key = """{"bytes":"0001d1de72f5fccb2899c0d2223a22fd5cb94a70b4a9"}""",
        keyHash = "expruLf2K5ap9bVwXghNUTWetgqC83FZBRgcomVtTVtKtCSuZy8B4S"
      )
    )

    val transactionResult = OperationResult.Transaction(
      status = "applied",
      allocated_destination_contract = None,
      balance_updates = None,
      big_map_diff = Some(mapDiffs),
      consumed_gas = None,
      originated_contracts = None,
      paid_storage_size_diff = None,
      storage = None,
      storage_size = None,
      errors = None
    )

    val parameters = Parameters(
      Micheline("""Pair 6000 (Pair "me want tacos" "tz2TSvNTh2epDMhZHrw73nV9piBX7kLZ9K9m")"""),
      entrypoint = Some("registerName")
    )

    val transaction = Transaction(
      counter = PositiveDecimal(0),
      amount = PositiveDecimal(0),
      fee = PositiveDecimal(0),
      gas_limit = PositiveDecimal(0),
      storage_limit = PositiveDecimal(0),
      source = PublicKeyHash(""),
      destination = tnsContractId,
      parameters = Some(Left(parameters)),
      parameters_micheline = None,
      metadata = ResultMetadata(transactionResult, List.empty, None)
    )

    val reverseLookupMapContent =
      """{
        | "args": [
        |  {
        |      "args": [
        |          {
        |              "prim": "False"
        |          },
        |          {
        |              "args": [
        |                  {
        |                      "string": "me want tacos"
        |                  },
        |                  {
        |                      "string": "tz1aTPZXhAmKmisY2iRe6dEhxwe7Db3cPoVc"
        |                  }
        |              ],
        |              "prim": "Pair"
        |          }
        |      ],
        |      "prim": "Pair"
        |  },
        |  {
        |      "args": [
        |          {
        |              "string": "2020-03-31T03:37:11Z"
        |          },
        |          {
        |              "args": [
        |                  {
        |                      "int": "6000"
        |                  },
        |                  {
        |                      "string": "tz2TSvNTh2epDMhZHrw73nV9piBX7kLZ9K9m"
        |                  }
        |              ],
        |              "prim": "Pair"
        |          }
        |      ],
        |      "prim": "Pair"
        |  }
        | ],
        | "prim": "Pair"
        |}""".stripMargin

  }

  /* Utility factory for test BigMapUpdate */
  private def createUpdate(mapId: BigDecimal, key: String, keyHash: String) =
    Left(
      Contract.BigMapUpdate(
        action = "update",
        key = Micheline(key),
        key_hash = ScriptId(keyHash),
        big_map = Decimal(mapId),
        value = None
      )
    )

}
