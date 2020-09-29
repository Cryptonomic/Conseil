package tech.cryptonomic.conseil.indexer.tezos.forks

import com.typesafe.scalalogging.LazyLogging
import scala.util.{Failure, Success}
import scala.concurrent.{ExecutionContext, Future, SyncVar}
import tech.cryptonomic.conseil.indexer.tezos.{TezosIndexedDataOperations, TezosNodeOperator}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BlockLevel, TezosBlockHash}
import tech.cryptonomic.conseil.indexer.forks.ForkDetector.SearchBlockId
import tech.cryptonomic.conseil.common.tezos.TezosTypes.BlockData
import scala.util.Try

/** Provides search features needed for the fork-handling process.
  * This component makes shared re-use of other Lorre components to optimize resources.
  * Based on those, it provides all instances required by the Fork Handler, e.g. SearchBlockId, SearchBlockData.
  *
  * @param nodeOps provides data from the remote node
  * @param indexedOps provides data already indexed
  */
class TezosForkSearchEngine(
    nodeOps: TezosNodeOperator,
    indexedOps: TezosIndexedDataOperations
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  /* we use this pair as a reference to compute rpc params needed to get a block via an offset */
  private type Anchor = (TezosBlockHash, BlockLevel)
  /* To avoid recomputing a different reference anchor for the remote call each time a new level is
   * requested by the detection algorithm, we put it in a locally "cached" variable that can be accessed
   * as an async blocking primitive.
   */
  private val searchAnchor: SyncVar[Anchor] = new SyncVar[Anchor]

  /* Set an initial value for the anchor */
  updatedAnchor()

  /* We can implement all search instances as a lambda thanks to jvm functional interfaces */

  /** Search block id by level on the indexer */
  def idsIndexerSearch: SearchBlockId[Future, TezosBlockHash] =
    (level: Long) => {
      logger.debug(s"Searching block from the indexer for level $level")
      indexedOps.fetchBlockAtLevel(level).map {
        //we don't care for the fallback as long as it won't match a real block hash
        case None => TezosBlockHash("")
        case Some(block) => TezosBlockHash(block.hash)
      }
    }

  /** Search block id by level on the node */
  def idsNodeSearch: SearchBlockId[Future, TezosBlockHash] =
    blocksNodeSearch.searchForLevel(_).map(_.hash)

  /* Getting data from the node is not that easy as we usually need to make
   * at least two calls to the rpc, first to get a reference and then to compute
   * the expected offset from that to fetch the requested level.
   * We collect all components involved in making the underlying requests
   * and make an effort to reuse existing knowledge and cache responses to
   * avoid doing excessive round-trips
   */

  /** Search block data by level on the node.
    * The search is not thread-safe
    */
  def blocksNodeSearch: SearchBlockData[Future] =
    (level: Long) => {
      logger.debug(s"Searching block from the node for level $level")
      //check if the cached reference is high enough to compute an offset, else fetch another one
      searchAnchor.get(1) match {
        case Some(anchor @ (hashRef, levelRef)) if level <= levelRef =>
          getFromNode(level, anchor)
        case _ =>
          updatedAnchor().flatMap(anchor => getFromNode(level, anchor))
      }
    }

  /* The code will make a call to get the block at the requested level by
   * doing 2 necessary requests:
   * - find a known reference block hash and level on the node, i.e. the head
   * - use the reference to ask for the required block data, computing the offset from
   *   the above reference block
   *
   * The function is recursive in nature with the goal to limit the remote calls to the strictly necessary,
   * therefore we pass-in the reference data to re-use a locally cached value initially.
   * Only if that value returns inconsistent results than we recompute the cached anchor with a real rpc
   *  call and try again.
   *
   * We keep doing the consistency check even after a remote request, because the node data might have been
   * invalidated between the fetch for a reference block (i.e. the current chain head) and the offset-based
   * search by level.
   */
  private def getFromNode(level: BlockLevel, anchor: Anchor): Future[BlockData] = {
    //if levels don't match, tries with fresh data from the remote node
    def confirm(candidate: BlockData): Future[BlockData] =
      if (candidate.header.level == level) Future.successful(candidate)
      else updatedAnchor().flatMap(anchor => getFromNode(level, anchor))

    anchor match {
      case (hashRef, levelRef) =>
        nodeOps
          .getBareBlock(hash = hashRef, offset = Some(levelRef - level))
          .flatMap(confirm)
    }
  }

  /* Reads a new anchor value from the node, setting it in the cache as a side-effect.
   * This method is not thread-safe.
   */
  private def updatedAnchor() =
    nodeOps
      .getBareBlockHead()
      .map(data => data.hash -> data.header.level)
      .andThen {
        case Failure(exception) =>
          logger.error("I could not read a valid reference block from the chain node head", exception)
        case Success(anchor) =>
          /* This implements a sequential "set" semantic
           * We can swallow the error, we just need to make sure that there's no value before putting a new one
           */
          Try(searchAnchor.take(1))
          searchAnchor.put(anchor)
          anchor
      }

}
