package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.TezosTypes.OperationMetadata.BalanceUpdate

/** type class collecting balance updates from any available operation sub-type */
trait HasBalanceUpdates[OP <: Operation] {

  /** Collects balance updates from the target object */
  def getAllBalanceUpdates(op: OP): List[BalanceUpdate]
}

/** exposes implicit instances of the typeclass for all relevant types,
  * i.e. all tezos operations
  */
object HasBalanceUpdates {

  //single parametric instance
  implicit def balanceUpdatesInstance[OP <: Operation] = new HasBalanceUpdates[OP] {
    override def getAllBalanceUpdates(op: OP) = op match {
      case e: Endorsement =>
        e.metadata.balance_updates
      case nr: SeedNonceRevelation =>
        nr.metadata.balance_updates
      case aa: ActivateAccount =>
        aa.metadata.balance_updates
      case r: Reveal =>
        r.metadata.balance_updates
      case t: Transaction =>
        t.metadata.balance_updates ++
          t.metadata.operation_result.balance_updates.getOrElse(List.empty)
      case o: Origination =>
        o.metadata.balance_updates ++
          o.metadata.operation_result.balance_updates.getOrElse(List.empty)
      case _ =>
        List.empty
    }
  }

  /** provides extension methods to get the balance updates when an implicit instance is in scope */
  object Syntax {

    /** extend implicitly with the extra method */
    implicit class WithBalanceUpdates[OP <: Operation](op: OP) {
      /** extension method */
      def getAllBalanceUpdates(implicit ev: HasBalanceUpdates[OP]): List[BalanceUpdate] =
        ev.getAllBalanceUpdates(op)
    }

  }

}