package tech.cryptonomic.conseil.routes.openapi

/** Trait adding parsing query strings as a lists */
trait QueryStringLists extends endpoints.algebra.Urls {
  /** Method for adding parsing query strings as a lists */
  def qsList[A: QueryStringParam](name: String, docs: Option[String] = None): QueryString[List[A]]
}
