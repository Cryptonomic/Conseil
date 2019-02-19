package tech.cryptonomic.conseil.config

import pureconfig.{CamelCase, ConfigFieldMapping, loadConfig}
import pureconfig.ConfigReader
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.ProductHint
import pureconfig.generic.auto._
import tech.cryptonomic.conseil.config.Platforms._
import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig.loadAkkaStreamingClientConfig

/** wraps all configuration needed to run Lorre */
trait LorreAppConfig {
  import LorreAppConfig._
  import scopt.{OptionParser, Read}

  /* used by scopt to parse the depth object */
  implicit private val depthRead: Read[Option[Depth]] = Read.reads {
    case "-1" | "all" => Some(Everything)
    case "0"  | "new" => Some(Newest)
    case Natural(n) => Some(Custom(n))
    case _ => None
  }

  private case class ArgumentsConfig(val depth: Depth = Newest, verbose: Boolean = false, network: String = "")

  private val argsParser = new OptionParser[ArgumentsConfig]("lorre") {
    arg[String]("network")
      .required()
      .action( (x, c) => c.copy(network = x))
      .text("which network to use")

    opt[Option[Depth]]('d', "depth")
      .validate{
        case Some(depth) => success
        case None => failure("I failed to parse a valid depth from the arguments")
      }.action((depth, c) => c.copy(depth = depth.get))
      .text("how many blocks to synchronize starting with head: use -1 or `all` for everything and 0 or `new` to only get new ones)")

    opt[Unit]('v', "verbose")
      .action((_, c) => c.copy(verbose = true))
      .text("print additional configuration info when the application is launched")

    help('h', "help").text("prints this usage text")
  }

  /** Reads all configuration upstart, will print all errors encountered during loading */
  protected def loadApplicationConfiguration(commandLineArgs: Array[String]): ConfigReader.Result[CombinedConfiguration] = {
    /** Use the pureconfig convention to handle configuration from the command line */
    def readArgs(args: Array[String]): ConfigReader.Result[ArgumentsConfig] =
      argsParser.parse(args, ArgumentsConfig()).toRight[ConfigReaderFailures](sys.exit(1))

    //applies convention to uses CamelCase when reading config fields
    implicit def hint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))

    val loadedConf = for {
      args <- readArgs(commandLineArgs)
      ArgumentsConfig(depth, verbose, network) = args
      lorre <- loadConfig[LorreConfiguration](namespace = "lorre")
      nodeRequests <- loadConfig[NetworkCallsConfiguration]("")
      node <- loadConfig[TezosNodeConfiguration](namespace = s"platforms.tezos.$network.node")
      streamingClient <- loadAkkaStreamingClientConfig(namespace = "akka.tezos-streaming-client")
      fetching <- loadConfig[BatchFetchConfiguration](namespace = "batchedFetches")
    } yield CombinedConfiguration(lorre, TezosConfiguration(network, depth, node), nodeRequests, streamingClient, fetching)

    //something went wrong
    loadedConf.left.foreach {
      failures =>
        printConfigurationError(context = "Lorre application", failures.toList.mkString("\n\n"))
    }
    loadedConf
  }

}

object LorreAppConfig {

  /** Collects all different aspects involved for Lorre */
  final case class CombinedConfiguration(
    lorre: LorreConfiguration,
    tezos: TezosConfiguration,
    nodeRequests: NetworkCallsConfiguration,
    streamingClientPool: HttpStreamingConfiguration,
    batching: BatchFetchConfiguration
  )
}