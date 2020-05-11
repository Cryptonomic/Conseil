package tech.cryptonomic.conseil.api.routes.platform.data

import akka.http.scaladsl.server.Route

/** Collects all of the data endpoints together */
trait ApiDataRoutes {

  /** Represents all routes for method 'POST' */
  def postRoute: Route

  /** Represents all routes for method 'GET' */
  def getRoute: Route
}
