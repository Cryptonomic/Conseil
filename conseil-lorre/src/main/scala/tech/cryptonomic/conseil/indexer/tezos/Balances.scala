package tech.cryptonomic.conseil.indexer.tezos

import cats.Show
import cats.syntax.option._
import monocle.Getter
import tech.cryptonomic.conseil.common.tezos.TezosTypes.OperationMetadata.BalanceUpdate
import tech.cryptonomic.conseil.common.tezos.TezosTypes._

/** defines distinct tagging values of sources for balances, as scala `Symbol` values */
object SymbolSourceLabels {
  type Label = Symbol
  final val OPERATION_SOURCE = 'operation
  final val OPERATION_RESULT_SOURCE = 'operation_result
  final val BLOCK_SOURCE = 'block

  /** Import this to have a `cats.Show` instance to show symbols as "clean" strings
    * whereas the cats standard instance will keep the single quote
    */
  object Show {
    implicit object showSymbol extends Show[Symbol] {
      def show(s: Symbol) = s.name
    }
  }
}

/** Provides instances of `monocle.Getter` for the tezos operations' hierarchy
  * Simply import this object to have the additinal methods to extract balances from
  * any Operation ADT type
  */
object OperationBalances {

  import SymbolSourceLabels._

  //single polymorphic instance
  implicit def opsBalanceUpdatesGetter[OP <: Operation] =
    Getter[BlockTagged[OP], Map[BlockTagged[Label], List[BalanceUpdate]]] {
      case b: BlockTagged[OP] =>
        b.content match {
          case e: Endorsement =>
            Map(b.updateContent(OPERATION_SOURCE) -> e.metadata.balance_updates)
          case nr: SeedNonceRevelation =>
            Map(b.updateContent(OPERATION_SOURCE) -> nr.metadata.balance_updates)
          case aa: ActivateAccount =>
            Map(b.updateContent(OPERATION_SOURCE) -> aa.metadata.balance_updates)
          case r: Reveal =>
            Map(b.updateContent(OPERATION_SOURCE) -> r.metadata.balance_updates)
          case t: Transaction =>
            Map(
              b.updateContent(OPERATION_SOURCE) -> t.metadata.balance_updates,
              b.updateContent(OPERATION_RESULT_SOURCE) -> t.metadata.operation_result.balance_updates
                    .getOrElse(List.empty)
            )
          case o: Origination =>
            Map(
              b.updateContent(OPERATION_SOURCE) -> o.metadata.balance_updates,
              b.updateContent(OPERATION_RESULT_SOURCE) -> o.metadata.operation_result.balance_updates
                    .getOrElse(List.empty)
            )
          case _ =>
            Map.empty
        }

    }

  implicit def opsBalanceHashGetter[OP <: Operation] =
    Getter[BlockTagged[OP], Option[String]](Function.const(Option.empty))

}

/** Provides instances of `monocle.Getter` for the tezos block data
  * Simply import this object to have the additinal methods to extract balances from data.
  */
object BlockBalances {

  import SymbolSourceLabels._

  //the updates might actually be missing from json
  implicit val blockBalanceUpdatesGetter = Getter[BlockData, Map[BlockTagged[Label], List[BalanceUpdate]]](
    data =>
      Map(
        BlockTagged.fromBlockData(data, BLOCK_SOURCE) -> (data.metadata match {
              case GenesisMetadata => List.empty
              case BlockHeaderMetadata(balance_updates, _, _, _, _, _) => balance_updates
            })
      )
  )

  implicit val blockBalanceHashGetter = Getter[BlockData, Option[String]](_.hash.value.some)

}
