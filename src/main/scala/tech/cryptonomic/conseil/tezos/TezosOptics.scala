package tech.cryptonomic.conseil.tezos

import java.time.ZonedDateTime
import TezosTypes._
import TezosTypes.OperationMetadata.BalanceUpdate

/** Provides [[http://julien-truffaut.github.io/Monocle/ monocle]] lenses and additional "optics"
  * for most common access and modifcation patterns for Tezos type hierarchies and ADTs
  */
object TezosOptics {

  import monocle.Optional
  import monocle.macros.{GenLens, GenPrism}

  object Blocks {

    val blockData = GenLens[Block](_.data)
    val dataHeader = GenLens[BlockData](_.header)
    val metadata = GenLens[BlockData](_.metadata)
    val headerTimestamp = GenLens[BlockHeader](_.timestamp)
    val metadataType = GenPrism[BlockMetadata, BlockHeaderMetadata]
    val metadataBalances = GenLens[BlockHeaderMetadata](_.balance_updates)


    val blockBalances: Optional[Block, List[BalanceUpdate]] =
      blockData composeLens metadata composePrism metadataType composeLens metadataBalances

    val setTimestamp: ZonedDateTime => Block => Block = blockData composeLens dataHeader composeLens headerTimestamp set _
    val setBalances: List[BalanceUpdate] => Block => Block = blockBalances set _
  }

}