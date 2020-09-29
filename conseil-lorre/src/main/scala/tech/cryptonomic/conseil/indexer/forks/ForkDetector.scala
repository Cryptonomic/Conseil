package tech.cryptonomic.conseil.indexer.forks

import cats._
import cats.implicits._
import com.typesafe.scalalogging.LazyLogging
import ForkDetector._

/** Definitions and common utilities for fork-detection */
object ForkDetector {

  /** A data type that expresses the outcomes of comparing block by-level-search results.
    * We refer to instances of the [[SearchBlockId]] interface, specifically.
    */
  sealed trait IdMatchResult

  /** Ids matched for a given level */
  case object SameId extends IdMatchResult

  /** Ids differed for a given level, therefore we assume a fork happened */
  case object ForkedId extends IdMatchResult

  /** Provides a block identifier, given a blockchain level,
    * i.e. how high in the chain is the block
    *
    * Generically provides a way to verify some chain position to verify
    * that a fork have happened, by comparing the block between a remote
    * and local version.
    *
    * - BlockId is the type identifying the block. We can't assume anything
    *   specific here, because each blockchain may have its own type to
    *   identify blocks uniquely
    * - Eff is a wrapping effect we expect from the operation call
    *   we expect that most implementation will require something like Future/IO/Try
    *   to be returned with the value, because such operation will need to reach
    *   across the network or on a local service/db in some way
    * - the search is expected to be able to match the chain internal representation for his
    *   level/height to an individual [[Long]] value, even if it's actually represented
    *   via a different type (e.g. a custom typed wrapped like BlockHeight(Int))
    */
  trait SearchBlockId[Eff[_], BlockId] {
    def searchForLevel(level: Long): Eff[BlockId]
  }

}

/** Defines the algorithms to detect and identify a fork
  * in the blockchain in comparison to the locally indexed data
  *
  * @param indexerSearch run the search on the local indexer
  * @param nodeSearch run the search on the remote blockchain node
  * @param monad a [[Monad]] provides ways to combine results in [[Eff]]
  * @param Eq for [[BlockId]] guarantees we can compare the ids
  */
class ForkDetector[Eff[_]: Monad, BlockId: Eq](
    indexerSearch: SearchBlockId[Eff, BlockId],
    nodeSearch: SearchBlockId[Eff, BlockId]
) extends LazyLogging {

  /** Extract blocks information from local and remote data for a specific level
    * checking if they match.
    *
    * @param level to check for a fork
    * @return a match result [[IdMatchResult]]
    */
  def checkOnLevel(level: Long): Eff[IdMatchResult] =
    (indexerSearch.searchForLevel(level), nodeSearch.searchForLevel(level)).mapN(
      (indexedBlock, chainBlock) =>
        if (indexedBlock.eqv(chainBlock)) SameId
        else ForkedId
    )

  /** Assuming a fork was detected for a specific level (i.e. high),
    * the algorithm backtracks to find the exact lowest level where the
    * locally indexed blocks start diverging from the forked blockchain.
    *
    * Note: this is not by itself a veritable proof that a fork necessarily
    * took place.
    * Technically speaking we are able to verify that the locally indexed data doesn't
    * match, whatever the cause may have been, and provides us with a reference
    * to re-align the local data with the blockchain node.
    *
    * The algorithm needs a level range to frame the actual search. Nothing prevents
    * the caller to use the first block - i.e. genesis level - as a lower boundary,
    * yet the search might be  more performant if a restricted range is available
    * guaranteeing a [[low]] value with a non-forked block.
    *
    * The input values should guarantee that `low < high`
    *
    * @param low provides a baseline level from which to do the search, should be a matching level
    * @param high provides a [known] level where the divergence was detected
    * @return the lowest possible level where the blocks doesn't match between the indexer and the node
    */
  def searchForkLevel(low: Long, high: Long): Eff[Long] =
    /* use binary search to find the first forked level in the range
     *
     * Caveat: if the Eff type doesn't provide tail-recursive support like [[cats.StackSafeMonad]],
     * there's risk of overflowing the stack with deeply nested results.
     * Considering a O(log N) steps, for 1e6 blocks and searching from the genesis would require
     * 20 steps at the worsts, which is quite reasonable.
     */
    //check for the stop condition
    high - low match {
      case 1 =>
        //termination
        high.pure[Eff]
      case diff if diff > 1 =>
        //one more step
        /* we need to round up the division to avoid bumping into an infinite loop
         * e.g with low = 1  high = 3 => mid = 1 which will once more call on the same inputs
         */
        val mid = ((low + high) / 2.0).ceil.toLong
        checkOnLevel(mid).flatMap {
          case SameId => searchForkLevel(mid, high)
          case ForkedId => searchForkLevel(low, mid)
        }
      case _ =>
        //no good!
        throw new IllegalArgumentException(s"Searching fork level using an invalid range [$low, $high]")
    }

}
