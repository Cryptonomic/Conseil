package tech.cryptonomic.conseil.common

import com.typesafe.scalalogging.LazyLogging

package object config extends LazyLogging {

  /** common way to print diagnostic for a configuration error on the logger */
  def printConfigurationError(context: String, details: String): Unit =
    logger.error(
      s"""I'm not able to correctly load the configuration in $context
         |Please check the configuration file for the following errors:
         |
         |$details""".stripMargin
    )
}
