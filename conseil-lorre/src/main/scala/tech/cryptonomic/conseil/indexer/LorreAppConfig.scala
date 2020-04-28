package tech.cryptonomic.conseil.indexer

import com.github.ghik.silencer.silent
import tech.cryptonomic.conseil.common.config._
import tech.cryptonomic.conseil.common.config.Platforms.PlatformConfiguration
import tech.cryptonomic.conseil.common.util.ConfigUtil.Pureconfig.{loadAkkaStreamingClientConfig, loadPlatformConfiguration}
import pureconfig.{CamelCase, ConfigFieldMapping, ConfigReader, loadConfig}
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.{EnumCoproductHint, ProductHint}
import pureconfig.generic.auto._
import scopt.{OptionParser, Read}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.BlockHash
import pureconfig.generic.FieldCoproductHint

/** wraps all configuration needed to run Lorre */
trait LorreAppConfig {
  import LorreAppConfig._

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
      streamingClient <- loadAkkaStreamingClientConfig(namespace = "akka.tezos-streaming-client")
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
}
