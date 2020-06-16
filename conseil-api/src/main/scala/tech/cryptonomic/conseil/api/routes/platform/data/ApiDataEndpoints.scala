package tech.cryptonomic.conseil.api.routes.platform.data

import endpoints.algebra
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataTypes.ApiQuery
import tech.cryptonomic.conseil.api.routes.validation.Validation
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{QueryResponseWithOutput, QueryValidationError}

trait ApiDataEndpoints extends algebra.JsonSchemaEntities with Validation {
  self: ApiDataJsonSchemas =>

  /** Common path among endpoints */
  val commonPath = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network")

  /** V2 Query endpoint definition */
  def queryEndpoint: Endpoint[((String, String, String), ApiQuery, Option[String]), Option[
    Either[List[QueryValidationError], QueryResponseWithOutput]
  ]] =
    endpoint(
      request = post(
        url = commonPath / segment[String](name = "entity"),
        entity = jsonRequest[ApiQuery](),
        headers = optHeader("apiKey")
      ),
      response = validated(
        response = jsonResponse[QueryResponseWithOutput](docs = Some("Query endpoint")),
        invalidDocs = Some("Can't query - invalid entity!")
      ).orNotFound(Some("Not found")),
      tags = List("Query")
    )

  /** Common method for compatibility queries */
  def compatibilityQuery[A: JsonResponse](endpointName: String): Response[Option[A]] =
    jsonResponse[A](docs = Some(s"Query compatibility endpoint for $endpointName")).orNotFound(Some("Not Found"))

}
