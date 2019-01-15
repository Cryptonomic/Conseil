package tech.cryptonomic.conseil.util

import org.scalatest.{WordSpec, Matchers, EitherValues}
import tech.cryptonomic.conseil.tezos.TezosTypes._

class JsonDecodersTest extends WordSpec with Matchers with EitherValues {

  import JsonDecoders.Circe._
  import JsonDecoders.Circe.Operations._
  import io.circe.parser.decode

  /* defines example tezos json definitions of operations and typed counterparts used in the tests */
  trait OperationsJsonData {
    import TezosOperations._
    import TezosOperations.OperationMetadata.BalanceUpdate
    import TezosOperations.OperationResult.Error

    val errorJson =
      """{
        |  "kind": "temporary",
        |  "id": "proto.alpha.gas_exhausted.operation"
        |}""".stripMargin

    val expectedError = Error("""{"kind":"temporary","id":"proto.alpha.gas_exhausted.operation"}""")

    val michelsonJson =
      """{
      |  "prim": "code",
      |  "args": [
      |      {
      |        "prim": "CAR"
      |      },
      |      {
      |        "prim": "NIL",
      |        "args": [
      |          {
      |            "prim": "operation"
      |          }
      |        ]
      |      },
      |      {
      |        "prim": "PAIR"
      |      }
      |  ]
      |}""".stripMargin

    val expectedMichelson = MichelsonV1("""{"prim":"code","args":[{"prim":"CAR"},{"prim":"NIL","args":[{"prim":"operation"}]},{"prim":"PAIR"}]}""")

    val bigmapdiffJson =
      s"""{
      |  "key_hash": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
      |  "key": $michelsonJson
      |}""".stripMargin

    val expectedBigMapDiff =
      Contract.BigMapDiff(
        key_hash = ScriptId("tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio"),
        key = expectedMichelson,
        value = None
      )

    val scriptJson =
      """{
      |  "code": [
      |      {
      |          "prim": "parameter",
      |          "args": [
      |              {
      |                  "prim": "string"
      |              }
      |          ]
      |      },
      |      {
      |          "prim": "storage",
      |          "args": [
      |              {
      |                  "prim": "string"
      |              }
      |          ]
      |      },
      |      {
      |          "prim": "code",
      |          "args": [
      |              [
      |                  {
      |                      "prim": "CAR"
      |                  },
      |                  {
      |                      "prim": "NIL",
      |                      "args": [
      |                          {
      |                              "prim": "operation"
      |                          }
      |                      ]
      |                  },
      |                  {
      |                      "prim": "PAIR"
      |                  }
      |              ]
      |          ]
      |      }
      |  ],
      |  "storage": {
      |      "string": "hello"
      |  }
      |}""".stripMargin

    val expectedScript =
      Scripted.Contracts(
        code = MichelsonV1("""[{"prim":"parameter","args":[{"prim":"string"}]},{"prim":"storage","args":[{"prim":"string"}]},{"prim":"code","args":[[{"prim":"CAR"},{"prim":"NIL","args":[{"prim":"operation"}]},{"prim":"PAIR"}]]}]"""),
        storage = MichelsonV1("""{"string":"hello"}""")
      )

    val endorsementJson =
      """{
        |  "kind": "endorsement",
        |  "level": 182308,
        |  "metadata": {
        |      "balance_updates": [
        |          {
        |              "kind": "contract",
        |              "contract": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
        |              "change": "-256000000"
        |          },
        |          {
        |              "kind": "freezer",
        |              "category": "deposits",
        |              "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
        |              "level": 1424,
        |              "change": "256000000"
        |          },
        |          {
        |              "kind": "freezer",
        |              "category": "rewards",
        |              "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
        |              "level": 1424,
        |              "change": "4000000"
        |          }
        |      ],
        |      "delegate": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
        |      "slots": [
        |          29,
        |          27,
        |          20,
        |          17
        |      ]
        |  }
        |}""".stripMargin

    val expectedEndorsement =
      Endorsement(
        level = 182308,
        metadata = EndorsementMetadata(
          slots =List(29, 27, 20, 17),
          delegate = PublicKeyHash("tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio"),
          balance_updates = List(
            BalanceUpdate(
              kind = "contract",
              contract = Some(ContractId("tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio")),
              change = -256000000,
              category = None,
              delegate = None,
              level = None
            ),
            BalanceUpdate(
              kind = "freezer",
              category = Some("deposits"),
              delegate = Some(PublicKeyHash("tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio")),
              change = 256000000,
              contract = None,
              level = Some(1424)
            ),
            BalanceUpdate(
              kind = "freezer",
              category = Some("rewards"),
              delegate = Some(PublicKeyHash("tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio")),
              change = 4000000,
              contract = None,
              level = Some(1424)
            )
          )
      )
    )

    val nonceRevelationJson =
      """{
      |  "kind": "seed_nonce_revelation",
      |  "level": 199360,
      |  "nonce": "4ddd711e76cf8c71671688aff7ce9ff67bf24bc16be31cd5dbbdd267456745e0",
      |  "metadata": {
      |      "balance_updates": [
      |          {
      |              "kind": "freezer",
      |              "category": "rewards",
      |              "delegate": "tz1aWXP237BLwNHJcCD4b3DutCevhqq2T1Z9",
      |              "level": 1557,
      |              "change": "125000"
      |          }
      |      ]
      |  }
      |}""".stripMargin

    val expectedNonceRevelation =
      SeedNonceRevelation(
        level = 199360,
        nonce = Nonce("4ddd711e76cf8c71671688aff7ce9ff67bf24bc16be31cd5dbbdd267456745e0"),
        metadata = BalanceUpdatesMetadata(
          balance_updates = List(
            BalanceUpdate(
              kind = "freezer",
              category = Some("rewards"),
              delegate = Some(PublicKeyHash("tz1aWXP237BLwNHJcCD4b3DutCevhqq2T1Z9")),
              level = Some(1557),
              change = 125000,
              contract = None
            )
          )
        )
      )

    val activationJson =
      """{
      |  "kind": "activate_account",
      |  "pkh": "tz1ieofA4fCLAnSgYbE9ZgDhdTuet34qGZWw",
      |  "secret": "026a9a6b7ea07238dab3e4322d93a6abe8da278a",
      |  "metadata": {
      |      "balance_updates": [
      |          {
      |              "kind": "contract",
      |              "contract": "tz1ieofA4fCLAnSgYbE9ZgDhdTuet34qGZWw",
      |              "change": "13448692695"
      |          }
      |      ]
      |  }
      |}""".stripMargin

    val expectedActivation =
      ActivateAccount(
        pkh = PublicKeyHash("tz1ieofA4fCLAnSgYbE9ZgDhdTuet34qGZWw"),
        secret = Secret("026a9a6b7ea07238dab3e4322d93a6abe8da278a"),
        metadata = BalanceUpdatesMetadata(
          balance_updates = List(
            BalanceUpdate(
              kind = "contract",
              contract = Some(ContractId("tz1ieofA4fCLAnSgYbE9ZgDhdTuet34qGZWw")),
              change = 13448692695L,
              category = None,
              delegate = None,
              level = None
            )
          )
        )
      )

    val revealJson =
      """{
      |  "kind": "reveal",
      |  "source": "KT1PPuBrvCGpJt54hVBgXMm2sKa6QpSwKrJq",
      |  "fee": "10000",
      |  "counter": "1",
      |  "gas_limit": "10000",
      |  "storage_limit": "257",
      |  "public_key": "edpktxRxk9r61tjEZCt5a2hY2MWC3gzECGL7FXS1K6WXGG28hTFdFz",
      |  "metadata": {
      |      "balance_updates": [
      |          {
      |              "kind": "contract",
      |              "contract": "KT1PPuBrvCGpJt54hVBgXMm2sKa6QpSwKrJq",
      |              "change": "-10000"
      |          },
      |          {
      |              "kind": "freezer",
      |              "category": "fees",
      |              "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
      |              "level": 1561,
      |              "change": "10000"
      |          }
      |      ],
      |      "operation_result": {
      |          "status": "applied",
      |          "consumed_gas": "10000"
      |      },
      |      "internal_operation_results": [
      |          {
      |              "kind": "reveal",
      |              "nonce": 1234,
      |              "public_key": "edpktxRxk9r61tjEZCt5a2hY2MWC3gzECGL7FXS1K6WXGG28hTFdFz",
      |              "source": "KT1PPuBrvCGpJt54hVBgXMm2sKa6QpSwKrJq",
      |              "result": {
      |                  "status": "applied",
      |                  "consumed_gas": "10000"
      |              }
      |          }
      |      ]
      |  }
      |}""".stripMargin

    //ignores the internal_operation_result
    val expectedReveal =
      Reveal(
        source = ContractId("KT1PPuBrvCGpJt54hVBgXMm2sKa6QpSwKrJq"),
        fee = PositiveDecimal(10000),
        counter = PositiveDecimal(1),
        gas_limit = PositiveDecimal(10000),
        storage_limit = PositiveDecimal(257),
        public_key = PublicKey("edpktxRxk9r61tjEZCt5a2hY2MWC3gzECGL7FXS1K6WXGG28hTFdFz"),
        metadata =
          ResultMetadata[OperationResult.Reveal](
            balance_updates = List(
              BalanceUpdate(
                kind = "contract",
                contract = Some(ContractId("KT1PPuBrvCGpJt54hVBgXMm2sKa6QpSwKrJq")),
                change = -10000L,
                category = None,
                delegate = None,
                level = None
              ),
              BalanceUpdate(
                kind = "freezer",
                category = Some("fees"),
                delegate = Some(PublicKeyHash("tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889")),
                level = Some(1561),
                change = 10000L,
                contract = None
              )
            ),
            operation_result =
              OperationResult.Reveal(
                status = "applied",
                consumed_gas = Some(Decimal(10000)),
                errors = None
              )
          )
      )

    //used to check if internal errors are correctly decoded
    val failedRevealJson =
      """{
      |  "kind": "reveal",
      |  "source": "tz1VXaVvVyLfZNWCcpHpKNSg61TEJVZtNJKf",
      |  "fee": "1300",
      |  "counter": "454133",
      |  "gas_limit": "100",
      |  "storage_limit": "0",
      |  "public_key": "edpkuuNeGwDGBBGNdp7eEDUnb3tJKfhyxoo9A8GkDbdHEaPYYG8MJj",
      |  "metadata": {
      |    "balance_updates": [
      |      {
      |        "kind": "contract",
      |        "contract": "tz1VXaVvVyLfZNWCcpHpKNSg61TEJVZtNJKf",
      |        "change": "-1300"
      |      },
      |      {
      |        "kind": "freezer",
      |        "category": "fees",
      |        "delegate": "tz1Ke2h7sDdakHJQh8WX4Z372du1KChsksyU",
      |        "level": 1567,
      |        "change": "1300"
      |      }
      |    ],
      |    "operation_result": {
      |      "status": "failed",
      |      "errors": [
      |        {
      |          "kind": "temporary",
      |          "id": "proto.alpha.gas_exhausted.operation"
      |        }
      |      ]
      |    }
      |  }
      }""".stripMargin

    val expectedFailedReveal =
      Reveal(
        source = ContractId("tz1VXaVvVyLfZNWCcpHpKNSg61TEJVZtNJKf"),
        fee = PositiveDecimal(1300),
        counter = PositiveDecimal(454133),
        gas_limit = PositiveDecimal(100),
        storage_limit = PositiveDecimal(0),
        public_key = PublicKey("edpkuuNeGwDGBBGNdp7eEDUnb3tJKfhyxoo9A8GkDbdHEaPYYG8MJj"),
        metadata =
          ResultMetadata(
            balance_updates = List(
              BalanceUpdate(
                kind = "contract",
                contract = Some(ContractId("tz1VXaVvVyLfZNWCcpHpKNSg61TEJVZtNJKf")),
                change = -1300L,
                category = None,
                delegate = None,
                level = None
              ),
              BalanceUpdate(
                kind = "freezer",
                category = Some("fees"),
                delegate = Some(PublicKeyHash("tz1Ke2h7sDdakHJQh8WX4Z372du1KChsksyU")),
                level = Some(1567),
                change = 1300L,
                contract = None
              )
            ),
            operation_result =
              OperationResult.Reveal(
                status = "failed",
                errors = Some(List(Error("""{"kind":"temporary","id":"proto.alpha.gas_exhausted.operation"}"""))),
                consumed_gas = None
              )
          )
      )

    val transactionJson =
      """{
      |  "kind": "transaction",
      |  "source": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
      |  "fee": "1416",
      |  "counter": "407940",
      |  "gas_limit": "11475",
      |  "storage_limit": "0",
      |  "amount": "0",
      |  "destination": "KT1CkkM5tYe9xRMQMbnayaULGoGaeBUH2Riy",
      |  "parameters": {
      |      "string": "world"
      |  },
      |  "metadata": {
      |      "balance_updates": [
      |          {
      |              "kind": "contract",
      |              "contract": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
      |              "change": "-1416"
      |          },
      |          {
      |              "kind": "freezer",
      |              "category": "fees",
      |              "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
      |              "level": 1583,
      |              "change": "1416"
      |          }
      |      ],
      |      "operation_result": {
      |          "status": "applied",
      |          "storage": {
      |              "string": "world"
      |          },
      |          "consumed_gas": "11375",
      |          "storage_size": "46"
      |      }
      |  }
      |}""".stripMargin

    val expectedTransaction =
      Transaction(
        source = ContractId("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
        fee = PositiveDecimal(1416),
        counter = PositiveDecimal(407940),
        gas_limit = PositiveDecimal(11475),
        storage_limit = PositiveDecimal(0),
        amount = PositiveDecimal(0),
        destination = ContractId("KT1CkkM5tYe9xRMQMbnayaULGoGaeBUH2Riy"),
        parameters = Some(MichelsonV1("""{"string":"world"}""")),
        metadata = ResultMetadata(
          balance_updates = List(
            BalanceUpdate(
              kind = "contract",
              contract = Some(ContractId("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR")),
              change = -1416L,
              category = None,
              delegate = None,
              level = None
            ),
            BalanceUpdate(
              kind = "freezer",
              category = Some("fees"),
              delegate = Some(PublicKeyHash("tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u")),
              level = Some(1583),
              change = 1416L,
              contract = None
            )
          ),
          operation_result =
            OperationResult.Transaction(
              status = "applied",
              storage = Some(MichelsonV1("""{"string":"world"}""")),
              consumed_gas = Some(Decimal(11375)),
              storage_size = Some(Decimal(46)),
              allocated_destination_contract = None,
              balance_updates = None,
              big_map_diff = None,
              originated_contracts = None,
              paid_storage_size_diff = None,
              errors = None
            )
        )
    )

    val originationJson =
      s"""{
      |  "kind": "origination",
      |  "source": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
      |  "fee": "1441",
      |  "counter": "407941",
      |  "gas_limit": "11362",
      |  "storage_limit": "323",
      |  "manager_pubkey": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
      |  "balance": "1000000",
      |  "spendable": false,
      |  "delegatable": false,
      |  "script": $scriptJson,
      |  "metadata": {
      |      "balance_updates": [
      |          {
      |              "kind": "contract",
      |              "contract": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
      |              "change": "-1441"
      |          },
      |          {
      |              "kind": "freezer",
      |              "category": "fees",
      |              "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
      |              "level": 1583,
      |              "change": "1441"
      |          }
      |      ],
      |      "operation_result": {
      |          "status": "applied",
      |          "balance_updates": [
      |              {
      |                  "kind": "contract",
      |                  "contract": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
      |                  "change": "-46000"
      |              },
      |              {
      |                  "kind": "contract",
      |                  "contract": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
      |                  "change": "-257000"
      |              },
      |              {
      |                  "kind": "contract",
      |                  "contract": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
      |                  "change": "-1000000"
      |              },
      |              {
      |                  "kind": "contract",
      |                  "contract": "KT1VuJAgTJT5x2Y2S3emAVSbUA5nST7j3QE4",
      |                  "change": "1000000"
      |              }
      |          ],
      |          "originated_contracts": [
      |              "KT1VuJAgTJT5x2Y2S3emAVSbUA5nST7j3QE4"
      |          ],
      |          "consumed_gas": "11262",
      |          "storage_size": "46",
      |          "paid_storage_size_diff": "46"
      |      }
      |  }
      |}""".stripMargin

    val expectedOrigination =
      Origination(
        source = ContractId("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
        fee = PositiveDecimal(1441),
        counter = PositiveDecimal(407941),
        gas_limit = PositiveDecimal(11362),
        storage_limit = PositiveDecimal(323),
        manager_pubkey = PublicKeyHash("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
        balance = PositiveDecimal(1000000),
        spendable = Some(false),
        delegatable = Some(false),
        delegate = None,
        script = Some(expectedScript),
        metadata = ResultMetadata(
          balance_updates = List(
            BalanceUpdate(
              kind = "contract",
              contract = Some(ContractId("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR")),
              change = -1441L,
              category = None,
              delegate = None,
              level = None
            ),
            BalanceUpdate(
              kind = "freezer",
              category = Some("fees"),
              delegate = Some(PublicKeyHash("tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889")),
              level = Some(1583),
              change = 1441L,
              contract = None
            )
          ),
          operation_result =
            OperationResult.Origination(
              status = "applied",
              balance_updates = Some(List(
                BalanceUpdate(
                  kind = "contract",
                  contract = Some(ContractId("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR")),
                  change = -46000L,
                  category = None,
                  delegate = None,
                  level = None
                ),
                BalanceUpdate(
                  kind = "contract",
                  contract = Some(ContractId("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR")),
                  change = -257000L,
                  category = None,
                  delegate = None,
                  level = None
                ),
                BalanceUpdate(
                  kind = "contract",
                  contract = Some(ContractId("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR")),
                  change = -1000000L,
                  category = None,
                  delegate = None,
                  level = None
                ),
                BalanceUpdate(
                  kind = "contract",
                  contract = Some(ContractId("KT1VuJAgTJT5x2Y2S3emAVSbUA5nST7j3QE4")),
                  change = 1000000L,
                  category = None,
                  delegate = None,
                  level = None
                )
              )),
              originated_contracts = Some(List(ContractId("KT1VuJAgTJT5x2Y2S3emAVSbUA5nST7j3QE4"))),
              consumed_gas = Some(Decimal(11262)),
              storage_size = Some(Decimal(46)),
              paid_storage_size_diff = Some(Decimal(46)),
              errors = None
            )
          )
        )

    val operationsGroupJson =
      s"""{
        |  "protocol": "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
        |  "chain_id": "NetXSzLHKwSumh7",
        |  "hash": "oobQTfjxVhEhbtWg3n51YDAfYr9HmXPpGVTqGhxiorsq4jAc53n",
        |  "branch": "BKs7LZjCLcPczh52nR3DdcqAFg2VKF89ZkW47UTPFCuBfMe7wpy",
        |  "contents": [
        |    $endorsementJson,
        |    $nonceRevelationJson,
        |    $activationJson,
        |    $failedRevealJson,
        |    $transactionJson,
        |    $originationJson
        |  ],
        |  "signature": "sigvs8WYSK3AgpWwpUXg8B9NyJjPcLYNqmZvNFR3UmtiiLfPTNZSEeU8qRs6LVTquyVUDdu4imEWTqD6sinURdJAmRoyffy9"
        |}""".stripMargin

    val expectedGroup =
      TezosOperations.Group(
        protocol = "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
        chain_id = Some(ChainId("NetXSzLHKwSumh7")),
        hash = OperationHash("oobQTfjxVhEhbtWg3n51YDAfYr9HmXPpGVTqGhxiorsq4jAc53n"),
        branch = BlockHash("BKs7LZjCLcPczh52nR3DdcqAFg2VKF89ZkW47UTPFCuBfMe7wpy"),
        signature = Some(Signature("sigvs8WYSK3AgpWwpUXg8B9NyJjPcLYNqmZvNFR3UmtiiLfPTNZSEeU8qRs6LVTquyVUDdu4imEWTqD6sinURdJAmRoyffy9")),
        contents = List(expectedEndorsement, expectedNonceRevelation, expectedActivation, expectedFailedReveal, expectedTransaction, expectedOrigination)
      )

  }

  "the json decoders" should {

    val validB58Hash = "signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
    val invalidB58Hash = "signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSl"
    val alphanumneric = "asdopkjfap2398ufa3908wimv3pw98vja3pw98v"
    val invalidAlphanumeric = "@*;akjfa80330"
    val invalidJson = """{wrongname: "name"}"""

    /** wrap in quotes to be a valid json string */
    val jsonStringOf = (content: String) => s""""$content""""

    "decode valid json base58check strings into a PublicKey" in {
      val decoded = decode[PublicKey](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe PublicKey(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a PublicKey" in {
      val decoded = decode[PublicKey](jsonStringOf(invalidB58Hash))
      decoded shouldBe 'left
    }

    "decode valid json base58check strings into a PublicKeyHash" in {
      val decoded = decode[PublicKeyHash](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe PublicKeyHash(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a PublicKeyHash" in {
      val decoded = decode[PublicKeyHash](jsonStringOf(invalidB58Hash))
      decoded shouldBe 'left
    }

    "decode valid json base58check strings into a Signature" in {
      val decoded = decode[Signature](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe Signature(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a Signature" in {
      val decoded = decode[Signature](jsonStringOf(invalidB58Hash))
      decoded shouldBe 'left
    }

    "decode valid json base58check strings into a BlockHash" in {
      val decoded = decode[BlockHash](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe BlockHash(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a BlockHash" in {
      val decoded = decode[BlockHash](jsonStringOf(invalidB58Hash))
      decoded shouldBe 'left
    }

    "decode valid json base58check strings into a OperationHash" in {
      val decoded = decode[OperationHash](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe OperationHash(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a OperationHash" in {
      val decoded = decode[OperationHash](jsonStringOf(invalidB58Hash))
      decoded shouldBe 'left
    }

    "decode valid json base58check strings into a AccountId" in {
      val decoded = decode[AccountId](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe AccountId(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a AccountId" in {
      val decoded = decode[AccountId](jsonStringOf(invalidB58Hash))
      decoded shouldBe 'left
    }

    "decode valid json base58check strings into a ContractId" in {
      val decoded = decode[ContractId](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe ContractId(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a ContractId" in {
      val decoded = decode[ContractId](jsonStringOf(invalidB58Hash))
      decoded shouldBe 'left
    }

    "decode valid json base58check strings into a ChainId" in {
      val decoded = decode[ChainId](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe ChainId(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a ChainId" in {
      val decoded = decode[ChainId](jsonStringOf(invalidB58Hash))
      decoded shouldBe 'left
    }

    "decode valid json base58check strings into a ScriptId" in {
      val decoded = decode[ScriptId](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe ScriptId(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a ScriptId" in {
      val decoded = decode[ScriptId](jsonStringOf(invalidB58Hash))
      decoded shouldBe 'left
    }

    "decode valid json alphanumneric strings into a Nonce" in {
      val decoded = decode[Nonce](jsonStringOf(alphanumneric))
      decoded.right.value shouldBe Nonce(alphanumneric)
    }

    "fail to decode an invalid json alphanumneric strings into a Nonce" in {
      val decoded = decode[Nonce](jsonStringOf(invalidAlphanumeric))
      decoded shouldBe 'left
    }

    "decode valid json alphanumneric strings into a Secret" in {
      val decoded = decode[Secret](jsonStringOf(alphanumneric))
      decoded.right.value shouldBe Secret(alphanumneric)
    }

    "fail to decode an invalid json alphanumneric strings into a Secret" in {
      val decoded = decode[Secret](jsonStringOf(invalidAlphanumeric))
      decoded shouldBe 'left
    }

    "decode valid json into MichelsonV1 values" in new OperationsJsonData {
      val decoded = decode[MichelsonV1](michelsonJson)
      decoded.right.value shouldEqual expectedMichelson
    }

    "fail to decode invalid json into MichelsonV1 values" in new OperationsJsonData {
      val decoded = decode[MichelsonV1](invalidJson)
      decoded shouldBe 'left
    }

    "decode valid json strings representing a PositiveBigNumber" in {
      val decoded = decode[TezosOperations.PositiveBigNumber](jsonStringOf("1000000000"))
      decoded.right.value shouldEqual TezosOperations.PositiveDecimal(1000000000)
    }

    "decode valid json strings representing zero as a PositiveBigNumber" in {
      val decoded = decode[TezosOperations.PositiveBigNumber](jsonStringOf("0"))
      decoded.right.value shouldEqual TezosOperations.PositiveDecimal(0)
    }

    "decode invalid json for PositiveBigNumber, representing negatives, as the original string" in {
      val decoded = decode[TezosOperations.PositiveBigNumber](jsonStringOf("-1000000000"))
      decoded.right.value shouldBe TezosOperations.InvalidPositiveDecimal("-1000000000")
    }

    "decode invalid json for PositiveBigNumber, not representing numbers, as the original string" in {
      val decoded = decode[TezosOperations.PositiveBigNumber](jsonStringOf("1AA000000000"))
      decoded.right.value shouldBe TezosOperations.InvalidPositiveDecimal("1AA000000000")
    }

    "decode valid json strings representing both positive and negative values as BigNumber" in {
      val decoded = decode[TezosOperations.BigNumber](jsonStringOf("1000000000"))
      decoded.right.value shouldEqual TezosOperations.Decimal(1000000000)

      val negDecoded = decode[TezosOperations.BigNumber](jsonStringOf("-1000000000"))
      negDecoded.right.value shouldBe TezosOperations.Decimal(-1000000000)
    }

    "decode invalid json for BigNumber, not representing numbers, as the original string" in {
      val decoded = decode[TezosOperations.BigNumber](jsonStringOf("1AA000000000"))
      decoded.right.value shouldBe TezosOperations.InvalidDecimal("1AA000000000")
    }

    "decode valid json into a BigMapDiff value" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Contract.BigMapDiff](bigmapdiffJson)
      decoded.right.value shouldEqual expectedBigMapDiff
    }

    "decode valid json into a Scipted.Cntracts value" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Scripted.Contracts](scriptJson)
      decoded.right.value shouldEqual expectedScript
    }

    "decode valid json into Error values" in new OperationsJsonData {
      val decoded = decode[TezosOperations.OperationResult.Error](errorJson)
      decoded.right.value shouldEqual expectedError
    }

    "fail to decode invalid json into Error values" in new OperationsJsonData {
      val decoded = decode[TezosOperations.OperationResult.Error](invalidJson)
      decoded shouldBe 'left
    }

    "decode an endorsement operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](endorsementJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.Endorsement]
      operation shouldEqual expectedEndorsement

    }

    "decode a seed nonce revelation operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](nonceRevelationJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.SeedNonceRevelation]
      operation shouldEqual expectedNonceRevelation

    }

    "decode an account activation operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](activationJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.ActivateAccount]
      operation shouldEqual expectedActivation

    }

    "decode an reveal operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](revealJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.Reveal]
      operation shouldEqual expectedReveal

    }

    "decode an transaction operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](transactionJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.Transaction]
      operation shouldEqual expectedTransaction

    }

    "decode an origination operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](originationJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.Origination]
      operation shouldEqual expectedOrigination

    }

    "decode a group of operations from json" in new OperationsJsonData {

      val decoded = decode[TezosOperations.Group](operationsGroupJson)
      decoded shouldBe 'right

      val operations = decoded.right.value
      operations shouldBe a [TezosOperations.Group]
      operations shouldEqual expectedGroup

    }

  }

}