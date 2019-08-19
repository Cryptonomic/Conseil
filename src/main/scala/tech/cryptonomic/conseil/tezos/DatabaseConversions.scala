package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.util.Conversion
import cats.{Id, Show}
import java.sql.Timestamp
import monocle.Getter
import io.scalaland.chimney.dsl._

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

  //Note, cycle 0 starts at the level 2 block
  def extractCycle(block: Block): Option[Int] =
    discardGenesis.lift(block.data.metadata) //this returns an Option[BlockHeaderMetadata]
      .map(_.level.cycle) //this is Option[Int]

  //implicit conversions to database row types

  implicit val averageFeesToFeeRow = new Conversion[Id, AverageFees, Tables.FeesRow] {
    override def convert(from: AverageFees) =
      Tables.FeesRow(
        low = from.low,
        medium = from.medium,
        high = from.high,
        timestamp = from.timestamp,
        kind = from.kind
      )
  }

  implicit val blockAccountsToAccountRows =
    new Conversion[List, BlockTagged[Map[AccountId, Account]], Tables.AccountsRow] {
      override def convert(from: BlockTagged[Map[AccountId, Account]]) = {
        val BlockTagged(hash, level, accounts) = from
        accounts.map {
          case (id, Account(manager, balance, spendable, delegate, script, counter)) =>
            Tables.AccountsRow(
              accountId = id.id,
              blockId = hash.value,
              manager = manager.value,
              spendable = spendable,
              delegateSetable = delegate.setable,
              delegateValue = delegate.value.map(_.value),
              counter = counter,
              script = script.map(_.code.expression),
              storage = script.map(_.storage.expression),
              balance = balance,
              blockLevel = level
            )
        }.toList
      }
    }

  implicit val accountRowsToContractRows = new Conversion[Id, Tables.AccountsRow, Tables.DelegatedContractsRow] {
    override def convert(from: Tables.AccountsRow) = from.into[Tables.DelegatedContractsRow].transform
  }

  implicit val blockToBlocksRow = new Conversion[Id, Block, Tables.BlocksRow] {
    override def convert(from: Block) = {
      val header = from.data.header
      val metadata = discardGenesis.lift(from.data.metadata)
      val CurrentVotes(expectedQuorum, proposal) = from.votes
      Tables.BlocksRow(
        level = header.level,
        proto = header.proto,
        predecessor = header.predecessor.value,
        timestamp = toSql(header.timestamp),
        validationPass = header.validation_pass,
        fitness = header.fitness.mkString(","),
        context = Some(header.context), //put in later
        signature = header.signature,
        protocol = from.data.protocol,
        chainId = from.data.chain_id,
        hash = from.data.hash.value,
        operationsHash = header.operations_hash,
        periodKind = metadata.map(_.voting_period_kind.toString),
        currentExpectedQuorum = expectedQuorum,
        activeProposal = proposal.map(_.id),
        baker = metadata.map(_.baker.value),
        nonceHash = metadata.flatMap(_.nonce_hash.map(_.value)),
        consumedGas = metadata.flatMap(md => extractBigDecimal(md.consumed_gas)),
        metaLevel = metadata.map(_.level.level),
        metaLevelPosition = metadata.map(_.level.level_position),
        metaCycle = metadata.map(_.level.cycle),
        metaCyclePosition = metadata.map(_.level.cycle_position),
        metaVotingPeriod = metadata.map(_.level.voting_period),
        metaVotingPeriodPosition = metadata.map(_.level.voting_period_position),
        expectedCommitment = metadata.map(_.level.expected_commitment),
        priority = header.priority
      )
    }
  }

  implicit val blockToOperationGroupsRow = new Conversion[List, Block, Tables.OperationGroupsRow] {
    override def convert(from: Block) =
      from.operationGroups.map { og =>
        Tables.OperationGroupsRow(
          protocol = og.protocol,
          chainId = og.chain_id.map(_.id),
          hash = og.hash.value,
          branch = og.branch.value,
          signature = og.signature.map(_.value),
          blockId = from.data.hash.value,
          blockLevel = from.data.header.level
        )
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
          convertBallot orElse
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
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block),
        branch = Some(block.operationGroups.find(h => h.hash == groupHash).get.branch.value),
        numberOfSlots = Some(metadata.slots.length)
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
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block)
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
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block)
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
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block)
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
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block)
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
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block)
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
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block)
      )
  }


  private val convertBallot: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Ballot(ballot, proposal, source)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "ballot",
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        ballot = Some(ballot.value),
        internal = false,
        proposal = proposal,
        source = source.map(_.id),
        cycle = extractCycle(block)
      )
  }

  private val convertUnhandledOperations: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, op) =>
      val kind = op match {
        case DoubleEndorsementEvidence => "double_endorsement_evidence"
        case DoubleBakingEvidence => "double_baking_evidence"
        case Proposals => "proposals"
        case _ => ""
      }
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = kind,
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block)
      )
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
      balances
        .get(from)
        .flatMap {
          case (tag, updates) =>
            updates.map {
              case OperationMetadata.BalanceUpdate(
                  kind,
                  change,
                  category,
                  contract,
                  delegate,
                  level
                  ) =>
                Tables.BalanceUpdatesRow(
                  id = 0,
                  source = tag.show,
                  sourceHash = hashing.get(from),
                  kind = kind,
                  contract = contract.map(_.id),
                  change = BigDecimal(change),
                  level = level.map(BigDecimal(_)),
                  delegate = delegate.map(_.value),
                  category = category
                )
            }
        }
        .toList
  }

  /** Utility alias when we need to keep related data paired together */
  type OperationTablesData = (Tables.OperationsRow, List[Tables.BalanceUpdatesRow])

  /** */
  implicit val internalOperationResultToOperation =
    new Conversion[Id, InternalOperationResults.InternalOperationResult, Operation] {
      override def convert(from: InternalOperationResults.InternalOperationResult): Id[Operation] =
        // counter/fee/gas_limit/storage_limit are not in internal operations results,
        // so values below are going to be discarded during transformation Operation -> OperationRow
        from match {
          case reveal: InternalOperationResults.Reveal =>
            reveal
              .into[Reveal]
              .withFieldConst(_.counter, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.fee, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.gas_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.storage_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.metadata, ResultMetadata[OperationResult.Reveal](reveal.result, List.empty, None))
              .transform
          case transaction: InternalOperationResults.Transaction =>
            transaction
              .into[Transaction]
              .withFieldConst(_.counter, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.fee, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.gas_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.storage_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(
                _.metadata,
                ResultMetadata[OperationResult.Transaction](
                  transaction.result,
                  transaction.result.balance_updates.getOrElse(List.empty),
                  None
                )
              )
              .transform

          case origination: InternalOperationResults.Origination =>
            origination
              .into[Origination]
              .withFieldConst(_.counter, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.fee, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.gas_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.storage_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(
                _.metadata,
                ResultMetadata[OperationResult.Origination](
                  origination.result,
                  origination.result.balance_updates.getOrElse(List.empty),
                  None
                )
              )
              .transform

          case delegation: InternalOperationResults.Delegation =>
            delegation
              .into[Delegation]
              .withFieldConst(_.counter, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.fee, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.gas_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.storage_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(
                _.metadata,
                ResultMetadata[OperationResult.Delegation](delegation.result, List.empty, None)
              )
              .transform
        }
    }

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
        group.contents.flatMap { op =>
          val internalOperationResults = op match {
            case r: Reveal => r.metadata.internal_operation_results.toList.flatten
            case t: Transaction => t.metadata.internal_operation_results.toList.flatten
            case o: Origination => o.metadata.internal_operation_results.toList.flatten
            case d: Delegation => d.metadata.internal_operation_results.toList.flatten
            case _ => List.empty
          }

          val internalRows = internalOperationResults.map { oop =>
            val op = oop.convertTo[Operation]
            (from, group.hash, op)
              .convertTo[Tables.OperationsRow]
              .copy(internal = true, nonce = Some(oop.nonce.toString)) -> op.convertToA[List, Tables.BalanceUpdatesRow]
          }
          val operationRow = (from, group.hash, op).convertTo[Tables.OperationsRow]
          val balanceUpdateRows = op.convertToA[List, Tables.BalanceUpdatesRow]
          (operationRow, balanceUpdateRows) :: internalRows
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
      Tables.DelegatesRow(
        pkh = keyHash.value,
        blockId = blockHash.value,
        balance = extractBigDecimal(delegate.balance),
        frozenBalance = extractBigDecimal(delegate.frozen_balance),
        stakingBalance = extractBigDecimal(delegate.staking_balance),
        delegatedBalance = extractBigDecimal(delegate.delegated_balance),
        deactivated = delegate.deactivated,
        gracePeriod = delegate.grace_period,
        blockLevel = blockLevel
      )
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
      val blockHash = block.data.hash.value
      val blockLevel = block.data.header.level
      ballots.map {
        case Voting.Ballot(PublicKeyHash(hash), Voting.Vote(vote)) =>
          Tables.BallotsRow(
            pkh = hash,
            ballot = vote,
            blockId = blockHash,
            blockLevel = blockLevel
          )
      }
    }
  }

  implicit val rollsToRows = new Conversion[List, (Block, List[Voting.BakerRolls]), Tables.RollsRow] {
    override def convert(from: (Block, List[Voting.BakerRolls])) = {
      val (block, bakers) = from
      val blockHash = block.data.hash.value
      val blockLevel = block.data.header.level
      bakers.map {
        case Voting.BakerRolls(PublicKeyHash(hash), rolls) =>
          Tables.RollsRow(
            pkh = hash,
            rolls = rolls,
            blockId = blockHash,
            blockLevel = blockLevel
          )
      }
    }
  }

}
