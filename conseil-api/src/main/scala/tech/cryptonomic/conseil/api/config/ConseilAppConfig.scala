package tech.cryptonomic.conseil.api.config

import com.github.ghik.silencer.silent
import com.typesafe.config.{ConfigObject, ConfigValue}
import com.typesafe.scalalogging.LazyLogging
import pureconfig.error.{ConfigReaderFailures, ExceptionThrown, FailureReason}
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import pureconfig.{ConfigReader, _}
import scopt.OptionParser
import tech.cryptonomic.conseil.api.security.Security
import tech.cryptonomic.conseil.api.security.Security._
import tech.cryptonomic.conseil.common.config.Platforms._
import tech.cryptonomic.conseil.common.config._

import scala.util.Try

/** wraps all configuration needed to run Conseil */
trait ConseilAppConfig {
  import ConseilAppConfig.CombinedConfiguration

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
    import ConseilAppConfig.Implicits._

    /** Use the pureconfig convention to handle configuration from the command line */
    def readArgs(args: Array[String]): ConfigReader.Result[ArgumentsConfig] =
      argsParser.parse(args, ArgumentsConfig()).toRight[ConfigReaderFailures](sys.exit(1))

    //this extra configuration might be needed as we add send operations to the conseil API
    // crypto <- loadConfig[SodiumConfiguration](namespace = "sodium.libraryPath")

    val loadedConf = for {
      args <- readArgs(commandLineArgs)
      ArgumentsConfig(failFast, verbose) = args
      conseil <- loadConfig[ConseilConfiguration](namespace = "conseil")
      platforms <- loadConfig[PlatformsConfiguration](namespace = "platforms")
      metadataOverrides <- loadConfig[MetadataConfiguration]
      securityApi <- Security()
      nautilusCloud <- loadConfig[Option[NautilusCloudConfiguration]]("nautilus-cloud")
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
      printConfigurationError(context = "Conseil application", failures.toList.mkString("\n\n"))
    }
    loadedConf

  }

}

object ConseilAppConfig extends LazyLogging {

  /** Collects all different aspects involved for Conseil */
  final case class CombinedConfiguration(
      server: ConseilConfiguration,
      platforms: PlatformsConfiguration,
      security: SecurityApi,
      failFast: FailFast,
      verbose: VerboseOutput,
      metadata: MetadataConfiguration,
      nautilusCloud: Option[NautilusCloudConfiguration]
  )

  private[config] object Implicits {
    import tech.cryptonomic.conseil.common.config.Platforms.PlatformConfiguration

    import scala.collection.JavaConverters._

    /** pureconfig reader for lists of tezos configurations, i.e. one for each network.
      * Take care: this will not load any TNS definition from the config file, since
      * it's supposed to be used only to decode the "platforms" configuration section, which
      * doesn't include the "tns" definition.
      */
    implicit private val tezosConfigurationReader: ConfigReader[List[TezosConfiguration]] =
      ConfigReader[ConfigObject].emap { obj =>
        //applies convention to uses CamelCase when reading config fields
        @silent("local method hint in value")
        implicit def hint[T: ConfigReader]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

        val availableNetworks = obj.keySet.asScala.toSet
        val config = obj.toConfig
        val parsed: Set[Either[FailureReason, TezosConfiguration]] =
          availableNetworks.map { network =>
            //try to read the node and tns subconfigs as an Either, might be missing or fail to be an object
            def findObject[T: ConfigReader](readValue: => ConfigValue) =
              Try(readValue).toEither.left.map(ExceptionThrown).flatMap(readAndFailWithFailureReason[T])

            val path = s"$network.node"
            findObject[TezosNodeConfiguration](config.getObject(path))
              .map(node => TezosConfiguration(network, node, tns = None))

          }
        foldReadResults(parsed) {
          _.toList
        }
      }

    implicit private val bitcoinConfigurationReader: ConfigReader[List[BitcoinConfiguration]] =
      ConfigReader[ConfigObject].map { obj =>
        obj.keySet.asScala.toSet.map(BitcoinConfiguration).toList
      }

    /** pureconfig reader for undefined Platform configurations */
    implicit private val unknownPlatformReader: ConfigReader[List[UnknownPlatformConfiguration]] =
      ConfigReader[ConfigObject].map(_.keySet.asScala.toList.map(key => UnknownPlatformConfiguration(key)))

    /** pureconfig reader that converts a top-level platforms definition to a structured mapping */
    implicit val platformsConfigurationsReader: ConfigReader[PlatformsConfiguration] =
      ConfigReader[ConfigObject].emap { confObj =>
        //polymorphic config reader for each platform, that will use the appropriate ConfigurationType reader for each platform type
        def extractConfigList[P <: BlockchainPlatform](platform: P)(
            implicit reader: ConfigReader[List[platform.ConfigurationType]]
        ): Either[FailureReason, (P, List[PlatformConfiguration])] =
          readAndFailWithFailureReason[List[platform.ConfigurationType]](confObj.get(platform.name)).map(platform -> _)

        //converts each string key to a proper platform type
        val availablePlatforms = confObj.keySet.asScala.map(BlockchainPlatform.fromString).toSet
        val parsed: Set[Either[FailureReason, (BlockchainPlatform, List[PlatformConfiguration])]] =
          availablePlatforms.map {
            case Tezos => extractConfigList(Tezos)
            case Bitcoin => extractConfigList(Bitcoin)
            case p @ UnknownPlatform(_) => extractConfigList(p)
          }

        foldReadResults(parsed) { platformEntries =>
          PlatformsConfiguration(platformEntries.toMap)
        }
      }

    /** converts multiple read failures to a single, generic, FailureReason */
    val reasonFromReadFailures: ConfigReaderFailures => FailureReason = (failures: ConfigReaderFailures) =>
      new FailureReason {
        override val description: String = failures.toList.map(_.description).mkString(" ")
      }

    /** extract a custom class type from the generic lightbend-config value failing with a `FailureReason` */
    def readAndFailWithFailureReason[T](
        value: ConfigValue
    )(implicit reader: ConfigReader[T]): Either[FailureReason, T] =
      reader.from(value).left.map(reasonFromReadFailures)

    /**
      * Check all results that may have failed, returning an `Either` that will hold a single failure if there's any.
      * In case of all successful parsing, the results are reduced with the passed-in function
      *
      * @param readResults output of some ConfigReader parsing
      * @param reduce      aggregator function that will be applied to successful results
      * @tparam T the input parsing expected type
      * @tparam R the final outcome type in case of no failed input
      */
    def foldReadResults[T, R](
        readResults: Traversable[Either[FailureReason, T]]
    )(reduce: Traversable[T] => R): Either[FailureReason, R] = {
      //elements might have failed, collect all failures
      val failureDescription = readResults.collect { case Left(f) => f.description }.mkString(" ")
      if (failureDescription.nonEmpty)
        logger.warn("Can't correctly read platform configuration, reasons are: {}", failureDescription)
      //test for failures, and if there's none, we can safely read the Right values
      Either.cond(
        test = !readResults.exists(_.isLeft),
        right = reduce(readResults.map(_.right.get)),
        left = new FailureReason {
          override val description: String = failureDescription
        }
      )
    }
  }
}
