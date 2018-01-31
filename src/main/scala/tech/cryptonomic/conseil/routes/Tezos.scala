package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter
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
          parameters(
            "limit".as[Int].?,
            "block_id".as[String].*,
            "block_level".as[Int].*,
            "block_netid".as[String].*,
            "block_protocol".as[String].*,
            "operation_id".as[String].*,
            "operation_source".as[String].*,
            "account_id".as[String].*,
            "account_manager".as[String].*,
            "account_delegate".as[String].*
          ) { (limit, block_ids, block_levels, block_netIDs, block_protocols, op_ids, op_sources, account_ids, account_managers, account_delegates) =>
            val filter: Filter = Filter(
              limit = limit, blockIDs = Some(block_ids.toSet),
              levels = Some(block_levels.toSet),
              netIDs = Some(block_netIDs.toSet),
              protocols = Some(block_protocols.toSet),
              operationIDs = Some(op_ids.toSet),
              operationSources = Some(op_sources.toSet),
              accountIDs = Some(account_ids.toSet),
              accountManagers = Some(account_managers.toSet),
              accountDelegates = Some(account_delegates.toSet)
            )
            ApiOperations.fetchBlocks(filter) match {
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
