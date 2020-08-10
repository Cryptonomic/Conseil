package tech.cryptonomic.conseil.indexer

import java.time.Instant

/** Assuming a fork is detected at a given level, defines the appropriate
  * compensating actions to be applied to the currently indexed data.
  *
  * - BlockId is the type identifying the block. We can't assume anything
  *   specific here, because each blockchain may have its own type to
  *   identify blocks uniquely
  * - Eff is a wrapping effect we expect from the operation call
  *   we expect that most implementation will require something like Future/IO/Try
  *   to be returned with the value, because such operation will need to reach
  *   across the network or on a local service/db in some way
  */
trait ForkAmender[Eff[_], BlockId] {

  /** Handle forks by invalidating all data which is "above" the identified fork level.
    * We expect to identify the level of blocks (i.e. the position in the chain) using a [[Long]]
    * value, even when it's actually represented via a different custom type in the specific chain
    * implementation.
    *
    * @param forkLevel the level whose block value starts to differ between forks
    * @param forkedBlockId the id of the locally indexed block corresponding to the fork level
    * @param indexedHeadLevel the level reached by the indexer before detecting the fork
    * @param detectionTime time when the detection occurred
    * @return a fork identifier along with amended entities
    */
  def amendFork(
      forkLevel: Long,
      forkedBlockId: BlockId,
      indexedHeadLevel: Long,
      detectionTime: Instant
  ): Eff[ForkAmender.Results]
}

object ForkAmender {

  /** The outcome values of amendment is a pair denoting
    * - an identifier for the fork, as a String
    * - the number of entities affected by the amendment (i.e. impacted records)
    */
  type Results = (String, Int)
}
