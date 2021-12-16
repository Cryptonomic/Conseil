package tech.cryptonomic.conseil.api.routes.platform.data

import endpoints4s.algebra
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataTypes.ApiQuery
import tech.cryptonomic.conseil.api.routes.validation.Validation
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.QueryResponseWithOutput

/** Trait, which provides default query endpoint and methods used while creating endpoints */
trait ApiDataEndpoints extends algebra.JsonEntitiesFromSchemas with Validation {
  self: ApiDataJsonSchemas =>

  /** Common path among endpoints */
  private def commonPath(platform: String) =
    path / "v2" / "data" / platform / segment[String](name = "network") / segment[String](name = "entity")

  /** V2 Query endpoint definition */
  def queryEndpoint(platform: String): Endpoint[
    (String, String, ApiQuery, Option[String]),
    Option[Validation.QueryValidating[QueryResponseWithOutput]]
  ] =
    endpoint(
      request = post(
        url = commonPath(platform),
        entity = jsonRequest[ApiQuery],
        headers = optRequestHeader("apiKey")
      ),
      response = validated(
        response = ok(jsonResponse[QueryResponseWithOutput], docs = Some("Query endpoint")),
        invalidDocs = Some("Can't query - invalid entity!")
      ).orNotFound(Some("Not found")),
      docs = EndpointDocs(
        tags = List("Query")
      )
    )

  /** Common method for compatibility queries */
  def compatibilityQuery[A: JsonResponse](endpointName: String): Response[Option[A]] =
    ok(jsonResponse[A], docs = Some(s"Query compatibility endpoint for $endpointName")).orNotFound(Some("Not Found"))

}
