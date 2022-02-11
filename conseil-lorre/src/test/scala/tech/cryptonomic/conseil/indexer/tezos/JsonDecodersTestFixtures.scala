package tech.cryptonomic.conseil.indexer.tezos

import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Scripted.Contracts

/* defines example tezos json definitions of delegates and related contracts with the typed counterparts used in the tests */
trait DelegatesJsonData {

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
    |      }
    |  ],
    |  "storage": {
    |      "string": "hello"
    |  }
    |}""".stripMargin

  val expectedScript = Contracts(
    storage = Micheline("""{"string":"hello"}"""),
    code = Micheline("""[{"prim":"parameter","args":[{"prim":"string"}]}]""")
  )

  val contractJson =
    s"""{
    |  "manager": "tz1Tzqh3CWLdPoH4kHSqcePatkBVKTwifCHY",
    |  "balance": "1000",
    |  "spendable": true,
    |  "delegate": { "setable": true, "value": "tz1LdZ6S8ScNMgaCLqrekDvbBWhLqtUebk23" },
    |  "script": $scriptJson,
    |  "counter": "401"
    |}""".stripMargin

  val expectedContract =
    Contract(
      manager = PublicKeyHash("tz1Tzqh3CWLdPoH4kHSqcePatkBVKTwifCHY"),
      balance = PositiveDecimal(1000),
      spendable = true,
      delegate = ContractDelegate(
        setable = true,
        value = Some(PublicKeyHash("tz1LdZ6S8ScNMgaCLqrekDvbBWhLqtUebk23"))
      ),
      script = Some(expectedScript),
      counter = PositiveDecimal(401)
    )

  val delegateJson =
    """{
    |  "balance": "15255730175061",
    |  "frozen_balance": "4579383927370",
    |  "frozen_balance_by_cycle": [
    |      {
    |          "cycle": 174,
    |          "deposit": "1321664000000",
    |          "fees": "572501",
    |          "rewards": "37062032929"
    |      },
    |      {
    |          "cycle": 175,
    |          "deposit": "1510400000000",
    |          "fees": "5341649990",
    |          "rewards": "45404449892"
    |      },
    |      {
    |          "cycle": 176,
    |          "deposit": "1550400000000",
    |          "fees": "301772470",
    |          "rewards": "44143066264"
    |      },
    |      {
    |          "cycle": 177,
    |          "deposit": "62848000000",
    |          "fees": "550000",
    |          "rewards": "1817833324"
    |      }
    |  ],
    |  "staking_balance": "15141238517762",
    |  "delegated_contracts": [
    |      "KT1WT8u7wBAaWsVsjQSxdtqE53RjCnEhMJL6",
    |      "KT1VHRry7UMdMcHj7RnHe8n5JXM4g6MQHxPk",
    |      "KT1V5aSEJQdFwZYLpnGDwJ4qpFo1NbEpLnGC",
    |      "KT1V3ri4nnNQTsG52ypMQhZsnZpJEDi6gB4J",
    |      "KT1UAq7cAaaza2Kf48fF9zAPhkiVgVd86Neo",
    |      "KT1UAdtncfLKewg3w34MvoTQAdMxBH2SE2yL",
    |      "KT1SbeNFNZfpGRqpqTRCkJ5JHZ7h7T65Qoin",
    |      "KT1SXEuU93Uu7vv3Xii8AeKCJkCcc2buUdXY",
    |      "KT1S3aDfc5CJqSXDXnyJVarmywDBFg9SaH1M",
    |      "KT1RN3h385ZbPx7CvyVcBzp6CFzR5uoX6Pds",
    |      "KT1QTN1gQcriFXH66CbuGiSthTQMSn132Xea",
    |      "KT1QDgrtfvZLaSxg5enpo7om32UVHJNYSmvJ",
    |      "KT1QB2y7MwCS2fsGDk283Nh3HHiye1fLvDXR",
    |      "KT1NG4zyrmWqyYDmj3rzvT5XUnzak1W3Qb1f",
    |      "KT1MzNXxBzmw8QcPVG5ZW4Km7sSecYDfMnDV",
    |      "KT1MUD3o2XcSVJ89Np1jXAB1iQ1AGdohUHPh",
    |      "KT1MCDY9jmC4Z21B5A1QNkLVi5i2KQSuLt8J",
    |      "KT1KfQCHrXMTHMPd9fbXumLfEPbzpsm6X7EJ",
    |      "KT1KMULX2UzK5TEoYNdpG6yVadyAkSLAVQEQ",
    |      "KT1JsyPreVdkdN8P53SDsWk4riGfzyz5cLYC",
    |      "KT1JsJsbqJrKyuDVvqoabnp77mxhhZpVa1Kx",
    |      "KT1HkQP7vjzz7pVUbfvpcXFRLwCaexAWZF1C",
    |      "KT1DjgmUxRZt5evA99somusn96Sjdnj1mhkd",
    |      "KT1DHv7zmAYF65Y4YeUfLLr4NRyQJb43pkCz",
    |      "KT1C2ZLQyXVMuRyc7kZeoXGGmhgsdBJX7Qj1",
    |      "KT1Bne3CrkWFYx8tD9nvqLKXQG2NkAMezT6k",
    |      "KT1BjqLYHZodyQLyzMC6vZFs3dEsWjTmA75h",
    |      "KT1AowcbP82HTC4qaLE7EKQPdC1J4D14he5d",
    |      "KT1AMAP9zczBGHAiQPwjyy17Pz2cjf9dQZuR",
    |      "KT19Rkg1gBDcUbqm4gDY3THLmMMoDx69bLwX",
    |      "KT19RhkHK8ssqXX1VxSSpyuyMXHK6V9RHizs",
    |      "KT18bmcAvpLZPv6BF79RqTmZLZQSjodr3f2S"
    |  ],
    |  "delegated_balance": "13935725110",
    |  "deactivated": false,
    |  "grace_period": 181
    |}""".stripMargin

  val expectedDelegate =
    Delegate(
      balance = PositiveDecimal(15255730175061L),
      frozen_balance = PositiveDecimal(4579383927370L),
      frozen_balance_by_cycle = List(
        CycleBalance(
          cycle = 174,
          deposit = Some(PositiveDecimal(1321664000000L)),
          deposits = None,
          fees = PositiveDecimal(572501),
          rewards = PositiveDecimal(37062032929L)
        ),
        CycleBalance(
          cycle = 175,
          deposit = Some(PositiveDecimal(1510400000000L)),
          deposits = None,
          fees = PositiveDecimal(5341649990L),
          rewards = PositiveDecimal(45404449892L)
        ),
        CycleBalance(
          cycle = 176,
          deposit = Some(PositiveDecimal(1550400000000L)),
          deposits = None,
          fees = PositiveDecimal(301772470L),
          rewards = PositiveDecimal(44143066264L)
        ),
        CycleBalance(
          cycle = 177,
          deposit = Some(PositiveDecimal(62848000000L)),
          deposits = None,
          fees = PositiveDecimal(550000L),
          rewards = PositiveDecimal(1817833324L)
        )
      ),
      staking_balance = PositiveDecimal(15141238517762L),
      delegated_contracts = List(
        ContractId("KT1WT8u7wBAaWsVsjQSxdtqE53RjCnEhMJL6"),
        ContractId("KT1VHRry7UMdMcHj7RnHe8n5JXM4g6MQHxPk"),
        ContractId("KT1V5aSEJQdFwZYLpnGDwJ4qpFo1NbEpLnGC"),
        ContractId("KT1V3ri4nnNQTsG52ypMQhZsnZpJEDi6gB4J"),
        ContractId("KT1UAq7cAaaza2Kf48fF9zAPhkiVgVd86Neo"),
        ContractId("KT1UAdtncfLKewg3w34MvoTQAdMxBH2SE2yL"),
        ContractId("KT1SbeNFNZfpGRqpqTRCkJ5JHZ7h7T65Qoin"),
        ContractId("KT1SXEuU93Uu7vv3Xii8AeKCJkCcc2buUdXY"),
        ContractId("KT1S3aDfc5CJqSXDXnyJVarmywDBFg9SaH1M"),
        ContractId("KT1RN3h385ZbPx7CvyVcBzp6CFzR5uoX6Pds"),
        ContractId("KT1QTN1gQcriFXH66CbuGiSthTQMSn132Xea"),
        ContractId("KT1QDgrtfvZLaSxg5enpo7om32UVHJNYSmvJ"),
        ContractId("KT1QB2y7MwCS2fsGDk283Nh3HHiye1fLvDXR"),
        ContractId("KT1NG4zyrmWqyYDmj3rzvT5XUnzak1W3Qb1f"),
        ContractId("KT1MzNXxBzmw8QcPVG5ZW4Km7sSecYDfMnDV"),
        ContractId("KT1MUD3o2XcSVJ89Np1jXAB1iQ1AGdohUHPh"),
        ContractId("KT1MCDY9jmC4Z21B5A1QNkLVi5i2KQSuLt8J"),
        ContractId("KT1KfQCHrXMTHMPd9fbXumLfEPbzpsm6X7EJ"),
        ContractId("KT1KMULX2UzK5TEoYNdpG6yVadyAkSLAVQEQ"),
        ContractId("KT1JsyPreVdkdN8P53SDsWk4riGfzyz5cLYC"),
        ContractId("KT1JsJsbqJrKyuDVvqoabnp77mxhhZpVa1Kx"),
        ContractId("KT1HkQP7vjzz7pVUbfvpcXFRLwCaexAWZF1C"),
        ContractId("KT1DjgmUxRZt5evA99somusn96Sjdnj1mhkd"),
        ContractId("KT1DHv7zmAYF65Y4YeUfLLr4NRyQJb43pkCz"),
        ContractId("KT1C2ZLQyXVMuRyc7kZeoXGGmhgsdBJX7Qj1"),
        ContractId("KT1Bne3CrkWFYx8tD9nvqLKXQG2NkAMezT6k"),
        ContractId("KT1BjqLYHZodyQLyzMC6vZFs3dEsWjTmA75h"),
        ContractId("KT1AowcbP82HTC4qaLE7EKQPdC1J4D14he5d"),
        ContractId("KT1AMAP9zczBGHAiQPwjyy17Pz2cjf9dQZuR"),
        ContractId("KT19Rkg1gBDcUbqm4gDY3THLmMMoDx69bLwX"),
        ContractId("KT19RhkHK8ssqXX1VxSSpyuyMXHK6V9RHizs"),
        ContractId("KT18bmcAvpLZPv6BF79RqTmZLZQSjodr3f2S")
      ),
      delegated_balance = PositiveDecimal(13935725110L),
      deactivated = false,
      grace_period = 181
    )
}

/* defines example tezos json definitions of accounts and typed counterparts used in the tests */
trait AccountsJsonData {
  val legacyAccountJson =
    """{
    |  "balance": "2921522468",
    |  "counter": "0",
    |  "delegate": {
    |      "setable": false,
    |      "value": "tz1LdZ6S8ScNMgaCLqrekDvbBWhLqtUebk23"
    |  },
    |  "spendable": true
    |}""".stripMargin

  val expectedLegacyAccount =
    Account(
      balance = 2921522468L,
      script = None,
      counter = Some(0),
      manager = None,
      spendable = Some(true),
      delegate = Some(
        Left(
          Protocol4Delegate(
            setable = false,
            value = Some(PublicKeyHash("tz1LdZ6S8ScNMgaCLqrekDvbBWhLqtUebk23"))
          )
        )
      ),
      isBaker = None,
      isActivated = None
    )

  val accountJson =
    """{
    |  "balance": "2921522468",
    |  "counter": "0",
    |  "delegate": "tz1LdZ6S8ScNMgaCLqrekDvbBWhLqtUebk23"
    |}""".stripMargin

  val expectedAccount =
    Account(
      balance = 2921522468L,
      delegate = Some(Right(PublicKeyHash("tz1LdZ6S8ScNMgaCLqrekDvbBWhLqtUebk23"))),
      script = None,
      counter = Some(0),
      manager = None,
      spendable = None,
      isBaker = None,
      isActivated = None
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
    |      }
    |  ],
    |  "storage": {
    |      "string": "hello"
    |  }
    |}""".stripMargin

  val expectedScript = Contracts(
    storage = Micheline("""{"string":"hello"}"""),
    code = Micheline("""[{"prim":"parameter","args":[{"prim":"string"}]}]""")
  )

  val accountScriptedJson =
    s"""{
    |  "balance": "2921522468",
    |  "counter": "0",
    |  "delegate": "tz1LdZ6S8ScNMgaCLqrekDvbBWhLqtUebk23",
    |  "script": $scriptJson
    |}""".stripMargin

}

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

  val expectedMicheline = Micheline(
    """{"prim":"code","args":[{"prim":"CAR"},{"prim":"NIL","args":[{"prim":"operation"}]},{"prim":"PAIR"}]}"""
  )

  val bigmapdiffAllocJson =
    s"""{
    |  "action": "alloc",
    |  "big_map": "428",
    |  "key_type": {
    |      "prim": "int"
    |  },
    |  "value_type": {
    |      "prim": "int"
    |  }
    |}""".stripMargin

  val expectedBigmapdiffAlloc =
    Contract.BigMapAlloc(
      action = "alloc",
      big_map = Decimal(BigDecimal(428)),
      key_type = Micheline("""{"prim":"int"}"""),
      value_type = Micheline("""{"prim":"int"}""")
    )

  val bigmapdiffUpdateJson =
    s"""{
    |  "action": "update",
    |  "key_hash": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
    |  "key": $michelineJson,
    |  "big_map": "428"
    |}""".stripMargin

  val expectedBigmapdiffUpdate =
    Contract.BigMapUpdate(
      action = "update",
      key_hash = ScriptId("tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio"),
      key = expectedMicheline,
      big_map = Decimal(BigDecimal(428)),
      value = None
    )

  val bigmapdiffCopyJson =
    s"""{
    |  "action": "copy",
    |  "source_big_map": "428",
    |  "destination_big_map": "748"
    |}""".stripMargin

  val expectedBigmapdiffCopy =
    Contract.BigMapCopy(
      action = "copy",
      source_big_map = Decimal(BigDecimal(428)),
      destination_big_map = Decimal(BigDecimal(748))
    )

  val bigmapdiffRemoveJson =
    s"""{
    |  "action": "remove",
    |  "big_map": "428"
    |}""".stripMargin

  val expectedBigmapdiffRemove =
    Contract.BigMapRemove(
      action = "remove",
      big_map = Decimal(BigDecimal(428))
    )

  val p4BigmapdiffJson =
    s"""{
    |  "key_hash": "tz1fyvFH2pd3V9UEq5psqVokVBYkt7rHTKio",
    |  "key": $michelineJson
    |}""".stripMargin

  val expectedP4BigMapDiff =
    Contract.Protocol4BigMapDiff(
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
      code = Micheline(
        """[{"prim":"parameter","args":[{"prim":"string"}]},{"prim":"storage","args":[{"prim":"string"}]},{"prim":"code","args":[[{"prim":"CAR"},{"prim":"NIL","args":[{"prim":"operation"}]},{"prim":"PAIR"}]]}]"""
      ),
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
        slot = None,
        slots = Some(List(29, 27, 20, 17)),
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

  val expectedReveal =
    Reveal(
      source = PublicKeyHash("KT1PPuBrvCGpJt54hVBgXMm2sKa6QpSwKrJq"),
      fee = PositiveDecimal(10000),
      counter = PositiveDecimal(1),
      gas_limit = PositiveDecimal(10000),
      storage_limit = PositiveDecimal(257),
      public_key = PublicKey("edpktxRxk9r61tjEZCt5a2hY2MWC3gzECGL7FXS1K6WXGG28hTFdFz"),
      metadata = ResultMetadata[OperationResult.Reveal](
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
        operation_result = OperationResult.Reveal(
          status = "applied",
          consumed_gas = Some(Decimal(10000)),
          errors = None
        ),
        internal_operation_results = Some(
          List(
            InternalOperationResults.Reveal(
              result = OperationResult.Reveal("applied", Some(Decimal(10000)), None),
              public_key = PublicKey("edpktxRxk9r61tjEZCt5a2hY2MWC3gzECGL7FXS1K6WXGG28hTFdFz"),
              nonce = 1234,
              source = PublicKeyHash("KT1PPuBrvCGpJt54hVBgXMm2sKa6QpSwKrJq"),
              kind = "reveal"
            )
          )
        )
      )
    )

  val transactionWithInternalTransactionResult = Transaction(
    counter = PositiveDecimal(120917),
    amount = PositiveDecimal(0),
    fee = PositiveDecimal(0),
    gas_limit = PositiveDecimal(2215),
    storage_limit = PositiveDecimal(0),
    source = PublicKeyHash("tz1MS1g7tETWfiPXtXx6Jx1XUrYJzzFY4QYN"),
    destination = ContractId("KT1XYHyoewY5CMDdcYB5BjN7dQbWreV5cWgH"),
    parameters =
      Some(Left(Parameters(Micheline("""{"string":"tz1MS1g7tETWfiPXtXx6Jx1XUrYJzzFY4QYN"}"""), Some("default")))),
    parameters_micheline = None,
    metadata = ResultMetadata(
      operation_result = OperationResult.Transaction(
        status = "applied",
        allocated_destination_contract = None,
        balance_updates = None,
        big_map_diff = None,
        consumed_gas = Some(Decimal(2008)),
        originated_contracts = None,
        paid_storage_size_diff = None,
        storage = Some(Micheline("""{"prim":"Unit"}""")),
        storage_size = Some(Decimal(69)),
        errors = None
      ),
      balance_updates = List(),
      internal_operation_results = Some(
        List(
          InternalOperationResults.Transaction(
            "transaction",
            PublicKeyHash("KT1XYHyoewY5CMDdcYB5BjN7dQbWreV5cWgH"),
            0,
            PositiveDecimal(1000),
            ContractId("tz1MS1g7tETWfiPXtXx6Jx1XUrYJzzFY4QYN"),
            Some(Left(Parameters(Micheline("""{"prim":"Unit"}"""), Some("default")))),
            None,
            OperationResult.Transaction(
              status = "applied",
              allocated_destination_contract = None,
              balance_updates = Some(
                List(
                  BalanceUpdate(
                    kind = "contract",
                    change = -1000,
                    category = None,
                    contract = Some(ContractId("KT1XYHyoewY5CMDdcYB5BjN7dQbWreV5cWgH")),
                    delegate = None,
                    level = None
                  ),
                  BalanceUpdate(
                    kind = "contract",
                    change = 1000,
                    category = None,
                    contract = Some(ContractId("tz1MS1g7tETWfiPXtXx6Jx1XUrYJzzFY4QYN")),
                    delegate = None,
                    level = None
                  )
                )
              ),
              big_map_diff = None,
              consumed_gas = Some(Decimal(107)),
              originated_contracts = None,
              paid_storage_size_diff = None,
              storage = None,
              storage_size = None,
              errors = None
            )
          )
        )
      )
    )
  )

  val transactionWithInternalTransactionJson =
    """
      |{
      |    "kind": "transaction",
      |    "source": "tz1MS1g7tETWfiPXtXx6Jx1XUrYJzzFY4QYN",
      |    "fee": "0",
      |    "counter": "120917",
      |    "gas_limit": "2215",
      |    "storage_limit": "0",
      |    "amount": "0",
      |    "destination": "KT1XYHyoewY5CMDdcYB5BjN7dQbWreV5cWgH",
      |    "parameters": {
      |        "entrypoint": "default",
      |        "value": {
      |            "string": "tz1MS1g7tETWfiPXtXx6Jx1XUrYJzzFY4QYN"
      |        }
      |    },
      |    "metadata": {
      |        "balance_updates": [],
      |        "operation_result": {
      |            "status": "applied",
      |            "storage": {
      |                "prim": "Unit"
      |            },
      |            "consumed_gas": "2008",
      |            "storage_size": "69"
      |        },
      |        "internal_operation_results": [
      |            {
      |                "kind": "transaction",
      |                "source": "KT1XYHyoewY5CMDdcYB5BjN7dQbWreV5cWgH",
      |                "nonce": 0,
      |                "amount": "1000",
      |                "destination": "tz1MS1g7tETWfiPXtXx6Jx1XUrYJzzFY4QYN",
      |                "parameters": {
      |                  "entrypoint": "default",
      |                  "value": {
      |                    "prim": "Unit"
      |                  }
      |                },
      |                "result": {
      |                    "status": "applied",
      |                    "balance_updates": [
      |                        {
      |                            "kind": "contract",
      |                            "contract": "KT1XYHyoewY5CMDdcYB5BjN7dQbWreV5cWgH",
      |                            "change": "-1000"
      |                        },
      |                        {
      |                            "kind": "contract",
      |                            "contract": "tz1MS1g7tETWfiPXtXx6Jx1XUrYJzzFY4QYN",
      |                            "change": "1000"
      |                        }
      |                    ],
      |                    "consumed_gas": "107"
      |                }
      |            }
      |        ]
      |    }
      |}
    """.stripMargin

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
      source = PublicKeyHash("tz1VXaVvVyLfZNWCcpHpKNSg61TEJVZtNJKf"),
      fee = PositiveDecimal(1300),
      counter = PositiveDecimal(454133),
      gas_limit = PositiveDecimal(100),
      storage_limit = PositiveDecimal(0),
      public_key = PublicKey("edpkuuNeGwDGBBGNdp7eEDUnb3tJKfhyxoo9A8GkDbdHEaPYYG8MJj"),
      metadata = ResultMetadata(
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
        operation_result = OperationResult.Reveal(
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
    |      "entrypoint": "default",
    |      "value": {
    |            "string": "world"
    |      }
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
      source = PublicKeyHash("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
      fee = PositiveDecimal(1416),
      counter = PositiveDecimal(407940),
      gas_limit = PositiveDecimal(11475),
      storage_limit = PositiveDecimal(0),
      amount = PositiveDecimal(0),
      destination = ContractId("KT1CkkM5tYe9xRMQMbnayaULGoGaeBUH2Riy"),
      parameters = Some(Left(Parameters(Micheline("""{"string":"world"}"""), Some("default")))),
      parameters_micheline = None,
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
        operation_result = OperationResult.Transaction(
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

  val backwardCompatibleTransactionJson =
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
      |      "string":"world"
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

  val backwardCompatibleExpectedTransaction =
    Transaction(
      source = PublicKeyHash("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
      fee = PositiveDecimal(1416),
      counter = PositiveDecimal(407940),
      gas_limit = PositiveDecimal(11475),
      storage_limit = PositiveDecimal(0),
      amount = PositiveDecimal(0),
      destination = ContractId("KT1CkkM5tYe9xRMQMbnayaULGoGaeBUH2Riy"),
      parameters = Some(Right(Micheline("""{"string":"world"}"""))),
      parameters_micheline = None,
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
        operation_result = OperationResult.Transaction(
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
      source = PublicKeyHash("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
      fee = PositiveDecimal(1441),
      counter = PositiveDecimal(407941),
      gas_limit = PositiveDecimal(11362),
      storage_limit = PositiveDecimal(323),
      manager_pubkey = Some(PublicKeyHash("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR")),
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
        operation_result = OperationResult.Origination(
          status = "applied",
          big_map_diff = None,
          balance_updates = Some(
            List(
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
            )
          ),
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
      source = PublicKeyHash("KT1Ck1Mrbxr6RhCiqN6TPfX3NvWnJimcAKG9"),
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
        operation_result = OperationResult.Delegation(
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
      |    $delegationJson,
      |    $backwardCompatibleTransactionJson
      |  ],
      |  "signature": "sigvs8WYSK3AgpWwpUXg8B9NyJjPcLYNqmZvNFR3UmtiiLfPTNZSEeU8qRs6LVTquyVUDdu4imEWTqD6sinURdJAmRoyffy9"
      |}""".stripMargin

  val expectedGroup =
    OperationsGroup(
      protocol = "ProtoALphaALphaALphaALphaALphaALphaALphaALphaDdp3zK",
      chain_id = Some(ChainId("NetXSzLHKwSumh7")),
      hash = OperationHash("oobQTfjxVhEhbtWg3n51YDAfYr9HmXPpGVTqGhxiorsq4jAc53n"),
      branch = TezosBlockHash("BKs7LZjCLcPczh52nR3DdcqAFg2VKF89ZkW47UTPFCuBfMe7wpy"),
      signature = Some(
        Signature("sigvs8WYSK3AgpWwpUXg8B9NyJjPcLYNqmZvNFR3UmtiiLfPTNZSEeU8qRs6LVTquyVUDdu4imEWTqD6sinURdJAmRoyffy9")
      ),
      contents = List(
        expectedEndorsement,
        expectedNonceRevelation,
        expectedActivation,
        expectedFailedReveal,
        expectedTransaction,
        expectedOrigination,
        expectedDelegation,
        backwardCompatibleExpectedTransaction
      )
    )

}
