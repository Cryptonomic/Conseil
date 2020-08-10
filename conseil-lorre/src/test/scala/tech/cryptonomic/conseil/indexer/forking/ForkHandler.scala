package tech.cryptonomic.conseil.indexer.forking

import cats._
import cats.implicits._
import java.time.Instant
import tech.cryptonomic.conseil.indexer.ForkDetector.{ForkedId, SameId, SearchBlockId}
import tech.cryptonomic.conseil.indexer.{ForkAmender, ForkDetector}

/** Provides the whole process of fork handling
  *  i.e detection + amendment, in the most straight-forward way.
  * It's based on individual components interacting together.
  *
  * @param indexerSearch retrieves blocks on the local indexer by level
  * @param nodeSearch retrieves blocks on the remote node by level
  * @param amender corrects data after a fork information is identified
  */
class ForkHandler[Eff[_]: Monad, BlockId: Eq](
    indexerSearch: SearchBlockId[Eff, BlockId],
    nodeSearch: SearchBlockId[Eff, BlockId],
    amender: ForkAmender[Eff, BlockId]
) {

  private val detector = new ForkDetector(indexerSearch, nodeSearch)

  /** Given the current indexer head level, probes for a possible fork
    * and evetually run compensating actions.
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
}
