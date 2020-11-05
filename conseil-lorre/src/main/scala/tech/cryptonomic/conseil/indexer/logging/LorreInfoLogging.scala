package tech.cryptonomic.conseil.indexer.logging

import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.config.Platforms.{BlockchainPlatform, PlatformConfiguration}
import tech.cryptonomic.conseil.common.io.MainOutputs.{showDatabaseConfiguration, showPlatformConfiguration}
import tech.cryptonomic.conseil.indexer.config.LorreConfiguration

trait LorreInfoLogging extends ConseilLogSupport {

  lazy val buildCommit = BuildInfo.gitHeadCommit.fold("")(hash => s"[commit-hash: ${hash take 7}]")

  /** Shows the main application info
    * @param platform the platform indexer is running for
    * @param network the network indexer is running for
    */
  def displayInfo(platform: String, network: String): Unit =
    logger.info(
      s"""
        | ==================================***==================================
        |  Lorre v.${BuildInfo.version}
        |  $buildCommit
        | ==================================***==================================
        |
        |  About to start processing data on the $network network for $platform
        |
        |""".stripMargin
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
      s"""
        | ==================================***==================================
        | Configuration details
        |
        | Connecting to ${platform.name} ${platformConf.network}
        | on ${showPlatformConfiguration(platformConf)}
        |
        | Reference hash for synchronization with the chain: ${lorreConf.headHash.getOrElse("head")}
        | Requested depth of synchronization: ${lorreConf.depth}
        | Environment set to skip failed download of chain data: ${ignoreFailures._2.getOrElse("yes")} [\u2020]
        |
        | ${showDatabaseConfiguration("lorre")}
        |
        | [\u2020] To let the process crash on error,
        |     set an environment variable named ${ignoreFailures._1} to "off" or "no"
        | ==================================***==================================
        |
      """.stripMargin
    )
}
