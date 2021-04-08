package tech.cryptonomic.conseil.api

import scribe._
import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.api.config.ConseilConfiguration
import tech.cryptonomic.conseil.common.config.Platforms.{PlatformConfiguration, PlatformsConfiguration}
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.io.MainOutputs._

/** Defines what to print when starting Conseil */
trait ConseilMainOutput extends ConseilLogSupport {

  /** Shows the main application info
    * @param serverConf configuration of the http server
    */
  protected[this] def displayInfo(serverConf: ConseilConfiguration): Unit = {
    val showCommit = BuildInfo.gitHeadCommit.fold("")(hash => s"[commit-hash: ${hash.take(7)}]")
    logger.info(
      s"""
        | ==================================***==================================
        |  Conseil v.${BuildInfo.version}
        |  $showCommit
        | ==================================***==================================
        |
        | Server started on ${serverConf.hostname} at port ${serverConf.port}
        | Bonjour...
        |
        |""".stripMargin
    )
  }

  /** Shows details on the current configuration */
  protected[this] def displayConfiguration(platformConfigs: PlatformsConfiguration): Unit =
    logger.info(
      s"""
        | ==================================***==================================
        | Configuration details
        |
        | ${showAvailablePlatforms(platformConfigs)}
        | ${showDatabaseConfiguration("conseil")}
        |
        | ==================================***==================================
        |
        """.stripMargin
    )

  /* prepare output to display existing platforms and networks */
  private def showAvailablePlatforms(conf: PlatformsConfiguration): String =
    conf.platforms
      .groupBy(_.platform)
      .map {
        case (platform, configuration) =>
          val networks = showAvailableNetworks(configuration)
          s"  Platform: ${platform.name}$networks"
      }
      .mkString("\n")

  /* prepare output to display existing networks */
  private def showAvailableNetworks(configuration: List[PlatformConfiguration]): String =
    configuration.map { c =>
      val disabled = if (!c.enabled) " (disabled)" else ""
      s"${c.network}$disabled"
    }.mkString("\n  - ", "\n  - ", "\n")

}
