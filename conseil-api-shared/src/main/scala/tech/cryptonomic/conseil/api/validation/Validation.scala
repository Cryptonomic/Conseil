package tech.cryptonomic.conseil.api.validation

import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryValidationError

/** Object adding validation for query */
object Validation {
  type QueryValidating[A] = Either[List[QueryValidationError], A]
}
