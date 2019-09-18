package tech.cryptonomic.conseil.util

import com.typesafe.scalalogging.{LazyLogging, Logger}
import cats._

/** Piggybacks on lightbend logging, adding typed effects to
  * the log commands via extension methods
  */
trait PureLogging extends LazyLogging {

  /* Add the extensions to the Logger*/
  implicit class PureLoggerOps(base: Logger) {

    /** Wraps the logging operation in a, usually lazy, effect type.
      * This means that logging doesn't really happen, until the effect
      * is actually "run"
      * @example {{{
      * logger.pureLog[IO](_.info("my log message"))
      * }}}
      */
    def pureLog[Eff[_]: Applicative](log: Logger => Unit): Eff[Unit] =
      Applicative[Eff].pure(log(base))

  }

}
