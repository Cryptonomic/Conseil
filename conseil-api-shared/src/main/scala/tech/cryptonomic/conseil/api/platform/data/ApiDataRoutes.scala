package tech.cryptonomic.conseil.api.platform.data

import cats.effect.IO

import sttp.tapir.server.ServerEndpoint

/** Collects all of the data endpoints together */
private[data] trait ApiDataRoutes {

  /** Represents all routes for method 'POST' */
  // def postRoute: Route

  /** Represents all routes for method 'GET' */
  def getRoute: List[ServerEndpoint[Any, IO]]
}
