package tech.cryptonomic.conseil.api.routes.validation

import endpoints.algebra
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.AttributesValidationError

/** Trait adding validation for querying attributes  */
trait AttributesQueryValidation {
  self: algebra.Responses =>

  /** Method for validating attribute query requests */
  def validatedAttributes[A](
      response: Response[A],
      invalidDocs: Documentation
  ): Response[Either[List[AttributesValidationError], A]]
}
