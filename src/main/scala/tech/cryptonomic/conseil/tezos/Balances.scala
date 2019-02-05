package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.TezosTypes.OperationMetadata.BalanceUpdate
import cats.Show

/** Type class collecting balance updates from any available source.
  * @tparam S the source type that owns the updates
  */
trait HasBalanceUpdates[S] {
  /** A type used to describe the exact source object that each update comes from*/
  type SourceId

  /** Collects balance updates from the source object
    * @param source is the object used to read the balances from
    * @param show is a constraint that guarantees that we can always represent the `SourceId` as a String
    * @return a Map where the key is used to identify where each updates list comes from, based on the specific `S`
    */
  def getAllBalanceUpdates(source: S)(implicit show: Show[SourceId]): Map[SourceId, List[BalanceUpdate]]

}

/** exposes implicit instances of the typeclass for all relevant types,
  * i.e. all tezos operations
  */
object HasBalanceUpdates {

  /** provides extension methods to get the balance updates when an implicit instance is in scope */
  object Syntax {

    /** This type makes explicit the actual type member of the result Key, which is not in `HasBalanceUpdates[S]`.
      * It's necessary to use this alias if we ever need to add type constraints on the Key
    */
    private type Aux[S, K] = HasBalanceUpdates[S] { type SourceId = K}

    /** extend implicitly with the extra method */
    implicit class WithBalanceUpdates[S](source: S) {
      /** extension method */
      def getAllBalanceUpdates[Key: Show](implicit ev: Aux[S, Key]): Map[Key, List[BalanceUpdate]] =
        ev.getAllBalanceUpdates(source)
    }

  }
}

/** Provides instances of `HasBalanceUpdates` for the tezos operations' hierarchy
  * Simply import this object to have the additinal methods to extract balances from
  * any Operation ADT type
  */
object OperationBalances {

  /** we want our own version of showing the source id for operations */
  implicit object showSymbol extends cats.Show[Symbol] {
    def show(s: Symbol) = s.name
  }

  //single parametric instance
  implicit def balanceUpdatesInstance[OP <: Operation] = new HasBalanceUpdates[OP] {

    type SourceId = Symbol

    override def getAllBalanceUpdates(op: OP)(implicit show: Show[Symbol]) = op match {
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
}