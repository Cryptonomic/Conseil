package tech.cryptonomic.conseil.api.routes.platform

import akka.actor.ActorSystem
import cats.effect.{ContextShift, IO}
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.api.config.ConseilConfiguration
import tech.cryptonomic.conseil.api.metadata.{AttributeValuesCacheConfiguration, MetadataService}
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.{TezosDataOperations, TezosDataRoutes}
import tech.cryptonomic.conseil.api.routes.platform.discovery.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.common.cache.MetadataCaching
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryOperations

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success}

class TezosApi(metadataOverrides: MetadataConfiguration, server: ConseilConfiguration)(
  implicit system: ActorSystem
) extends Api
  with LazyLogging {

  implicit private val dispatcher: ExecutionContext = system.dispatcher
  private val apiDispatcher = system.dispatchers.lookup("akka.http.dispatcher")

  private lazy val cacheOverrides = new AttributeValuesCacheConfiguration(metadataOverrides)

  // This part is a temporary middle ground between current implementation and moving code to use IO
  implicit private val contextShift: ContextShift[IO] = IO.contextShift(dispatcher)
  private lazy val metadataCaching = MetadataCaching.empty[IO].unsafeRunSync()

  private lazy val dataOperations: TezosDataOperations = new TezosDataOperations()

  override lazy val discoveryOperations: PlatformDiscoveryOperations = {
    val operations = TezosPlatformDiscoveryOperations(
      dataOperations,
      metadataCaching,
      cacheOverrides,
      server.cacheTTL,
      server.highCardinalityLimit
    )
    operations.init().onComplete {
      case Failure(exception) => logger.error("Pre-caching metadata failed", exception)
      case Success(_) => logger.info("Pre-caching successful!")
    }

    operations.initAttributesCache().onComplete {
      case Failure(exception) => logger.error("Pre-caching attributes failed", exception)
      case Success(_) => logger.info("Pre-caching attributes successful!")
    }
    operations
  }

  override def dataEndpoint(metadataService: MetadataService): ApiDataRoutes =
    TezosDataRoutes(metadataService, metadataOverrides, dataOperations, server.maxQueryResultSize)(apiDispatcher)
}
