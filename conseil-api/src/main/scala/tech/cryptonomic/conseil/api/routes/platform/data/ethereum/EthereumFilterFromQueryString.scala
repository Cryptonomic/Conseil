package tech.cryptonomic.conseil.api.routes.platform.data.ethereum

import cats.Functor
import cats.syntax.functor._
import endpoints.algebra.JsonEntities
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilter.Sorting
import tech.cryptonomic.conseil.api.routes.platform.data.ApiFilterQueryString
import tech.cryptonomic.conseil.common.util.TupleFlattenUtil.FlattenHigh._
import tech.cryptonomic.conseil.common.util.TupleFlattenUtil._

private[ethereum] trait EthereumFilterFromQueryString extends ApiFilterQueryString { self: JsonEntities =>

  /** Query string functor adding map operation */
  implicit def qsFunctor: Functor[QueryString]

  /** Query params type alias */
  type EthereumQueryParams = (
      Option[Int],
      Set[String],
      Set[String],
      Set[String],
      Option[String],
      Option[Sorting]
  )

  /** Function for extracting query string with query params */
  private def filterQs: QueryString[EthereumQueryParams] = {
    val raw = limit &
          qs[Set[String]]("block_id") &
          qs[Set[String]]("block_hash") &
          qs[Set[String]]("transaction_id") &
          sortBy &
          order

    raw map (flatten(_))
  }

  /** Function for mapping query string to Filter */
  val ethereumQsFilter: QueryString[EthereumFilter] = filterQs.map(EthereumFilter.tupled)

}
