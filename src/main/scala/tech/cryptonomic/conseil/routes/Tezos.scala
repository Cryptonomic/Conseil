package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route}
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{ApiOperations, TezosNodeInterface, TezosNodeOperator}
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.{DatabaseUtil, JsonUtil}

import scala.util.{Failure, Success}

/**
  * Tezos-specific routes.
  */
object Tezos extends LazyLogging {

  val dbHandle = DatabaseUtil.db

  val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)

  // Directive for extracting out filter parameters for most GET operations.
  val gatherConseilFilter: Directive[Tuple1[Filter]] = parameters(
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
  ).tflatMap{ tuple =>
    val (limit, block_ids, block_levels, block_chainIDs, block_protocols, op_ids, op_sources, account_ids, account_managers, account_delegates) = tuple
    val filter: Filter = Filter(
      limit = limit, blockIDs = Some(block_ids.toSet),
      levels = Some(block_levels.toSet),
      chainIDs = Some(block_chainIDs.toSet),
      protocols = Some(block_protocols.toSet),
      operationIDs = Some(op_ids.toSet),
      operationSources = Some(op_sources.toSet),
      accountIDs = Some(account_ids.toSet),
      accountManagers = Some(account_managers.toSet),
      accountDelegates = Some(account_delegates.toSet)
    )
    provide(filter)
  }

  // Directive for gathering account information for most POST operations.
  val gatherKeyInfo: Directive[Tuple1[KeyStore]] = parameters(
    "publicKey".as[String],
    "privateKey".as[String],
    "publicKeyHash".as[String]
  ).tflatMap{ tuple =>
    val (publicKey, privateKey, publicKeyHash) = tuple
    val keyStore = KeyStore(publicKey = publicKey, privateKey = privateKey, publicKeyHash = publicKeyHash)
    provide(keyStore)
  }

  val route: Route = pathPrefix(Segment) { network =>
    get {
      gatherConseilFilter{ filter =>
        pathPrefix("blocks") {
          pathEnd {
            ApiOperations.fetchBlocks(filter) match {
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
        } ~ pathPrefix("accounts") {
          pathEnd {
            ApiOperations.fetchAccounts(filter) match {
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
          pathEnd {
            ApiOperations.fetchOperationGroups(filter) match {
              case Success(operationGroups) => complete(JsonUtil.toJson(operationGroups))
              case Failure(e) => failWith(e)
            }
          } ~ path(Segment) { operationGroupId =>
            ApiOperations.fetchOperationGroup(operationGroupId) match {
              case Success(operationGroup) => complete(JsonUtil.toJson(operationGroup))
              case Failure(e) => failWith(e)
            }
          }
        }
      }
    } ~ post {
      path("generate_identity") {
        nodeOp.createIdentity() match {
          case Success(identity) => complete(JsonUtil.toJson(identity))
          case Failure(e) => failWith(e)
        }
      } ~ gatherKeyInfo { keyStore =>
        path("request_faucet") {
          nodeOp.fundAccountWithFaucet(network, keyStore) match {
            case Success(result) => complete(JsonUtil.toJson(result))
            case Failure(e) => failWith(e)
          }
        } ~  path("originate_account") {
          parameters("amount".as[Float], "spendable".as[Boolean], "delegatable".as[Boolean], "delegate".as[String], "fee".as[Float]) {
            (amount, spendable, delegatable, delegate, fee) =>
            nodeOp.sendOriginationOperation(network, keyStore, amount, delegate, delegatable, spendable, fee) match {
              case Success(result) => complete(JsonUtil.toJson(result))
              case Failure(e) => failWith(e)
            }
          }
        } ~  path("set_delegate") {
          parameters("delegate".as[String], "fee".as[Float]) {
            (delegate, fee) =>
              nodeOp.sendDelegationOperation(network, keyStore, delegate, fee) match {
                case Success(result) => complete(JsonUtil.toJson(result))
                case Failure(e) => failWith(e)
              }
          }
        }~  path("send_transaction") {
          parameters("amount".as[Float], "to".as[String], "fee".as[Float]) {
            (amount, to, fee) =>
              nodeOp.sendTransactionOperation(network, keyStore, to, amount, fee) match {
                case Success(result) => complete(JsonUtil.toJson(result))
                case Failure(e) => failWith(e)
              }
          }
        }
      }
    }
  }
}
