package tech.cryptonomic.conseil.api.util

/* The following code is adapted from https://github.com/seahrh/concurrent-scala and subject to licensing terms hereby specified
 * MIT License
 *
 * Copyright (c) 2017 Ruhong
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
import scala.annotation.tailrec
import scala.concurrent.duration.{Deadline, Duration, DurationLong}
import scala.concurrent._
import scala.language.postfixOps
import scala.util.{Failure, Random, Success, Try}

/** Provides self-contained functionality to define a retry policy for
  * any operation, embedding that in a Future, that will be retried if
  * any exception is thrown.
  *
  * Different time-scheduling policies can be used to define the retry logic.
  */
object Retry {

  class TooManyRetriesException extends RuntimeException

  class DeadlineExceededException extends RuntimeException

  private val minBackoff: Duration = 100 milliseconds

  private def exponentialBackoffMultiplier(retryCnt: Int): Long = scala.math.pow(2, retryCnt).round

  /**
    * exponential back off for retry
    */
  def exponentialBackoff(retryCnt: Int): Duration =
    exponentialBackoffMultiplier(retryCnt) * minBackoff

  def exponentialBackoffAtMostOneHour(retryCnt: Int): Duration = {
    val max: Duration = 1 hours
    val d: Duration = exponentialBackoff(retryCnt)
    if (d < max) d else max
  }

  def exponentialBackoffWithJitter(retryCnt: Int): Duration = {
    val jitter: Duration = Random.nextFloat * minBackoff
    exponentialBackoffMultiplier(retryCnt) * (minBackoff + jitter)
  }

  /**
    * retries at regular intervals
    *
    * @param every the wait time between retries
    */
  def noBackoff(every: Duration = minBackoff): Int => Duration = Function.const(every) _

  /**
    * retries at regular intervals adding jittering
    *
    * @param every the wait time between retries
    */
  def noBackoffWithJitter(every: Duration = minBackoff): Int => Duration = {
    val jitter: Duration = Random.nextFloat * minBackoff
    noBackoff(every) andThen (_ + jitter)
  }

  private val doNotGiveUp = Function.const[Boolean, Throwable](false) _

  /**
    * retry a particular block that can fail
    *
    * @param maxRetry          how many times to retry before to giveup; None means always retry
    * @param deadline          how long to retry before giving up; default None
    * @param backoff           a back-off function that returns a Duration after which to retry.
    *                          Default is an exponential backoff at 100 milliseconds steps
    * @param giveUpOnThrowable if you want to stop retrying on a particular exception
    * @param block             a block of code to retry
    * @param executionContext  an execution context where to execute the block
    * @return an eventual Future succeeded with the value computed or failed with one of:
    *         `TooManyRetriesException`
    *         if there were too many retries without an exception being caught.
    *         Probably impossible if you pass decent parameters
    *         `DeadlineExceededException` if the retry didn't succeed before the provided deadline
    *         `Throwable` the last encountered exception
    */
  def retry[T](
      maxRetry: Option[Int],
      deadline: Option[Deadline] = None,
      backoff: (Int) => Duration = exponentialBackoff,
      giveUpOnThrowable: Throwable => Boolean = doNotGiveUp
  )(block: => T)(implicit executionContext: ExecutionContext): Future[T] = {
    val p = Promise[T]()

    @tailrec
    def recursiveRetry(
        retryCnt: Int,
        exception: Option[Throwable]
    )(f: () => T): Unit = {
      val isOverdue: Boolean = deadline.exists(_.isOverdue)
      val isTooManyRetries: Boolean = maxRetry.exists(_ < retryCnt)
      if (isOverdue) {
        val e = new DeadlineExceededException().initCause(exception.orNull)
        p failure e
      } else if (isTooManyRetries) {
        val e = new TooManyRetriesException().initCause(exception.orNull)
        p failure e
      } else {
        Try {
          deadline match {
            case Some(d) =>
              Await.result(Future {
                f()
              }, d.timeLeft)
            case _ => f()
          }
        } match {
          case Success(v) =>
            p success v
          case Failure(t) if !giveUpOnThrowable(t) =>
            blocking {
              val interval = backoff(retryCnt).toMillis
              Thread.sleep(interval)
            }
            recursiveRetry(retryCnt + 1, Option(t))(f)
          case Failure(t) =>
            p failure t
        }
      }
    }

    Future {
      recursiveRetry(0, None)(() => block)
    }
    p.future
  }
}
