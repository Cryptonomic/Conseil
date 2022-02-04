package tech.cryptonomic.conseil.api.util

import cats.effect.IO

import scala.concurrent.duration.FiniteDuration

object syntax {
  implicit class RetryOps[A](io: IO[A]) {
    def retry(maxRetry: Int, giveUpAfter: FiniteDuration, onFail: A => IO[A]): IO[A] =
      new ExponentialBackoffRetry().retry(maxRetry, giveUpAfter, onFail)(io)
    def retry(maxRetry: Int, giveUpAfter: FiniteDuration): IO[A] =
      new ExponentialBackoffRetry().retry(maxRetry, giveUpAfter)(io)
    def retry(maxRetry: Int): IO[A] = new ExponentialBackoffRetry().retry(maxRetry)(io)
    def retry(): IO[A] = new ExponentialBackoffRetry().retry()(io)
  }
}
