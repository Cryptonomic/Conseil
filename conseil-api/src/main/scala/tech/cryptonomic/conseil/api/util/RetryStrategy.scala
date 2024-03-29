package tech.cryptonomic.conseil.api.util

import tech.cryptonomic.conseil.api.ConseilApi.NoNetworkEnabledError

object RetryStrategy {

  /** Method which defines custom strategy, when retry mechanism should not continue
    *
    * Currently retry stops when:
    * - there is no network enabled in the configuration
    */
  val retryGiveUpStrategy: Throwable => Boolean = {
    case _: NoNetworkEnabledError => true
    case _ => false
  }

}
