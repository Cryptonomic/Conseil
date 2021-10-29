package tech.cryptonomic.conseil.indexer.tezos.forks

import java.time.Instant

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BlockData, TezosBlockHash}
import tech.cryptonomic.conseil.indexer.tezos.{TezosBlocksDataFetchers, TezosIndexedDataOperations, TezosRPCInterface}
import cats._
import cats.implicits._
import tech.cryptonomic.conseil.indexer.forks.ForkAmender
import tech.cryptonomic.conseil.indexer.forks.ForkDetector.SearchBlockId

import scala.concurrent.{ExecutionContext, Future}

class BacktracingForkProcessor(
    val network: String,
    val node: TezosRPCInterface,
    tezosIndexedDataOperations: TezosIndexedDataOperations,
    indexerSearch: SearchBlockId[Future, TezosBlockHash],
    amender: ForkAmender[Future, TezosBlockHash]
)(ec: ExecutionContext)
    extends TezosBlocksDataFetchers
    with ConseilLogSupport {

  /** parallelism in the multiple requests decoding on the RPC interface */
  override def fetchConcurrency: Int = 50

  implicit override def fetchFutureContext: ExecutionContext = ec

  /**
    * Checks if there is a fork from given level down to (level - depth)
    * @param level - from which level we start check for fork
    * @param depth - how deep we check for forks
    * @return a list of failed fork checks of levels
    */
  def checkDepthLevel(level: Long, depth: Long): Future[List[Offset]] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.{fetch, fetchMerge}
    import tech.cryptonomic.conseil.common.tezos.TezosOptics.Blocks._
    implicit val fetcher = blocksRangeFetcher(level)
    val res = fetch[Long, BlockData, Future, List, Throwable].run((0L to depth).toList)
    res.flatMap { lst =>
      lst.map {
        case (l, chainBlock) =>
          tezosIndexedDataOperations.fetchBlockAtLevel(chainBlock.header.level).map { indexedBlock =>
            if (indexedBlock.forall(_.hash == chainBlock.hash.value)) {
              -1
            } else {
              logger.debug(s"Hashes don't match: ${chainBlock.header.level} ${indexedBlock.map(_.hash)} && ${chainBlock.hash.value}")
              l
            }
          }
      }.sequence
    }.map(_.filter(_ > 0))
  }

  def handleForkFrom(currentHeadLevel: Long, depth: Long): Future[Option[ForkAmender.Results]] = {
    checkDepthLevel(currentHeadLevel, depth).flatMap {
      case Nil => Option.empty.pure[Future]
      case xs =>
        logger.info(s"Found forked blocks ${xs.sorted.mkString(",")}")
        val forkLevel = xs.min
        for {
          forkBlockId <- indexerSearch.searchForLevel(forkLevel)
          amendment <- amender.amendFork(forkLevel, forkBlockId, currentHeadLevel, Instant.now())
        } yield amendment.some
    }
  }

}
