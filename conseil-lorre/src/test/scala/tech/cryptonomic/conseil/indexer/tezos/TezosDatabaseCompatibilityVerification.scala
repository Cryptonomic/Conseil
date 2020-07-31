package tech.cryptonomic.conseil.indexer.tezos

import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BlockData, OperationMetadata}
import tech.cryptonomic.conseil.common.testkit.util.RandomGenerationKit
import tech.cryptonomic.conseil.common.tezos.TezosTypes.BlockHeaderMetadata
import tech.cryptonomic.conseil.common.tezos.Tables

/** Provides methods to verify if all fields for the tezos entities can
  * be safely stored in a database, specifially when randomly generated.
  */
trait TezosDatabaseCompatibilityVerification {
  self: RandomGenerationKit =>
  /* the self-type constraints guarantees that any mix-in will also contain the
   * traits declared, so we can safely use the methods therein
   */

  /** Are all data fields in the entity safe to write on db? */
  protected def canBeWrittenToDb(blockData: BlockData): Boolean = {
    val header = blockData.header

    blockData.chain_id.forall(canBeWrittenToDb) &&
    canBeWrittenToDb(blockData.protocol) &&
    canBeWrittenToDb(header.predecessor.value) &&
    canBeWrittenToDb(header.context) &&
    header.operations_hash.forall(canBeWrittenToDb) &&
    header.fitness.forall(canBeWrittenToDb)

  }

  /** Are all data fields in the entity safe to write on db? */
  protected def canBeWrittenToDb(metadata: BlockHeaderMetadata): Boolean =
    metadata.balance_updates.forall(canBeWrittenToDb) &&
      metadata.nonce_hash.forall(hash => canBeWrittenToDb(hash.value)) &&
      canBeWrittenToDb(metadata.baker.value)

  /** Are all data fields in the entity safe to write on db? */
  protected def canBeWrittenToDb(balanceUpdate: OperationMetadata.BalanceUpdate): Boolean =
    canBeWrittenToDb(balanceUpdate.kind) &&
      balanceUpdate.category.forall(canBeWrittenToDb) &&
      balanceUpdate.contract.forall(id => canBeWrittenToDb(id.id)) &&
      balanceUpdate.delegate.forall(pkh => canBeWrittenToDb(pkh.value))

  /** Are all data fields in the entity safe to write on db? */
  protected def canBeWrittenToDb(operationRows: Tables.OperationsRow): Boolean =
    canBeWrittenToDb(operationRows.operationGroupHash) &&
      canBeWrittenToDb(operationRows.blockHash) &&
      operationRows.branch.forall(canBeWrittenToDb) &&
      operationRows.delegate.forall(canBeWrittenToDb) &&
      operationRows.slots.forall(canBeWrittenToDb) &&
      operationRows.nonce.forall(canBeWrittenToDb) &&
      operationRows.pkh.forall(canBeWrittenToDb) &&
      operationRows.secret.forall(canBeWrittenToDb) &&
      operationRows.source.forall(canBeWrittenToDb) &&
      operationRows.publicKey.forall(canBeWrittenToDb) &&
      operationRows.destination.forall(canBeWrittenToDb) &&
      operationRows.parameters.forall(canBeWrittenToDb) &&
      operationRows.parametersEntrypoints.forall(canBeWrittenToDb) &&
      operationRows.parametersMicheline.forall(canBeWrittenToDb) &&
      operationRows.managerPubkey.forall(canBeWrittenToDb) &&
      operationRows.proposal.forall(canBeWrittenToDb) &&
      operationRows.script.forall(canBeWrittenToDb) &&
      operationRows.storage.forall(canBeWrittenToDb) &&
      operationRows.status.forall(canBeWrittenToDb) &&
      operationRows.originatedContracts.forall(canBeWrittenToDb) &&
      operationRows.ballot.forall(canBeWrittenToDb) &&
      operationRows.errors.forall(canBeWrittenToDb)

}
