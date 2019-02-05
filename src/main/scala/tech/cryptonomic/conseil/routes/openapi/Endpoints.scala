package tech.cryptonomic.conseil.routes.openapi

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import endpoints.algebra
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.generic.chain.DataTypes.{ApiQuery, QueryValidationError}
import tech.cryptonomic.conseil.util.RouteHandling

trait Endpoints
  extends algebra.Endpoints
    with algebra.JsonSchemaEntities
    with RouteHandling
    with JsonSchemas
    with Validation {

  //{{protocol}}://{{hostname}}:{{port}}/v2/data/{{platform}}/{{network}}/{{entity}}
  def queryEndpoint: Endpoint[((String, String, String), ApiQuery, String), Option[Either[List[QueryValidationError], List[Map[String, Option[Any]]]]]] =
    endpoint(
      request = post(url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / segment[String](name = "entity"),
        entity = jsonRequest[ApiQuery](),
        headers = header("apiKey", None)),
      response = validated(
        response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query endpoint")),
        invalidDocs = Some("Can't query - invalid entity!")
      ).orNotFound(Some("Not found"))
    )

  def validated[A](response: A => Route, invalidDocs: Documentation): Either[List[QueryValidationError], A] => Route = {
    case Left(errors) =>
      complete(StatusCodes.BadRequest -> s"Errors: \n${errors.mkString("\n")}")
    case Right(success) =>
      response(success)
  }

  //  "limit".as[Int].?,
  //  "block_id".as[String].*,
  //  "block_level".as[Int].*,
  //  "block_netid".as[String].*,
  //  "block_protocol".as[String].*,
  //  "operation_id".as[String].*,
  //  "operation_source".as[String].*,
  //  "operation_destination".as[String].*,
  //  "operation_participant".as[String].*,
  //  "operation_kind".as[String].*,
  //  "account_id".as[String].*,
  //  "account_manager".as[String].*,
  //  "account_delegate".as[String].*,
  //  "sort_by".as[String].?,
  //  "order".as[String].?

  //  def queryBlocksEndpoint = endpoint(
  //    get(path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "blocks"),
  //    ? qs),
  //    jsonResponse[List[Map[String, Option[Any]]]](docs = Some("List of available Pizzas in menu"))
  //  )

}
