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


    val dataL = GenLens[Block](_.data)
    val headerL = GenLens[BlockData](_.header)
    val metadataL = GenLens[BlockData](_.metadata)
    val headerTimestampL = GenLens[BlockHeader](_.timestamp)
    val metadataPrism = GenPrism[BlockMetadata, BlockHeaderMetadata]
    val headerBalancesL = GenLens[BlockHeaderMetadata](_.balance_updates)
    val blockMetadataBalancesL: Optional[Block, List[BalanceUpdate]] =
      dataL composeLens metadataL composePrism metadataPrism composeLens headerBalancesL

    val setTimestamp: ZonedDateTime => Block => Block = dataL composeLens headerL composeLens headerTimestampL set _
    val setBalances: List[BalanceUpdate] => Block => Block = blockMetadataBalancesL set _
  }

}