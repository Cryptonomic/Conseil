package tech.cryptonomic.conseil.api.routes.validation

import endpoints.algebra
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryValidationError

/** Trait adding validation for query  */
trait Validation {
  self: algebra.Responses =>

  /** Method for validating query request */
  def validated[A](response: Response[A], invalidDocs: Documentation): Response[Either[List[QueryValidationError], A]]
}
