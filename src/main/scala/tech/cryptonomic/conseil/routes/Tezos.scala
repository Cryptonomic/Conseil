package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Directive, Route, StandardRoute}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.{ApiOperations, TezosNodeInterface, TezosNodeOperator}
import tech.cryptonomic.conseil.tezos.ApiOperations.Filter
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.{DatabaseUtil, JsonUtil}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

/**
  * Tezos-specific routes.
  */
object Tezos extends LazyLogging {

  val dbHandle = DatabaseUtil.db

  val nodeOp: TezosNodeOperator = new TezosNodeOperator(TezosNodeInterface)

  private val conf = ConfigFactory.load

  // Directive for extracting out filter parameters for most GET operations.
  val gatherConseilFilter: Directive[Tuple1[Filter]] = parameters(
    "limit".as[Int].?,
    "block_id".as[String].*,
    "block_level".as[Int].*,
    "block_netid".as[String].*,
    "block_protocol".as[String].*,
    "operation_id".as[String].*,
    "operation_source".as[String].*,
    "operation_destination".as[String].*,
    "account_id".as[String].*,
    "account_manager".as[String].*,
    "account_delegate".as[String].*,
    "operation_kind".as[String].*,
    "sort_by".as[String].?,
    "order".as[String].?
  ).tflatMap{ tuple =>
    val (limit, block_ids, block_levels, block_chainIDs, block_protocols, op_ids, op_sources, op_destinations, account_ids, account_managers, account_delegates, operation_kind, sort_by, order) = tuple
    val filter: Filter = Filter(limit = limit, blockIDs = Some(block_ids.toSet), levels = Some(block_levels.toSet), chainIDs = Some(block_chainIDs.toSet), protocols = Some(block_protocols.toSet), operationGroupIDs = Some(op_ids.toSet), operationSources = Some(op_sources.toSet), operationDestinations = Some(op_destinations.toSet), operationKinds = Some(operation_kind.toSet), accountIDs = Some(account_ids.toSet), accountManagers = Some(account_managers.toSet), accountDelegates = Some(account_delegates.toSet), sortBy = sort_by, order = order)
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

  def endRoute(jsonTarget: Future[Any]): StandardRoute =
    complete(jsonTarget.map(tableRow => JsonUtil.toJson(tableRow)))

  val route: Route = pathPrefix(Segment) { network =>
   // validate(conf.hasPath(s"platform.tezos.$network"), s"Network not supported, current options include: 'zeronet'") {
      get {
        validate(conf.hasPath(s"platforms.tezos.$network"), s"$network not supported, current options include: 'zeronet'") {
        gatherConseilFilter { filter =>
          pathPrefix("blocks") {
            pathEnd {
              endRoute(ApiOperations.fetchBlocks(filter))
            } ~ path("head") {
              endRoute(ApiOperations.fetchLatestBlock())
            } ~ path(Segment) { blockId =>
              endRoute(ApiOperations.fetchBlock(blockId))
            }
          } ~ pathPrefix("accounts") {
            pathEnd {
              endRoute(ApiOperations.fetchAccounts(filter))
            } ~ path(Segment) { accountId =>
              endRoute(ApiOperations.fetchAccount(accountId))
            }
          } ~ pathPrefix("operation_groups") {
            pathEnd {
              endRoute(ApiOperations.fetchOperationGroups(filter))
            } ~ path(Segment) { operationGroupId =>
              endRoute(ApiOperations.fetchOperationGroup(operationGroupId))
            }
          } ~ pathPrefix("operations") {
            pathEnd {
              endRoute(ApiOperations.fetchOperations(filter))
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
          path("originate_account") {
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
    //}
  }
}
