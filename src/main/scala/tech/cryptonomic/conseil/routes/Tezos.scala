package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{ApiOperations, TezosNodeInterface}
import tech.cryptonomic.conseil.util.{DatabaseUtil, JsonUtil}

import scala.util.{Failure, Success}

/**
  * Tezos-specific routes.
  */
object Tezos extends LazyLogging {

  val dbHandle = DatabaseUtil.db

  val route: Route = pathPrefix(Segment) { network =>
    pathPrefix("blocks") {
      get {
        pathEnd {
          parameters("limit".as[Int] ? 100) { (limit) =>
            ApiOperations.fetchBlocks(limit) match {
              case Success(blocks) => complete(JsonUtil.toJson(blocks))
              case Failure(e) => failWith(e)
            }
          }
        } ~ path("head") {
          ApiOperations.fetchLatestBlock() match {
            case Success(block) => complete(JsonUtil.toJson(block))
            case Failure(e) => failWith(e)
          }
        } ~ path(Segment) { blockId =>
          ApiOperations.fetchBlock(blockId) match {
            case Success(block) => complete(JsonUtil.toJson(block))
            case Failure(e) => failWith(e)
          }
        }
      }
    } ~ pathPrefix("accounts") {
      get {
        pathEnd {
          ApiOperations.fetchAccounts() match {
            case Success(accounts) => complete(JsonUtil.toJson(accounts))
            case Failure(e) => failWith(e)
          }
        } ~ path(Segment) { accountId =>
          ApiOperations.fetchAccount(accountId) match {
            case Success(account) => complete(JsonUtil.toJson(account))
            case Failure(e) => failWith(e)
          }
        }
      } ~ pathPrefix("operations") {
        get {
          pathEnd {
            complete(TezosNodeInterface.runQuery(network, "blocks/head/proto/operations"))
          }
        }
      }
    }
  }
}
