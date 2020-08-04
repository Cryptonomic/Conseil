package tech.cryptonomic.conseil.api.routes.platform.data

import cats.Functor
import endpoints.algebra
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting._

/** Trait, which provides the most common query strings used for creating filters in API */
trait ApiFilterQueryString { self: algebra.JsonEntities =>

  /** Query string functor adding map operation */
  implicit def qsFunctor: Functor[QueryString]

  /** Query string used for limiting number of returned results from API */
  val limit: QueryString[Option[Int]] = qs("limit")

  /** Query string used for choosing optional sorting field. It will be combined with 'sorting' */
  val sortBy: QueryString[Option[String]] = qs("sort_by")

  /** Query string used for choosing optional sorting order. It will be combined with 'sort_by' */
  val order: QueryString[Option[Sorting]] = qs("sorting")

  implicit lazy val sortingQueryString: QueryStringParam[Sorting] = stringQueryString.xmapPartial(fromString)(asString)
  implicit lazy val optionalSortingQueryString: QueryStringParam[Option[Sorting]] = optionalQueryStringParam[Sorting]
}
