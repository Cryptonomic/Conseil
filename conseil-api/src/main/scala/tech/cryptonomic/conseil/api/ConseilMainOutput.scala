package tech.cryptonomic.conseil.api

import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.api.config.ConseilConfiguration
import tech.cryptonomic.conseil.common.config.Platforms.{PlatformConfiguration, PlatformsConfiguration}
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.io.MainOutputs._
import tech.cryptonomic.conseil.common.util.syntax._

import cats.effect.IO

/** Defines what to print when starting Conseil */
trait ConseilMainOutput extends ConseilLogSupport {

  /** Shows the main application info
    * @param serverConf configuration of the http server
    */
  protected[this] def displayInfo(serverConf: ConseilConfiguration) = {
    val showCommit = BuildInfo.gitHeadCommit.fold("")(hash => s"[commit-hash: ${hash.take(7)}]")
    IO(s"""
        | ==================================***==================================
        |  Conseil v.${BuildInfo.version}
        |  $showCommit
        | ==================================***==================================
        |
        | Server started on ${serverConf.hostname} at port ${serverConf.port}
        | Bonjour...
        |
        |""".stripMargin)
  }

  /** Shows details on the current configuration */
  protected[this] def displayConfiguration(platformConfigs: PlatformsConfiguration) =
    for {
      showPlatforms <- showAvailablePlatforms(platformConfigs)
        .handleErrorWith(_ => IO("can't load platforms").debug)
      showDatabase <- showDatabaseConfiguration("conseil")
        .handleErrorWith(_ => IO("can't load database config").debug)
    } yield s"""
        | ==================================***==================================
        | Configuration details
        |
        | $showPlatforms
        | $showDatabase
        |
        | ==================================***==================================
        |
        """.stripMargin

  /* prepare output to display existing platforms and networks */
  private def showAvailablePlatforms(conf: PlatformsConfiguration): IO[String] =
    IO(
      conf.platforms
        .groupBy(_.platform)
        .map { case (platform, configuration) =>
          val networks = showAvailableNetworks(configuration)
          s"  Platform: ${platform.name}$networks"
        }
        .mkString("\n")
    ).handleError(_ => "show available platforms")

  /* prepare output to display existing networks */
  private def showAvailableNetworks(configuration: List[PlatformConfiguration]): String =
    configuration.map { c =>
      val disabled = if (!c.enabled) " (disabled)" else ""
      s"${c.network}$disabled"
    }.mkString("\n  - ", "\n  - ", "\n")

}
