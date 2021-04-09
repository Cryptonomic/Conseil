package tech.cryptonomic.conseil.common.tezos

import java.time.{Instant, ZonedDateTime}
import scala.util.Try
import cats.Functor

/**
  * Classes used for deserializing Tezos node RPC results.
  */
object TezosTypes {

  /** Case class representing hash to identify blocks across many block chains */
  final case class TezosBlockHash(value: String) extends AnyVal

  //TODO use in a custom decoder for json strings that needs to have a proper encoding
  lazy val isBase58Check: String => Boolean = (s: String) => {
    val pattern = "^[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]*$".r.pattern
    pattern.matcher(s).matches
  }

  /** use to remove ambiguities about the meaning in voting proposals usage */
  type ProposalSupporters = Int

  /** alias used to harmonize any reference to a block level */
  type BlockLevel = Long

  final case class PublicKey(value: String) extends AnyVal

  final case class PublicKeyHash(value: String) extends AnyVal

  /** we use this alias to highlight the semantic of use, but it's another PKH */
  type AccountId = PublicKeyHash

  /** Wraps a string into an account id value, i.e. a public key hash */
  val makeAccountId = PublicKeyHash(_)

  final case class Signature(value: String) extends AnyVal

  final case class OperationHash(value: String) extends AnyVal

  final case class ContractId(id: String) extends AnyVal

  final case class ChainId(id: String) extends AnyVal

  final case class ProtocolId(id: String) extends AnyVal

  final case class Nonce(value: String) extends AnyVal

  final case class NonceHash(value: String) extends AnyVal

  final case class Secret(value: String) extends AnyVal

  final case class Micheline(expression: String) extends AnyVal

  final case class ScriptId(value: String) extends AnyVal

  /** a conventional value to get the latest block in the chain */
  final lazy val blockHeadHash = TezosBlockHash("head")

  final case class Block(
      data: BlockData,
      operationGroups: List[OperationsGroup],
      votes: CurrentVotes
  )

  final case class BlockData(
      protocol: String,
      chain_id: Option[String],
      hash: TezosBlockHash,
      header: BlockHeader,
      metadata: BlockMetadata
  )

  final case class BlockHeader(
      level: BlockLevel,
      proto: Int,
      predecessor: TezosBlockHash,
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
      voting_period_kind: Option[VotingPeriod.Kind],
      voting_period_info: Option[VotingPeriodInfo],
      level: Option[BlockHeaderMetadataLevel],
      level_info: Option[BlockHeaderMetadataLevelInfo]
  ) extends BlockMetadata

  final case class BlockHeaderMetadataLevel(
      level: BlockLevel,
      level_position: Int,
      cycle: Int,
      cycle_position: Int,
      voting_period: Int,
      voting_period_position: Int,
      expected_commitment: Boolean
  )

  final case class BlockHeaderMetadataLevelInfo(
      level: BlockLevel,
      level_position: Int,
      cycle: Int,
      cycle_position: Int,
      expected_commitment: Boolean
  )

  final case class VotingPeriodInfo(
      voting_period: VotingPeriodObject,
      position: Int,
      remaining: Int
  )

  final case class VotingPeriodObject(
      index: Int,
      kind: VotingPeriod.Kind,
      start_position: Int
  )

  /** only accepts standard block metadata, discarding the genesis metadata */
  val discardGenesis: BlockMetadata => Option[BlockHeaderMetadata] = {
    val partialSelector: PartialFunction[BlockMetadata, BlockHeaderMetadata] = {
      case md: BlockHeaderMetadata => md
    }
    partialSelector.lift
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

    /** retro-compat adapter from protocol 5+ */
    type CompatBigMapDiff = Either[BigMapDiff, Protocol4BigMapDiff]

    final case class Protocol4BigMapDiff(
        key_hash: ScriptId,
        key: Micheline,
        value: Option[Micheline]
    )

    sealed trait BigMapDiff extends Product with Serializable

    final case class BigMapUpdate(
        action: String,
        key: Micheline,
        key_hash: ScriptId,
        big_map: BigNumber,
        value: Option[Micheline]
    ) extends BigMapDiff

    final case class BigMapCopy(
        action: String,
        source_big_map: BigNumber,
        destination_big_map: BigNumber
    ) extends BigMapDiff

    final case class BigMapAlloc(
        action: String,
        big_map: BigNumber,
        key_type: Micheline,
        value_type: Micheline
    ) extends BigMapDiff

    final case class BigMapRemove(
        action: String,
        big_map: BigNumber
    ) extends BigMapDiff

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
  type ParametersCompatibility = Either[Parameters, Micheline]

  /** The set of operations kinds */
  val knownOperationKinds = Set(
    "seed_nonce_revelation",
    "delegation",
    "transaction",
    "activate_account",
    "origination",
    "reveal",
    "double_endorsement_evidence",
    "double_baking_evidence",
    "endorsement",
    "proposals",
    "ballot",
    "endorsement_with_slot"
  )

  final case class Operations(
      kind: String,
      level: BlockLevel
  )
  final case class EndorsementInternalObject(
      branch: String,
      operations: Operations
  )

  final case class Endorsement(
      level: BlockLevel,
      metadata: EndorsementMetadata
  ) extends Operation

  final case class EndorsementWithSlot(
      endorsement: EndorsementInternalObject,
      metadata: EndorsementMetadata
  ) extends Operation

  final case class SeedNonceRevelation(
      level: BlockLevel,
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
      source: PublicKeyHash,
      metadata: ResultMetadata[OperationResult.Reveal]
  ) extends Operation

  final case class Transaction(
      counter: PositiveBigNumber,
      amount: PositiveBigNumber,
      fee: PositiveBigNumber,
      gas_limit: PositiveBigNumber,
      storage_limit: PositiveBigNumber,
      source: PublicKeyHash,
      destination: ContractId,
      parameters: Option[ParametersCompatibility],
      parameters_micheline: Option[ParametersCompatibility],
      metadata: ResultMetadata[OperationResult.Transaction]
  ) extends Operation

  final case class Parameters(
      value: Micheline,
      entrypoint: Option[String] = None
  )

  final case class Origination(
      counter: PositiveBigNumber,
      fee: PositiveBigNumber,
      source: PublicKeyHash,
      balance: PositiveBigNumber,
      gas_limit: PositiveBigNumber,
      storage_limit: PositiveBigNumber,
      manager_pubkey: Option[PublicKeyHash],
      delegatable: Option[Boolean],
      delegate: Option[PublicKeyHash],
      spendable: Option[Boolean],
      script: Option[Scripted.Contracts],
      metadata: ResultMetadata[OperationResult.Origination]
  ) extends Operation

  final case class Delegation(
      counter: PositiveBigNumber,
      source: PublicKeyHash,
      fee: PositiveBigNumber,
      gas_limit: PositiveBigNumber,
      storage_limit: PositiveBigNumber,
      delegate: Option[PublicKeyHash],
      metadata: ResultMetadata[OperationResult.Delegation]
  ) extends Operation

  final case object DoubleEndorsementEvidence extends Operation
  final case object DoubleBakingEvidence extends Operation
  final case class Proposals(source: Option[ContractId], period: Option[Int], proposals: Option[List[String]])
      extends Operation
  final case class Ballot(
      ballot: Voting.Vote,
      proposal: Option[String],
      source: Option[ContractId],
      period: Option[Int]
  ) extends Operation

  //metadata definitions, both shared or specific to operation kind
  final case class EndorsementMetadata(
      slot: Option[Int],
      slots: List[Int],
      delegate: PublicKeyHash,
      balance_updates: List[OperationMetadata.BalanceUpdate]
  )

  //for now we ignore internal results, as it gets funny as sitting naked on a wasps' nest
  final case class ResultMetadata[RESULT](
      operation_result: RESULT,
      balance_updates: List[OperationMetadata.BalanceUpdate],
      internal_operation_results: Option[List[InternalOperationResults.InternalOperationResult]] = None
  )

// Internal operations result definitions
  object InternalOperationResults {

    sealed trait InternalOperationResult extends Product with Serializable {
      def nonce: Int
    }

    case class Reveal(
        kind: String,
        source: PublicKeyHash,
        nonce: Int,
        public_key: PublicKey,
        result: OperationResult.Reveal
    ) extends InternalOperationResult

    case class Parameters(entrypoint: String, value: Micheline)

    case class Transaction(
        kind: String,
        source: PublicKeyHash,
        nonce: Int,
        amount: PositiveBigNumber,
        destination: ContractId,
        parameters: Option[ParametersCompatibility],
        parameters_micheline: Option[ParametersCompatibility],
        result: OperationResult.Transaction
    ) extends InternalOperationResult

    /* some fields are only kept for backward-compatibility, as noted*/
    case class Origination(
        kind: String,
        source: PublicKeyHash,
        nonce: Int,
        manager_pubkey: Option[PublicKeyHash], // retro-compat from protocol 5+
        balance: PositiveBigNumber,
        spendable: Option[Boolean], // retro-compat from protocol 5+
        delegatable: Option[Boolean], // retro-compat from protocol 5+
        delegate: Option[PublicKeyHash],
        script: Option[Scripted.Contracts],
        result: OperationResult.Origination
    ) extends InternalOperationResult

    case class Delegation(
        kind: String,
        source: PublicKeyHash,
        nonce: Int,
        delegate: Option[PublicKeyHash],
        result: OperationResult.Delegation
    ) extends InternalOperationResult

  }

  //generic metadata, used whenever balance updates are the only thing inside
  final case class BalanceUpdatesMetadata(
      balance_updates: List[OperationMetadata.BalanceUpdate]
  )

  /** defines common result structures, following the json-schema definitions */
  object OperationResult {
    /* we're not yet decoding the complex schema for errors,
     * as every error can have additional custom fields
     * see: https://tezos.gitlab.io/api/errors.html
     */
    final case class Error(json: String) extends AnyVal

    //we're currently making no difference between different statuses in any of the results

    /** Utility to handle some known status strings */
    object Status extends Enumeration {
      type Status = Value
      val applied, failed, skipped, backtracked = Value

      def parse(in: String): Option[Status] =
        Try(withName(in)).toOption

    }

    final case class Reveal(
        status: String,
        consumed_gas: Option[BigNumber],
        errors: Option[List[Error]]
    )

    final case class Transaction(
        status: String,
        allocated_destination_contract: Option[Boolean],
        balance_updates: Option[List[OperationMetadata.BalanceUpdate]],
        big_map_diff: Option[List[Contract.CompatBigMapDiff]],
        consumed_gas: Option[BigNumber],
        originated_contracts: Option[List[ContractId]],
        paid_storage_size_diff: Option[BigNumber],
        storage: Option[Micheline],
        storage_size: Option[BigNumber],
        errors: Option[List[Error]]
    )

    final case class Origination(
        status: String,
        big_map_diff: Option[List[Contract.CompatBigMapDiff]],
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
        level: Option[BlockLevel]
    )
  }

  /** a grouping of operations with common "header" information */
  final case class OperationsGroup(
      protocol: String,
      chain_id: Option[ChainId],
      hash: OperationHash,
      branch: TezosBlockHash,
      contents: List[Operation],
      signature: Option[Signature]
  )

  final case class AppliedOperationBalanceUpdates(
      kind: String,
      contract: Option[String],
      change: Int,
      category: Option[String],
      delegate: Option[String],
      level: Option[BlockLevel]
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

  /** retro-compat adapter from protocol 5+ */
  type AccountDelegate = Either[Protocol4Delegate, PublicKeyHash]

  final case class Protocol4Delegate(
      setable: Boolean,
      value: Option[PublicKeyHash]
  )

  final case class Account(
      balance: scala.math.BigDecimal,
      delegate: Option[AccountDelegate],
      script: Option[Scripted.Contracts],
      counter: Option[Int],
      manager: Option[PublicKeyHash], // retro-compat from protocol 5+
      spendable: Option[Boolean], // retro-compat from protocol 5+
      isBaker: Option[Boolean],
      isActivated: Option[Boolean]
  )

  /** convenience class to keep multiple values referencing a specific block */
  case class BlockReference(
      hash: TezosBlockHash,
      level: BlockLevel,
      timestamp: Option[Instant],
      cycle: Option[Int],
      period: Option[Int]
  )

  /** Keeps track of association between some domain type and a block reference
    * Synthetic class, no domain correspondence, it's used to simplify signatures
    */
  final case class BlockTagged[T](
      ref: BlockReference,
      content: T
  ) {
    val asTuple = (ref.hash, ref.level, ref.timestamp, ref.cycle, ref.period, content)

    /** Helper method for updating content of the class */
    def updateContent[A](newContent: A): BlockTagged[A] = this.copy(content = newContent)
  }

  /** Companion object for BlockTagged */
  object BlockTagged {

    /** Now we can easily map the content without touching the referenced block data */
    implicit val functorForBlockTagged = new Functor[BlockTagged] {
      override def map[A, B](fa: BlockTagged[A])(f: A => B): BlockTagged[B] = fa.copy(content = f(fa.content))
    }

    /** Tags given content with BlockData */
    def fromBlockData[T](block: BlockData, content: T): BlockTagged[T] =
      BlockTagged(
        BlockReference(
          block.hash,
          block.header.level,
          Some(block.header.timestamp.toInstant),
          TezosOptics.Blocks.extractCycle(block),
          TezosOptics.Blocks.extractPeriod(block.metadata)
        ),
        content
      )
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
  ) {

    /** Compute rolls based on staked balance */
    def rolls: Option[Int] = Baking.computeRollsFromStakes(staking_balance)

  }

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
    final case class BallotCounts(yay: Int, nay: Int, pass: Int)
  }

  /** Utilities related to the baking process */
  object Baking {

    //we'll move this to a proper chain configuration value later on
    /** how big is a baker roll in tez */
    final val BakerRollsSize = BigDecimal.decimal(8000)

    /** Starting from the staking balance, infers number of rolls for a given baker. */
    def computeRollsFromStakes(stakingBalance: PositiveBigNumber): Option[Int] =
      stakingBalance match {
        case PositiveDecimal(value) =>
          Some((value quot (BakerRollsSize * BigDecimal(1e6))).toIntExact) // taking scale of mutez into account
        case InvalidPositiveDecimal(jsonString) =>
          None
      }

  }

  object Syntax {

    /** provides a factory to tag any content with a reference block */
    implicit class BlockTagger[T](val content: T) extends AnyVal {

      /** creates a BlockTagged[T] instance based on any `T` value, adding the block reference */
      def taggedWithBlock(ref: BlockReference): BlockTagged[T] = BlockTagged(ref, content)

      /** creates a BlockTagged[T] instance based on any `T` value, adding the block reference */
      def taggedWithBlockData(data: BlockData): BlockTagged[T] =
        BlockTagged.fromBlockData(data, content)
    }

  }

  /** Baking rights model */
  final case class BakingRights(
      level: BlockLevel,
      delegate: String,
      priority: Int,
      estimated_time: Option[ZonedDateTime],
      cycle: Option[Int],
      governancePeriod: Option[Int]
  )

  /** Endorsing rights model */
  final case class EndorsingRights(
      level: BlockLevel,
      delegate: String,
      slots: List[Int],
      estimated_time: Option[ZonedDateTime],
      cycle: Option[Int],
      governancePeriod: Option[Int],
      endorsedBlock: Option[BlockLevel]
  )

  /**
    * Information for fetching baking/endorsing rights
    * @param blockHash        hash of a block
    * @param cycle            block cycle
    * @param governancePeriod governance period
    */
  final case class RightsFetchKey(blockHash: TezosBlockHash, cycle: Option[Int], governancePeriod: Option[Int])

  object Fee {

    /**
      * Representation of estimation of average fees for a given operation kind.
      * @param low       Medium - one standard deviation
      * @param medium    The mean of fees on a given operation kind
      * @param high      Medium + one standard deviation
      * @param timestamp The timestamp when the calculation took place
      * @param kind      The kind of operation being averaged over
      * @param cycle     The latest operation cycle when the computation took place
      * @param level     The latest operation level when the computation took place
      */
    final case class AverageFees(
        low: Int,
        medium: Int,
        high: Int,
        timestamp: java.sql.Timestamp,
        kind: String,
        cycle: Option[Int],
        level: BlockLevel
    )
  }

}
