package tech.cryptonomic.conseil

import com.typesafe.scalalogging.Logger
import tech.cryptonomic.conseil.config.Platforms._

trait LorreOutput {
  self =>

  protected[this] def logger: Logger

  protected[this] def printMainInfo(platformConf: PlatformConfiguration) =
    logger.info("""
      | =========================***=========================
      |  Lorre v.{}
      |  {}
      | =========================***=========================
      |
      |  About to start processing data on the {} network
      |""".stripMargin,
      BuildInfo.version,
      BuildInfo.gitHeadCommit.fold("")(hash => s"[commit-hash: ${hash.take(7)}]"),
      platformConf.network
    )

  protected[this] def printConfiguration[C <: PlatformConfiguration](
    platform: BlockchainPlatform,
    platformConf: C,
    ignoreFailures: Option[String]) =
    logger.info("""
      | ==================================***==================================
      | Configuration details
      |
      | Connecting to {} {}
      | {}
      |
      | Requested depth of synchronization with the chain: {}
      | Environment set to skip failed download of blocks or accounts: {}
      | ==================================***==================================
    """.stripMargin,
      platform.name,
      platformConf.network,
      printPlatformConfiguration(platformConf),
      platformConf.depth,
      ignoreFailures.getOrElse("yes")
    )

  private[this] val printPlatformConfiguration: PartialFunction[PlatformConfiguration, String] = {
    case TezosConfiguration(_, _, TezosNodeConfiguration(host, port, protocol, prefix)) =>
      s"on node $protocol://$host:$port/$prefix"
    case _ =>
      "on a non-descript platform configuration"
  }

}