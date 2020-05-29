package tech.cryptonomic.conseil.indexer.config

import com.github.ghik.silencer.silent
import com.typesafe.config.Config
import pureconfig.error.{ConfigReaderFailures, ThrowableFailure}
import pureconfig.generic.auto._
import pureconfig.generic.{EnumCoproductHint, FieldCoproductHint, ProductHint}
import pureconfig.{loadConfig, CamelCase, ConfigFieldMapping, ConfigReader}
import scopt.{OptionParser, Read}
import tech.cryptonomic.conseil.common.config.Platforms._
import tech.cryptonomic.conseil.common.config.{PlatformConfiguration => _, _}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.BlockHash

import scala.util.Try

/** wraps all configuration needed to run Lorre */
trait LorreAppConfig {
  import LorreAppConfig._
  import LorreAppConfig.Loaders._

  /* used by scopt to parse the depth object */
  implicit private val depthRead: Read[Option[Depth]] = Read.reads {
    case "-1" | "all" => Some(Everything)
    case "0" | "new" => Some(Newest)
    case Natural(n) => Some(Custom(n))
    case _ => None
  }

  /* used by scopt to parse the depth object */
  implicit private val blockHashRead: Read[BlockHash] = Read.reads(BlockHash)

  private case class ArgumentsConfig(
      depth: Depth = Newest,
      verbose: Boolean = false,
      headHash: Option[BlockHash] = None,
      platform: String = "",
      network: String = ""
  )

  private val argsParser = new OptionParser[ArgumentsConfig]("lorre") {
    arg[String]("platform")
      .required()
      .action((x, c) => c.copy(platform = x))
      .text("which platform to use")

    arg[String]("network")
      .required()
      .action((x, c) => c.copy(network = x))
      .text("which network to use")

    opt[Option[BlockHash]]('h', "headHash")
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

    //applies convention to uses CamelCase when reading config fields
    @silent("local method hint in method loadApplicationConfiguration is never used")
    implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))
    @silent("local val depthHint in method loadApplicationConfiguration is never used")
    implicit val depthHint: EnumCoproductHint[Depth] = new EnumCoproductHint[Depth]
    @silent("local val chainEventHint in method loadApplicationConfiguration is never used")
    implicit val chainEventHint: FieldCoproductHint[ChainEvent] = new FieldCoproductHint[ChainEvent]("type") {
      override def fieldValue(name: String): String = name.head.toLower +: name.tail
    }

    val loadedConf = for {
      args <- readArgs(commandLineArgs)
      ArgumentsConfig(depth, verbose, headHash, platform, network) = args
      lorre <- loadConfig[LorreConfiguration](namespace = "lorre").map(_.copy(depth = depth, headHash = headHash))
      nodeRequests <- loadConfig[NetworkCallsConfiguration]("lorre")
      platform <- loadPlatformConfiguration(platform, network)
      streamingClient <- loadAkkaStreamingClientConfig(namespace = "akka.streaming-client")
      fetching <- loadConfig[BatchFetchConfiguration](namespace = "lorre.batchedFetches")
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
      printConfigurationError(context = "Lorre application", failures.toList.mkString("\n\n"))
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

  /** Used to pattern match on natural numbers */
  private[config] object Natural {
    def unapply(s: String): Option[Int] = util.Try(s.toInt).filter(_ > 0).toOption
  }

  private[config] object Loaders {

    /*** Reads a specific platform configuration based on given 'platform' and 'network' */
    def loadPlatformConfiguration(
        platform: String,
        network: String
    ): Either[ConfigReaderFailures, PlatformConfiguration] = {
      @silent("local method hint in method loadPlatformConfiguration is never used")
      implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))
      // Note that Lorre process allows to run only one integration per process.
      // Configuration file can contain more than one, thus required parameters.
      BlockchainPlatform.fromString(platform) match {
        case Tezos =>
          for {
            node <- loadConfig[TezosNodeConfiguration](namespace = s"platforms.$platform.$network.node")
            tns <- loadConfig[Option[TNSContractConfiguration]](namespace = s"tns.$network")
          } yield TezosConfiguration(network, node, tns)
        case Bitcoin =>
          for {
            node <- loadConfig[BitcoinNodeConfiguration](namespace = s"platforms.$platform.$network.node")
          } yield BitcoinConfiguration(network, node)
        case UnknownPlatform(_) => Right(UnknownPlatformConfiguration(network))
      }
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
