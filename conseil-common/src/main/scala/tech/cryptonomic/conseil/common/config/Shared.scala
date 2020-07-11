package tech.cryptonomic.conseil.common.config

import tech.cryptonomic.conseil.common.tezos.TezosTypes.BlockLevel

sealed trait ChainEvent extends Product with Serializable

object ChainEvent {

  type AccountIdPattern = String

  //used to store strings as typed enumerated values with no runtime overhead, and custom rendering
  case class ChainEventType private (render: String) extends AnyVal with Product with Serializable

  //these will be used as keys in the configuration and db, keep them consistent
  val accountsRefresh: ChainEventType = ChainEventType("accountsRefresh")

  //these will be used as values
  final case class AccountsRefresh(levels: Map[String, List[BlockLevel]]) extends ChainEvent

}

final case class VerboseOutput(on: Boolean) extends AnyVal
