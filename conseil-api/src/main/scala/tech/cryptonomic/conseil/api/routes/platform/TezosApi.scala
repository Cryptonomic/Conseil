package tech.cryptonomic.conseil.api.routes.platform

import tech.cryptonomic.conseil.api.config.ConseilConfiguration
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.{TezosDataOperations, TezosDataRoutes}
import tech.cryptonomic.conseil.common.config.MetadataConfiguration

import scala.concurrent.ExecutionContext

class TezosApi(metadataService: MetadataService, metadataOverrides: MetadataConfiguration, server: ConseilConfiguration)(
    implicit dispatcher: ExecutionContext
) extends Api {

  private lazy val dataOperations: TezosDataOperations = new TezosDataOperations()

  override def routes: ApiDataRoutes =
    TezosDataRoutes(metadataService, metadataOverrides, dataOperations, server.maxQueryResultSize)
}
