package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import endpoints4s.algebra.JsonEntities
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilterQueryString

private[ethereum] trait EthereumFilterFromQueryString extends ApiFilterQueryString { self: JsonEntities =>

  /** Query params type alias */
  type EthereumQueryParams = (
      Option[Int],
      Set[String],
      Set[String],
      Set[String],
      Set[String],
      Option[String],
      Option[Sorting]
  )

  /** Function for extracting query string with query params */
  private def filterQs: QueryString[EthereumQueryParams] =
    limit &
      qs[Set[String]]("block_id") &
      qs[Set[String]]("block_hash") &
      qs[Set[String]]("transaction_id") &
      qs[Set[String]]("account_addresses") &
      sortBy &
      order

  /** Function for mapping query string to Filter */
  val ethereumQsFilter: QueryString[EthereumFilter] =
    filterQs.xmap(EthereumFilter.tupled)(filter =>
      (
        filter.limit,
        filter.blockIds,
        filter.blockHashes,
        filter.transactionHashes,
        filter.accountAddresses,
        filter.sortBy,
        filter.order
      )
    )

}
