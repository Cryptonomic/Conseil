package tech.cryptonomic.conseil.indexer.logging

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport

import scala.concurrent.duration.{Duration, NANOSECONDS}

trait LorreProgressLogging extends ConseilLogSupport {

  /** Keeps track of time passed between different partial checkpoints of some entity processing
    * Designed to be partially applied to set properties of the whole process once, and then only compute partial completion
    *
    * @param entityName a string that will be logged to identify what kind of resource is being processed
    * @param totalToProcess how many entities there were in the first place
    * @param processStartNanos a nano-time from jvm monotonic time, used to identify when the whole processing operation began
    * @param processed how many entities were processed at the current checkpoint
    */
  def logProcessingProgress(entityName: String, totalToProcess: Long, processStartNanos: Long)(
      processed: Int
  ): Unit = {
    val elapsed = System.nanoTime() - processStartNanos
    val progress = processed.toDouble / totalToProcess
    val progressPercent = "%.2f".format(progress * 100)
    logger.info("================================== Progress Report ==================================")
    logger.info(s"Completed processing $progressPercent% of total requested ${entityName}s")

    val etaMins = Duration(scala.math.ceil(elapsed / progress) - elapsed, NANOSECONDS).toMinutes
    if (processed < totalToProcess && etaMins > 1) logger.info(s"Estimated time to finish is around $etaMins minutes")
    logger.info("=====================================================================================")
  }

}
