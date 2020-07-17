package tech.cryptonomic.conseil.api.routes.platform.data

import endpoints.algebra
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting._

trait ApiFilterQueryString { self: algebra.JsonEntities =>

  implicit val sortingQueryString: QueryStringParam[Sorting] = stringQueryString.xmapPartial(fromString)(asString)
  implicit val optionalSortingQueryString: QueryStringParam[Option[Sorting]] = optionalQueryStringParam[Sorting]

}
