package tech.cryptonomic.conseil.indexer.tezos

import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BlockData, OperationMetadata}
import tech.cryptonomic.conseil.common.testkit.util.RandomGenerationKit
import tech.cryptonomic.conseil.common.tezos.TezosTypes.BlockHeaderMetadata

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

}
