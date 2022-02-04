package tech.cryptonomic.conseil.api.util

import tech.cryptonomic.conseil.common.util.syntax._

import cats.effect.IO

sealed trait RetryException extends RuntimeException

object RetryException {
  case object RetriesExceededException extends RetryException
  case object TimedOutException extends RetryException
  case object UnknownRetryException extends RetryException

  implicit class ReRaiseExceptionOps[A, E <: RetryException](e: E) {
    def raise = IO.raiseError[A](e).debug
  }
}
