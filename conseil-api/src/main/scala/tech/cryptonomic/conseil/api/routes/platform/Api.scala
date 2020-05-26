package tech.cryptonomic.conseil.api.routes.platform

import tech.cryptonomic.conseil.api.metadata.MetadataService
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes

trait Api {
  def dataEndpoint(metadataService: MetadataService): ApiDataRoutes
}
