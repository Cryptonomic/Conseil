package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.model.Model
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.util.Conversion
import tech.cryptonomic.conseil.util.Conversion.Id

import scala.util.Try

object DatabaseConversions {

  //single field conversions
  private def concatenateToString[A, T[_] <: scala.collection.GenTraversableOnce[_]](traversable: T[A]): String = traversable.mkString("[", ",", "]")

  private def extractBigDecimal(number: TezosOperations.PositiveBigNumber): Option[BigDecimal] = number match {
    case TezosOperations.PositiveDecimal(value) => Some(value)
    case _ => None
  }

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

  implicit val blockAccountsToAccountRows = new Conversion[List, BlockAccounts, Tables.AccountsRow] {
    override def convert(from: BlockAccounts) = {
      val BlockAccounts(hash, level, accounts) = from
      accounts.map {
        case (id, Account(manager, balance, spendable, delegate, script, counter)) =>
        Tables.AccountsRow(
          accountId = id.id,
          blockId = hash.value,
          manager = manager,
          spendable = spendable,
          delegateSetable = delegate.setable,
          delegateValue = delegate.value,
          counter = counter,
          script = script.map(_.toString),
          balance = balance,
          blockLevel = level
        )
      }.toList
    }
  }

  implicit val blockToBlocksRow = new Conversion[Id, Block, Tables.BlocksRow] {
    override def convert(from: Block) = {
      val header = from.metadata.header
      Tables.BlocksRow(
        level = header.level,
        proto = header.proto,
        predecessor = header.predecessor.value,
        timestamp = header.timestamp,
        validationPass = header.validationPass,
        fitness = header.fitness.mkString(","),
        context = Some(header.context), //put in later
        signature = header.signature,
        protocol = from.metadata.protocol,
        chainId = from.metadata.chain_id,
        hash = from.metadata.hash.value,
        operationsHash = header.operations_hash
      )
    }
  }

  implicit val blockToOperationGroupsRow = new Conversion[List, Block, Tables.OperationGroupsRow] {
    override def convert(from: Block) =
      from.operationGroups.map{ og =>
        Tables.OperationGroupsRow(
          protocol = og.protocol,
          chainId = og.chain_id.map(_.id),
          hash = og.hash.value,
          branch = og.branch.value,
          signature = og.signature.map(_.value),
          blockId = from.metadata.hash.value
        )
      }
  }

  //Cannot directly convert a single operation to a row, because we need the block and operation-group info to build the database row
  implicit val operationToOperationsRowReader = new Conversion[Id, (Block, OperationHash, TezosOperations.Operation), Tables.OperationsRow] {
    override def convert(from: (Block, OperationHash, TezosOperations.Operation)) =
      (convertEndorsement orElse
      convertNonceRevelation orElse
      convertActivateAccount orElse
      convertReveal orElse
      convertTransaction orElse
      convertOrigination orElse
      convertDelegation orElse
      convertUnhandledOperations)(from)
  }

  private val convertEndorsement: PartialFunction[(Block, OperationHash, TezosOperations.Operation), Tables.OperationsRow] = {
    case (block, groupHash, TezosOperations.Endorsement(level, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "endorsement",
        level = Some(level),
        delegate = Some(metadata.delegate.value),
        slots = Some(metadata.slots).map(concatenateToString),
        blockHash = block.metadata.hash.value,
        timestamp = block.metadata.header.timestamp
      )
  }

  private val convertNonceRevelation: PartialFunction[(Block, OperationHash, TezosOperations.Operation), Tables.OperationsRow] = {
    case (block, groupHash, TezosOperations.SeedNonceRevelation(level, nonce, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "seed_nonce_revelation",
        level = Some(level),
        nonce = Some(nonce.value),
        blockHash = block.metadata.hash.value,
        timestamp = block.metadata.header.timestamp
      )
  }

  private val convertActivateAccount: PartialFunction[(Block, OperationHash, TezosOperations.Operation), Tables.OperationsRow] = {
    case (block, groupHash, TezosOperations.ActivateAccount(pkh, secret, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "activate_account",
        pkh = Some(pkh.value),
        secret = Some(secret.value),
        blockHash = block.metadata.hash.value,
        timestamp = block.metadata.header.timestamp
    )
  }

  private val convertReveal: PartialFunction[(Block, OperationHash, TezosOperations.Operation), Tables.OperationsRow] = {
    case (block, groupHash, TezosOperations.Reveal(counter, fee, gas_limit, storage_limit, pk, source, metadata)) =>
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
        blockHash = block.metadata.hash.value,
        timestamp = block.metadata.header.timestamp
    )
  }

  private val convertTransaction: PartialFunction[(Block, OperationHash, TezosOperations.Operation), Tables.OperationsRow] = {
    case (block, groupHash, TezosOperations.Transaction(counter, amount, fee, gas_limit, storage_limit, source, destination, parameters, metadata)) =>
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
        blockHash = block.metadata.hash.value,
        timestamp = block.metadata.header.timestamp
    )
  }

  private val convertOrigination: PartialFunction[(Block, OperationHash, TezosOperations.Operation), Tables.OperationsRow] = {
    case (block, groupHash, TezosOperations.Origination(counter, fee, source, balance, gas_limit, storage_limit, mpk, delegatable, delegate, spendable, script, metadata)) =>
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
        status = Some(metadata.operation_result.status),
        blockHash = block.metadata.hash.value,
        timestamp = block.metadata.header.timestamp
    )
  }

  private val convertDelegation: PartialFunction[(Block, OperationHash, TezosOperations.Operation), Tables.OperationsRow] = {
    case (block, groupHash, TezosOperations.Delegation(counter, source, fee, gas_limit, storage_limit, delegate, metadata)) =>
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
        blockHash = block.metadata.hash.value,
        timestamp = block.metadata.header.timestamp
    )
  }

  private val convertUnhandledOperations: PartialFunction[(Block, OperationHash, TezosOperations.Operation), Tables.OperationsRow] = {
    case (block, groupHash, op) =>
      val kind = op match {
        case TezosOperations.DoubleEndorsementEvidence => "double_endorsement_evidence"
        case TezosOperations.DoubleBakingEvidence => "double_baking_evidence"
        case TezosOperations.Proposals => "proposals"
        case TezosOperations.Ballot => "ballot"
        case _ => ""
      }
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = kind,
        blockHash = block.metadata.hash.value,
        timestamp = block.metadata.header.timestamp
      )
  }

  implicit val blockToOperationsRow = new Conversion[List, Block, Tables.OperationsRow] {
    import tech.cryptonomic.conseil.util.ConversionSyntax._

    override def convert(from: Block) =
      from.operationGroups.flatMap { group =>
        group.contents.map { op =>
          (from, group.hash, op).convertTo[Tables.OperationsRow]
        }
      }

  }

  implicit val modelOperationToRow = new Conversion[Id, Model.Operation, Tables.OperationsRow] {
    override def convert(from: Model.Operation) =
      Tables.OperationsRow(
        operationId = from.operationId,
        operationGroupHash = from.operationGroupHash,
        kind = from.kind,
        level = from.level,
        delegate = from.delegate,
        slots = from.slots,
        nonce = from.nonce,
        pkh = from.pkh,
        secret = from.secret,
        source = from.source,
        fee = from.fee,
        counter = from.counter,
        gasLimit = from.gasLimit,
        storageLimit = from.storageLimit,
        publicKey = from.publicKey,
        amount = from.amount,
        destination = from.destination,
        parameters = from.parameters,
        managerPubkey = from.managerPubkey,
        balance = from.balance,
        spendable = from.spendable,
        delegatable = from.delegatable,
        script = from.script,
        blockHash = from.blockHash,
        timestamp = from.timestamp
      )
}

  def convertBlockAccountsAssociation(blockHash: BlockHash, blockLevel: Int, ids: List[AccountId]): List[Tables.AccountsCheckpointRow] =
    ids.map(
      accountId =>
        Tables.AccountsCheckpointRow(
          accountId = accountId.id,
          blockId = blockHash.value,
          blockLevel = blockLevel
        )
    )

}
