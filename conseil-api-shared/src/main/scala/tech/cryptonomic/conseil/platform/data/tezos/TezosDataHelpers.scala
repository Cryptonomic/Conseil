// package tech.cryptonomic.conseil.platform.data.tezos
// 
// // import akka.http.scaladsl.server.Route
// // import endpoints.akkahttp.server.Endpoints
// // import endpoints.algebra.Documentation
// import tech.cryptonomic.conseil.validation.Validation.QueryValidating
// // import tech.cryptonomic.conseil.platform.data.ApiServerJsonSchema
// import tech.cryptonomic.conseil.platform.data.ApiValidation.defaultValidated
// import tech.cryptonomic.conseil.common.tezos.Tables
// import sttp.tapir.server.ServerEndpoint
// import cats.effect.IO
// 
// /** Trait with helpers needed for data routes */
// private[tezos] class TezosDataHelpers extends TezosDataEndpoints { // with ApiServerJsonSchema {
// 
//   /** Method for validating query request */
//   override def validated[A](response: A => ServerEndpoint[Any, IO]): QueryValidating[A] => ServerEndpoint[Any, IO] =
//     defaultValidated(response)
// 
//   /** Represents the function, that is going to encode the blockchain specific data types */
//   override protected def customAnyEncoder = {
//     case x: Tables.BlocksRow => blocksRowSchema.encoder.encode(x)
//     case x: Tables.AccountsRow => accountsRowSchema.encoder.encode(x)
//     case x: Tables.OperationGroupsRow => operationGroupsRowSchema.encoder.encode(x)
//     case x: Tables.OperationsRow => operationsRowSchema.encoder.encode(x)
//   }
// 
// }
