package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.generic.chain.DataTypes.{QueryResponseWithOutput, QueryValidationError}

/** Trait adding validation for query  */
trait Validation {
  self: algebra.Responses =>
  /** Method for validating query request */
  def validated(response: Response[QueryResponseWithOutput], invalidDocs: Documentation): Response[Either[List[QueryValidationError], QueryResponseWithOutput]]
}
