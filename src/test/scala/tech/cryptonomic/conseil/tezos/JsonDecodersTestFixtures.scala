package tech.cryptonomic.conseil.tezos

import TezosTypes._

/* defines example tezos json definitions of operations and typed counterparts used in the tests */
trait OperationsJsonData {
  import OperationMetadata.BalanceUpdate
  import OperationResult.Error

  val errorJson =
    """{
      |  "kind": "temporary",
      |  "id": "proto.alpha.gas_exhausted.operation"
      |}""".stripMargin

  val expectedError = Error("""{"kind":"temporary","id":"proto.alpha.gas_exhausted.operation"}""")

  val michelineJson =
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

  val expectedMicheline = Micheline("""{"prim":"code","args":[{"prim":"CAR"},{"prim":"NIL","args":[{"prim":"operation"}]},{"prim":"PAIR"}]}""")

  val bigmapdiffJson =
    s"""{
    |  "key_hash": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
    |  "key": $michelineJson
    |}""".stripMargin

  val expectedBigMapDiff =
    Contract.BigMapDiff(
      key_hash = ScriptId("tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio"),
      key = expectedMicheline,
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
      code = Micheline("""[{"prim":"parameter","args":[{"prim":"string"}]},{"prim":"storage","args":[{"prim":"string"}]},{"prim":"code","args":[[{"prim":"CAR"},{"prim":"NIL","args":[{"prim":"operation"}]},{"prim":"PAIR"}]]}]"""),
      storage = Micheline("""{"string":"hello"}""")
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
      parameters = Some(Micheline("""{"string":"world"}""")),
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
            storage = Some(Micheline("""{"string":"world"}""")),
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

    // uses an inconsistent field name for managerPubkey
    val alphanetOriginationJson =
      s"""{
      |  "kind": "origination",
      |  "source": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
      |  "fee": "1441",
      |  "counter": "407941",
      |  "gas_limit": "11362",
      |  "storage_limit": "323",
      |  "managerPubkey": "tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR",
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

  val delegationJson =
    """{
    |  "kind": "delegation",
    |  "source": "KT1Ck1Mrbxr6RhCiqN6TPfX3NvWnJimcAKG9",
    |  "fee": "1400",
    |  "counter": "2",
    |  "gas_limit": "10100",
    |  "storage_limit": "0",
    |  "delegate": "tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u",
    |  "metadata": {
    |      "balance_updates": [
    |          {
    |              "kind": "contract",
    |              "contract": "KT1Ck1Mrbxr6RhCiqN6TPfX3NvWnJimcAKG9",
    |              "change": "-1400"
    |          },
    |          {
    |              "kind": "freezer",
    |              "category": "fees",
    |              "delegate": "tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889",
    |              "level": 1612,
    |              "change": "1400"
    |          }
    |      ],
    |      "operation_result": {
    |          "status": "applied",
    |          "consumed_gas": "10000"
    |      }
    |  }
    |}""".stripMargin

  val expectedDelegation =
    Delegation(
      source = ContractId("KT1Ck1Mrbxr6RhCiqN6TPfX3NvWnJimcAKG9"),
      fee = PositiveDecimal(1400),
      counter = PositiveDecimal(2),
      gas_limit = PositiveDecimal(10100),
      storage_limit = PositiveDecimal(0),
      delegate = Some(PublicKeyHash("tz1boot2oCjTjUN6xDNoVmtCLRdh8cc92P1u")),
      metadata = ResultMetadata(
        balance_updates = List(
          BalanceUpdate(
            kind = "contract",
            contract = Some(ContractId("KT1Ck1Mrbxr6RhCiqN6TPfX3NvWnJimcAKG9")),
            change = -1400L,
            category = None,
            delegate = None,
            level = None
          ),
          BalanceUpdate(
            kind = "freezer",
            category = Some("fees"),
            delegate = Some(PublicKeyHash("tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889")),
            level = Some(1612),
            change = 1400L,
            contract = None
          )
        ),
        operation_result =
          OperationResult.Delegation(
            status = "applied",
            consumed_gas = Some(Decimal(10000)),
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
      |    $originationJson,
      |    $delegationJson
      |  ],
      |  "signature": "sigvs8WYSK3AgpWwpUXg8B9NyJjPcLYNqmZvNFR3UmtiiLfPTNZSEeU8qRs6LVTquyVUDdu4imEWTqD6sinURdJAmRoyffy9"
      |}""".stripMargin

  val expectedGroup =
    OperationsGroup(
      protocol = "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
      chain_id = Some(ChainId("NetXSzLHKwSumh7")),
      hash = OperationHash("oobQTfjxVhEhbtWg3n51YDAfYr9HmXPpGVTqGhxiorsq4jAc53n"),
      branch = BlockHash("BKs7LZjCLcPczh52nR3DdcqAFg2VKF89ZkW47UTPFCuBfMe7wpy"),
      signature = Some(Signature("sigvs8WYSK3AgpWwpUXg8B9NyJjPcLYNqmZvNFR3UmtiiLfPTNZSEeU8qRs6LVTquyVUDdu4imEWTqD6sinURdJAmRoyffy9")),
      contents = List(
        expectedEndorsement,
        expectedNonceRevelation,
        expectedActivation,
        expectedFailedReveal,
        expectedTransaction,
        expectedOrigination,
        expectedDelegation)
    )

}
