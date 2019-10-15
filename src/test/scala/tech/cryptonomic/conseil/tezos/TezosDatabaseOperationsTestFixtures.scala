package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp
import java.time.ZonedDateTime

import scala.util.Random
import tech.cryptonomic.conseil.util.{RandomGenerationKit, RandomSeed}
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, BlocksRow, DelegatesRow, OperationGroupsRow}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.tezos.TezosTypes.Scripted.Contracts

trait TezosDataGeneration extends RandomGenerationKit {
  import TezosTypes.Syntax._
  import TezosTypes.Voting.Vote

  /* randomly populate a number of fees */
  def generateFees(howMany: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[AverageFees] = {
    require(howMany > 0, "the test can generates a positive number of fees, you asked for a non positive value")

    val rnd = new Random(randomSeed.seed)

    (1 to howMany).map { current =>
      val low = rnd.nextInt(10)
      val medium = rnd.nextInt(10) + 10
      val high = rnd.nextInt(10) + 20
      AverageFees(
        low = low,
        medium = medium,
        high = high,
        timestamp = new Timestamp(startAt.getTime + current),
        kind = "kind",
        level = None,
        cycle = None
      )
    }.toList
  }

  /* randomly generates a number of accounts with associated block data */
  def generateAccounts(howMany: Int, blockHash: BlockHash, blockLevel: Int)(
      implicit randomSeed: RandomSeed
  ): BlockTagged[Map[AccountId, Account]] = {
    require(howMany > 0, "the test can generates a positive number of accounts, you asked for a non positive value")

    val rnd = new Random(randomSeed.seed)

    val accounts = (1 to howMany).map { currentId =>
      AccountId(String valueOf currentId) ->
        Account(
          balance = rnd.nextInt,
          counter = Some(currentId),
          delegate = Some(Right(PublicKeyHash("delegate-value"))),
          script = Some(Contracts(Micheline("storage"), Micheline("script"))),
          manager = None,
          spendable = None
        )
    }.toMap

    accounts.taggedWithBlock(blockHash, blockLevel)
  }

  /* randomly generates a number of delegates with associated block data */
  def generateDelegates(delegatedHashes: List[String], blockHash: BlockHash, blockLevel: Int)(
      implicit randomSeed: RandomSeed
  ): BlockTagged[Map[PublicKeyHash, Delegate]] = {
    require(
      delegatedHashes.nonEmpty,
      "the test can generates a positive number of delegates, you can't pass an empty list of account key hashes"
    )

    val rnd = new Random(randomSeed.seed)

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(rnd)

    val delegates = delegatedHashes.zipWithIndex.map {
      case (accountPkh, counter) =>
        PublicKeyHash(generateHash(10)) ->
            Delegate(
              balance = PositiveDecimal(rnd.nextInt()),
              frozen_balance = PositiveDecimal(rnd.nextInt()),
              frozen_balance_by_cycle = List.empty,
              staking_balance = PositiveDecimal(rnd.nextInt()),
              delegated_contracts = List(ContractId(accountPkh)),
              delegated_balance = PositiveDecimal(rnd.nextInt()),
              deactivated = false,
              grace_period = rnd.nextInt()
            )
    }.toMap

    delegates.taggedWithBlock(blockHash, blockLevel)
  }

  /* randomly populate a number of blocks based on a level range */
  def generateBlocks(toLevel: Int, startAt: ZonedDateTime)(implicit randomSeed: RandomSeed): List[Block] = {
    require(
      toLevel > 0,
      "the test can generate blocks up to a positive chain level, you asked for a non positive value"
    )

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    //same for all blocks
    val chainHash = generateHash(5)

    //fix a seed generator and provides a generation function
    val randomMetadataLevel = {
      val rnd = new Random(randomSeed.seed)
      () =>
        BlockHeaderMetadataLevel(
          level = rnd.nextInt(),
          level_position = rnd.nextInt(),
          cycle = rnd.nextInt(),
          cycle_position = rnd.nextInt(),
          voting_period = rnd.nextInt(),
          voting_period_position = rnd.nextInt(),
          expected_commitment = rnd.nextBoolean()
        )
    }

    def generateOne(level: Int, predecessorHash: BlockHash, genesis: Boolean = false): Block =
      Block(
        BlockData(
          protocol = "protocol",
          chain_id = Some(chainHash),
          hash = BlockHash(generateHash(10)),
          header = BlockHeader(
            level = level,
            proto = 1,
            predecessor = predecessorHash,
            timestamp = startAt.plusSeconds(level),
            validation_pass = 0,
            operations_hash = None,
            fitness = Seq.empty,
            priority = Some(0),
            context = s"context$level",
            signature = Some(s"sig${generateHash(10)}")
          ),
          metadata =
            if (genesis) GenesisMetadata
            else
              BlockHeaderMetadata(
                balance_updates = List.empty,
                baker = PublicKeyHash(generateHash(10)),
                voting_period_kind = VotingPeriod.proposal,
                nonce_hash = Some(NonceHash(generateHash(10))),
                consumed_gas = PositiveDecimal(0),
                level = randomMetadataLevel()
              )
        ),
        operationGroups = List.empty,
        votes = CurrentVotes.empty
      )

    //we need a block to start
    val genesis = generateOne(0, BlockHash("genesis"), true)

    //use a fold to pass the predecessor hash, to keep a plausibility of sort
    (1 to toLevel)
      .foldLeft(List(genesis)) {
        case (chain, lvl) =>
          val currentBlock = generateOne(lvl, chain.head.data.hash)
          currentBlock :: chain
      }
      .reverse

  }

  /** Randomly geneates a single block, for a specific level
    * WARN the algorithm is linear in the level requested, don't use it with high values
    */
  def generateSingleBlock(
      atLevel: Int,
      atTime: ZonedDateTime,
      balanceUpdates: List[OperationMetadata.BalanceUpdate] = List.empty
  )(implicit randomSeed: RandomSeed): Block = {
    import TezosOptics.Blocks._
    import mouse.any._

    val generated = generateBlocks(toLevel = atLevel, startAt = atTime).last

    generated |> setTimestamp(atTime) |> setBalances(balanceUpdates)
  }

  def generateBalanceUpdates(howMany: Int)(implicit randomSeed: RandomSeed): List[OperationMetadata.BalanceUpdate] = {
    require(
      howMany > 0,
      "the test can only generate a positive number of balance updates, you asked for a non positive value"
    )

    val randomSource = new Random(randomSeed.seed)

    //custom hash generator with predictable seed
    val generateAlphaNumeric: Int => String = alphaNumericGenerator(randomSource)

    List.fill(howMany) {
      OperationMetadata.BalanceUpdate(
        kind = generateAlphaNumeric(10),
        change = randomSource.nextLong(),
        category = Some(generateAlphaNumeric(10)),
        contract = Some(ContractId(generateAlphaNumeric(10))),
        delegate = Some(PublicKeyHash(generateAlphaNumeric(10))),
        level = Some(randomSource.nextInt(100))
      )
    }

  }

  /* randomly populate a number of blocks based on a level range */
  def generateBlockRows(toLevel: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[Tables.BlocksRow] = {
    require(
      toLevel > 0,
      "the test can generate blocks up to a positive chain level, you asked for a non positive value"
    )

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    //same for all blocks
    val chainHash = generateHash(5)

    val startMillis = startAt.getTime

    def generateOne(level: Int, predecessorHash: String): BlocksRow =
      BlocksRow(
        level = level,
        proto = 1,
        predecessor = predecessorHash,
        timestamp = new Timestamp(startMillis + level),
        validationPass = 0,
        fitness = "fitness",
        protocol = "protocol",
        context = Some(s"context$level"),
        signature = Some(s"sig${generateHash(10)}"),
        chainId = Some(chainHash),
        hash = generateHash(10),
        operationsHash = Some(generateHash(10)),
        periodKind = Some("period_kind"),
        currentExpectedQuorum = Some(1000),
        activeProposal = None,
        baker = Some(generateHash(10)),
        nonceHash = Some(generateHash(10)),
        consumedGas = Some(0)
      )

    //we need somewhere to start with
    val genesis = generateOne(0, "genesis")

    //use a fold to pass the predecessor hash, to keep a plausibility of sort
    (1 to toLevel)
      .foldLeft(List(genesis)) {
        case (chain, lvl) =>
          val currentBlock = generateOne(lvl, chain.head.hash)
          currentBlock :: chain
      }
      .reverse

  }

  /* create an operation group for each block passed in, using random values, with the requested copies of operations */
  def generateOperationGroup(block: Block, generateOperations: Boolean)(
      implicit randomSeed: RandomSeed
  ): OperationsGroup = {

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    OperationsGroup(
      protocol = "protocol",
      chain_id = block.data.chain_id.map(ChainId),
      hash = OperationHash(generateHash(10)),
      branch = BlockHash(generateHash(10)),
      signature = Some(Signature(s"sig${generateHash(10)}")),
      contents = if (generateOperations) Operations.sampleOperations else List.empty
    )
  }

  /* create an empty operation group for each block passed in, using random values */
  def generateOperationGroupRows(
      blocks: BlocksRow*
  )(implicit randomSeed: RandomSeed): List[Tables.OperationGroupsRow] = {
    require(blocks.nonEmpty, "the test won't generate any operation group without a block to start with")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    blocks
      .map(
        block =>
          Tables.OperationGroupsRow(
            protocol = "protocol",
            chainId = block.chainId,
            hash = generateHash(10),
            branch = generateHash(10),
            signature = Some(s"sig${generateHash(10)}"),
            blockId = block.hash,
            blockLevel = block.level
          )
      )
      .toList
  }

  /* create operations related to a specific group, with random data */
  def generateOperationsForGroup(
      block: BlocksRow,
      group: OperationGroupsRow,
      howMany: Int = 3
  ): List[Tables.OperationsRow] =
    List.fill(howMany) {
      Tables.OperationsRow(
        kind = "operation-kind",
        operationGroupHash = group.hash,
        operationId = -1,
        blockHash = block.hash,
        blockLevel = block.level,
        timestamp = block.timestamp,
        level = Some(block.level),
        internal = false
      )
    }

  /* create operation rows to hold the given fees */
  def wrapFeesWithOperations(fees: Seq[Option[BigDecimal]], block: BlocksRow, group: OperationGroupsRow) =
    fees.zipWithIndex.map {
      case (fee, index) =>
        Tables.OperationsRow(
          kind = "kind",
          operationGroupHash = group.hash,
          operationId = -1,
          fee = fee,
          blockHash = block.hash,
          blockLevel = block.level,
          timestamp = new Timestamp(block.timestamp.getTime + index),
          level = Some(block.level),
          internal = false
        )
    }

  /* randomly generates a number of account rows for some block */
  def generateAccountRows(howMany: Int, block: BlocksRow): List[AccountsRow] = {
    require(howMany > 0, "the test can only generate a positive number of accounts, you asked for a non positive value")

    (1 to howMany).map { currentId =>
      AccountsRow(
        accountId = String valueOf currentId,
        blockId = block.hash,
        balance = 0,
        counter = Some(0),
        delegate = None,
        script = None
      )
    }.toList

  }

  /* randomly generates a number of delegate rows for some block */
  def generateDelegateRows(howMany: Int, block: BlocksRow)(implicit randomSeed: RandomSeed): List[DelegatesRow] = {
    require(
      howMany > 0,
      "the test can only generate a positive number of delegates, you asked for a non positive value"
    )

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    List.fill(howMany) {
      DelegatesRow(
        blockId = block.hash,
        pkh = generateHash(10),
        balance = Some(0),
        frozenBalance = Some(0),
        stakingBalance = Some(0),
        delegatedBalance = Some(0),
        deactivated = true,
        gracePeriod = 0
      )
    }

  }

  object Voting {

    import tech.cryptonomic.conseil.tezos.TezosTypes.Voting._

    def generateProposals(howMany: Int, forBlock: Block)(implicit randomSeed: RandomSeed) = {
      require(
        howMany > 0,
        "the test can only generate a positive number of proposals, you asked for a non positive value"
      )

      //custom hash generator with predictable seed
      val randomGen = new Random(randomSeed.seed)
      val generateHash: Int => String = alphaNumericGenerator(randomGen)

      //prefill the count of protocols for each Proposal (at least one each)
      val protocolCounts = Array.fill(howMany)(1 + randomGen.nextInt(4))

      List.tabulate(howMany) { current =>
        val protocols = List.fill(protocolCounts(current))((ProtocolId(generateHash(10)), randomGen.nextInt()))
        Proposal(
          protocols = protocols,
          block = forBlock
        )
      }

    }

    def generateBakersRolls(howMany: Int)(implicit randomSeed: RandomSeed) = {
      require(howMany > 0, "the test can only generate a positive number of bakers, you asked for a non positive value")

      //custom hash generator with predictable seed
      val randomGen = new Random(randomSeed.seed)
      val generateHash: Int => String = alphaNumericGenerator(randomGen)

      //prefill the rolls
      val rolls = Array.fill(howMany)(randomGen.nextInt(1000))

      List.tabulate(howMany) { current =>
        BakerRolls(pkh = PublicKeyHash(generateHash(10)), rolls = rolls(current))
      }

    }

    def generateBallots(howMany: Int)(implicit randomSeed: RandomSeed) = {
      require(
        howMany > 0,
        "the test can only generate a positive number of ballots, you asked for a non positive value"
      )

      val knownVotes = Array("yay", "nay", "pass")

      //custom hash generator with predictable seed
      val randomGen = new Random(randomSeed.seed)
      val generateHash: Int => String = alphaNumericGenerator(randomGen)
      //custom vote chooser
      val randomVote = () => knownVotes(randomGen.nextInt(knownVotes.size))

      //prefill the votes
      val votes = Array.fill(howMany)(randomVote())

      List.tabulate(howMany) { current =>
        Ballot(pkh = PublicKeyHash(generateHash(10)), ballot = Vote(votes(current)))
      }

    }

  }

  object Operations {

    import OperationMetadata.BalanceUpdate

    val sampleScriptedContract =
      Contracts(
        code = Micheline(
          """[{"prim":"parameter","args":[{"prim":"string"}]},{"prim":"storage","args":[{"prim":"string"}]},{"prim":"code","args":[[{"prim":"CAR"},{"prim":"NIL","args":[{"prim":"operation"}]},{"prim":"PAIR"}]]}]"""
        ),
        storage = Micheline("""{"string":"hello"}""")
      )

    val sampleEndorsement =
      Endorsement(
        level = 182308,
        metadata = EndorsementMetadata(
          slot = None,
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
          )
        )
      )

    val sampleTransaction =
      Transaction(
        source = PublicKeyHash("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
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
        source = PublicKeyHash("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
        fee = PositiveDecimal(1441),
        counter = PositiveDecimal(407941),
        gas_limit = PositiveDecimal(11362),
        storage_limit = PositiveDecimal(323),
        manager_pubkey = None,
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
            originated_contracts = Some(List(ContractId("KT1VuJAgTJT5x2Y2S3emAVSbUA5nST7j3QE4"))),
            consumed_gas = Some(Decimal(11262)),
            storage_size = Some(Decimal(46)),
            paid_storage_size_diff = Some(Decimal(46)),
            errors = None
          )
        )
      )

    val sampleDelegation =
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

    val sampleBallot =
      Ballot(
        ballot = Vote("yay"),
        proposal = Some("PsBABY5HQTSkA4297zNHfsZNKtxULfL18y95qb3m53QJiXGmrbU"),
        source = Some(ContractId("tz1VceyYUpq1gk5dtp6jXQRtCtY8hm5DKt72"))
      )

    val sampleProposals =
      Proposals(
        source = Some(ContractId("tz1VceyYUpq1gk5dtp6jXQRtCtY8hm5DKt72")),
        period = Some(10),
        proposals = Some(List("Psd1ynUBhMZAeajwcZJAeq5NrxorM6UCU4GJqxZ7Bx2e9vUWB6z)"))
      )

    val sampleOperations =
      sampleEndorsement :: sampleNonceRevelation :: sampleAccountActivation :: sampleReveal :: sampleTransaction :: sampleOrigination :: sampleDelegation ::
          DoubleEndorsementEvidence :: DoubleBakingEvidence :: sampleProposals :: sampleBallot :: Nil

  }

}
