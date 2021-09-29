package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import endpoints4s.algebra.JsonEntities
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting

/** Trait containing helper functions which are necessary for parsing query parameter strings as Filter */
private[bitcoin] trait BitcoinFilterFromQueryString { self: JsonEntities =>

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
  private def filterQs: QueryString[BitcoinQueryParams] =
    qs[Option[Int]]("limit") &
        qs[List[String]]("block_id") &
        qs[List[String]]("block_hash") &
        qs[List[String]]("transaction_id") &
        qs[List[String]]("account_addresses") &
        qs[Option[String]]("sort_by") &
        qs[Option[String]]("order")

  /** Function for mapping query string to Filter */
  val bitcoinQsFilter: QueryString[BitcoinFilter] =
    filterQs.xmap {
      case (limit, blockIds, blockHashes, transactionIds, accountAddresses, sortBy, order) =>
        BitcoinFilter(
          limit,
          blockIds.toSet,
          blockHashes.toSet,
          transactionIds.toSet,
          accountAddresses.toSet,
          sortBy,
          order.flatMap(Sorting.fromValidString(_).toEither.toOption)
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
