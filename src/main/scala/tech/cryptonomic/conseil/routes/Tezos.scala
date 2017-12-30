package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.ApiOperations
import tech.cryptonomic.conseil.util.{DatabaseUtil, JsonUtil}

import scala.util.{Failure, Success}

/**
  * Tezos-specific routes.
  */
object Tezos extends LazyLogging {

  val dbHandle = DatabaseUtil.db
  //val tezosDB = ApiOperations

  val route: Route = pathPrefix(Segment) { network =>
    pathPrefix("blocks") {
      get {
        pathEnd {
          ApiOperations.fetchBlocks() match {
            case Success(blocks) => complete(JsonUtil.toJson(blocks))
            case Failure(e) => failWith(e)
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
      }
    } ~ pathPrefix("operations") {
      get {
        pathEnd {
          ApiOperations.fetchOperationGroups() match {
            case Success(operationGroups) => complete(JsonUtil.toJson(operationGroups))
            case Failure(e) => failWith(e)
          }
        } ~ path(Segment) { operation_group_hash =>
          ApiOperations.fetchOperationGroupWithOperations(operation_group_hash) match {
            case Success(operationGroup) => complete(JsonUtil.toJson(operationGroup))
            case Failure(e) => failWith(e)
          }
        }
      }
    }
  }
}
