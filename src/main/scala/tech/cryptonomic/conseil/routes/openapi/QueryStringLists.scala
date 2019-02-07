package tech.cryptonomic.conseil.routes.openapi

trait QueryStringLists extends endpoints.algebra.Endpoints {
  def qsList[A: QueryStringParam](name: String, docs: Option[String] = None): QueryString[List[A]]
}
