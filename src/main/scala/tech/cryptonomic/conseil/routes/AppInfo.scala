package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Route
import endpoints.akkahttp
import tech.cryptonomic.conseil.BuildInfo
import tech.cryptonomic.conseil.routes.openapi.AppInfoEndpoint

/** defines endpoints to expose the currently deployed application information (e.g version, ...) */
object AppInfo extends AppInfoEndpoint with akkahttp.server.Endpoints with akkahttp.server.JsonSchemaEntities {

  /** data type collecting relevant information to expose */
  case class Info(application: String, version: String)

  /** the endpoints to expose application information through http */
  val route: Route = appInfoEndpoint.implementedBy(_ => Info(application = BuildInfo.name, version = BuildInfo.version))
}
