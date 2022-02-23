package tech.cryptonomic.conseil.api.platform.data

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport

import cats.effect.IO

import sttp.tapir.server.ServerEndpoint

/** Collects all of the data endpoints together */
private[data] trait ApiDataRoutes extends ConseilLogSupport {

  /** Represents all routes for method 'POST' */
  // def postRoute: Route

  /** Represents all routes for method 'GET' */
  def getRoute: List[ServerEndpoint[Any, IO]]
}
