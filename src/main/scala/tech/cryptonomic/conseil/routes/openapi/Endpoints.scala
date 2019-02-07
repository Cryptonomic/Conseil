package tech.cryptonomic.conseil.routes.openapi

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.{complete, parameter}
import akka.http.scaladsl.server.Route
import endpoints.algebra
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.generic.chain.DataTypes.{ApiQuery, QueryValidationError}
import tech.cryptonomic.conseil.util.RouteHandling
import akka.http.scaladsl.server.Directives._


trait Endpoints
  extends algebra.Endpoints
    with algebra.JsonSchemaEntities
    with RouteHandling
    with JsonSchemas
    with QueryStringListsServer
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

  def blocksEndpoint: Endpoint[((String, String, (((((((Option[Int], List[String], List[Int]), List[String], List[String]), List[String], List[String]), List[String], List[String]), List[String], List[String]), List[String], List[String]), Option[String], Option[String])), String), Option[List[Map[String, Option[Any]]]]] =
    endpoint(
      request = get(
        url = path / "v2" / "data" / segment[String](name = "platform") / segment[String](name = "network") / "blocks" /? myQueryStringParams,
        headers = header("apiKey")),
      response = jsonResponse[List[Map[String, Option[Any]]]](docs = Some("Query compatibility endpoint")).orNotFound(Some("Not found"))
    )


  val myQueryStringParams =
    optQs[Int]("limit") &
      qsList[String]("blockIDs") &
      qsList[Int]("levels") &
      qsList[String]("chainIDs") &
      qsList[String]("protocols") &
      qsList[String]("operationGroupIDs") &
      qsList[String]("operationSources") &
      qsList[String]("operationDestinations") &
      qsList[String]("operationParticipants") &
      qsList[String]("operationKinds") &
      qsList[String]("accountIDs") &
      qsList[String]("accountManagers") &
      qsList[String]("accountDelegates") &
      optQs[String]("sortBy") &
      optQs[String]("order")

}
