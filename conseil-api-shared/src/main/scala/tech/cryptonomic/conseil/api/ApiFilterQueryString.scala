package tech.cryptonomic.conseil.api

import sttp.tapir._

import tech.cryptonomic.conseil.api.ApiFilter.Sorting
import tech.cryptonomic.conseil.api.ApiFilter.Sorting._

/** Trait, which provides the most common query strings used for creating filters in API */
trait ApiFilterQueryString {

  /** Query string used for limiting number of returned results from API */
  val limit: EndpointInput.Query[Option[Int]] = query[Option[Int]]("limit")

  /** Query string used for choosing optional sorting field. It will be combined with 'sorting' */
  val sortBy: EndpointInput.Query[Option[String]] = query[Option[String]]("sort_by")

  /** Query string used for choosing optional sorting order. It will be combined with 'sort_by' */
  implicit def sortingCodec: Codec[List[String], Option[Sorting], CodecFormat.TextPlain] =
    Codec
      .listHeadOption[String, String, CodecFormat.TextPlain]
      .map(_.flatMap(fromString))(_.map(asString))

  val order: EndpointInput.Query[Option[Sorting]] = query[Option[Sorting]]("sorting")

}
