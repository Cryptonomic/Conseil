package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.TezosTypes.OperationMetadata.BalanceUpdate

/** Type class collecting balance updates from any available source.
  * @tparam S the source type that owns the updates
  */
trait HasBalanceUpdates[S] {

  /** Collects balance updates from the source object */
  def getAllBalanceUpdates(source: S): Map[Symbol, List[BalanceUpdate]]
}

/** exposes implicit instances of the typeclass for all relevant types,
  * i.e. all tezos operations
  */
object HasBalanceUpdates {
  type SourceDescription = Symbol

  //single parametric instance
  implicit def balanceUpdatesInstance[OP <: Operation] = new HasBalanceUpdates[OP] {
    override def getAllBalanceUpdates(op: OP) = op match {
      case e: Endorsement =>
        Map('operation -> e.metadata.balance_updates)
      case nr: SeedNonceRevelation =>
        Map('operation -> nr.metadata.balance_updates)
      case aa: ActivateAccount =>
        Map('operation -> aa.metadata.balance_updates)
      case r: Reveal =>
        Map('operation -> r.metadata.balance_updates)
      case t: Transaction =>
        Map(
          'operation -> t.metadata.balance_updates,
          'operation_result -> t.metadata.operation_result.balance_updates.getOrElse(List.empty)
        )
      case o: Origination =>
        Map(
          'operation -> o.metadata.balance_updates,
          'operation_result -> o.metadata.operation_result.balance_updates.getOrElse(List.empty)
        )
      case _ =>
        Map.empty
    }
  }

  /** provides extension methods to get the balance updates when an implicit instance is in scope */
  object Syntax {

    /** extend implicitly with the extra method */
    implicit class WithBalanceUpdates[S](source: S) {
      /** extension method */
      def getAllBalanceUpdates(implicit ev: HasBalanceUpdates[S]): Map[Symbol, List[BalanceUpdate]] =
        ev.getAllBalanceUpdates(source)
    }

  }

}