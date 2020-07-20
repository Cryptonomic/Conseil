package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import cats.Functor
import cats.syntax.functor._
import endpoints.algebra
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilterQueryString
import tech.cryptonomic.conseil.common.util.TupleFlattenUtil.FlattenHigh._
import tech.cryptonomic.conseil.common.util.TupleFlattenUtil._

/** Trait containing helper functions which are necessary for parsing query parameter strings as Filter  */
private[tezos] trait TezosFilterFromQueryString extends ApiFilterQueryString { self: algebra.JsonEntities =>

  /** Query string functor adding map operation */
  implicit def qsFunctor: Functor[QueryString]

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
  private def filterQs: QueryString[TezosQueryParams] = {
    val raw = limit &
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
    raw map (flatten(_))
  }

  /** Function for mapping query string to Filter */
  val tezosQsFilter: QueryString[TezosFilter] = filterQs.map(TezosFilter.tupled)

}
