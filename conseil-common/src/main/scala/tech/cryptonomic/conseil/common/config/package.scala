package tech.cryptonomic.conseil.common

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import pureconfig.error.ConfigReaderFailures

package object config extends ConseilLogSupport {

  /** common way to print diagnostic for a configuration error on the logger */
  def printConfigurationError(context: String, details: String): Unit =
    logger.error(
      s"""I'm not able to correctly load the configuration in $context
         |Please check the configuration file for the following errors:
         |
         |$details""".stripMargin
    )

  /**
    * Extension for the `ConfigReaderFailures` that prints all of the errors in human-readable form.
    * Note that this code has been copied from pureconfig:0.13, but due to the issue with reading
    * optional values directly from the configuration we are not going to update this version yet.
    */
  implicit class ConfigReaderFailuresOps(failures: ConfigReaderFailures) {
    import pureconfig.error.{ConfigReaderFailure, ConvertFailure}

    import scala.collection.mutable

    def prettyPrint(identLevel: Int = 0, identSize: Int = 2): String = {
      def tabs(n: Int): String = " " * ((identLevel + n) * identSize)
      def descriptionWithLocation(failure: ConfigReaderFailure, ident: Int): String = {
        val failureLines = failure.description.split("\n")
        // (failure.origin.fold(s"${tabs(ident)}- ${failureLines.head}")(
        (failure.location.fold(s"${tabs(ident)}- ${failureLines.head}")(
          f => s"${tabs(ident)}- ${f.description} ${failureLines.head}"
        ) ::
            failureLines.tail.map(l => s"${tabs(ident + 1)}$l").toList).mkString("\n")
      }

      val linesBuffer = mutable.Buffer.empty[String]
      val (convertFailures, otherFailures) = failures.toList.partition(_.isInstanceOf[ConvertFailure])

      val failuresByPath = convertFailures.asInstanceOf[List[ConvertFailure]].groupBy(_.path).toList.sortBy(_._1)

      otherFailures.foreach { failure =>
        linesBuffer += descriptionWithLocation(failure, 0)
      }

      if (otherFailures.nonEmpty && convertFailures.nonEmpty) {
        linesBuffer += ""
      }

      failuresByPath.foreach {
        case (p, failures) =>
          linesBuffer += (tabs(0) + (if (p.isEmpty) s"at the root:" else s"at '$p':"))
          failures.foreach { failure =>
            linesBuffer += descriptionWithLocation(failure, 1)
          }
      }
      linesBuffer.mkString(System.lineSeparator())
    }
  }
}
