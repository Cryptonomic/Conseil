package tech.cryptonomic.conseil.api.routes.platform

import tech.cryptonomic.conseil.api.config.ConseilConfiguration
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.{BitcoinDataOperations, BitcoinDataRoutes}
import tech.cryptonomic.conseil.common.config.MetadataConfiguration

import scala.concurrent.ExecutionContext

class BitcoinApi(
    metadataService: MetadataService,
    metadataOverrides: MetadataConfiguration,
    server: ConseilConfiguration
)(
    implicit dispatcher: ExecutionContext
) extends Api {

  private val dataOperations = new BitcoinDataOperations()

  override val routes: ApiDataRoutes =
    BitcoinDataRoutes(metadataService, metadataOverrides, dataOperations, server.maxQueryResultSize)

}
