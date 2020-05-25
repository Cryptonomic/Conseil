package tech.cryptonomic.conseil.indexer.config

import tech.cryptonomic.conseil.common.config.ChainEvent
import tech.cryptonomic.conseil.common.tezos.TezosTypes.BlockHash

import scala.concurrent.duration.FiniteDuration

/** configurations related to a chain-node network calls */
final case class NetworkCallsConfiguration(
    requestAwaitTime: FiniteDuration,
    GETResponseEntityTimeout: FiniteDuration,
    POSTResponseEntityTimeout: FiniteDuration
)

/** generic configuration for the lorre */
final case class LorreConfiguration(
    sleepInterval: FiniteDuration,
    bootupRetryInterval: FiniteDuration,
    bootupConnectionCheckTimeout: FiniteDuration,
    feeUpdateInterval: Int,
    numberOfFeesAveraged: Int,
    depth: Depth,
    headHash: Option[BlockHash],
    chainEvents: List[ChainEvent],
    blockRightsFetching: BakingAndEndorsingRights
)

/** configuration for fetching baking and endorsing rights */
final case class BakingAndEndorsingRights(
    initDelay: FiniteDuration,
    interval: FiniteDuration,
    cyclesToFetch: Int,
    cycleSize: Int,
    fetchSize: Int,
    updateSize: Int
)

final case class BatchFetchConfiguration(
    accountConcurrencyLevel: Int,
    blockOperationsConcurrencyLevel: Int,
    blockPageSize: Int,
    blockPageProcessingTimeout: FiniteDuration,
    accountPageProcessingTimeout: FiniteDuration,
    delegatePageProcessingTimeout: FiniteDuration
)

/** sodium library references */
final case class SodiumConfiguration(libraryPath: String) extends AnyVal with Product with Serializable

/** holds custom-verified lightbend configuration for the akka-http-client hostpool used to stream requests */
final case class HttpStreamingConfiguration(pool: com.typesafe.config.Config)
