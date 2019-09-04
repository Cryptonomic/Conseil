package tech.cryptonomic.conseil.util
import scala.concurrent.duration.{Duration, NANOSECONDS}

/** Utility class to add time metering of code blocks and logging the details
  * @param log is the function that actually does the logging
  */
class MeteringLogger(log: String => Unit) {

  /** Wraps some code block to log the time taken to execute, in milliseconds
    * @param logDescription used to identify what's being measured in the log prints
    * @param block the code to measure
    * @return the result of execuding the wrapped code
    */
  def metered[T](logDescription: => String)(block: => T): T = {
    val start = System.nanoTime()
    val t = block
    val lapse = Duration(System.nanoTime() - start, NANOSECONDS).toMillis
    log(s"$logDescription took $lapse milliseconds")
    t
  }

}
