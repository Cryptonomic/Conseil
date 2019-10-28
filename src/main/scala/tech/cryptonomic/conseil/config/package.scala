package tech.cryptonomic.conseil

import com.typesafe.scalalogging.LazyLogging

package object config extends LazyLogging {
  import cats.effect.IO

  /** common way to print diagnostic for a configuration error on the logger */
  def printConfigurationError(context: String, details: String): Unit =
    logger.error(
      s"""I'm not able to correctly load the configuration in $context
         |Please check the configuration file for the following errors:
         |
         |$details""".stripMargin
    )

  /** common way to print diagnostic for a configuration error on the logger, in a pure IO context */
  def delayedLogConfigurationError(context: String, details: String): IO[Unit] =
    IO(
      logger.error(
        s"""I'm not able to correctly load the configuration in $context
         |Please check the configuration file for the following errors:
         |
         |$details""".stripMargin
      )
    )
}
