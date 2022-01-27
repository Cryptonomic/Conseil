package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import endpoints4s.algebra
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilterQueryString

/** Trait containing helper functions which are necessary for parsing query parameter strings as Filter  */
private[tezos] trait TezosFilterFromQueryString extends ApiFilterQueryString { self: algebra.JsonEntities =>

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
  private def filterQs: QueryString[TezosQueryParams] =
    limit &
        qs[Set[String]]("block_id") &
        qs[Set[Int]]("block_level") &
        qs[Set[String]]("block_netid") &
        qs[Set[String]]("block_protocol") &
        qs[Set[String]]("operation_id") &
        qs[Set[String]]("operation_source") &
        qs[Set[String]]("operation_destination") &
        qs[Set[String]]("operation_participant") &
        qs[Set[String]]("operation_kind") &
        qs[Set[String]]("account_id") &
        qs[Set[String]]("account_manager") &
        qs[Set[String]]("account_delegate") &
        sortBy &
        order

  /** Function for mapping query string to Filter */
  val tezosQsFilter: QueryString[TezosFilter] =
    filterQs.xmap(TezosFilter.tupled)(
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
