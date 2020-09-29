package tech.cryptonomic.conseil.indexer.tezos.processing

import tech.cryptonomic.conseil.common.tezos.TezosTypes.TezosBlockHash
import tech.cryptonomic.conseil.common.tezos.TezosTypesInstances._
import tech.cryptonomic.conseil.indexer.forks.{ForkAmender, ForkHandler}
import tech.cryptonomic.conseil.indexer.forks.ForkDetector.SearchBlockId
import tech.cryptonomic.conseil.indexer.tezos.forks.{ConsistentForkDetector, SearchBlockData}
import cats.Monad

/** Tezos specific logic to identify and handle forks in the chain,
  * and to correct them.
  * This will allow the indexer to proceed re-syncing from
  * the point were the divergence occurred.
  *
  * @param nodeSearch retrieves block ids on the remote node by level
  * @param nodeDataSearch retrieves blocks' data on the remote node by level
  * @param indexerSearch retrieves block ids on the local indexer by level
  * @param amender corrects data after a fork information is identified
  */
final class ForksProcessor[Eff[_]: Monad](
    nodeSearch: SearchBlockId[Eff, TezosBlockHash],
    nodeDataSearch: SearchBlockData[Eff],
    indexerSearch: SearchBlockId[Eff, TezosBlockHash],
    amender: ForkAmender[Eff, TezosBlockHash]
) extends ForkHandler[Eff, TezosBlockHash](indexerSearch, amender) {

  override protected val detector = new ConsistentForkDetector[Eff](
    indexerSearch,
    nodeSearch,
    nodeDataSearch,
    maxAttempts = 5
  )

}
