package tech.cryptonomic.conseil.tezos

import scala.annotation.implicitNotFound
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.TezosTypes.OperationMetadata.BalanceUpdate
import cats.Show

/** Type class collecting balance updates from any available source.
  * @tparam S the source type that owns the updates
  */
@implicitNotFound("No instance provided to extract balance updates from ${S}")
trait HasBalanceUpdates[S] {
  /** A type used to describe the exact source object that each update comes from*/
  type SourceDescriptor

  /** Collects balance updates from the source object
    * @param source is the object used to read the balances from
    * @param show is a constraint that guarantees that we can always represent the `SourceDescriptor` as a String
    * @return a Map where the key is used to identify where each updates list comes from, based on the specific `S`
    */
  def getAllBalanceUpdates(source: S)(implicit show: Show[SourceDescriptor]): Map[SourceDescriptor, List[BalanceUpdate]]

  /** provides a hash identifying the source, when that makes sense */
  def getHash(source: S): Option[String] = None

}

/** exposes implicit instances of the typeclass for all relevant types,
  * i.e. all tezos operations
  */
object HasBalanceUpdates {

  /** This type makes explicit the actual type member of the result keys (i.e. the specific `SourceDescriptor`),
   *  which is not available for type inference in `HasBalanceUpdates[S]`.
    * It's necessary to use this alias if we ever need to add type constraints on the SourceDescriptor type
    */
  type Aux[S, K] = HasBalanceUpdates[S] { type SourceDescriptor = K}

  /** provides extension methods to get the balance updates when an implicit instance is in scope */
  object Syntax {

    /** extend implicitly with the extra method */
    implicit class WithBalanceUpdates[S](source: S) {
      /** extension methods */
      def getAllBalanceUpdates[Key: Show](implicit ev: Aux[S, Key]): Map[Key, List[BalanceUpdate]] =
        ev.getAllBalanceUpdates(source)

      def getHash(implicit ev: HasBalanceUpdates[S]): Option[String] = ev.getHash(source)
    }

  }
}

/** defines distinct values of sources for balances, as scala `Symbol` values */
object SymbolSourceDescriptor {
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

  //single parametric instance
  implicit def balanceUpdatesInstance[OP <: Operation] = new HasBalanceUpdates[OP] {

    type SourceDescriptor = Symbol
    import SymbolSourceDescriptor._

    override def getAllBalanceUpdates(op: OP)(implicit show: Show[Symbol]) = op match {
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
  }

}

/** Provides instances of `HasBalanceUpdates` for the tezos block metadata
  * Simply import this object to have the additinal methods to extract balances from metadata.
  */
object BlockBalances {

  implicit val balanceUpdatesInstance = new HasBalanceUpdates[BlockMetadata] {
    type SourceDescriptor = Symbol
    import SymbolSourceDescriptor._

    override def getAllBalanceUpdates(metadata: BlockMetadata)(implicit show: Show[Symbol]) =
      Map(BLOCK_SOURCE -> metadata.balance_updates)

    override def getHash(metadata: BlockMetadata) = Option(metadata.hash.value)

  }


}