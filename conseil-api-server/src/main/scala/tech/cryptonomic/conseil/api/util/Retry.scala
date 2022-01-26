package tech.cryptonomic.conseil.api.util

import cats.effect.IO
import cats.syntax.all._

import scala.concurrent.duration._
import scala.util.Random

import tech.cryptonomic.conseil.api.util.syntax._

sealed trait Retry[A] {
  case object RetriesExceededException extends RuntimeException
  case object TimedOutException extends RuntimeException

  def minBackoff: Long

  def retry(maxRetry: Int = 3, giveUpAfter: FiniteDuration = 5.seconds, onFail: A => IO[A] = IO(_))(io: IO[A]): IO[A]
}

final class ExponentialBackoffRetry[A]() extends Retry[A] {
  private def exponentialMultiplier(cnt: Int) = scala.math.pow(2, cnt)

  private def exponentialBackoffWithJitter(cnt: Int) =
    (exponentialMultiplier(cnt) * (1 + Random.nextFloat()) * minBackoff).round.millis

  override val minBackoff = 200

  override def retry(maxRetry: Int, giveUpAfter: FiniteDuration, onFail: A => IO[A])(io: IO[A]): IO[A] = {
    def retryRec(cnt: Int = 1)(io: IO[A]): IO[A] = io.handleErrorWith(errorHandler(cnt))

    def errorHandler(cnt: Int) =
      (_: Throwable) =>
        if (cnt > maxRetry) IO.raiseError(RetriesExceededException)
        else
          IO.sleep(exponentialBackoffWithJitter(cnt)) >>
            IO(s"retrying ${cnt} time").debug >>
            retryRec(cnt + 1)(io.flatMap(onFail))

    retryRec()(io) // .timeoutTo(giveUpAfter, IO.raiseError(throw TimedOutException))
  }
}
