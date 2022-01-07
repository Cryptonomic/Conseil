package tech.cryptonomic.conseil.platform.data.ethereum

import tech.cryptonomic.conseil.ApiFilter.Sorting
import tech.cryptonomic.conseil.ApiFilterQueryString
import sttp.tapir._

private[ethereum] trait EthereumFilterFromQueryString extends ApiFilterQueryString {

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
  private def filterQs: EndpointInput[EthereumQueryParams] =
    limit and
        query[Set[String]]("block_id") and
        query[Set[String]]("block_hash") and
        query[Set[String]]("transaction_id") and
        query[Set[String]]("account_addresses") and
        sortBy and
        order

  /** Function for mapping query string to Filter */
  def ethereumQsFilter =
    filterQs.map(EthereumFilter.tupled)(
      filter =>
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
