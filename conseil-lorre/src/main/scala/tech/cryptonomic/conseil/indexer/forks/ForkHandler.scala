package tech.cryptonomic.conseil.indexer.forks

import cats._
import cats.implicits._
import java.time.Instant
import ForkDetector.{ForkedId, SameId, SearchBlockId}

/** Provides the whole process of fork handling
  *  i.e detection + amendment, in the most straight-forward way.
  * It's based on individual components interacting together.
  *
  * @param indexerSearch retrieves blocks on the local indexer by level
  * @param amender corrects data after a fork information is identified
  */
abstract class ForkHandler[Eff[_]: Monad, BlockId: Eq](
    indexerSearch: SearchBlockId[Eff, BlockId],
    amender: ForkAmender[Eff, BlockId]
) {

  /** the specific logic to detect forks and eventually track down the exact level */
  protected def detector: ForkDetector[Eff, BlockId]

  /** Given the current indexer head level, probes for a possible fork
    * and eventually run compensating actions.
    *
    * @param currentHeadLevel the tip of the locally indexed chain
    * @return None if no fork happened, or the result of data amendment
    */
  def handleFork(currentHeadLevel: Long): Eff[Option[ForkAmender.Results]] =
    detector.checkOnLevel(currentHeadLevel).flatMap {
      case SameId =>
        Option.empty.pure[Eff]
      case ForkedId =>
        for {
          forkLevel <- detector.searchForkLevel(0, currentHeadLevel)
          forkBlockId <- indexerSearch.searchForLevel(forkLevel)
          amendment <- amender.amendFork(forkLevel, forkBlockId, currentHeadLevel, Instant.now())
        } yield amendment.some
    }

  def handleForkFrom(currentHeadLevel: Long, depth: Long) = {
    detector.checkDepthLevel(currentHeadLevel, depth).flatMap {
      case Nil => Option.empty.pure[Eff]
      case xs =>
        val forkLevel = xs.min
        for {
          forkBlockId <- indexerSearch.searchForLevel(forkLevel)
          amendment <- amender.amendFork(forkLevel, forkBlockId, currentHeadLevel, Instant.now())
        } yield amendment.some
    }
  }
}
