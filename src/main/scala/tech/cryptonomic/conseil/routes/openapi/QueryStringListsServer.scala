package tech.cryptonomic.conseil.routes.openapi
import akka.http.scaladsl.server.Directives._


trait QueryStringListsServer extends QueryStringLists with endpoints.akkahttp.server.Endpoints {
  def qsList[A: QueryStringParam](name: String, docs: Option[String] = None): QueryString[List[A]] =
    new QueryString[List[A]](parameter(name.as[A].*).tmap(_._1.toList))
}
