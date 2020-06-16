package tech.cryptonomic.conseil.api.routes.platform

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.api.config.ConseilConfiguration
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.{BitcoinDataOperations, BitcoinDataRoutes}
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryOperations

class BitcoinApi(metadataOverrides: MetadataConfiguration, server: ConseilConfiguration)(
    implicit system: ActorSystem
) extends Api
    with LazyLogging {

  private val apiDispatcher = system.dispatchers.lookup("akka.http.dispatcher")

  private val dataOperations = new BitcoinDataOperations()

  override def dataEndpoint(metadataService: MetadataService): ApiDataRoutes =
    BitcoinDataRoutes(metadataService, metadataOverrides, dataOperations, server.maxQueryResultSize)(apiDispatcher)

  override def discoveryOperations: PlatformDiscoveryOperations = ???
}
