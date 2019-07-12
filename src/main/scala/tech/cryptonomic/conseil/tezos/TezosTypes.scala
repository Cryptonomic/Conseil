package tech.cryptonomic.conseil.tezos

import monocle.Traversal
import monocle.function.all._
import monocle.macros.{GenLens, GenPrism}
import monocle.std.option._

/**
  * Classes used for deserializing Tezos node RPC results.
  */
object TezosTypes {

  /*Reminder: we might want to move this to TezosOptics object*/
  object Lenses {
    private val operationGroups = GenLens[Block](_.operationGroups)
    private val operations = GenLens[OperationsGroup](_.contents)

    private val origination = GenPrism[Operation, Origination]
    private val transaction = GenPrism[Operation, Transaction]

    private val script = GenLens[Origination](_.script)
    private val parameters = GenLens[Transaction](_.parameters)

    private val storage = GenLens[Scripted.Contracts](_.storage)
    private val code = GenLens[Scripted.Contracts](_.code)

    private val expression = GenLens[Micheline](_.expression)

    private val scriptLens: Traversal[Block, Scripted.Contracts] =
      operationGroups composeTraversal each composeLens
          operations composeTraversal each composePrism
          origination composeLens
          script composePrism some

    val storageLens: Traversal[Block, String] = scriptLens composeLens storage composeLens expression
    val codeLens: Traversal[Block, String] = scriptLens composeLens code composeLens expression

    val parametersLens: Traversal[Block, String] =
      operationGroups composeTraversal each composeLens
          operations composeTraversal each composePrism
          transaction composeLens
          parameters composePrism some composeLens
          expression
  }

  //TODO use in a custom decoder for json strings that needs to have a proper encoding
  lazy val isBase58Check: String => Boolean = (s: String) => {
    val pattern = "^[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]*$".r.pattern
    pattern.matcher(s).matches
  }

  /** convenience alias to simplify declarations of block hash+level tuples */
  type BlockReference = (BlockHash, Int)

  /** use to remove ambiguities about the meaning in voting proposals usage */
  type ProposalSupporters = Int

  final case class PublicKey(value: String) extends AnyVal

  final case class PublicKeyHash(value: String) extends AnyVal

  final case class Signature(value: String) extends AnyVal

  final case class BlockHash(value: String) extends AnyVal

  final case class OperationHash(value: String) extends AnyVal

  final case class ContractId(id: String) extends AnyVal

  final case class AccountId(id: String) extends AnyVal

  final case class ChainId(id: String) extends AnyVal

  final case class ProtocolId(id: String) extends AnyVal

  final case class Nonce(value: String) extends AnyVal

  final case class NonceHash(value: String) extends AnyVal

  final case class Secret(value: String) extends AnyVal

  final case class Micheline(expression: String) extends AnyVal

  final case class ScriptId(value: String) extends AnyVal

  /** a conventional value to get the latest block in the chain */
  final lazy val blockHeadHash = BlockHash("head")

  final case class BlockData(
      protocol: String,
      chain_id: Option[String],
      hash: BlockHash,
      header: BlockHeader,
      metadata: BlockMetadata
  )

  final case class BlockHeader(
      level: Int,
      proto: Int,
      predecessor: BlockHash,
      timestamp: java.time.ZonedDateTime,
      validation_pass: Int,
      operations_hash: Option[String],
      fitness: Seq[String],
      priority: Option[Int],
      context: String,
      signature: Option[String]
  )

  sealed trait BlockMetadata extends Product with Serializable

  final case object GenesisMetadata extends BlockMetadata

  final case class BlockHeaderMetadata(
      balance_updates: List[OperationMetadata.BalanceUpdate],
      nonce_hash: Option[NonceHash],
      consumed_gas: PositiveBigNumber,
      baker: PublicKeyHash,
      voting_period_kind: VotingPeriod.Kind,
      level: BlockHeaderMetadataLevel
  ) extends BlockMetadata

  final case class BlockHeaderMetadataLevel(
      level: Int,
      level_position: Int,
      cycle: Int,
      cycle_position: Int,
      voting_period: Int,
      voting_period_position: Int,
      expected_commitment: Boolean
  )

  /** only accepts standard block metadata, discarding the genesis metadata */
  def discardGenesis: PartialFunction[BlockMetadata, BlockHeaderMetadata] = {
    case md: BlockHeaderMetadata => md
  }

  /** Naming can be deceiving, we're sticking with the json schema use of `positive_bignumber`
    * all the while accepting `0` as valid
    */
  sealed trait PositiveBigNumber extends Product with Serializable
  final case class PositiveDecimal(value: BigDecimal) extends PositiveBigNumber
  final case class InvalidPositiveDecimal(jsonString: String) extends PositiveBigNumber

  sealed trait BigNumber extends Product with Serializable
  final case class Decimal(value: BigDecimal) extends BigNumber
  final case class InvalidDecimal(jsonString: String) extends BigNumber

  object Contract {
    final case class BigMapDiff(
        key_hash: ScriptId,
        key: Micheline,
        value: Option[Micheline]
    )
  }

  object Scripted {
    final case class Contracts(
        storage: Micheline,
        code: Micheline
    )
  }

  /** root of the operation hiearchy */
  sealed trait Operation extends Product with Serializable

  //operations definition

  final case class Endorsement(
      level: Int,
      metadata: EndorsementMetadata
  ) extends Operation

  final case class SeedNonceRevelation(
      level: Int,
      nonce: Nonce,
      metadata: BalanceUpdatesMetadata
  ) extends Operation

  final case class ActivateAccount(
      pkh: PublicKeyHash,
      secret: Secret,
      metadata: BalanceUpdatesMetadata
  ) extends Operation

  final case class Reveal(
      counter: PositiveBigNumber,
      fee: PositiveBigNumber,
      gas_limit: PositiveBigNumber,
      storage_limit: PositiveBigNumber,
      public_key: PublicKey,
      source: ContractId,
      metadata: ResultMetadata[OperationResult.Reveal]
  ) extends Operation

  final case class Transaction(
      counter: PositiveBigNumber,
      amount: PositiveBigNumber,
      fee: PositiveBigNumber,
      gas_limit: PositiveBigNumber,
      storage_limit: PositiveBigNumber,
      source: ContractId,
      destination: ContractId,
      parameters: Option[Micheline],
      metadata: ResultMetadata[OperationResult.Transaction]
  ) extends Operation

  final case class Origination(
      counter: PositiveBigNumber,
      fee: PositiveBigNumber,
      source: ContractId,
      balance: PositiveBigNumber,
      gas_limit: PositiveBigNumber,
      storage_limit: PositiveBigNumber,
      manager_pubkey: PublicKeyHash,
      delegatable: Option[Boolean],
      delegate: Option[PublicKeyHash],
      spendable: Option[Boolean],
      script: Option[Scripted.Contracts],
      metadata: ResultMetadata[OperationResult.Origination]
  ) extends Operation

  final case class Delegation(
      counter: PositiveBigNumber,
      source: ContractId,
      fee: PositiveBigNumber,
      gas_limit: PositiveBigNumber,
      storage_limit: PositiveBigNumber,
      delegate: Option[PublicKeyHash],
      metadata: ResultMetadata[OperationResult.Delegation]
  ) extends Operation

  final case object DoubleEndorsementEvidence extends Operation
  final case object DoubleBakingEvidence extends Operation
  final case object Proposals extends Operation
  final case object Ballot extends Operation

  //metadata definitions, both shared or specific to operation kind

  final case class EndorsementMetadata(
      slots: List[Int],
      delegate: PublicKeyHash,
      balance_updates: List[OperationMetadata.BalanceUpdate]
  )

  //for now we ignore internal results, as it gets funny as sitting naked on a wasps' nest
  final case class ResultMetadata[RESULT](
      operation_result: RESULT,
      balance_updates: List[OperationMetadata.BalanceUpdate]
  )

  //generic metadata, used whenever balance updates are the only thing inside
  final case class BalanceUpdatesMetadata(
      balance_updates: List[OperationMetadata.BalanceUpdate]
  )

  /** defines common result structures, following the json-schema definitions */
  object OperationResult {
    //we're not yet encoding the complex schema for errors, storing them as simple strings
    final case class Error(json: String) extends AnyVal

    //we're currently making no difference between different statuses in any of the results

    final case class Reveal(
        status: String,
        consumed_gas: Option[BigNumber],
        errors: Option[List[Error]]
    )

    final case class Transaction(
        status: String,
        allocated_destination_contract: Option[Boolean],
        balance_updates: Option[List[OperationMetadata.BalanceUpdate]],
        big_map_diff: Option[List[Contract.BigMapDiff]],
        consumed_gas: Option[BigNumber],
        originated_contracts: Option[List[ContractId]],
        paid_storage_size_diff: Option[BigNumber],
        storage: Option[Micheline],
        storage_size: Option[BigNumber],
        errors: Option[List[Error]]
    )

    final case class Origination(
        status: String,
        balance_updates: Option[List[OperationMetadata.BalanceUpdate]],
        consumed_gas: Option[BigNumber],
        originated_contracts: Option[List[ContractId]],
        paid_storage_size_diff: Option[BigNumber],
        storage_size: Option[BigNumber],
        errors: Option[List[Error]]
    )

    final case class Delegation(
        status: String,
        consumed_gas: Option[BigNumber],
        errors: Option[List[Error]]
    )

  }

  /** defines common metadata structures, following the json-schema definitions */
  object OperationMetadata {
    //we're currently making no difference between contract or freezer updates
    final case class BalanceUpdate(
        kind: String,
        change: Long,
        category: Option[String],
        contract: Option[ContractId],
        delegate: Option[PublicKeyHash],
        level: Option[Int]
    )
  }

  /** a grouping of operations with common "header" information */
  final case class OperationsGroup(
      protocol: String,
      chain_id: Option[ChainId],
      hash: OperationHash,
      branch: BlockHash,
      contents: List[Operation],
      signature: Option[Signature]
  )

  final case class AppliedOperationBalanceUpdates(
      kind: String,
      contract: Option[String],
      change: Int,
      category: Option[String],
      delegate: Option[String],
      level: Option[Int]
  )

  final case class AppliedOperationResultStatus(
      status: String,
      errors: Option[List[String]],
      storage: Option[Any],
      balanceUpdates: Option[AppliedOperationBalanceUpdates],
      originatedContracts: Option[String],
      consumedGas: Option[Int],
      storageSizeDiff: Option[Int]
  )

  final case class AccountDelegate(
      setable: Boolean,
      value: Option[PublicKeyHash]
  )

  final case class Account(
      manager: PublicKeyHash,
      balance: scala.math.BigDecimal,
      spendable: Boolean,
      delegate: AccountDelegate,
      script: Option[Scripted.Contracts],
      counter: Int
  )

  /** Keeps track of association between some domain type and a block reference
    * Synthetic class, no domain correspondence, it's used to simplify signatures
    */
  final case class BlockTagged[T](
      blockHash: BlockHash,
      blockLevel: Int,
      content: T
  ) {
    val asTuple = (blockHash, blockLevel, content)
  }

  final case class Delegate(
      balance: PositiveBigNumber,
      frozen_balance: PositiveBigNumber,
      frozen_balance_by_cycle: List[CycleBalance],
      staking_balance: PositiveBigNumber,
      delegated_contracts: List[ContractId],
      delegated_balance: PositiveBigNumber,
      deactivated: Boolean,
      grace_period: Int
  )

  final case class CycleBalance(
      cycle: Int,
      deposit: PositiveBigNumber,
      fees: PositiveBigNumber,
      rewards: PositiveBigNumber
  )

  final case class Contract(
      manager: PublicKeyHash,
      balance: PositiveBigNumber,
      spendable: Boolean,
      delegate: ContractDelegate,
      script: Option[Scripted.Contracts],
      counter: PositiveBigNumber
  )

  final case class ContractDelegate(
      setable: Boolean,
      value: Option[PublicKeyHash]
  )

  object VotingPeriod extends Enumeration {
    type Kind = Value
    val proposal, promotion_vote, testing_vote, testing = Value
  }

  val defaultVotingPeriod: VotingPeriod.Kind = VotingPeriod.proposal

  final case class CurrentVotes(
      quorum: Option[Int],
      active: Option[ProtocolId]
  )

  final object CurrentVotes {
    val empty = CurrentVotes(quorum = None, active = None)
  }

  final case class Block(
      data: BlockData,
      operationGroups: List[OperationsGroup],
      votes: CurrentVotes
  )

  final case class ManagerKey(
      manager: String,
      key: Option[String]
  )

  final case class ForgedOperation(operation: String)

  final case class AppliedOperationError(
      kind: String,
      id: String,
      hash: String
  )

  final case class AppliedOperationResult(
      operation: String,
      status: String,
      operationKind: Option[String],
      balanceUpdates: Option[List[AppliedOperationBalanceUpdates]],
      originatedContracts: Option[List[String]],
      errors: Option[List[AppliedOperationError]]
  )

  final case class AppliedOperation(
      kind: String,
      balance_updates: Option[List[AppliedOperationBalanceUpdates]],
      operation_results: Option[List[AppliedOperationResult]],
      id: Option[String],
      contract: Option[String]
  )

  final case class InjectedOperation(injectedOperation: String)

  final case class MichelsonExpression(prim: String, args: List[String])

  object Voting {

    final case class Vote(value: String) extends AnyVal
    final case class Proposal(protocols: List[(ProtocolId, ProposalSupporters)], block: Block)
    final case class BakerRolls(pkh: PublicKeyHash, rolls: Int)
    final case class Ballot(pkh: PublicKeyHash, ballot: Vote)
  }

  object Syntax {

    /** provides a factory to tag any content with a reference block */
    implicit class BlockTagger[T](val content: T) extends AnyVal {

      /** creates a BlockTagged[T] instance based on any `T` value, adding the block reference */
      def taggedWithBlock(hash: BlockHash, level: Int): BlockTagged[T] = BlockTagged(hash, level, content)
    }

  }

}
