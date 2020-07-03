package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import cats.Functor
import cats.syntax.functor._
import endpoints.algebra
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting
import tech.cryptonomic.conseil.common.util.TupleFlattenUtil.FlattenHigh._
import tech.cryptonomic.conseil.common.util.TupleFlattenUtil._

/** Trait containing helper functions which are necessary for parsing query parameter strings as Filter  */
private[tezos] trait TezosFilterFromQueryString { self: algebra.JsonEntities =>

  /** Query string functor adding map operation */
  implicit def qsFunctor: Functor[QueryString]

  /** Query params type alias */
  type TezosQueryParams = (
      Option[Int],
      List[String],
      List[Int],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      List[String],
      Option[String],
      Option[String]
  )

  /** Function for extracting query string with query params */
  private def filterQs: QueryString[TezosQueryParams] = {
    val raw =
      qs[Option[Int]]("limit") &
          qs[List[String]]("block_id") &
          qs[List[Int]]("block_level") &
          qs[List[String]]("block_netid") &
          qs[List[String]]("block_protocol") &
          qs[List[String]]("operation_id") &
          qs[List[String]]("operation_source") &
          qs[List[String]]("operation_destination") &
          qs[List[String]]("operation_participant") &
          qs[List[String]]("operation_kind") &
          qs[List[String]]("account_id") &
          qs[List[String]]("account_manager") &
          qs[List[String]]("account_delegate") &
          qs[Option[String]]("sort_by") &
          qs[Option[String]]("order")
    raw map (flatten(_))
  }

  /** Function for mapping query string to Filter */
  val tezosQsFilter: QueryString[TezosFilter] =
    filterQs.map {
      case (
          limit,
          blockIDs,
          levels,
          chainIDs,
          protocols,
          operationGroupIDs,
          operationSources,
          operationDestinations,
          operationParticipants,
          operationKinds,
          accountIDs,
          accountManagers,
          accountDelegates,
          sortBy,
          order
          ) =>
        TezosFilter(
          limit,
          blockIDs.toSet,
          levels.toSet,
          chainIDs.toSet,
          protocols.toSet,
          operationGroupIDs.toSet,
          operationSources.toSet,
          operationDestinations.toSet,
          operationParticipants.toSet,
          operationKinds.toSet,
          accountIDs.toSet,
          accountManagers.toSet,
          accountDelegates.toSet,
          sortBy,
          order.flatMap(Sorting.fromString)
        )
    }

}
