package tech.cryptonomic.conseil.config

import akka.actor.ActorSystem
import com.github.ghik.silencer.silent
import tech.cryptonomic.conseil.config.Platforms._

import tech.cryptonomic.conseil.tezos.{ApiOperations, TezosNodeInterface, TezosNodeOperator}
import tech.cryptonomic.conseil.util.DatabaseUtil
import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig.loadAkkaStreamingClientConfig
import tech.cryptonomic.conseil.util.IOUtils.{lift, IOLogging}
import pureconfig.{loadConfig, CamelCase, ConfigFieldMapping, ConfigReader}
import pureconfig.error.ConfigReaderFailures
import pureconfig.generic.{EnumCoproductHint, FieldCoproductHint, ProductHint}
import pureconfig.generic.auto._
import scopt.{OptionParser, Read}
import tech.cryptonomic.conseil.tezos.TezosTypes.BlockHash
import pureconfig.error.ThrowableFailure
import cats.data.{Kleisli, Reader}
import cats.effect.{ContextShift, IO, Resource}
import cats.syntax.all._
import slick.jdbc.PostgresProfile.api.Database

object LorreAppConfig {

  type ConfiguredResources = (CombinedConfiguration, Database, ActorSystem, TezosNodeOperator, ApiOperations)

  final private[LorreAppConfig] val LORRE_FAILURE_IGNORE_VAR = "LORRE_FAILURE_IGNORE"

  final case class ProcessingFailuresPolicy(varName: String, ignore: Boolean)

  final case class LorreConfigurationError(message: String) extends RuntimeException(message)

  /** Collects all different aspects involved for Lorre */
  final case class CombinedConfiguration(
      lorre: LorreConfiguration,
      tezos: TezosConfiguration,
      nodeRequests: NetworkCallsConfiguration,
      streamingClientPool: HttpStreamingConfiguration,
      batching: BatchFetchConfiguration,
      failurePolicy: ProcessingFailuresPolicy,
      verbose: VerboseOutput
  )
}

/** wraps all configuration needed to run Lorre */
trait LorreAppConfig {
  self: IOLogging =>
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
  private val loadApplicationConfiguration: Kleisli[IO, List[String], CombinedConfiguration] = Kleisli {
    commandLineArgs =>
      import cats.instances.either._

      lazy val failuresPolicy = {
        val envConf = sys.env.get(LORRE_FAILURE_IGNORE_VAR)
        ProcessingFailuresPolicy(
          varName = LORRE_FAILURE_IGNORE_VAR,
          ignore = envConf.exists(ignore => ignore.toLowerCase == "true" || ignore.toLowerCase == "yes")
        )
      }

      /** Use the pureconfig convention to handle configuration from the command line */
      def readArgs(args: List[String]): ConfigReader.Result[ArgumentsConfig] =
        argsParser
          .parse(args, ArgumentsConfig())
          .toRight[ConfigReaderFailures](ConfigReaderFailures(new ThrowableFailure(new IllegalArgumentException, None)))

      //applies convention to uses CamelCase when reading config fields
      @silent("""local method hint in value \$anonfun is never used""")
      implicit def hint[T]: ProductHint[T] = ProductHint[T](ConfigFieldMapping(CamelCase, CamelCase))
      @silent("""local val depthHint in value \$anonfun is never used""")
      implicit val depthHint: EnumCoproductHint[Depth] = new EnumCoproductHint[Depth]
      @silent("""local val chainEventHint in value \$anonfun is never used""")
      implicit val chainEventHint = new FieldCoproductHint[ChainEvent]("type") {
        override def fieldValue(name: String): String = name.head.toLower +: name.tail
      }

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
          failuresPolicy,
          VerboseOutput(verbose)
        )

      //if something went wrong, we log and wrap a message into a custom IO error
      loadedConf.leftTraverse { failures =>
        delayedLogConfigurationError(context = "Lorre application", failures.toList.mkString("\n\n")) >>
          LorreConfigurationError("Wrong configuration file or command line options").pure[IO]
      }.flatMap(_.liftTo[IO])

  }

  /** Accepts the main application arguments and the program logic as inputs to produce
    * a complete output
    * @param lorreArgs are the command line arguments
    * @param program is the function that, passed in all needed dependency services, will produce the main output
    * @param contextShift an implict concurrent context needed to setup the needed dependencies to run the application
    */
  protected def useConfiguredResources[R](lorreArgs: List[String])(
      program: ConfiguredResources => IO[R]
  )(implicit contextShift: ContextShift[IO]): IO[R] =
    loadApplicationConfiguration
      .flatMapF(conf => setupResources.run(conf).use { case (db, as, tno, api) => program(conf, db, as, tno, api) })
      .run(lorreArgs)

  /**  Starts from a valid configuration, to build external resources access
    * e.g. Database, rpc node, actor system
    * the allocated values are wrapped as Resources to allow guaranteed release-safety using the "bracket" pattern
    * i.e. providing effectful creation and release operations to be called at the right moment
    */
  protected def setupResources(implicit cs: ContextShift[IO]): Reader[
    CombinedConfiguration,
    Resource[IO, (Database, ActorSystem, TezosNodeOperator, ApiOperations)]
  ] = Reader {
    case CombinedConfiguration(
        lorreConf,
        tezosConf,
        callsConf,
        streamingClientConf,
        batchingConf,
        failuresPolicy,
        verbose
        ) =>
      //database write access
      val db = Resource.fromAutoCloseable(IO(DatabaseUtil.lorreDb)).widen[Database]

      //access to higher level operations on conseil's stored data
      val apiOps = Resource.make(
        acquire = IO(new ApiOperations)
      )(
        release = api =>
          liftLog(_.info("Releasing the conseil API resources")) >>
              IO(api.release())
      )

      //both system and dispatcher are needed for all async operations in code to follow
      val actorSystem = Resource.make(
        acquire = IO(ActorSystem("lorre-system"))
      )(
        release = actorSys =>
          liftLog(_.info("Terminating the Actor System")) >>
              lift(actorSys.terminate()).void
      )

      //access to tezos node rpc
      def nodeRpc(implicit system: ActorSystem) =
        Resource.make(
          acquire = IO(new TezosNodeInterface(tezosConf, callsConf, streamingClientConf))
        )(
          release = rpc =>
            liftLog(_.info("Shutting down the blockchain node interface")) >>
                lift(rpc.shutdown()).void
        )

      //using this as a trick to wrap acquisition and release of the whole resource stack with logs
      def logging =
        Resource.make(
          acquire = liftLog(_.info("Acquiring resources based on config"))
        )(
          release = _ => liftLog(_.info("All things closed"))
        )

      //high-level api to fetch data from the tezos node
      val node = for {
        _ <- logging
        sys <- actorSystem
        rpc <- nodeRpc(sys)
      } yield new TezosNodeOperator(rpc, tezosConf.network, batchingConf)(sys.dispatcher)

      (db, actorSystem, node, apiOps).tupled
  }

}
