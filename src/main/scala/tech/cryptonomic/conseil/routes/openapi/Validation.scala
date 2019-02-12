package tech.cryptonomic.conseil.routes.openapi

import endpoints.algebra
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.generic.chain.DataTypes.QueryValidationError

trait Validation extends algebra.Responses {
  def validated[A](response: Response[A], invalidDocs: Documentation): Response[Either[List[QueryValidationError], A]]
}
