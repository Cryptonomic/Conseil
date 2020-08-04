package tech.cryptonomic.conseil.api.routes.platform.data

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.util.ByteString
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.api.routes.platform.data.CsvConversions._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{
  OutputType,
  QueryResponseWithOutput,
  QueryValidationError
}
import tech.cryptonomic.conseil.common.util.Conversion.Syntax._

/** Provides default, real validation method and everything what is related to validation */
object ApiValidation {

  /** Function validating request for the query endpoint */
  def defaultValidated[A](
      response: A => Route,
      invalidDocs: Documentation
  ): Either[List[QueryValidationError], A] => Route = {
    case Left(errors) =>
      complete(StatusCodes.BadRequest -> s"Errors: \n${errors.mkString("\n")}")
    case Right(QueryResponseWithOutput(queryResponse, OutputType.csv)) =>
      complete(HttpEntity.Strict(ContentTypes.`text/csv(UTF-8)`, ByteString(queryResponse.convertTo[String])))
    case Right(QueryResponseWithOutput(queryResponse, OutputType.sql)) =>
      complete(HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString(queryResponse.head("sql").get.toString)))
    case Right(success) =>
      response(success)
  }
}
