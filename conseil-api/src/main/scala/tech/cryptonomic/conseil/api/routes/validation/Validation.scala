package tech.cryptonomic.conseil.api.routes.validation

import endpoints4s.algebra
import endpoints4s.algebra.Documentation
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryValidationError

/** Trait adding validation for query  */
trait Validation {
  self: algebra.Responses =>
  import Validation.QueryValidating

  /** Method for validating query request */
  def validated[A](response: Response[A], invalidDocs: Documentation): Response[QueryValidating[A]]
}

object Validation {
  type QueryValidating[A] = Either[List[QueryValidationError], A]
}
