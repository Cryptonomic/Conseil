package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.tezos.TezosTypes.BlockHash
import scala.concurrent.duration.FiniteDuration

final case class ServerConfiguration(
    hostname: String,
    port: Int,
    cacheTTL: FiniteDuration,
    maxQueryResultSize: Int,
    highCardinalityLimit: Int,
    startupDeadline: FiniteDuration
)

sealed trait ChainEvent extends Product with Serializable

object ChainEvent {

  //used to store strings as typed enumerated values with no runtime overhead, and custom rendering
  case class ChainEventType private (render: String) extends AnyVal with Product with Serializable

  //these will be used as keys in the configuration and db, keep them consistent
  val accountsRefresh = ChainEventType("accountsRefresh")

  //these will be used as values
  final case class AccountsRefresh(levels: List[Int]) extends ChainEvent

}

final case class LorreConfiguration(
    sleepInterval: FiniteDuration,
    bootupRetryInterval: FiniteDuration,
    bootupConnectionCheckTimeout: FiniteDuration,
    feeUpdateInterval: Int,
    numberOfFeesAveraged: Int,
    depth: Depth,
    headHash: Option[BlockHash],
    chainEvents: List[ChainEvent]
    rights: Rights
)

final case class BatchFetchConfiguration(
    accountConcurrencyLevel: Int,
    blockOperationsConcurrencyLevel: Int,
    blockPageSize: Int,
    blockPageProcessingTimeout: FiniteDuration,
    accountPageProcessingTimeout: FiniteDuration,
    delegatePageProcessingTimeout: FiniteDuration
)

/** configurations related to a chain-node network calls */
final case class NetworkCallsConfiguration(
    requestAwaitTime: FiniteDuration,
    GETResponseEntityTimeout: FiniteDuration,
    POSTResponseEntityTimeout: FiniteDuration
)

/** holds custom-verified lightbend configuration for the akka-http-client hostpool used to stream requests */
final case class HttpStreamingConfiguration(pool: com.typesafe.config.Config)

/** sodium library references */
final case class SodiumConfiguration(libraryPath: String) extends AnyVal with Product with Serializable

/** holds configuration for the akka-http-caching used in metadata endpoint */
final case class HttpCacheConfiguration(cacheConfig: com.typesafe.config.Config)

/** configuration for fetching baking and endorsing rights */
final case class Rights(
    initDelay: FiniteDuration,
    interval: FiniteDuration,
    cyclesFetch: Int,
    cycleSize: Int,
    fetchSize: Int
)

/** used to pattern match on natural numbers */
object Natural {
  def unapply(s: String): Option[Int] = util.Try(s.toInt).filter(_ > 0).toOption
}

final case class VerboseOutput(on: Boolean) extends AnyVal
final case class FailFast(on: Boolean) extends AnyVal

/** configuration for fetching keys from nautilus cloud instance */
final case class NautilusCloudConfiguration(
    host: String,
    port: Int,
    path: String,
    key: String,
    delay: FiniteDuration,
    interval: FiniteDuration
)
