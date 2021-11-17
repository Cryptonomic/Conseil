package tech.cryptonomic.conseil.api.config

import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.auto._
import pureconfig.{ConfigReader, _}
import scopt.OptionParser
import tech.cryptonomic.conseil.api.security.Security
import tech.cryptonomic.conseil.api.security.Security._
import tech.cryptonomic.conseil.common.config.Platforms._
import tech.cryptonomic.conseil.common.config._

/** wraps all configuration needed to run Conseil */
trait ConseilAppConfig {
  import ConseilAppConfig._

  private case class ArgumentsConfig(
      failFast: Boolean = false,
      verbose: Boolean = false
  )

  /** Lazily reads all configuration upstart, will print all errors encountered during loading */
  private val argsParser = new OptionParser[ArgumentsConfig]("conseil-api") {
    opt[Unit]('v', "verbose")
      .action((_, c) => c.copy(verbose = true))
      .text("print additional configuration info when the application is launched")

    opt[Unit]('f', "fail-fast")
      .action((_, c) => c.copy(failFast = true))
      .text("stops without retrying if the initialization process fails for any reason")

    help('h', "help").text("prints this usage text")
  }

  protected def loadApplicationConfiguration(
      commandLineArgs: Array[String]
  ): ConfigReader.Result[CombinedConfiguration] = {

    /** Use the pureconfig convention to handle configuration from the command line */
    def readArgs(args: Array[String]): ConfigReader.Result[ArgumentsConfig] =
      argsParser.parse(args, ArgumentsConfig()).toRight[ConfigReaderFailures](sys.exit(1))

    //this extra configuration might be needed as we add send operations to the conseil API
    // crypto <- loadConfig[SodiumConfiguration](namespace = "sodium.library-path")

    val loadedConf = for {
      args <- readArgs(commandLineArgs)
      ArgumentsConfig(failFast, verbose) = args
      conseil <- loadConfig[ConseilConfiguration](namespace = "conseil")
      platforms <- loadConfig[PlatformsConfiguration]
      metadataOverrides <- loadConfig[MetadataConfiguration]
      securityApi <- Security()
      nautilusCloud <- loadConfig[NautilusCloudConfiguration]("nautilus-cloud")
    } yield
      CombinedConfiguration(
        conseil,
        platforms,
        securityApi,
        FailFast(failFast),
        VerboseOutput(verbose),
        metadataOverrides,
        nautilusCloud
      )

    //something went wrong
    loadedConf.left.foreach { failures =>
      printConfigurationError(context = "Conseil application", failures.prettyPrint())
    }
    loadedConf

  }

}

object ConseilAppConfig extends PlatformConfigurationHint {

  /** Collects all different aspects involved for Conseil */
  final case class CombinedConfiguration(
      server: ConseilConfiguration,
      platforms: PlatformsConfiguration,
      security: SecurityApi,
      failFast: FailFast,
      verbose: VerboseOutput,
      metadata: MetadataConfiguration,
      nautilusCloud: NautilusCloudConfiguration
  )
}
