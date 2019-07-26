package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.config.Platforms._
import tech.cryptonomic.conseil.config.Security._
import pureconfig.generic.auto._
import pureconfig.{loadConfig, ConfigReader}
import pureconfig.error.ConfigReaderFailures
import scopt.OptionParser

/** wraps all configuration needed to run Conseil */
trait ConseilAppConfig {

  type Configurations = (
      ServerConfiguration,
      PlatformsConfiguration,
      SecurityApi,
      VerboseOutput,
      MetadataConfiguration,
      Option[NautilusCloudConfiguration]
  )

  /** Lazily reads all configuration upstart, will print all errors encountered during loading */
  private val argsParser = new OptionParser[VerboseOutput]("conseil") {
    opt[Unit]('v', "verbose")
      .action((_, conf) => VerboseOutput(true))
      .text("print additional configuration info when the application is launched")

    help('h', "help").text("prints this usage text")
  }

  protected def loadApplicationConfiguration(commandLineArgs: Array[String]): ConfigReader.Result[Configurations] = {
    import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

    /** Use the pureconfig convention to handle configuration from the command line */
    def readArgs(args: Array[String]): ConfigReader.Result[VerboseOutput] =
      argsParser.parse(args, VerboseOutput(false)).toRight[ConfigReaderFailures](sys.exit(1))

    //this extra configuration might be needed as we add send operations to the conseil API
    // crypto <- loadConfig[SodiumConfiguration](namespace = "sodium.libraryPath")

    val loadedConf = for {
      verbose <- readArgs(commandLineArgs)
      server <- loadConfig[ServerConfiguration](namespace = "conseil")
      platforms <- loadConfig[PlatformsConfiguration](namespace = "platforms")
      metadataOverrides <- loadConfig[MetadataConfiguration]
      securityApi <- Security()
      nautilusCloud <- loadConfig[Option[NautilusCloudConfiguration]]("nautilus-cloud")
    } yield (server, platforms, securityApi, verbose, metadataOverrides, nautilusCloud)

    //something went wrong
    loadedConf.left.foreach { failures =>
      printConfigurationError(context = "Conseil application", failures.toList.mkString("\n\n"))
    }
    loadedConf

  }

}
