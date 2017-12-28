package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosNodeOperations

/**
  * Tezos-specific routes.
  */
object Tezos extends LazyLogging {

  val route: Route = pathPrefix(Segment) { network =>
    pathPrefix("blocks") {
      get {
        pathEnd {
          complete(TezosNodeOperations.runQuery(network, "blocks"))
        } ~ path("head") {
          complete(TezosNodeOperations.runQuery(network, "blocks/head"))
        } ~ path(Segment) { blockId =>
          complete(TezosNodeOperations.runQuery(network, s"blocks/$blockId"))
        }
      }
    } ~ pathPrefix("accounts") {
      get {
        pathEnd {
          complete(TezosNodeOperations.runQuery(network, "blocks/head/proto/context/contracts"))
        } ~ path(Segment) { accountId =>
          complete(TezosNodeOperations.runQuery(network, s"blocks/head/proto/context/contracts/$accountId"))
        }
      } ~ pathPrefix("operations") {
        get {
          pathEnd {
            complete(TezosNodeOperations.runQuery(network, "blocks/head/proto/operations"))
          }
        }
      }
    }
  }
}
