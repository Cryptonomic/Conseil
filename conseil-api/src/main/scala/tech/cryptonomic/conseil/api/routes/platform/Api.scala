package tech.cryptonomic.conseil.api.routes.platform

import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryOperations

trait Api {
  def dataEndpoint(metadataService: MetadataService): ApiDataRoutes
  def discoveryOperations: PlatformDiscoveryOperations
}
