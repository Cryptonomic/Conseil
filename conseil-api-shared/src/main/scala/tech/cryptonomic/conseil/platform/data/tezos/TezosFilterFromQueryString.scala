package tech.cryptonomic.conseil.platform.data.tezos

import sttp.tapir._
import tech.cryptonomic.conseil.ApiFilter.Sorting
import tech.cryptonomic.conseil.ApiFilterQueryString

/** Trait containing helper functions which are necessary for parsing query parameter strings as Filter  */
// protected
object TezosFilterFromQueryString extends ApiFilterQueryString {

  /** Query params type alias */
  type TezosQueryParams = (
      Option[Int],
      Set[String],
      Set[Int],
      Set[String],
      Set[String],
      Set[String],
      Set[String],
      Set[String],
      Set[String],
      Set[String],
      Set[String],
      Set[String],
      Set[String],
      Option[String],
      Option[Sorting]
  )

  //TODO Before updating endpoints higher than 0.10 we need to decrease number of parameters in single structure first
  /** Function for extracting query string with query params */
  def filterQs =
    limit and
        query[Set[String]]("block_id") and
        query[Set[Int]]("block_level") and
        query[Set[String]]("block_netid") and
        query[Set[String]]("block_protocol") and
        query[Set[String]]("operation_id") and
        query[Set[String]]("operation_source") and
        query[Set[String]]("operation_destination") and
        query[Set[String]]("operation_participant") and
        query[Set[String]]("operation_kind") and
        query[Set[String]]("account_id") and
        query[Set[String]]("account_manager") and
        query[Set[String]]("account_delegate") and
        sortBy and
        order

  /** Function for mapping query string to Filter */
  val tezosQsFilter =
    filterQs.map(TezosFilter.tupled)(
      filter =>
        (
          filter.limit,
          filter.blockIDs,
          filter.levels,
          filter.chainIDs,
          filter.protocols,
          filter.operationGroupIDs,
          filter.operationSources,
          filter.operationDestinations,
          filter.operationParticipants,
          filter.operationKinds,
          filter.accountIDs,
          filter.accountManagers,
          filter.accountDelegates,
          filter.sortBy,
          filter.order
        )
    )

}
