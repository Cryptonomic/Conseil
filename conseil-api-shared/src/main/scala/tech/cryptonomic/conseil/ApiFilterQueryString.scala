package tech.cryptonomic.conseil

import sttp.tapir._
import tech.cryptonomic.conseil.ApiFilter.Sorting
import tech.cryptonomic.conseil.ApiFilter.Sorting._

/** Trait, which provides the most common query strings used for creating filters in API */
trait ApiFilterQueryString {

  /** Query string used for limiting number of returned results from API */
  val limit = query[Option[String]]("limit")

  /** Query string used for choosing optional sorting field. It will be combined with 'sorting' */
  val sortBy = query[Option[Int]]("sort_by")

  /** Query string used for choosing optional sorting order. It will be combined with 'sort_by' */
  import io.circe.generic.semiauto._

  implicit val xd: io.circe.Codec.AsObject[Sorting] = deriveCodec[Sorting]
  val order = query[Option[Sorting]]("sorting")

  implicit lazy val sortingQueryString =
    query[String](_: String)
      .map(fromValidString(_))(_.map(asString(_)).getOrElse("ascending"))

  implicit lazy val optionalSortingQueryString = query[Option[String]](_)

}
