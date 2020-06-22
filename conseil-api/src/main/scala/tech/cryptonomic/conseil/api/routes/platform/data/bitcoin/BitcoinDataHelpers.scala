package tech.cryptonomic.conseil.api.routes.platform.data.bitcoin

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import akka.util.ByteString
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataHelpers
import tech.cryptonomic.conseil.common.bitcoin.Tables
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._

trait BitcoinDataHelpers extends BitcoinDataEndpoints with ApiDataHelpers {

  import io.circe._
  import io.circe.syntax._
  import tech.cryptonomic.conseil.api.routes.platform.data.CsvConversions._
  import tech.cryptonomic.conseil.common.util.Conversion.Syntax._

  /** Function validating request for the query endpoint */
  override def validated[A](
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

  implicit override val fieldSchema: JsonSchema[Field] = fieldJsonSchema(formattedFieldSchema)

  /** Represents the function, that is going to encode the blockchain specific data types */
  override protected def customAnyEncoder: PartialFunction[Any, Json] = {
    case x: Tables.BlocksRow => x.asJson(blocksRowSchema.encoder)
  }

}
