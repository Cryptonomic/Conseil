package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.ApiOperations._
import scala.language.higherKinds

object ApiFiltering {

  //check on any of the sub-sets
  private def isAnyOfFilter(subFilters: Set[_]*): Boolean =
    subFilters.exists(_.nonEmpty)

  // Predicates to determine existence of specific type of filter

  /** Will the filter affect blocks? */
  def isBlockFilter(filter: Filter): Boolean =
    isAnyOfFilter(
      filter.blockIDs,
      filter.levels,
      filter.chainIDs,
      filter.protocols
    )

  /** Will the filter affect operation groups? */
  def isOperationGroupFilter(filter: Filter): Boolean =
    isAnyOfFilter(
      filter.operationGroupIDs,
      filter.operationSources
    )

  /** Will the filter affect operations? */
  def isOperationFilter(filter: Filter): Boolean =
    isAnyOfFilter(
      filter.operationKinds,
      filter.operationSources,
      filter.operationDestinations
    )

  /** Will the filter affect accounts? */
  def isAccountFilter(filter: Filter): Boolean =
    isAnyOfFilter(
      filter.accountDelegates,
      filter.accountIDs,
      filter.accountManagers
    )

  /* always get a limit to results */
  def getFilterLimit(filter: Filter): Int = filter.limit.getOrElse(Filter.defaultLimit)

}

/**
  * type class to apply complex filtering operations based on
  * underlying data access
  * @tparam F   An effect that the output will be wrapped into, e.g. `scala.util.Try`, `scala.concurrent.Future`
  * @tparam OUT The specific output value or each result row
  */
trait ApiFiltering[F[_], OUT] {

  /**
    * Applies filtering
    * @param filter              A `Filter` instance
    * @param maxLevelForAccounts How far in the chain we have accounts for
    * @return                    A sequence of filtered results wrapped in the effect `F`
    */
  def apply(filter: Filter)(maxLevelForAccounts: BigDecimal): F[Seq[OUT]]

}
