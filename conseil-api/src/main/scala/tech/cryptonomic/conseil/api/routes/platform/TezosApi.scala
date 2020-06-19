package tech.cryptonomic.conseil.api.routes.platform

import akka.actor.ActorSystem
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.api.config.ConseilConfiguration
import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.api.routes.platform.data.tezos.{TezosDataOperations, TezosDataRoutes}
import tech.cryptonomic.conseil.common.config.MetadataConfiguration

import scala.concurrent.ExecutionContext

class TezosApi(metadataOverrides: MetadataConfiguration, server: ConseilConfiguration)(
    implicit system: ActorSystem
) extends Api
    with LazyLogging {

  implicit private val apiDispatcher: ExecutionContext =
    system.dispatchers.lookup("akka.http.dispatcher")

  private lazy val dataOperations: TezosDataOperations = new TezosDataOperations()

  override def dataEndpoint(metadataService: MetadataService): ApiDataRoutes =
    TezosDataRoutes(metadataService, metadataOverrides, dataOperations, server.maxQueryResultSize)
}
