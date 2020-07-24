package tech.cryptonomic.conseil.api.routes.platform.data

import cats.Functor
import endpoints.algebra
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting._

trait ApiFilterQueryString { self: algebra.JsonEntities =>

  /** Query string functor adding map operation */
  implicit def qsFunctor: Functor[QueryString]

  val limit: QueryString[Option[Int]] = qs("limit")

  val sortBy: QueryString[Option[String]] = qs("sort_by")

  val order: QueryString[Option[Sorting]] = qs("sorting")

  implicit lazy val sortingQueryString: QueryStringParam[Sorting] = stringQueryString.xmapPartial(fromString)(asString)
  implicit lazy val optionalSortingQueryString: QueryStringParam[Option[Sorting]] = optionalQueryStringParam[Sorting]
}
