package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp
import scala.util.Random

import tech.cryptonomic.conseil.util.{RandomGenerationKit, RandomSeed}
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees

trait TezosDataGeneration extends RandomGenerationKit {

  /* randomly populate a number of fees */
  def generateFees(howMany: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[AverageFees] = {
    require(howMany > 0, "the test can generates a positive number of fees, you asked for a non positive value")

    val rnd = new Random(randomSeed.seed)

    (1 to howMany).map {
      current =>
        val low = rnd.nextInt(10)
        val medium = rnd.nextInt(10) + 10
        val high = rnd.nextInt(10) + 20
        AverageFees(
          low = low,
          medium = medium,
          high = high,
          timestamp = new Timestamp(startAt.getTime + current),
          kind = "kind"
        )
    }.toList
  }

  /* randomly generates a number of accounts with associated block data */
  def generateAccounts(howMany: Int, blockHash: BlockHash, blockLevel: Int)(implicit randomSeed: RandomSeed): BlockAccounts = {
    require(howMany > 0, "the test can generates a positive number of accounts, you asked for a non positive value")

    val rnd = new Random(randomSeed.seed)

    val accounts = (1 to howMany).map {
      currentId =>
        (AccountId(String valueOf currentId),
          Account(
            manager = "manager",
            balance = rnd.nextInt,
            spendable = true,
            delegate = AccountDelegate(setable = false, value = Some("delegate-value")),
            script = Some("script"),
            counter = currentId
          )
        )
    }.toMap

    BlockAccounts(blockHash, blockLevel, accounts)
  }

  /* randomly populate a number of blocks based on a level range */
  def generateBlocks(toLevel: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[Block] = {
    require(toLevel > 0, "the test can generate blocks up to a positive chain level, you asked for a non positive value")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    //same for all blocks
    val chainHash = generateHash(5)

    val startMillis = startAt.getTime

    def generateOne(level: Int, predecessorHash: BlockHash): Block =
      Block(
        BlockMetadata(
          "protocol",
          Some(chainHash),
          BlockHash(generateHash(10)),
          BlockHeader(
            level = level,
            proto = 1,
            predecessor = predecessorHash,
            timestamp = new Timestamp(startMillis + level),
            validationPass = 0,
            operations_hash = None,
            fitness = Seq.empty,
            context = s"context$level",
            signature = Some(s"sig${generateHash(10)}")
          )),
        operationGroups = List.empty
      )

    //we need a block to start
    val genesis = generateOne(0, BlockHash("genesis"))

    //use a fold to pass the predecessor hash, to keep a plausibility of sort
    (1 to toLevel).foldLeft(List(genesis)) {
      case (chain, lvl) =>
        val currentBlock = generateOne(lvl, chain.head.metadata.hash)
        currentBlock :: chain
    }.reverse

  }


  /* randomly populate a number of blocks based on a level range */
  def generateBlockRows(toLevel: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[Tables.BlocksRow] = {
    require(toLevel > 0, "the test can generate blocks up to a positive chain level, you asked for a non positive value")

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
      hash = generateHash(10)
    )

    //we need somewhere to start with
    val genesis = generateOne(0, "genesis")

    //use a fold to pass the predecessor hash, to keep a plausibility of sort
    (1 to toLevel).foldLeft(List(genesis)) {
      case (chain, lvl) =>
        val currentBlock = generateOne(lvl, chain.head.hash)
        currentBlock :: chain
    }.reverse

  }

  /* create an operation group for each block passed in, using random values, with the requested copies of operations */
  def generateOperationGroup(block: Block, generateOperations: Boolean)(implicit randomSeed: RandomSeed): OperationsGroup = {

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    OperationsGroup(
      protocol = "protocol",
      chain_id = block.metadata.chain_id.map(ChainId),
      hash = OperationHash(generateHash(10)),
      branch = BlockHash(generateHash(10)),
      signature = Some(Signature(s"sig${generateHash(10)}")),
      contents = if (generateOperations) Operations.sampleOperations else List.empty
    )
  }

  /* create an empty operation group for each block passed in, using random values */
  def generateOperationGroupRows(blocks: BlocksRow*)(implicit randomSeed: RandomSeed): List[Tables.OperationGroupsRow] = {
    require(blocks.nonEmpty, "the test won't generate any operation group without a block to start with")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    blocks.map(
      block =>
        Tables.OperationGroupsRow(
          protocol = "protocol",
          chainId = block.chainId,
          hash = generateHash(10),
          branch = generateHash(10),
          signature = Some(s"sig${generateHash(10)}"),
          blockId = block.hash
        )
    ).toList
  }

  /* create operations related to a specific group, with random data */
  def generateOperationsForGroup(block: BlocksRow, group: OperationGroupsRow, howMany: Int = 3): List[DBTableMapping.Operation] =
    List.fill(howMany) {
      DBTableMapping.Operation(
        kind = "operation-kind",
        operationGroupHash = group.hash,
        operationId = -1,
        blockHash = block.hash,
        blockLevel = block.level,
        timestamp = block.timestamp,
        level = Some(block.level)
      )
    }

  /* create operation rows to hold the given fees */
  def wrapFeesWithOperations(
    fees: Seq[Option[BigDecimal]],
    block: BlocksRow,
    group: OperationGroupsRow) = {

    fees.zipWithIndex.map {
      case (fee, index) =>
        DBTableMapping.Operation(
          kind = "kind",
          operationGroupHash = group.hash,
          operationId = -1,
          fee = fee,
          blockHash = block.hash,
          blockLevel = block.level,
          timestamp = new Timestamp(block.timestamp.getTime + index),
          level = Some(block.level)
        )
    }

  }

  /* randomly generates a number of account rows for some block */
  def generateAccountRows(howMany: Int, block: BlocksRow): List[AccountsRow] = {
    require(howMany > 0, "the test can generates a positive number of accounts, you asked for a non positive value")

    (1 to howMany).map {
      currentId =>
        AccountsRow(
          accountId = String valueOf currentId,
          blockId = block.hash,
          manager = "manager",
          spendable = true,
          delegateSetable = false,
          delegateValue = None,
          counter = 0,
          script = None,
          balance = 0
        )
    }.toList

  }

  object Operations {

    import OperationMetadata.BalanceUpdate

    val sampleScriptedContract =
      Scripted.Contracts(
        code = Micheline("""[{"prim":"parameter","args":[{"prim":"string"}]},{"prim":"storage","args":[{"prim":"string"}]},{"prim":"code","args":[[{"prim":"CAR"},{"prim":"NIL","args":[{"prim":"operation"}]},{"prim":"PAIR"}]]}]"""),
        storage = Micheline("""{"string":"hello"}""")
      )

    val sampleEndorsement =
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
          operation_result =
            OperationResult.Delegation(
              status = "applied",
              consumed_gas = Some(Decimal(10000)),
              errors = None
            )
        )
      )

    val sampleOperations =
      sampleEndorsement :: sampleNonceRevelation :: sampleAccountActivation :: sampleReveal :: sampleTransaction :: sampleOrigination :: sampleDelegation ::
        DoubleEndorsementEvidence :: DoubleBakingEvidence :: Proposals :: Ballot :: Nil

  }

}