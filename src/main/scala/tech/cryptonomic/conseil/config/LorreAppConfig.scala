package tech.cryptonomic.conseil.config

import com.github.ghik.silencer.silent
import tech.cryptonomic.conseil.config.Platforms._
import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig.loadAkkaStreamingClientConfig
import pureconfig.{loadConfig, CamelCase, ConfigFieldMapping, ConfigReader}
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.{EnumCoproductHint, ProductHint}
import pureconfig.generic.auto._
import scopt.{OptionParser, Read}
import tech.cryptonomic.conseil.tezos.TezosTypes.BlockHash
import pureconfig.error.ThrowableFailure
import cats.effect.IO

/** wraps all configuration needed to run Lorre */
trait LorreAppConfig {
  import LorreAppConfig._

  final val LORRE_FAILURE_IGNORE_VAR = "LORRE_FAILURE_IGNORE"

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
      network: String = ""
  )

  private val argsParser = new OptionParser[ArgumentsConfig]("lorre") {
    arg[String]("network")
      .required()
      .action((x, c) => c.copy(network = x))
      .text("which network to use")

    opt[Option[BlockHash]]('h', "headHash")
      .action((x, c) => c.copy(headHash = x))
      .text("from which block to start. Default to actual head")

    opt[Option[Depth]]('d', "depth").validate {
      case Some(depth) => success
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
      commandLineArgs: List[String]
  ): IO[CombinedConfiguration] = {

    import cats.syntax.all._
    import cats.instances.either._

    /** Use the pureconfig convention to handle configuration from the command line */
    def readArgs(args: List[String]): ConfigReader.Result[ArgumentsConfig] =
      argsParser
        .parse(args, ArgumentsConfig())
        .toRight[ConfigReaderFailures](ConfigReaderFailures(new ThrowableFailure(new IllegalArgumentException, None)))

    //applies convention to uses CamelCase when reading config fields
    @silent("local method hint in method loadApplicationConfiguration is never used")
    implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))
    @silent("local val seasonHint in method loadApplicationConfiguration is never used")
    implicit val seasonHint: EnumCoproductHint[Depth] = new EnumCoproductHint[Depth]

    val loadedConf = for {
      args <- readArgs(commandLineArgs)
      ArgumentsConfig(depth, verbose, headHash, network) = args
      lorre <- loadConfig[LorreConfiguration](namespace = "lorre").map(_.copy(depth = depth, headHash = headHash))
      nodeRequests <- loadConfig[NetworkCallsConfiguration]("lorre")
      node <- loadConfig[TezosNodeConfiguration](namespace = s"platforms.tezos.$network.node")
      streamingClient <- loadAkkaStreamingClientConfig(namespace = "akka.tezos-streaming-client")
      fetching <- loadConfig[BatchFetchConfiguration](namespace = "lorre.batchedFetches")
    } yield
      CombinedConfiguration(
        lorre,
        TezosConfiguration(network, node),
        nodeRequests,
        streamingClient,
        fetching,
        VerboseOutput(verbose)
      )

    //if something went wrong, we log and wrap a message into a custom IO error
    loadedConf.leftTraverse { failures =>
      delayedLogConfigurationError(context = "Lorre application", failures.toList.mkString("\n\n")) >>
        LorreConfigurationError("Wrong configuration file or command line options").pure[IO]
    }.flatMap(_.liftTo[IO])

  }

}

object LorreAppConfig {

  final case class LorreConfigurationError(message: String) extends RuntimeException(message)

  /** Collects all different aspects involved for Lorre */
  final case class CombinedConfiguration(
      lorre: LorreConfiguration,
      tezos: TezosConfiguration,
      nodeRequests: NetworkCallsConfiguration,
      streamingClientPool: HttpStreamingConfiguration,
      batching: BatchFetchConfiguration,
      verbose: VerboseOutput
  )
}
