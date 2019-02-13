package tech.cryptonomic.conseil.tezos

import java.time.ZonedDateTime
import TezosTypes._
import TezosTypes.OperationMetadata.BalanceUpdate

/** Provides [[http://julien-truffaut.github.io/Monocle/ monocle]] lenses and additional "optics"
  * for most common access and modifcation patterns for Tezos type hierarchies and ADTs
  */
object TezosOptics {

  import monocle.macros.GenLens

  val blockData = GenLens[Block](_.data)
  val blockHeader = GenLens[BlockData](_.header)
  val blockMetadata = GenLens[BlockData](_.metadata)
  val blockHeaderTimestamp = GenLens[BlockHeader](_.timestamp)
  val blockHeaderBalances = GenLens[BlockHeaderMetadata](_.balance_updates)

  val setTimestamp: ZonedDateTime => Block => Block = blockData composeLens blockHeader composeLens blockHeaderTimestamp set _
  val setBalances: List[BalanceUpdate] => Block => Block = blockData composeLens blockMetadata composeLens blockHeaderBalances set _



}