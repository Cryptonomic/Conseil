package tech.cryptonomic.conseil.common.config

sealed trait ChainEvent extends Product with Serializable

//TODO Could be moved to Lorre, but 'AccountIdPattern' is hold by 'TezosDatabaseOperations'
object ChainEvent {

  type AccountIdPattern = String

  //used to store strings as typed enumerated values with no runtime overhead, and custom rendering
  case class ChainEventType private (render: String) extends AnyVal with Product with Serializable

  //these will be used as keys in the configuration and db, keep them consistent
  val accountsRefresh = ChainEventType("accountsRefresh")

  //these will be used as values
  final case class AccountsRefresh(levels: Map[String, List[Int]]) extends ChainEvent

}

final case class VerboseOutput(on: Boolean) extends AnyVal
