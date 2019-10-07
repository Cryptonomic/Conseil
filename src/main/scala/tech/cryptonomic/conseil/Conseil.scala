package tech.cryptonomic.conseil

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directive
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import cats.effect.{ContextShift, IO}
import cats.implicits._
import ch.megard.akka.http.cors.scaladsl.CorsDirectives._
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import tech.cryptonomic.conseil.config.{ConseilAppConfig, Security}
import tech.cryptonomic.conseil.directives.EnableCORSDirectives
import tech.cryptonomic.conseil.io.MainOutputs.ConseilOutput
import tech.cryptonomic.conseil.metadata.{AttributeValuesCacheConfiguration, MetadataService, UnitTransformation}
import tech.cryptonomic.conseil.routes._
import tech.cryptonomic.conseil.tezos.{ApiOperations, MetadataCaching, TezosPlatformDiscoveryOperations}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, ExecutionContextExecutor, Future}
import scala.util.{Failure, Success, Try}

object Conseil
    extends App
    with LazyLogging
    with EnableCORSDirectives
    with ConseilAppConfig
    with FailFastCirceSupport
    with ConseilOutput {

  loadApplicationConfiguration(args) match {
    case Left(errors) =>
    //nothing to do
    case Right(loadedConfiguration) =>
      implicit val system: ActorSystem = ActorSystem("conseil-system")
      implicit val materializer: ActorMaterializer = ActorMaterializer()
      implicit val executionContext: ExecutionContextExecutor = system.dispatcher

      val serverBinding = Future
        .fromTry(
          initServices(loadedConfiguration).product(Success(loadedConfiguration))
        )
        .andThen {
          case Failure(error) =>
            logger.error(
              "The server was not started correctly, I failed to create the required Metadata service",
              error
            )
            Await.ready(system.terminate(), 10.seconds)
        }
        .flatMap((runServer _).tupled)

      sys.addShutdownHook {
        serverBinding
          .flatMap(_.unbind().andThen { case _ => logger.info("Server stopped...") })
          .andThen {
            case _ => system.terminate()
          }
          .onComplete(_ => logger.info("We're done here, nothing else to see"))
      }

  }

  /** Reads configuration to setup the fundamental application services
    * @param appConfig static configuration as read from files
    * @return a metadata services object, if all went fine
    */
  def initServices(
      appConfig: Configurations
  )(implicit executionContext: ExecutionContext, system: ActorSystem, mat: ActorMaterializer): Try[MetadataService] = {
    val (server, platforms, securityApi, verbose, metadataOverrides, nautilusCloud) = appConfig

    nautilusCloud.foreach { ncc =>
      system.scheduler.schedule(ncc.delay, ncc.interval)(Security.updateKeys(ncc))
    }

    // This part is a temporary middle ground between current implementation and moving code to use IO
    implicit val contextShift: ContextShift[IO] = IO.contextShift(executionContext)
    val metadataCaching = MetadataCaching.empty[IO].unsafeRunSync()

    lazy val transformation = new UnitTransformation(metadataOverrides)
    lazy val cacheOverrides = new AttributeValuesCacheConfiguration(metadataOverrides)

    lazy val tezosPlatformDiscoveryOperations =
      TezosPlatformDiscoveryOperations(
        ApiOperations,
        metadataCaching,
        cacheOverrides,
        server.cacheTTL,
        server.highCardinalityLimit
      )(
        executionContext,
        contextShift
      )

    tezosPlatformDiscoveryOperations.init().onComplete {
      case Failure(exception) => logger.error("Pre-caching metadata failed", exception)
      case Success(_) => logger.info("Pre-caching successful!")
    }

    tezosPlatformDiscoveryOperations.initAttributesCache.onComplete {
      case Failure(exception) => logger.error("Pre-caching attributes failed", exception)
      case Success(_) => logger.info("Pre-caching attributes successful!")
    }

    // this val is not lazy to force to fetch metadata and trigger logging at the start of the application
    Try(new MetadataService(platforms, transformation, cacheOverrides, tezosPlatformDiscoveryOperations))
  }

  /** Starts the web server
    * @param metadataService the metadata information to build the querying functionality
    * @param appConfig static configuration as read from files
    */
  def runServer(
      metadataService: MetadataService,
      appConfig: Configurations
  )(implicit executionContext: ExecutionContext, system: ActorSystem, mat: ActorMaterializer) = {
    val (server, platforms, securityApi, verbose, metadataOverrides, nautilusCloud) = appConfig

    val tezosDispatcher = system.dispatchers.lookup("akka.tezos-dispatcher")

    lazy val platformDiscovery = PlatformDiscovery(metadataService)
    lazy val data = Data(metadataService, server)(tezosDispatcher)

    val validateApiKey: Directive[Tuple1[String]] = optionalHeaderValueByName("apikey").tflatMap[Tuple1[String]] {
      apiKeyTuple =>
        val apiKey = apiKeyTuple match {
          case Tuple1(apiKey) => apiKey
          case _ => None
        }

        onComplete(securityApi.validateApiKey(apiKey)).flatMap {
          case Success(true) => provide(apiKey.getOrElse(""))
          case _ =>
            complete((Unauthorized, apiKey.fold("Missing API key") { _ =>
              "Incorrect API key"
            }))
        }
    }

    val route = concat(
      pathPrefix("docs") {
        pathEndOrSingleSlash {
          getFromResource("web/index.html")
        }
      },
      pathPrefix("swagger-ui") {
        getFromResourceDirectory("web/swagger-ui/")
      },
      Docs.route,
      cors() {
        enableCORS {
          concat(
            validateApiKey { _ =>
              concat(
                logRequest("Conseil", Logging.DebugLevel) {
                  AppInfo.route
                },
                logRequest("Metadata Route", Logging.DebugLevel) {
                  platformDiscovery.route
                },
                logRequest("Data Route", Logging.DebugLevel) {
                  data.getRoute ~ data.postRoute
                }
              )
            },
            options {
              // Support for CORS pre-flight checks.
              complete("Supported methods : GET and POST.")
            }
          )
        }
      }
    )

    val bindingFuture = Http().bindAndHandle(route, server.hostname, server.port)
    displayInfo(server)
    if (verbose.on) displayConfiguration(platforms)

    bindingFuture
  }

}
