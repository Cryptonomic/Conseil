package tech.cryptonomic.conseil.indexer

import com.typesafe.scalalogging.Logger
import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.common.config.LorreConfiguration
import tech.cryptonomic.conseil.common.config.Platforms.{BlockchainPlatform, PlatformConfiguration}
import tech.cryptonomic.conseil.common.io.MainOutputs.{showDatabaseConfiguration, showPlatformConfiguration}

import scala.concurrent.duration.{Duration, NANOSECONDS}

trait LorreLogging {

  protected def logger: Logger

  /** Keeps track of time passed between different partial checkpoints of some entity processing
    * Designed to be partially applied to set properties of the whole process once, and then only compute partial completion
    *
    * @param entityName a string that will be logged to identify what kind of resource is being processed
    * @param totalToProcess how many entities there were in the first place
    * @param processStartNanos a nano-time from jvm monotonic time, used to identify when the whole processing operation began
    * @param processed how many entities were processed at the current checkpoint
    */
  def logProcessingProgress(entityName: String, totalToProcess: Int, processStartNanos: Long)(
      processed: Int
  ): Unit = {
    val elapsed = System.nanoTime() - processStartNanos
    val progress = processed.toDouble / totalToProcess
    logger.info("================================== Progress Report ==================================")
    logger.info("Completed processing {}% of total requested {}s", "%.2f".format(progress * 100), entityName)

    val etaMins = Duration(scala.math.ceil(elapsed / progress) - elapsed, NANOSECONDS).toMinutes
    if (processed < totalToProcess && etaMins > 1) logger.info("Estimated time to finish is around {} minutes", etaMins)
    logger.info("=====================================================================================")
  }

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
