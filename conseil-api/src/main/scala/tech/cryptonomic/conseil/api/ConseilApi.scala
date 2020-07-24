package tech.cryptonomic.conseil.api

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.effect.{ContextShift, IO}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.api.ConseilApi.NoNetworkEnabledError
import tech.cryptonomic.conseil.api.config.ConseilAppConfig.CombinedConfiguration
import tech.cryptonomic.conseil.api.directives.{EnableCORSDirectives, RecordingDirectives, ValidatingDirectives}
import tech.cryptonomic.conseil.api.metadata.{AttributeValuesCacheConfiguration, MetadataService, UnitTransformation}
import tech.cryptonomic.conseil.api.routes.Docs
import tech.cryptonomic.conseil.api.routes.info.AppInfo
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.{BitcoinDataOperations, BitcoinDataRoutes}
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.{TezosDataOperations, TezosDataRoutes}
import tech.cryptonomic.conseil.api.routes.platform.discovery.{GenericPlatformDiscoveryOperations, PlatformDiscovery}
import tech.cryptonomic.conseil.api.security.Security
import tech.cryptonomic.conseil.common.cache.MetadataCaching
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.config.Platforms.BlockchainPlatform
import tech.cryptonomic.conseil.common.sql.DatabaseMetadataOperations
import tech.cryptonomic.conseil.common.util.DatabaseUtil

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

object ConseilApi {

  /** Exception, which is thrown when no network is enabled in configuration */
  case class NoNetworkEnabledError(message: String) extends Exception(message)

  /** Creates Conseil API based on a given configuration */
  def create(config: CombinedConfiguration)(implicit system: ActorSystem): ConseilApi = new ConseilApi(config)
}

class ConseilApi(config: CombinedConfiguration)(implicit system: ActorSystem)
    extends EnableCORSDirectives
    with LazyLogging {

  private val transformation = new UnitTransformation(config.metadata)
  private val cacheOverrides = new AttributeValuesCacheConfiguration(config.metadata)

  implicit private val mat: ActorMaterializer = ActorMaterializer()
  implicit private val dispatcher: ExecutionContext = system.dispatcher
  implicit private val contextShift: ContextShift[IO] = IO.contextShift(dispatcher)

  config.nautilusCloud.foreach { ncc =>
    system.scheduler.schedule(ncc.delay, ncc.interval)(Security.updateKeys(ncc))
  }

  config.platforms.platforms.foreach { conf =>
    if (conf.enabled)
      logger.info(
        s"Configuring endpoints to expose data for platform: ${conf.platform.name} and network ${conf.network}."
      )
    else
      logger.warn(
        s"Configuration for platform: ${conf.platform.name} and network: ${conf.network} is disabled, skipping..."
      )
  }

  // this val is not lazy to force to fetch metadata and trigger logging at the start of the application
  private val metadataService =
    new MetadataService(config.platforms, transformation, cacheOverrides, ApiCache.cachedDiscoveryOperations)

  lazy val route: Route = {
    lazy val platformDiscovery = PlatformDiscovery(metadataService)

    lazy val routeDirectives = new RecordingDirectives
    lazy val securityDirectives = new ValidatingDirectives(config.security)

    import routeDirectives._
    import securityDirectives._

    concat(
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
          implicit val correlationId: UUID = UUID.randomUUID()
          handleExceptions(loggingExceptionHandler) {
            extractClientIP {
              ip =>
                recordResponseValues(ip)(mat, correlationId) {
                  timeoutHandler {
                    concat(
                      validateApiKey { _ =>
                        concat(
                          logRequest("Conseil", Logging.DebugLevel) {
                            AppInfo.route
                          },
                          logRequest("Metadata Route", Logging.DebugLevel) {
                            platformDiscovery.route
                          },
                          concat(ApiCache.cachedDataEndpoints.map {
                            case (platform, routes) =>
                              logRequest(s"$platform Data Route", Logging.DebugLevel) {
                                routes.getRoute ~ routes.postRoute
                              }
                          }.toSeq: _*)
                        )
                      },
                      options {
                        // Support for CORS pre-flight checks.
                        complete("Supported methods : GET and POST.")
                      }
                    )
                  }
                }
            }
          }
        }
      }
    )
  }

  /**
    * Object, which initializes and holds all of the APIs (blockchain-specific endpoints) in the map.
    *
    * Note that only APIs that are visible (configured via metadata config file)
    * will be initialized and eventually exposed.
    */
  private object ApiCache {
    implicit private val dispatcher: ExecutionContext = system.dispatchers.lookup("akka.http.dispatcher")
    private lazy val cache: Map[BlockchainPlatform, ApiDataRoutes] = forVisiblePlatforms {
      case Platforms.Tezos =>
        val operations = new TezosDataOperations()
        TezosDataRoutes(metadataService, config.metadata, operations, config.server.maxQueryResultSize)
      case Platforms.Bitcoin =>
        val operations = new BitcoinDataOperations()
        BitcoinDataRoutes(metadataService, config.metadata, operations, config.server.maxQueryResultSize)
    }

    private val cacheOverrides = new AttributeValuesCacheConfiguration(config.metadata)
    private val metadataCaching = MetadataCaching.empty[IO].unsafeRunSync()
    private val metadataOperations: DatabaseMetadataOperations = new DatabaseMetadataOperations {
      override lazy val dbReadHandle = DatabaseUtil.conseilDb
    }

    lazy val cachedDiscoveryOperations: GenericPlatformDiscoveryOperations =
      new GenericPlatformDiscoveryOperations(
        metadataOperations,
        metadataCaching,
        cacheOverrides,
        config.server.cacheTTL,
        config.server.highCardinalityLimit
      )

    /**
      * Map, that contains list of available `ApiDataRoutes` accessed by platform name.
      *
      * @see `tech.cryptonomic.conseil.common.config.Platforms` to get list of possible platforms.
      */
    lazy val cachedDataEndpoints: Map[String, ApiDataRoutes] =
      cache.map {
        case (key, value) => key.name -> value
      }

    private val visiblePlatforms =
      transformation.overridePlatforms(config.platforms.getPlatforms())

    /**
      * Function, that while used is going to execute function `init` for every platform found (and visible) in configuration.
      *
      * @param init function, which is going to initialize type `T` for given blockchain's platform.
      * @tparam T the type of the entity that is going to be created for every blockchain's platform.
      * @return a map with blockchain's platform and type `T`.
      */
    private def forVisiblePlatforms[T](init: BlockchainPlatform => T): Map[BlockchainPlatform, T] =
      visiblePlatforms.map { platform =>
        val blockchainPlatform = BlockchainPlatform.fromString(platform.name)
        blockchainPlatform -> init(blockchainPlatform)
      }.toMap

    private val visibleNetworks = for {
      platform <- visiblePlatforms
      network <- transformation.overrideNetworks(platform.path, config.platforms.getNetworks(platform.name))
    } yield platform -> network

    if (visibleNetworks.nonEmpty) { // At least one blockchain is enabled
      cachedDiscoveryOperations.init(visibleNetworks).onComplete {
        case Failure(exception) => logger.error("Pre-caching metadata failed", exception)
        case Success(_) => logger.info("Pre-caching successful!")
      }

      cachedDiscoveryOperations.initAttributesCache(visibleNetworks).onComplete {
        case Failure(exception) => logger.error("Pre-caching attributes failed", exception)
        case Success(_) => logger.info("Pre-caching attributes successful!")
      }
    } else {
      throw NoNetworkEnabledError(
        """|Pre-caching can't be done, because there is no enabled block-chain defined.
           | This probably means the application is NOT properly configured.
           | The API needs a platform section to be defined, which lists at least one enabled chain and network.
           | Please double-check the configuration file and start the API service again.
           |""".stripMargin
      )
    }
  }

}
