package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.tezos.ApiOperations._
import scala.language.higherKinds

object ApiFiltering {

  // Predicates to determine existence of specific type of filter

  //common check
  private def nonEmpty(subFilter: Option[Set[_]]) = subFilter.exists(_.nonEmpty)

  /** Will the filter affect blocks? */
  def isBlockFilter(filter: Filter): Boolean =
    nonEmpty(filter.blockIDs) ||
      nonEmpty(filter.levels) ||
      nonEmpty(filter.chainIDs) ||
      nonEmpty(filter.protocols)

  /** Will the filter affect operation groups? */
  def isOperationGroupFilter(filter: Filter): Boolean =
    nonEmpty(filter.operationGroupIDs) ||
      nonEmpty(filter.operationSources)

  /** Will the filter affect operations? */
  def isOperationFilter(filter: Filter): Boolean =
    nonEmpty(filter.operationKinds) ||
      nonEmpty(filter.operationSources) ||
      nonEmpty(filter.operationDestinations)

  /** Will the filter affect accounts? */
  def isAccountFilter(filter: Filter): Boolean =
    nonEmpty(filter.accountDelegates) ||
      nonEmpty(filter.accountIDs) ||
      nonEmpty(filter.accountManagers)

  /* always get a limit to results */
  def getFilterLimit(filter: Filter): Int = filter.limit.getOrElse(Filter.defaultLimit)

}

/**
  * type class to apply complex filtering operations based on
  * underlying data access
  * @tparam F   An effect that the output will be wrapped into, e.g. [[Try]], [[scala.concurrent.Future]]
  * @tparam OUT The specific output value or each result row
  */
trait ApiFiltering[F[_], OUT] {

  /**
    * Applies filtering
    * @param filter              A [[Filter]] instance
    * @param maxLevelForAccounts How far in the chain we have accounts for
    * @return                    A sequence of filtered results wrapped in the effect `F`
    */
  def apply(filter: Filter)(maxLevelForAccounts: BigDecimal): F[Seq[OUT]]

}
