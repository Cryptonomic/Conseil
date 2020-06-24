package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin.generic

import cats.Functor
import cats.syntax.functor._
import endpoints.algebra.JsonEntities
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting
import tech.cryptonomic.conseil.common.util.TupleFlattenUtil.FlattenHigh._
import tech.cryptonomic.conseil.common.util.TupleFlattenUtil._

/** Trait containing helper functions which are necessary for parsing query parameter strings as Filter */
private[bitcoin] trait BitcoinFilterFromQueryString { self: JsonEntities =>

  /** Query string functor adding map operation */
  implicit def qsFunctor: Functor[QueryString]

  /** Query params type alias */
  type QueryParams = (
      Option[Int],
      List[String],
      List[String],
      Option[String],
      Option[String]
  )

  /** Function for extracting query string with query params */
  private def filterQs: QueryString[QueryParams] = {
    val raw =
      qs[Option[Int]]("limit") &
          qs[List[String]]("block_id") &
          qs[List[String]]("transaction_id") &
          qs[Option[String]]("sort_by") &
          qs[Option[String]]("order")
    raw map (flatten(_))
  }

  /** Function for mapping query string to Filter */
  val qsFilter: QueryString[BitcoinFilter] =
    filterQs.map {
      case (limit, blockIds, transactionIds, sortBy, order) =>
        BitcoinFilter(limit, blockIds.toSet, transactionIds.toSet, sortBy, order.flatMap(Sorting.fromString))
    }

}
