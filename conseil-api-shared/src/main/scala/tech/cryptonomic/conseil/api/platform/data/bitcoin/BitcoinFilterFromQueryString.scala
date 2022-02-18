package tech.cryptonomic.conseil.api.platform.data.bitcoin

import sttp.tapir._

import tech.cryptonomic.conseil.api.ApiFilter.Sorting

/** Trait containing helper functions which are necessary for parsing query parameter strings as Filter */
private[bitcoin] trait BitcoinFilterFromQueryString {

  /** Query params type alias */
  type BitcoinQueryParams = (
      Option[Int],
      List[String],
      List[String],
      List[String],
      List[String],
      Option[String],
      Option[String]
  )

  /** Function for extracting query string with query params */
  private def filterQs: EndpointInput[BitcoinQueryParams] =
    query[Option[Int]]("limit") and
        query[List[String]]("block_id") and
        query[List[String]]("block_hash") and
        query[List[String]]("transaction_id") and
        query[List[String]]("account_addresses") and
        query[Option[String]]("sort_by") and
        query[Option[String]]("order")

  /** Function for mapping query string to Filter */
  val bitcoinQsFilter: EndpointInput[BitcoinFilter] =
    filterQs.map { btcQP =>
      BitcoinFilter(
        btcQP._1,
        btcQP._2.toSet,
        btcQP._3.toSet,
        btcQP._4.toSet,
        btcQP._5.toSet,
        btcQP._6,
        btcQP._7.flatMap(Sorting.fromValidString(_).toEither.toOption)
      )
    }(
      filter =>
        (
          filter.limit,
          filter.blockIds.toList,
          filter.blockHashes.toList,
          filter.transactionIDs.toList,
          filter.accountAddresses.toList,
          filter.sortBy,
          filter.order.map(Sorting.asString)
        )
    )

}
