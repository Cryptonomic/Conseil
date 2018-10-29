package tech.cryptonomic.conseil.config

import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.duration.FiniteDuration

object ConseilConfig extends LazyLogging {

  final case class ServerConfiguration(hostname: String, port: Int)

  final case class LorreConfiguration(
    sleepInterval: FiniteDuration,
    feeUpdateInterval: Int,
    purgeAccountsInterval: Int,
    numberOfFeesAveraged: Int
  )

  final case class BatchFetchConfiguration(
    accountConcurrencyLevel: Int,
    blockOperationsConcurrencyLevel: Int
  )

  /** configurations related to tezos-node network node calls */
  final case class TezosRequestsConfiguration(
    requestAwaitTime: FiniteDuration,
    GETResponseEntityTimeout: FiniteDuration,
    POSTResponseEntityTimeout: FiniteDuration
  )

  /** configurations to identify the tezos node */
  final case class TezosNodeConfiguration(
    hostname: String,
    port: Int,
    protocol: String,
    pathPrefix: String
  )

  /** collects all config related to tezos */
  final case class TezosConfiguration(network: String, nodeConfig: TezosNodeConfiguration, requestConfig: TezosRequestsConfiguration)

  /** sodium library references */
  final case class SodiumConfiguration(libraryPath: String) extends AnyVal with Product with Serializable

  /** common way to print diagnostic for a configuration error on the logger */
  def printConfigurationError(context: String, details: String): Unit =
    logger.error(
      s"""I'm not able to correctly load the configuration in $context
         |Please check the configuration file for the following errors:
         |
         |$details""".stripMargin
    )

}