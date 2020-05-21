package tech.cryptonomic.conseil.api.config

import scala.concurrent.duration.FiniteDuration

final case class ConseilConfiguration(
    hostname: String,
    port: Int,
    cacheTTL: FiniteDuration,
    maxQueryResultSize: Int,
    highCardinalityLimit: Int,
    startupDeadline: FiniteDuration
)

/** configuration for fetching keys from nautilus cloud instance */
final case class NautilusCloudConfiguration(
    host: String,
    port: Int,
    path: String,
    key: String,
    delay: FiniteDuration,
    interval: FiniteDuration
)

final case class FailFast(on: Boolean) extends AnyVal
