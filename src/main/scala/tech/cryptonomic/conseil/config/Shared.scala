package tech.cryptonomic.conseil.config

import scala.concurrent.duration.FiniteDuration

final case class ServerConfiguration(hostname: String, port: Int)

final case class LorreConfiguration(
  sleepInterval: FiniteDuration,
  feeUpdateInterval: Int,
  numberOfFeesAveraged: Int
)

final case class BatchFetchConfiguration(
  accountConcurrencyLevel: Int,
  blockOperationsConcurrencyLevel: Int
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
