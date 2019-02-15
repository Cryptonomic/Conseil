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

/** Provides instances of `HasBalanceUpdates` for the tezos operations' hierarchy
  * Simply import this object to have the additinal methods to extract balances from
  * any Operation ADT type
  */
object OperationBalances {

  import SymbolSourceLabels._

  //single polymorphic instance
  implicit def opsBalanceUpdatesGetter[OP <: Operation] = Getter[OP, Map[Label, List[BalanceUpdate]]] {
    case e: Endorsement =>
      Map(OPERATION_SOURCE -> e.metadata.balance_updates)
    case nr: SeedNonceRevelation =>
      Map(OPERATION_SOURCE -> nr.metadata.balance_updates)
    case aa: ActivateAccount =>
      Map(OPERATION_SOURCE -> aa.metadata.balance_updates)
    case r: Reveal =>
      Map(OPERATION_SOURCE -> r.metadata.balance_updates)
    case t: Transaction =>
      Map(
        OPERATION_SOURCE -> t.metadata.balance_updates,
        OPERATION_RESULT_SOURCE -> t.metadata.operation_result.balance_updates.getOrElse(List.empty)
      )
    case o: Origination =>
      Map(
        OPERATION_SOURCE -> o.metadata.balance_updates,
        OPERATION_RESULT_SOURCE -> o.metadata.operation_result.balance_updates.getOrElse(List.empty)
      )
    case _ =>
      Map.empty
  }

  implicit def opsBalanceHashGetter[OP <: Operation] = Getter[OP, Option[String]](Function.const(Option.empty))

}

/** Provides instances of `HasBalanceUpdates` for the tezos block data
  * Simply import this object to have the additinal methods to extract balances from data.
  */
object BlockBalances {

  import SymbolSourceLabels._

  //the updates might actually be missing from json
  implicit val blockBalanceUpdatesGetter = Getter[BlockData, Map[Label, List[BalanceUpdate]]](
    data => Map(BLOCK_SOURCE -> Option(data.metadata.balance_updates).getOrElse(List.empty))
  )

  implicit val blockBalanceHashGetter = Getter[BlockData, Option[String]](_.hash.value.some)

}