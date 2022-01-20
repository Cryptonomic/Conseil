// package tech.cryptonomic.conseil.platform.data
// 
// import cats.effect.IO
// // import akka.http.scaladsl.model.{ContentTypes, HttpEntity, StatusCodes}
// // import akka.http.scaladsl.server.Directives.complete
// // import akka.http.scaladsl.server.Route
// // import akka.util.ByteString
// import tech.cryptonomic.conseil.validation.Validation.QueryValidating
// import tech.cryptonomic.conseil.platform.data.CsvConversions._
// import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{OutputType, QueryResponseWithOutput}
// import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
// import sttp.tapir.server.ServerEndpoint
// 
// /** Provides default, real validation method and everything what is related to validation */
// object ApiValidation {
// 
//   /** Function validating request for the query endpoint */
//   def defaultValidated[A](response: A => ServerEndpoint[Any, IO]): QueryValidating[A] => ServerEndpoint[Any, IO] = {
//     case Left(errors) =>
//       complete(StatusCodes.BadRequest -> s"Errors: \n${errors.mkString("\n")}")
//     case Right(QueryResponseWithOutput(queryResponse, OutputType.csv)) =>
//       complete(HttpEntity.Strict(ContentTypes.`text/csv(UTF-8)`, ByteString(queryResponse.convertTo[String])))
//     case Right(QueryResponseWithOutput(queryResponse, OutputType.sql)) =>
//       complete(HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString(queryResponse.head("sql").get.toString)))
//     case Right(success) =>
//       response(success)
//   }
// }
