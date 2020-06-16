package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import cats.Functor
import endpoints.algebra

trait BitcoinApiFilterFromQueryString { self: algebra.JsonEntities =>
  import tech.cryptonomic.conseil.common.util.TupleFlattenUtil._
  import FlattenHigh._

  /** Query string functor adding map operation */
  implicit def qsFunctor: Functor[QueryString]

}
