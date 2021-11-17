package tech.cryptonomic.conseil.common.io

import scala.reflect.ClassTag
import scala.util.{Failure, Try}
import java.nio.file.Paths
import scribe._
import scribe.format._
import scribe.modify.LevelFilter
import scribe.output.{Color, ColoredOutput}
// import pureconfig.ConfigSource
import pureconfig.generic.auto._
import java.net.URL

/** Defiinitions used to configure and use logging throughout the system */
object Logging {

  /* where do we search for config if none is externally provided */
  private lazy val resourcePath = Paths.get(getClass().getResource("/logging.conf").toURI())

  /* check if the user provided a custom log conf */
  private lazy val userProvidedPath = sys.props.get("logging.conf").map(Paths.get(_))

  /** We need to initialize the logging system which reads any available configuration on the resource path.
    * The expected config file is named "logging.conf" and uses HOCON format.
    * See lightbend logging to learn more about the format.
    *
    * An external config can be provided via `-Dlogging.conf=<your-file>` when running the app.
    *
    * The Conseil Wiki provides the details on the file content and options.
    */
  def init(): Unit = {
    val showConfig =
      userProvidedPath.fold("scanning resources for logging.conf files")(conf => s"using provided file at $conf")
    info(s"Initializing Conseil logging from configuration: $showConfig...")

    userProvidedPath match {
      case Some(value) =>
        pureconfig
          .loadConfig[Config](value, "logging")
          // ConfigSource.default
          //   .at("logging")
          //   .load[Config]
          .fold(
            failures => {
              error("I can't load the logging configuration from path")
              // failures.toList.foreach(fail => error(s"${fail.description} ${fail.origin.getOrElse("")}"))
              failures.toList.foreach(fail => error(s"${fail.description} ${fail.location.getOrElse("")}"))
            },
            configure
          )
      case None =>
        pureconfig
          .loadConfig[Config]("logging")
          // ConfigSource.default
          //   .at("logging")
          //   .load[Config]
          .fold(
            failures => {
              error("I can't load the logging configuration")
              // failures.toList.foreach(fail => error(s"${fail.description} ${fail.origin.getOrElse("")}"))
              failures.toList.foreach(fail => error(s"${fail.description} ${fail.location.getOrElse("")}"))
            },
            configure
          )
    }

    info("Conseil logging configuration done")
  }

  /** Configuration for logging as read from a configuration file.
    * We expect to load configuration from a file which reflects this class structure.
    *
    * Here follows a generic schema for the logging.conf file content
    * Read the paramters detail for the meaning of the different values
    *
    * {{{
    * muted: true/false
    * output-level: "DEBUG" [OPTIONAL: if missing defaults to INFO]
    * output-json-with-service-name: <my-application-instance-name> [OPTIONAL]
    * ouput-to-logstash: <logstash-http-address> [OPTIONAL]
    * loggers: [
    *   {
    *     name: "<logger-name>",
    *     muted: true/false [OPTIONAL: if missing defaults to false]
    *     from-env: "ENV_VAR_NAME_DEFINING_A_LEVEL" [OTIONAL: if missing, or if var unset, will read from level]
    *     level: "INFO" [OPTIONAL: if missing, will read from env only, or ignore this logger]
    *   }
    *   , ...
    * ]
    * }}}
    *
    * The individual logger entries will override a logging threshold and is not needed to enable a logging
    * category, which is considered on by default
    *
    *
    * @param muted when enabled, it will stop every logging from occurring, like sending to dev/null
    * @param loggers a list of configurations for specific loggers
    * @param outputLevel the global threshold to append on the log
    * @param outputJsonWithServiceName if defined, will output the log entries as json, using this name in a service attribute
    * @param outputToLogstash when using json as output, will use this optional URL to send the entries in a logstash service
    */
  case class Config(
      muted: Boolean = false,
      loggers: List[LoggerConfig] = List.empty,
      outputLevel: Option[String],
      enableJsonOutput: Boolean,
      outputJsonWithServiceName: Option[String] = None,
      outputToLogstash: Option[URL] = None
  )

  /** A single logger configuration.
    *
    * Levels should conform to the naming defined in [[scribe.Level]]
    *
    * @param name identifies the logger, usually as a FQN for a class, a package, a string
    * @param level the minimum level assigned to that logger, everything of lower priority won't show
    * @param fromEnv optionally read from the environment variable so named, which level to assign
    */
  case class LoggerConfig(
      name: String,
      muted: Boolean = false,
      fromEnv: Option[String],
      level: Option[String]
  ) {

    private def searchEnv = fromEnv.flatMap(sys.env.get(_))

    /** Extracts the log-level as a typed value
      * It will first lookup the [[fromEnv]] value and fallback to the explicit [[level]]
      */
    def logLevel: Try[Level] =
      Try(
        (searchEnv orElse level).map(Level(_)).get
      ).recoverWith {
        case err if (fromEnv.isEmpty && level.isEmpty) =>
          error(
            s"The logging configuration might be incorrect for $name. The logger level seems to be missing or incorrect. Use muted: true, to turn off a logger.",
            err
          )
          Failure(err)
      }
  }

  /* For the given config reads details about how to custom tailor individual loggers */
  private def configure(loggingConfig: Config) = {
    /* Assigns the same formatting to any logger by defining it on the parent root logger */
    /* Cross-check config values to assess the appropriate logging schema */
    val configuredRootLogger =
      (loggingConfig.muted, loggingConfig.enableJsonOutput, loggingConfig.outputToLogstash) match {
        case (true, _, _) =>
          //remove anything
          warn("Setting any logging to OFF")
          Logger.root.clearHandlers().clearModifiers().withModifier(LevelFilter.ExcludeAll)
        case (_, false, _) =>
          //standard out logger with shared formatting for any child
          Logger.root
            .clearHandlers()
            .withHandler(formatter = sharedFormatter, minimumLevel = loggingConfig.outputLevel.map(Level(_)))
        case (_, true, logstashEndpoint) =>
          //outputs json formatting, based on logstash standards, possibly sending to an external server directly
          warn("Setting json logging")
          val jsonHandler = JsonLogging.JsonWriter(
            loggingConfig.outputJsonWithServiceName.getOrElse("conseil-service"),
            logstash = logstashEndpoint
          )
          Logger.root
            .clearHandlers()
            .withHandler(writer = jsonHandler, minimumLevel = loggingConfig.outputLevel.map(Level(_)))
      }

    configuredRootLogger.replace()

    /* Apply any custom config to the individual logger */
    loggingConfig.loggers.foreach {
      case loggerConf =>
        Logger(loggerConf.name)
          .clearHandlers()
          /* Accept an explicit flag to mute, independently of included level
           * It can be implicitly muted if an environment value is searched and not found, with no explicit default
           */
          .withModifier(
            if (loggerConf.muted) LevelFilter.ExcludeAll
            else loggerConf.logLevel.map(LevelFilter >= _).getOrElse(LevelFilter.ExcludeAll)
          )
          .replace()
    }

  }

  /** Here we get common shared configuration for logging to
    * mix-up with other modules
    */
  trait ConseilLogSupport extends scribe.Logging {
    ConseilLogger.prepare(logger)

    if (logger.includes(Level.Debug))
      logger.debug(s"Debug level enabled for this logger: ${getClass().getCanonicalName}")
  }

  /** Use this to build custom logger instances as an alternative to
    * mixin-in [[ConseilLogSupport]]
    */
  object ConseilLogger {
    /* dirty trick to force initialization of logging */
    // private val logging = Logging

    /** Create a logger corresponding to a class */
    def of[A: ClassTag](level: Option[Level] = None): Logger =
      prepare(Logger[A], level)

    /** Create a logger corresponding to a simple name */
    def apply(loggerName: String, level: Option[Level] = None): Logger =
      prepare(Logger(loggerName), level)

    /* Define here any logic that needs be applied to our custom version
     * of loggers for conseil.
     * Let's keep any preparation logic in a one single place.
     */
    private[Logging] def prepare(logger: Logger, level: Option[Level] = None): Logger =
      level match {
        case Some(level) => logger.withMinimumLevel(level).replace()
        case None => logger
      }
  }

  /** Define a standard to format logging by default */
  lazy val sharedFormatter: Formatter =
    formatter"$date [$threadName] $levelColoredPaddedRight $classNameAbbreviated ($position) - $messageColored$mdc$newLine"

  /* written based on other existing colored outputs */
  private def messageColored: FormatBlock = FormatBlock { logRecord =>
    val color = logRecord.level match {
      case Level.Trace => Color.White
      case Level.Debug => Color.Green
      case Level.Info => Color.Blue
      case Level.Warn => Color.Yellow
      case Level.Error => Color.Red
      case Level.Fatal => Color.Magenta
      case _ => Color.Cyan
    }
    new ColoredOutput(color, message.format(logRecord))
  }

}
