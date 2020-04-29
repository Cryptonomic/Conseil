package tech.cryptonomic.conseil.api

import java.util.UUID

import akka.actor.ActorSystem
import akka.event.Logging
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import cats.effect.{ContextShift, IO}
import ch.megard.akka.http.cors.scaladsl.CorsDirectives.cors
import akka.http.scaladsl.server.Directives._
import tech.cryptonomic.conseil.api.config.ConseilAppConfig.CombinedConfiguration
import tech.cryptonomic.conseil.api.directives.{EnableCORSDirectives, RecordingDirectives, ValidatingDirectives}
import tech.cryptonomic.conseil.api.metadata.{AttributeValuesCacheConfiguration, MetadataService, UnitTransformation}
import tech.cryptonomic.conseil.api.routes.Docs
import tech.cryptonomic.conseil.api.routes.info.AppInfo
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataEndpoints
import tech.cryptonomic.conseil.api.routes.platform.discovery.PlatformDiscovery
import tech.cryptonomic.conseil.api.routes.platform.{Api, TezosApi}
import tech.cryptonomic.conseil.api.security.Security
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.config.Platforms.BlockchainPlatform
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryOperations
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.Platform

import scala.concurrent.ExecutionContext

object ConseilApi {
  def create(config: CombinedConfiguration)(implicit system: ActorSystem): ConseilApi = new ConseilApi(config)
}

class ConseilApi(config: CombinedConfiguration)(implicit system: ActorSystem) extends EnableCORSDirectives {

  private val transformation = new UnitTransformation(config.metadata)
  private val cacheOverrides = new AttributeValuesCacheConfiguration(config.metadata)

  private val apiCache = new ApiCache()

  implicit private val mat: ActorMaterializer = ActorMaterializer()
  implicit private val dispatcher: ExecutionContext = system.dispatcher
  implicit private val contextShift: ContextShift[IO] = IO.contextShift(dispatcher)

  config.nautilusCloud.foreach { ncc =>
    system.scheduler.schedule(ncc.delay, ncc.interval)(Security.updateKeys(ncc))
  }

  // this val is not lazy to force to fetch metadata and trigger logging at the start of the application
  private val metadataService =
    new MetadataService(config.platforms, transformation, cacheOverrides, apiCache.cachedDiscoveryOperations)

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
                          apiCache
                            .cachedDataEndpoints(metadataService)
                            .map {
                              case (platform, routes) =>
                                logRequest(s"$platform Data Route", Logging.DebugLevel) {
                                  routes.getRoute ~ routes.postRoute
                                }
                            }
                            .reduce(_ ~ _)
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

  private class ApiCache {
    private lazy val cache: Map[BlockchainPlatform, Api] = forVisiblePlatforms {
      case Platforms.Tezos => new TezosApi(config.metadata, config.server)
    }

    lazy val cachedDiscoveryOperations: Map[String, PlatformDiscoveryOperations] = cache.map {
      case (key, value) => key.name -> value.discoveryOperations
    }

    lazy val cachedDataEndpoints: MetadataService => Map[String, ApiDataEndpoints] = service =>
      cache.map {
        case (key, value) => key.name -> value.dataEndpoint(service)
      }

    private def forVisiblePlatforms(f: BlockchainPlatform => Api): Map[BlockchainPlatform, Api] =
      transformation
        .overridePlatforms(config.platforms.getPlatforms)
        .map(toBlockchainPlatform)
        .map(platform => platform -> f(platform))
        .toMap

    private def toBlockchainPlatform(v: Platform): BlockchainPlatform = BlockchainPlatform.fromString(v.name)
  }

}
