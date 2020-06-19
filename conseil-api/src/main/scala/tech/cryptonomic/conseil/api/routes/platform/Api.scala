package tech.cryptonomic.conseil.api.routes.platform

import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataRoutes

trait Api {
  def routes: ApiDataRoutes
}
