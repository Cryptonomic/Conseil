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

  private def parseBigDecimal(s: String): Option[BigDecimal] = Try(BigDecimal(s)).toOption

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
          chainId = og.chain_id,
          hash = og.hash.value,
          branch = og.branch,
          signature = og.signature,
          blockId = from.metadata.hash.value
        )
      }
  }

  implicit val operationToOperationsRowReader = new Conversion[Id, Operation, (Block, OperationHash) => Tables.OperationsRow] {
    override def convert(from: Operation) =
      (block, groupHash) =>
        Tables.OperationsRow(
          operationId = 0,
          operationGroupHash = groupHash.value,
          kind = from.kind,
          level = from.level,
          delegate = from.delegate,
          slots = from.slots.map(concatenateToString),
          nonce = from.nonce,
          pkh = from.pkh,
          secret = from.secret,
          source = from.source,
          fee = from.fee.flatMap(parseBigDecimal),
          counter = from.counter.map(BigDecimal.apply),
          gasLimit = from.gasLimit.flatMap(parseBigDecimal),
          storageLimit = from.storageLimit.flatMap(parseBigDecimal),
          publicKey = from.publicKey,
          amount = from.amount.flatMap(parseBigDecimal),
          destination = from.destination,
          parameters = from.parameters.map(_.toString),
          managerPubkey = from.managerPubKey,
          balance = from.balance.flatMap(parseBigDecimal),
          spendable = from.spendable,
          delegatable = from.delegatable,
          script = None,
          blockHash = block.metadata.hash.value,
          timestamp = block.metadata.header.timestamp
        )
  }

  implicit val blockToOperationsRow = new Conversion[List, Block, Tables.OperationsRow] {
    override def convert(from: Block) =
      from.operationGroups.flatMap { og =>
        og.contents.fold(List.empty[Tables.OperationsRow]) {
          operations =>
            operations.map(Conversion[Id, Operation, (Block, OperationHash) => Tables.OperationsRow].convert)
              .map(_.apply(from, og.hash))
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
