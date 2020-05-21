package tech.cryptonomic.conseil.indexer.logging

import com.typesafe.scalalogging.Logger
import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.common.config.Platforms.{BlockchainPlatform, PlatformConfiguration}
import tech.cryptonomic.conseil.common.io.MainOutputs.{showDatabaseConfiguration, showPlatformConfiguration}
import tech.cryptonomic.conseil.indexer.config.LorreConfiguration

trait LorreInfoLogging {

  protected def logger: Logger

  /** Shows the main application info
    * @param platform the platform indexer is running for
    * @param network the network indexer is running for
    */
  def displayInfo(platform: String, network: String): Unit =
    logger.info(
      """
        | ==================================***==================================
        |  Lorre v.{}
        |  {}
        | ==================================***==================================
        |
        |  About to start processing data on the {} network for {}
        |
        |""".stripMargin,
      BuildInfo.version,
      BuildInfo.gitHeadCommit.fold("")(hash => s"[commit-hash: ${hash.take(7)}]"),
      network,
      platform
    )

  /** Shows details on the current configuration
    * @param platform the platform used by Lorre
    * @param platformConf the details on platform-specific configuratoin (e.g. node connection)
    * @param ignoreFailures env-var name and value read to describe behaviour on common application failure
    * @tparam C the custom platform configuration type (depending on the currently hit blockchain)
    */
  def displayConfiguration[C <: PlatformConfiguration](
      platform: BlockchainPlatform,
      platformConf: C,
      lorreConf: LorreConfiguration,
      ignoreFailures: (String, Option[String])
  ): Unit =
    logger.info(
      """
        | ==================================***==================================
        | Configuration details
        |
        | Connecting to {} {}
        | on {}
        |
        | Reference hash for synchronization with the chain: {}
        | Requested depth of synchronization: {}
        | Environment set to skip failed download of chain data: {} [\u2020]
        |
        | {}
        |
        | [\u2020] To let the process crash on error,
        |     set an environment variable named {} to "off" or "no"
        | ==================================***==================================
        |
      """.stripMargin,
      platform.name,
      platformConf.network,
      showPlatformConfiguration(platformConf),
      lorreConf.headHash.fold("head")(_.value),
      lorreConf.depth,
      ignoreFailures._2.getOrElse("yes"),
      showDatabaseConfiguration("lorre"),
      ignoreFailures._1
    )
}
