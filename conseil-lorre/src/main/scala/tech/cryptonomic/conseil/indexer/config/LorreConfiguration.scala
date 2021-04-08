package tech.cryptonomic.conseil.indexer.config

import tech.cryptonomic.conseil.common.config.ChainEvent

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
    feesAverageTimeWindow: FiniteDuration,
    depth: Depth,
    headHash: Option[String],
    chainEvents: List[ChainEvent],
    blockRightsFetching: BakingAndEndorsingRights,
    enabledFeatures: Features
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

/** details how to handle data pagination when fetching from the chain */
final case class BatchFetchConfiguration(
    accountConcurrencyLevel: Int,
    blockOperationsConcurrencyLevel: Int,
    blockPageSize: Int,
    blockPageProcessingTimeout: FiniteDuration,
    accountPageProcessingTimeout: FiniteDuration,
    delegatePageProcessingTimeout: FiniteDuration
)

/** custom select specific features to be enabled when chain-indexing */
final case class Features(
    blockRightsFetchingIsOn: Boolean,
    forkHandlingIsOn: Boolean
)

/** sodium library references */
final case class SodiumConfiguration(libraryPath: String) extends AnyVal with Product with Serializable

/** holds custom-verified lightbend configuration for the akka-http-client hostpool used to stream requests */
final case class HttpStreamingConfiguration(pool: com.typesafe.config.Config)
