package tech.cryptonomic.conseil.indexer.config

import com.github.ghik.silencer.silent
import com.typesafe.config.Config
import pureconfig._
import pureconfig.error._
import pureconfig.generic.auto._
import pureconfig.generic.FieldCoproductHint
import scopt.{OptionParser, Read}
import tech.cryptonomic.conseil.common.config.Platforms._
import tech.cryptonomic.conseil.common.config.{PlatformConfiguration => _, _}
import tech.cryptonomic.conseil.indexer.config.ConfigUtil.Depth._

import scala.util.{Success, Try}

/** wraps all configuration needed to run Lorre */
trait LorreAppConfig {
  import LorreAppConfig.Loaders._
  import LorreAppConfig._

  /* used by scopt to parse the blockchain platform object */
  implicit private val platformRead: Read[BlockchainPlatform] = Read.reads { str =>
    Try(BlockchainPlatform.fromString(str)) match {
      case Success(platform) => platform
      case _ => throw new IllegalArgumentException("'" + str + "' is not a valid platform.")
    }
  }

  private case class ArgumentsConfig(
      depth: Depth = Newest,
      verbose: Boolean = false,
      headHash: Option[String] = None,
      platform: BlockchainPlatform = Tezos, // we are setting up Tezos by default, but it does not really matter.
      network: String = ""
  )

  private val argsParser = new OptionParser[ArgumentsConfig]("lorre") {
    arg[BlockchainPlatform]("platform")
      .required()
      .action((x, c) => c.copy(platform = x))
      .text("which platform to use")

    arg[String]("network")
      .required()
      .action((x, c) => c.copy(network = x))
      .text("which network to use")

    opt[Option[String]]('h', "headHash")
      .action((x, c) => c.copy(headHash = x))
      .text("from which block to start. Default to actual head")

    opt[Option[Depth]]('d', "depth").validate {
      case Some(_) => success
      case None => failure("I failed to parse a valid depth from the arguments")
    }.action((depth, c) => c.copy(depth = depth.get))
      .text(
        "how many blocks to synchronize starting with head: use -1 or `all` for everything and 0 or `new` to only get new ones)"
      )

    opt[Unit]('v', "verbose")
      .action((_, c) => c.copy(verbose = true))
      .text("print additional configuration info when the application is launched")

    help('h', "help").text("prints this usage text")
  }

  /** Reads all configuration upstart, will print all errors encountered during loading */
  protected def loadApplicationConfiguration(
      commandLineArgs: Array[String]
  ): ConfigReader.Result[CombinedConfiguration] = {

    /** Use the pureconfig convention to handle configuration from the command line */
    def readArgs(args: Array[String]): ConfigReader.Result[ArgumentsConfig] =
      argsParser.parse(args, ArgumentsConfig()).toRight[ConfigReaderFailures](sys.exit(1))

    @silent("local val chainEventHint in method loadApplicationConfiguration is never used")
    implicit val chainEventHint: FieldCoproductHint[ChainEvent] = new FieldCoproductHint[ChainEvent]("type") {
      override def fieldValue(name: String): String = name.head.toLower +: name.tail
    }
    @silent("local val lorreConfigHint in method loadApplicationConfiguration is never used")
    implicit val lorreConfigHint = new FieldCoproductHint[LorreConfigurationHelper]("type") {
      override protected def fieldValue(name: String): String = name.take(name.length - "Helper".length).toLowerCase
    }
    @silent("local val lorreConfigReader in method loadApplicationConfiguration is never used")
    implicit val lorreConfigReader: ConfigReader[LorreConfiguration] =
      ConfigReader[LorreConfigurationHelper].map(_.toConf)

    val loadedConf = for {
      args <- readArgs(commandLineArgs)
      ArgumentsConfig(depth, verbose, headHash, platform, network) = args
      lorre <- loadConfig[LorreConfiguration](namespace = "lorre").map { conf =>
        /** In general, [[ArgumentsConfig]] should take precedence over [[LorreConfiguration]] */
        if (depth == Newest && headHash.isDefined) conf.copy(headHash = headHash)
        else if (depth != Newest) {
          if (!headHash.isDefined) conf.copy(depth = depth)
          else conf.copy(depth = depth, headHash = headHash)
        } else conf
      }
      nodeRequests <- loadConfig[NetworkCallsConfiguration]("lorre")
      platform <- loadPlatformConfiguration(platform.name, network)
      streamingClient <- loadAkkaStreamingClientConfig(namespace = "akka.streaming-client")
      fetching <- loadConfig[BatchFetchConfiguration](namespace = "lorre.batched-fetches")
    } yield
      CombinedConfiguration(
        lorre,
        platform,
        nodeRequests,
        streamingClient,
        fetching,
        VerboseOutput(verbose)
      )

    //something went wrong
    loadedConf.left.foreach { failures =>
      printConfigurationError(context = "Lorre application", failures.prettyPrint())
    }
    loadedConf
  }

}

object LorreAppConfig {

  final val LORRE_FAILURE_IGNORE_VAR = "LORRE_FAILURE_IGNORE"

  /** Collects all different aspects involved for Lorre */
  final case class CombinedConfiguration(
      lorre: LorreConfiguration,
      platform: PlatformConfiguration,
      nodeRequests: NetworkCallsConfiguration,
      streamingClientPool: HttpStreamingConfiguration,
      batching: BatchFetchConfiguration,
      verbose: VerboseOutput
  )

  private[config] object Loaders extends PlatformConfigurationHint {

    /*** Reads a specific platform configuration based on given 'platform' and 'network' */
    def loadPlatformConfiguration(
        platform: String,
        network: String,
        config: Option[Config] = None // for testing only
    ): Either[ConfigReaderFailures, PlatformConfiguration] =
      // Note that Lorre process allows to run only one integration per process.
      // Configuration file can contain more than one, thus required parameters.
      config
        .map(loadConfig[PlatformsConfiguration])
        .getOrElse(loadConfig[PlatformsConfiguration])
        .flatMap {
          _.platforms.find(x => x.platform.name == platform && x.network == network) match {
            case Some(platformsConfig) if platformsConfig.enabled => Right(platformsConfig)
            case Some(platformsConfig) if !platformsConfig.enabled =>
              Left(ConfigReaderFailures(new ConfigReaderFailure {

                override def description: String =
                  s"Could not run Lorre for platform: $platform, network: $network because this network is disabled"
                override def location: Option[ConfigValueLocation] = None
              }))
            case None =>
              Left(ConfigReaderFailures(new ConfigReaderFailure {
                override def description: String = s"Could not find platform: $platform, network: $network"
                override def location: Option[ConfigValueLocation] = None
              }))
          }
        }
        .flatMap {
          case c: TezosConfiguration =>
            loadConfig[Option[TNSContractConfiguration]](namespace = s"tns.$network").map(tns => c.copy(tns = tns))
          case c => Right(c)
        }

    /**
      * Reads a specific entry in the configuration file, to create a valid akka-http client host-pool configuration
      *
      * @param namespace the path where the custom configuration will be searched-for
      */
    def loadAkkaStreamingClientConfig(
        namespace: String
    ): Either[ConfigReaderFailures, HttpStreamingConfiguration] =
      // this is where akka searches for the config entry for host connection pool
      loadConfigForEntryPath(namespace, "akka.http.host-connection-pool").map(HttpStreamingConfiguration)

    private def loadConfigForEntryPath(
        namespace: String,
        referenceEntryPath: String
    ): Either[ConfigReaderFailures, Config] = {
      //read a conseil-specific entry into the expected path for the config
      def loadValidatedConfig(rootConfig: Config): Either[ConfigReaderFailures, Config] =
        Try(
          rootConfig
            .getConfig(namespace)
            .atPath(referenceEntryPath) //puts the config entry where expected by akka
            .withFallback(rootConfig) //adds default values, where not overriden
            .ensuring(
              endConfig =>
                //verifies all expected entries are there
                Try(endConfig.checkValid(rootConfig.getConfig(referenceEntryPath), referenceEntryPath)).isSuccess
            )
        ).toEither.left.map {
          //wraps the error into pureconfig's one
          t =>
            ConfigReaderFailures(ThrowableFailure(t, None))
        }

      //compose the configuration reading outcomes
      for {
        akkaConf <- loadConfig[Config]
        validatedConfig <- loadValidatedConfig(akkaConf)
      } yield validatedConfig
    }
  }

}
