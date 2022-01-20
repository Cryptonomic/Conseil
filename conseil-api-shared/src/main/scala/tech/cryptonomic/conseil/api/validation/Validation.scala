package tech.cryptonomic.conseil.api.validation

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryValidationError
import org.http4s.Response

/** Trait adding validation for query */
trait Validation {
  // self: algebra.Responses =>
  import Validation.QueryValidating

  /** Method for validating query request */
  // def validated[A](response: Response[A]): Response[QueryValidating[A]]
}

object Validation {
  type QueryValidating[A] = Either[List[QueryValidationError], A]
}
