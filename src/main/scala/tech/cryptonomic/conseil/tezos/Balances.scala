package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.TezosTypes.OperationMetadata.BalanceUpdate
import cats.syntax.option._
import cats.Show
import monocle.Getter

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
    Getter[BlockTagged[OP], Map[Label, List[BlockTagged[BalanceUpdate]]]] {
      case b: BlockTagged[OP] =>
        b.content match {
          case e: Endorsement =>
            Map(OPERATION_SOURCE -> e.metadata.balance_updates.map(b.updateContent))
          case nr: SeedNonceRevelation =>
            Map(OPERATION_SOURCE -> nr.metadata.balance_updates.map(b.updateContent))
          case aa: ActivateAccount =>
            Map(OPERATION_SOURCE -> aa.metadata.balance_updates.map(b.updateContent))
          case r: Reveal =>
            Map(OPERATION_SOURCE -> r.metadata.balance_updates.map(b.updateContent))
          case t: Transaction =>
            Map(
              OPERATION_SOURCE -> t.metadata.balance_updates.map(b.updateContent),
              OPERATION_RESULT_SOURCE -> t.metadata.operation_result.balance_updates
                    .getOrElse(List.empty)
                    .map(b.updateContent)
            )
          case o: Origination =>
            Map(
              OPERATION_SOURCE -> o.metadata.balance_updates.map(b.updateContent),
              OPERATION_RESULT_SOURCE -> o.metadata.operation_result.balance_updates
                    .getOrElse(List.empty)
                    .map(b.updateContent)
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
  implicit val blockBalanceUpdatesGetter = Getter[BlockData, Map[Label, List[BlockTagged[BalanceUpdate]]]](
    data =>
      Map(
        BLOCK_SOURCE -> (data.metadata match {
              case GenesisMetadata => List.empty
              case BlockHeaderMetadata(balance_updates, _, _, _, _, _) =>
                balance_updates.map(BlockTagged.fromBlockData(data, _))
            })
      )
  )

  implicit val blockBalanceHashGetter = Getter[BlockData, Option[String]](_.hash.value.some)

}
