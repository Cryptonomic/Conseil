package tech.cryptonomic.conseil.indexer.tezos.forks

import cats._
import cats.implicits._
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.indexer.forks.ForkDetector
import tech.cryptonomic.conseil.indexer.forks.ForkDetector.SearchBlockId
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BlockData, TezosBlockHash}
import tech.cryptonomic.conseil.common.tezos.TezosTypesInstances._
import ConsistentForkDetector.ConsistentDetectionRepeatingFailure

/* Common types and utils */
object ConsistentForkDetector {

  case class ConsistentDetectionRepeatingFailure(message: String, attempts: Int) extends RuntimeException(message)

}

/** Provides guarantees of a more consistent fork level, given
  * a tezos node that might return unstable results - from different
  * alternating forks - during the search.
  * The returned value is guaranteed to be "locally consistent", i.e.
  * if we consider the locally stored blocks before the detected value, and those
  * available from the node, starting from the fork value on, we would see a
  * consistent sequence of blocks, correctly linked via their
  * predecessor field.
  *
  * @param indexerSearch same as for the generic [[ForkDetector]]
  * @param nodeSearch same as for the generic [[ForkDetector]]
  * @param nodeData will provide [[BlockData]] from the node for a given level
  * @param maxAttempts how many times to try upon inconsistent fork level detection, has to be strictly positive
  */
class ConsistentForkDetector[Eff[_]: Monad](
    indexerSearch: SearchBlockId[Eff, TezosBlockHash],
    nodeSearch: SearchBlockId[Eff, TezosBlockHash],
    nodeData: SearchBlockData[Eff],
    maxAttempts: Int
) extends ForkDetector[Eff, TezosBlockHash](indexerSearch, nodeSearch)
    with ConseilLogSupport {

  /* validate early */
  assert(
    maxAttempts > 0,
    s"Please provide the ForkDetector with a positive value for number of attempts, when results are inconsistent. The max-attempts given was $maxAttempts"
  )

  /** See [[ForkDetector#searchForkLevel]] */
  override def searchForkLevel(low: Long, high: Long): Eff[Long] = {
    /* we capture the original search and add post consistency check
     * with recursive retries in case of failure
     */
    val runRegularSearch = () => super.searchForkLevel(low, high)

    def attemptSearch(attemptsLeft: Int): Eff[Long] = {
      if (attemptsLeft == 0)
        throw new ConsistentDetectionRepeatingFailure(
          "I've not been able to detect the block level at which the fork happened. Any attempt gave inconsistent results between local and remote data.",
          maxAttempts
        )
      logger.warn(
        s"Looking for the block where the local indexer diverged from the chain node due to a fork. I will make $attemptsLeft attempt(s)."
      )
      val candidate = runRegularSearch()
      val consistencyCheck = candidate.flatMap(detectionIsLocallyConsistent)
      consistencyCheck.ifM(ifTrue = candidate, ifFalse = attemptSearch(attemptsLeft - 1))
    }

    attemptSearch(maxAttempts)
  }

  /** We want to make sure that during the search execution, which probably handles
    * multiple I/O calls to compare indexer and node results, the node has kept giving
    * consistent results.
    * It might happen that network calls to both services were not fast enough, to the
    * point that the fork on which the reference node was at the time of detection changed
    * during the algorithm execution. This might be a reasonable expectation in a highly
    * unstable moment of the chain, while decentralized consensus is being reached.
    * This would lead to possibly inconsistent results on the evaluated fork level.
    * We just make sure that the node block at such level is consistent with the previous
    * block hash, as found on the indexer.
    * This would guarantee that no sub-sequence of previous blocks was actually invalid,
    * since each block will be then consistent to its predecessor locally.
    *
    * @param candidateForkLevel the detected fork level
    * @return true if the block at fork level is consistent with the local predecessors
    */
  private def detectionIsLocallyConsistent(candidateForkLevel: Long): Eff[Boolean] =
    (
      nodeData.searchForLevel(candidateForkLevel).map(_.header.predecessor),
      indexerSearch.searchForLevel(candidateForkLevel - 1)
    ).mapN(_ == _).flatTap {
      case true =>
        logger
          .info(s"The fork block level was identified at $candidateForkLevel")
          .pure[Eff]
      case false =>
        logger
          .error(
            s"I failed the verification of the potential fork by matching remote block predecessor with the locally-indexed block, for level $candidateForkLevel"
          )
          .pure[Eff]
    }

}

/** Analog to the [[SearchBlockId]] which returns the whole [[BlockData]]
  * for a given search level.
  */
trait SearchBlockData[Eff[_]] {
  def searchForLevel(level: Long): Eff[BlockData]
}
