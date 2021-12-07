package tech.cryptonomic.conseil

import sttp.tapir._
import tech.cryptonomic.conseil.ApiFilter.Sorting
import tech.cryptonomic.conseil.ApiFilter.Sorting._

/** Trait, which provides the most common query strings used for creating filters in API */
trait ApiFilterQueryString {

  /** Query string used for limiting number of returned results from API */
  val limit: EndpointInput.Query[Option[Int]] = query[Option[Int]]("limit")

  /** Query string used for choosing optional sorting field. It will be combined with 'sorting' */
  val sortBy: EndpointInput.Query[Option[String]] = query[Option[String]]("sort_by")

  /** Query string used for choosing optional sorting order. It will be combined with 'sort_by' */
  // import io.circe.generic.semiauto._
  // implicit val sortingCodec: io.circe.Codec.AsObject[Sorting] = deriveCodec[Sorting]

  val order: EndpointInput.Query[Option[Sorting]] = ??? // query[Option[Sorting]]("sorting")

  implicit val sortingQueryString =
    query[String](_: String)
      .map(fromValidString(_))(_.map(asString).getOrElse("ascending")) // FIXME: correct default?

  implicit lazy val optionalSortingQueryString = query[Option[String]](_)

}
