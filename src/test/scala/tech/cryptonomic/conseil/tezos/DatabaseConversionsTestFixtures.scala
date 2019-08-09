package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.TezosTypes.OperationMetadata.BalanceUpdate
import tech.cryptonomic.conseil.tezos.TezosTypes.Voting.Vote

trait DBConversionsData {

  val sampleScriptedContract =
    Scripted.Contracts(
      code = Micheline(
        """[{"prim":"parameter","args":[{"prim":"string"}]},{"prim":"storage","args":[{"prim":"string"}]},{"prim":"code","args":[[{"prim":"CAR"},{"prim":"NIL","args":[{"prim":"operation"}]},{"prim":"PAIR"}]]}]"""
      ),
      storage = Micheline("""{"string":"hello"}""")
    )

  val sampleEndorsement =
    Endorsement(
      level = 182308,
      metadata = EndorsementMetadata(
        slots = List(29, 27, 20, 17),
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

  val sampleNonceRevelation =
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

  val sampleAccountActivation =
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

  val sampleReveal =
    Reveal(
      source = ContractId("KT1PPuBrvCGpJt54hVBgXMm2sKa6QpSwKrJq"),
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
        )
      )
    )

  val sampleTransaction =
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

  val sampleOrigination =
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
      script = Some(sampleScriptedContract),
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
          originated_contracts = Some(
            ContractId("KT1VuJAgTJT5x2Y2S3emAVSbUA5nST7j3QE4") :: ContractId("KT1Hx96yGgGk2q7Jmwm1dnYAMdRoLJNn5gnC") :: Nil
          ),
          consumed_gas = Some(Decimal(11262)),
          storage_size = Some(Decimal(46)),
          paid_storage_size_diff = Some(Decimal(46)),
          errors = None
        )
      )
    )

  val sampleDelegation =
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
        operation_result = OperationResult.Delegation(
          status = "applied",
          consumed_gas = Some(Decimal(10000)),
          errors = None
        )
      )
    )

  val sampleBallot =
    Ballot(
      ballot = Vote("yay"),
      proposal = Some("PsBABY5HQTSkA4297zNHfsZNKtxULfL18y95qb3m53QJiXGmrbU"),
      source = Some(ContractId("tz1VceyYUpq1gk5dtp6jXQRtCtY8hm5DKt72"))
    )

}
