package tech.cryptonomic.conseil.routes.openapi
import akka.http.scaladsl.server.Directives._

/** Trait providing query string list implementation for akka-http server */
trait QueryStringListsServer extends QueryStringLists with endpoints.akkahttp.server.Endpoints {

  /** Implementation of the query string list parsing for query params */
  def qsList[A: QueryStringParam](name: String, docs: Option[String] = None): QueryString[List[A]] =
    new QueryString[List[A]](parameter(name.as[A].*).map(_.toList))
}
