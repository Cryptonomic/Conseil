package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.util.Conversion
import cats.{Id, Show}
import cats.syntax.option._
import mouse.any._
import java.sql.Timestamp
import monocle.Getter
import io.scalaland.chimney.dsl._
import io.scalaland.chimney.Transformer

object DatabaseConversions {

  //adapts from the java timestamp to sql
  private def toSql(datetime: java.time.ZonedDateTime): Timestamp = Timestamp.from(datetime.toInstant)

  //single field conversions
  def concatenateToString[A, T[_] <: scala.collection.GenTraversableOnce[_]](traversable: T[A]): String =
    traversable.mkString("[", ",", "]")

  def toCommaSeparated[A, T[_] <: scala.collection.GenTraversableOnce[_]](traversable: T[A]): String =
    traversable.mkString(",")

  def extractBigDecimal(number: PositiveBigNumber): Option[BigDecimal] = number match {
    case PositiveDecimal(value) => Some(value)
    case _ => None
  }

  def extractBigDecimal(number: BigNumber): Option[BigDecimal] = number match {
    case Decimal(value) => Some(value)
    case _ => None
  }

  //implicit conversions to database row types

  implicit val averageFeesToFeeRow = new Conversion[Id, AverageFees, Tables.FeesRow] {
    override def convert(from: AverageFees) = from.transformInto[Tables.FeesRow]
  }

  implicit val blockAccountsToAccountRows =
    new Conversion[List, BlockTagged[Map[AccountId, Account]], Tables.AccountsRow] {
      override def convert(from: BlockTagged[Map[AccountId, Account]]) = {
        val BlockTagged(hash, level, accounts) = from

        implicit val rowTransformer: Transformer[(AccountId, Account), Tables.AccountsRow] =
          (entry: (AccountId, Account)) => {
            val (id, acc) = entry
            acc
              .into[Tables.AccountsRow]
              .withFieldConst(_.accountId, id.id)
              .withFieldConst(_.blockId, hash.value)
              .withFieldConst(_.blockLevel, BigDecimal(level))
              .withFieldComputed(_.delegateSetable, _.delegate.setable)
              .withFieldComputed(_.delegateValue, _.delegate.value.map((_.value)))
              .withFieldComputed(_.script, _.script.map(_.code.expression))
              .withFieldComputed(_.storage, _.script.map(_.storage.expression))
              .transform
          }

        accounts.toList.transformInto[List[Tables.AccountsRow]]
      }
    }

  implicit val accountRowsToContractRows = new Conversion[Id, Tables.AccountsRow, Tables.DelegatedContractsRow] {
    override def convert(from: Tables.AccountsRow) = from.transformInto[Tables.DelegatedContractsRow]
  }

  implicit val blockToBlocksRow = new Conversion[Id, Block, Tables.BlocksRow] {
    override def convert(from: Block) = {
      val metadata = discardGenesis.lift(from.data.metadata)
      from
        .into[Tables.BlocksRow]
        .withFieldComputed(_.level, _.data.header.level)
        .withFieldComputed(_.proto, _.data.header.proto)
        .withFieldComputed(_.predecessor, _.data.header.predecessor.value)
        .withFieldComputed(_.timestamp, _.data.header.timestamp |> toSql)
        .withFieldComputed(_.validationPass, _.data.header.validation_pass)
        .withFieldComputed(_.fitness, _.data.header.fitness |> toCommaSeparated)
        .withFieldComputed(_.context, _.data.header.context.some)
        .withFieldComputed(_.signature, _.data.header.signature)
        .withFieldComputed(_.protocol, _.data.protocol)
        .withFieldComputed(_.chainId, _.data.chain_id)
        .withFieldComputed(_.hash, _.data.hash.value)
        .withFieldComputed(_.operationsHash, _.data.header.operations_hash)
        .withFieldConst(_.periodKind, metadata.map(_.voting_period_kind.toString))
        .withFieldComputed(_.currentExpectedQuorum, _.votes.quorum)
        .withFieldComputed(_.activeProposal, _.votes.active.map(_.id))
        .withFieldConst(_.baker, metadata.map(_.baker.value))
        .withFieldConst(_.nonceHash, metadata.flatMap(_.nonce_hash.map(_.value)))
        .withFieldConst(_.consumedGas, metadata.flatMap(_.consumed_gas |> extractBigDecimal))
        .withFieldConst(_.metaLevel, metadata.map(_.level.level))
        .withFieldConst(_.metaLevelPosition, metadata.map(_.level.level_position))
        .withFieldConst(_.metaCycle, metadata.map(_.level.cycle))
        .withFieldConst(_.metaCyclePosition, metadata.map(_.level.cycle_position))
        .withFieldConst(_.metaVotingPeriod, metadata.map(_.level.voting_period))
        .withFieldConst(_.metaVotingPeriodPosition, metadata.map(_.level.voting_period_position))
        .withFieldConst(_.expectedCommitment, metadata.map(_.level.expected_commitment))
        .transform
    }
  }

  implicit val blockToOperationGroupsRow = new Conversion[List, Block, Tables.OperationGroupsRow] {
    override def convert(from: Block) = {
      val rowTransformation =
        (og: OperationsGroup) => {
          og.into[Tables.OperationGroupsRow]
            .withFieldConst(_.blockId, from.data.hash.value)
            .withFieldConst(_.blockLevel, from.data.header.level)
            .withFieldComputed(_.chainId, _.chain_id.map(_.id))
            .withFieldComputed(_.signature, _.signature.map(_.value))
            .transform
        }

      from.operationGroups.map(rowTransformation)
    }
  }

  //Cannot directly convert a single operation to a row, because we need the block and operation-group info to build the database row
  implicit val operationToOperationsRow = new Conversion[Id, (Block, OperationHash, Operation), Tables.OperationsRow] {
    override def convert(from: (Block, OperationHash, Operation)) =
      (convertEndorsement orElse
          convertNonceRevelation orElse
          convertActivateAccount orElse
          convertReveal orElse
          convertTransaction orElse
          convertOrigination orElse
          convertDelegation orElse
          convertUnhandledOperations)(from)
  }

  private val convertEndorsement: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Endorsement(level, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "endorsement",
        level = Some(level),
        delegate = Some(metadata.delegate.value),
        slots = Some(metadata.slots).map(concatenateToString),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp)
      )
  }

  private val convertNonceRevelation: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, SeedNonceRevelation(level, nonce, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "seed_nonce_revelation",
        level = Some(level),
        nonce = Some(nonce.value),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp)
      )
  }

  private val convertActivateAccount: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, ActivateAccount(pkh, secret, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "activate_account",
        pkh = Some(pkh.value),
        secret = Some(secret.value),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp)
      )
  }

  private val convertReveal: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Reveal(counter, fee, gas_limit, storage_limit, pk, source, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "reveal",
        source = Some(source.id),
        fee = extractBigDecimal(fee),
        counter = extractBigDecimal(counter),
        gasLimit = extractBigDecimal(gas_limit),
        storageLimit = extractBigDecimal(storage_limit),
        publicKey = Some(pk.value),
        status = Some(metadata.operation_result.status),
        consumedGas = metadata.operation_result.consumed_gas.flatMap(extractBigDecimal),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp)
      )
  }

  private val convertTransaction: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (
        block,
        groupHash,
        Transaction(counter, amount, fee, gas_limit, storage_limit, source, destination, parameters, metadata)
        ) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "transaction",
        source = Some(source.id),
        fee = extractBigDecimal(fee),
        counter = extractBigDecimal(counter),
        gasLimit = extractBigDecimal(gas_limit),
        storageLimit = extractBigDecimal(storage_limit),
        amount = extractBigDecimal(amount),
        destination = Some(destination.id),
        parameters = parameters.map(_.expression),
        status = Some(metadata.operation_result.status),
        consumedGas = metadata.operation_result.consumed_gas.flatMap(extractBigDecimal),
        storageSize = metadata.operation_result.storage_size.flatMap(extractBigDecimal),
        paidStorageSizeDiff = metadata.operation_result.paid_storage_size_diff.flatMap(extractBigDecimal),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp)
      )
  }

  private val convertOrigination: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (
        block,
        groupHash,
        Origination(
          counter,
          fee,
          source,
          balance,
          gas_limit,
          storage_limit,
          mpk,
          delegatable,
          delegate,
          spendable,
          script,
          metadata
        )
        ) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "origination",
        delegate = delegate.map(_.value),
        source = Some(source.id),
        fee = extractBigDecimal(fee),
        counter = extractBigDecimal(counter),
        gasLimit = extractBigDecimal(gas_limit),
        storageLimit = extractBigDecimal(storage_limit),
        managerPubkey = Some(mpk.value),
        balance = extractBigDecimal(balance),
        spendable = spendable,
        delegatable = delegatable,
        script = script.map(_.code.expression),
        storage = script.map(_.storage.expression),
        status = Some(metadata.operation_result.status),
        consumedGas = metadata.operation_result.consumed_gas.flatMap(extractBigDecimal),
        storageSize = metadata.operation_result.storage_size.flatMap(extractBigDecimal),
        paidStorageSizeDiff = metadata.operation_result.paid_storage_size_diff.flatMap(extractBigDecimal),
        originatedContracts = metadata.operation_result.originated_contracts.map(_.map(_.id)).map(toCommaSeparated),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp)
      )
  }

  private val convertDelegation: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Delegation(counter, source, fee, gas_limit, storage_limit, delegate, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "delegation",
        delegate = delegate.map(_.value),
        source = Some(source.id),
        fee = extractBigDecimal(fee),
        counter = extractBigDecimal(counter),
        gasLimit = extractBigDecimal(gas_limit),
        storageLimit = extractBigDecimal(storage_limit),
        status = Some(metadata.operation_result.status),
        consumedGas = metadata.operation_result.consumed_gas.flatMap(extractBigDecimal),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp)
      )
  }

  private val convertUnhandledOperations: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, op) =>
      val kind = op match {
        case DoubleEndorsementEvidence => "double_endorsement_evidence"
        case DoubleBakingEvidence => "double_baking_evidence"
        case Proposals => "proposals"
        case Ballot => "ballot"
        case _ => ""
      }
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = kind,
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp)
      )
  }

  implicit val blockToOperationsRow = new Conversion[List, Block, Tables.OperationsRow] {
    import tech.cryptonomic.conseil.util.Conversion.Syntax._

    override def convert(from: Block) =
      from.operationGroups.flatMap { group =>
        group.contents.map { op =>
          (from, group.hash, op).convertTo[Tables.OperationsRow]
        }
      }

  }

  /** Not all operations have some form of balance updates... we're ignoring some type, like ballots,
    * thus we can't extract from those anyway.
    * We get back an empty List, where not applicable
    * We define a typeclass/trait that encodes the availability of balance updates, to reuse it for operations as well as blocks
    * @tparam T the source type that contains the balance updates values
    * @tparam Label the source Label type, which has a `Show` contraint, to be representable as a string
    * @param balances an optic instance that lets extract the Map of tagged updates from the source
    * @param hashing an optic instance to extract an optional reference hash value
    */
  implicit def anyToBalanceUpdates[T, Label](
      implicit
      balances: Getter[T, Map[Label, List[OperationMetadata.BalanceUpdate]]],
      hashing: Getter[T, Option[String]],
      showing: Show[Label]
  ) = new Conversion[List, T, Tables.BalanceUpdatesRow] {
    import cats.syntax.show._

    override def convert(from: T) =
      for {
        (label, updates) <- balances.get(from).toList
        update <- updates
      } yield
        Tables.BalanceUpdatesRow(
          id = 0,
          source = label.show,
          sourceHash = hashing.get(from),
          kind = update.kind,
          contract = update.contract.map(_.id),
          change = BigDecimal(update.change),
          level = update.level.map(BigDecimal(_)),
          delegate = update.delegate.map(_.value),
          category = update.category
        )
  }

  /** Utility alias when we need to keep related data paired together */
  type OperationTablesData = (Tables.OperationsRow, List[Tables.BalanceUpdatesRow])

  /** Will convert to paired list of operations with related balance updates
    * with one HUGE CAVEAT: both have only temporary, meaningless, `sourceId`s
    *
    * To correctly create the relation on the db, we must first store the operations, get
    * each generated id, and pass it to the associated balance-updates
    */
  implicit val blockToOperationTablesData = new Conversion[List, Block, OperationTablesData] {
    import tech.cryptonomic.conseil.util.Conversion.Syntax._
    import tech.cryptonomic.conseil.tezos.SymbolSourceLabels.Show._
    import tech.cryptonomic.conseil.tezos.OperationBalances._

    override def convert(from: Block) =
      from.operationGroups.flatMap { group =>
        group.contents.map { op =>
          val operationRow = (from, group.hash, op).convertTo[Tables.OperationsRow]
          val balanceUpdateRows = op.convertToA[List, Tables.BalanceUpdatesRow]
          (operationRow, balanceUpdateRows)
        }
      }

  }

  implicit val blockAccountsAssociationToCheckpointRow =
    new Conversion[List, (BlockHash, Int, List[AccountId]), Tables.AccountsCheckpointRow] {
      override def convert(from: (BlockHash, Int, List[AccountId])) = {
        val (blockHash, blockLevel, ids) = from
        ids.map(
          accountId =>
            Tables.AccountsCheckpointRow(
              accountId = accountId.id,
              blockId = blockHash.value,
              blockLevel = blockLevel
            )
        )
      }

    }

  implicit val blockDelegatesAssociationToCheckpointRow =
    new Conversion[List, (BlockHash, Int, List[PublicKeyHash]), Tables.DelegatesCheckpointRow] {
      override def convert(from: (BlockHash, Int, List[PublicKeyHash])) = {
        val (blockHash, blockLevel, pkhs) = from
        pkhs.map(
          keyHash =>
            Tables.DelegatesCheckpointRow(
              delegatePkh = keyHash.value,
              blockId = blockHash.value,
              blockLevel = blockLevel
            )
        )
      }

    }

  implicit val delegateToRow = new Conversion[Id, (BlockHash, Int, PublicKeyHash, Delegate), Tables.DelegatesRow] {
    override def convert(from: (BlockHash, Int, PublicKeyHash, Delegate)) = {
      val (blockHash, blockLevel, keyHash, delegate) = from
      delegate
        .into[Tables.DelegatesRow]
        .withFieldConst(_.pkh, keyHash.value)
        .withFieldConst(_.blockId, blockHash.value)
        .withFieldConst(_.blockLevel, blockLevel)
        .withFieldComputed(_.balance, _.balance |> extractBigDecimal)
        .withFieldComputed(_.frozenBalance, _.frozen_balance |> extractBigDecimal)
        .withFieldComputed(_.stakingBalance, _.staking_balance |> extractBigDecimal)
        .withFieldComputed(_.delegatedBalance, _.delegated_balance |> extractBigDecimal)
        .withFieldRenamed(_.grace_period, _.gracePeriod)
        .transform
    }
  }

  implicit val proposalToRow = new Conversion[List, Voting.Proposal, Tables.ProposalsRow] {
    override def convert(from: Voting.Proposal) = {
      val Voting.Proposal(protocols, block) = from
      val blockHash = block.data.hash.value
      val blockLevel = block.data.header.level
      protocols.map {
        case (ProtocolId(id), supporters) =>
          Tables.ProposalsRow(
            protocolHash = id,
            blockId = blockHash,
            blockLevel = blockLevel,
            supporters = Some(supporters)
          )
      }
    }
  }

  implicit val ballotsToRows = new Conversion[List, (Block, List[Voting.Ballot]), Tables.BallotsRow] {
    override def convert(from: (Block, List[Voting.Ballot])) = {
      val (block, ballots) = from

      ballots.map(
        _.into[Tables.BallotsRow]
          .withFieldConst(_.blockId, block.data.hash.value)
          .withFieldConst(_.blockLevel, block.data.header.level)
          .transform
      )
    }
  }

  implicit val rollsToRows = new Conversion[List, (Block, List[Voting.BakerRolls]), Tables.RollsRow] {
    override def convert(from: (Block, List[Voting.BakerRolls])) = {
      val (block, bakers) = from
      bakers.map(
        _.into[Tables.RollsRow]
          .withFieldConst(_.blockId, block.data.hash.value)
          .withFieldConst(_.blockLevel, block.data.header.level)
          .transform
      )
    }
  }

}
