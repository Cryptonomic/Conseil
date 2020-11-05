package tech.cryptonomic.conseil.common.io

import wvlet.log._
import wvlet.log.LogFormatter.{appendStackTrace, highlightLog, withColor, SourceCodeLogFormatter}
import java.util.logging.ConsoleHandler
import java.time.Instant
import java.time.format.DateTimeFormatter
import java.time.ZoneOffset
import java.util.logging.Formatter
import scala.reflect.ClassTag
import scala.util.Try

/** Defiinitions used to configure and use logging throughout the system */
object Logging {

  /* locally simplifies output formatting via extension methods on the text to be printed */
  implicit private class Coloured(val text: String) extends AnyVal {
    def colored(color: String): String = withColor(color, text)
  }

  /* general initialization */
  Logger.setDefaultFormatter(customFormatter)

  /** Here we get common shared configuration for logging to
    * mix-up with other modules
    */
  trait ConseilLogSupport extends LogSupport {

    /** You can override this variable name to possibly override the default
      * log level provided via configuration, specifying an environment variable
      * with this name, and a value corresponding to a valid
      * [[Logger.LogLevel]]
      *
      * Leave this empty to keep the default configuration.
      */
    protected def loggerLevelEnvVarName: Option[String] = None

    private def envVarNameToLevel =
      for {
        envName <- loggerLevelEnvVarName
        envEntry <- sys.env.get(envName)
        envLevel <- Try(LogLevel(envEntry)).toOption
      } yield envLevel

    /** Override this to define a custom logging level programmatically
      * for this class' logger.
      *
      * This will also cancel any possible effect of defining a [[loggerLevelEnvVarName]]
      * for this class.
      */
    protected def loggerLevel: Option[LogLevel] = envVarNameToLevel

    ConseilLogger.prepare(logger, level = loggerLevel)

    if (logger.isEnabled(LogLevel.DEBUG)) logger.debug("Debug level enabled for this log category")
  }

  /** Use this to build custom logger instances as an alternative to
    * mixin-in [[ConseilLogSupport]]
    */
  object ConseilLogger {

    /** Create a logger corresponding to a class */
    def of[A: ClassTag](level: Option[LogLevel] = None): Logger =
      prepare(Logger.of[A], level)

    /** Create a logger corresponding to a simple name */
    def apply(loggerName: String, level: Option[LogLevel] = None): Logger =
      prepare(Logger(loggerName), level)

    /* Define here any logic that needs be applied to our custom version
     * of loggers for conseil.
     * Let's keep any preparation logic in a one single place.
     */
    private[Logging] def prepare(logger: Logger, level: Option[LogLevel] = None): Logger = {
      logger.resetHandler(new ConsoleOutHandler(customFormatter))
      level.foreach { actualLevel =>
        logger.setLogLevel(actualLevel)
        logger.getHandlers.foreach(_.setLevel(actualLevel.jlLevel))
      }
      logger
    }

  }

  /** Define a standard to format logging by default */
  lazy val commonFormatter: LogFormatter = SourceCodeLogFormatter

  /* Currently copies the SourceCodeLogFormatter internals but can be personalized as we move on */
  lazy val customFormatter: LogFormatter = (r: LogRecord) => {
    val loc =
      r.source
        .map(source => s"(${source.fileLoc})".colored(Console.BLUE))
        .getOrElse("")

    val logTag = highlightLog(r.level, r.level.name.toUpperCase)

    // format: off
    val log =
      f"${formatLogTime(r.getMillis()).colored(Console.BLUE)} [${Thread.currentThread().getName()} thread#${r.getThreadID()}] ${logTag}%14s ${r.leafLoggerName.colored(Console.WHITE)} ${loc} - ${highlightLog(r.level, r.getMessage)}\n"
    // format: on
    appendStackTrace(log, r)
  }

  /* custom formatting of timestamps along the lines of the logback default layout
   * we preserve the existing legacy pattern as long as it's useful
   *
   * Feel free to adapt this to the current application necessities
   */
  private def formatLogTime(time: Long): String =
    DateTimeFormatter.ISO_DATE_TIME.format(Instant.ofEpochMilli(time).atOffset(ZoneOffset.UTC))

  /** Will print on the out console channel */
  private class ConsoleOutHandler(formatter: Formatter) extends ConsoleHandler {
    setOutputStream(System.out)
    setFormatter(formatter)
  }

}
