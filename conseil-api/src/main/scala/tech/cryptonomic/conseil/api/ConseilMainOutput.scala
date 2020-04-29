package tech.cryptonomic.conseil.api

import com.typesafe.scalalogging.Logger
import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.api.config.ConseilConfiguration
import tech.cryptonomic.conseil.common.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.common.io.MainOutputs._

/** Defines what to print when starting Conseil */
trait ConseilMainOutput {

  /** we need to have a logger */
  protected[this] def logger: Logger

  /** Shows the main application info
    * @param serverConf configuration of the http server
    */
  protected[this] def displayInfo(serverConf: ConseilConfiguration): Unit =
    logger.info(
      """
        | ==================================***==================================
        |  Conseil v.{}
        |  {}
        | ==================================***==================================
        |
        | Server started on {} at port {}
        | Bonjour...
        |
        |""".stripMargin,
      BuildInfo.version,
      BuildInfo.gitHeadCommit.fold("")(hash => s"[commit-hash: ${hash.take(7)}]"),
      serverConf.hostname,
      serverConf.port
    )

  /** Shows details on the current configuration */
  protected[this] def displayConfiguration(platformConfigs: PlatformsConfiguration): Unit =
    logger.info(
      """
        | ==================================***==================================
        | Configuration details
        |
        | {}
        | {}
        |
        | ==================================***==================================
        |
        """.stripMargin,
      showAvailablePlatforms(platformConfigs),
      showDatabaseConfiguration("conseil")
    )

}
