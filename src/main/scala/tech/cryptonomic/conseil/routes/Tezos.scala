package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosUtil

/**
  * Tezos-specific functionality.
  */
object Tezos extends LazyLogging {

  val route = pathPrefix(Segment) { network =>
    pathPrefix("blocks") {
      get {
        pathEnd {
          complete(TezosUtil.runQuery(network, "blocks"))
        } ~ path("head") {
          complete(TezosUtil.runQuery(network, "blocks/head"))
        } ~ path(Segment) { blockId =>
          complete(TezosUtil.runQuery(network, s"blocks/${blockId}"))
        }
      }
    } ~ pathPrefix("accounts") {
      get {
        pathEnd {
          complete(TezosUtil.runQuery(network, "blocks/head/proto/context/contracts"))
        } ~ path(Segment) { accountId =>
          complete(TezosUtil.runQuery(network, s"blocks/head/proto/context/contracts/${accountId}"))
        }
      } ~ pathPrefix("operations") {
        get {
          pathEnd {
            complete(TezosUtil.runQuery(network, "blocks/head/proto/operations"))
          }
        }
      }
    }
  }
}
