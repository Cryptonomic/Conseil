package tech.cryptonomic.conseil

import sttp.tapir._
import tech.cryptonomic.conseil.ApiFilter.Sorting
import tech.cryptonomic.conseil.ApiFilter.Sorting._

/** Trait, which provides the most common query strings used for creating filters in API */
trait ApiFilterQueryString { // self: algebra.JsonEntities =>

  /** Query string used for limiting number of returned results from API */
  val limit = query[Option[String]]("limit")

  /** Query string used for choosing optional sorting field. It will be combined with 'sorting' */
  val sortBy = query[Option[Int]]("sort_by")

  /** Query string used for choosing optional sorting order. It will be combined with 'sort_by' */
  import io.circe.generic.semiauto._
  import sttp.tapir._
  // import sttp.tapir.generic.auto._
  // import sttp.tapir.json.circe._
  // import sttp.model.StatusCode
  implicit val xd = deriveCodec[Sorting]
  val order = query[Option[Sorting]]("sorting")

  // implicit lazy val sortingQueryString: QueryStringParam[Sorting] =
  implicit lazy val sortingQueryString =
    query[String](_).map(fromValidString)(asString _)
  // query[String].xmapPartial(fromValidString)(asString)

  // implicit lazy val optionalSortingQueryString: QueryStringParam[Option[Sorting]] =
  implicit lazy val optionalSortingQueryString = query[Option[String]](_)

}
