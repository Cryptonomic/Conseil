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

  implicit val dispatcher = TezosNodeInterface.system.dispatchers.lookup("tezos-dispatcher")

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
    "operation_participant".as[String].*,
    "account_id".as[String].*,
    "account_manager".as[String].*,
    "account_delegate".as[String].*,
    "operation_kind".as[String].*,
    "sort_by".as[String].?,
    "order".as[String].?
  ).tflatMap{ tuple =>
    val (limit, block_ids, block_levels, block_chainIDs, block_protocols, op_ids, op_sources, op_destinations, op_participants, account_ids, account_managers, account_delegates, operation_kind, sort_by, order) = tuple
    val filter: Filter = Filter(
      limit = limit,
      blockIDs = Some(block_ids.toSet),
      levels = Some(block_levels.toSet),
      chainIDs = Some(block_chainIDs.toSet),
      protocols = Some(block_protocols.toSet),
      operationGroupIDs = Some(op_ids.toSet),
      operationSources = Some(op_sources.toSet),
      operationDestinations = Some(op_destinations.toSet),
      operationParticipants = Some(op_participants.toSet),
      operationKinds = Some(operation_kind.toSet),
      accountIDs = Some(account_ids.toSet),
      accountManagers = Some(account_managers.toSet),
      accountDelegates = Some(account_delegates.toSet),
      sortBy = sort_by, order = order)
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
        validate(filter.limit.isEmpty || (filter.limit.isDefined && (filter.limit.get <= 10000)), s"Cannot ask for more than 10000 entries") {
          pathPrefix("blocks") {
            pathEnd {
              complete(ApiOperations.fetchBlocks(filter) map {
                blocks => JsonUtil.toJson(blocks)
              })
            } ~ path("head") {
              complete(ApiOperations.fetchLatestBlock() map {
                block => JsonUtil.toJson(block)
              })
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
          } ~ pathPrefix("operation_groups") {
            pathEnd {
              ApiOperations.fetchOperationGroups(filter) match {
                case Success(operationGroups) => complete(JsonUtil.toJson(operationGroups))
                case Failure(e) => failWith(e)
              }
            } ~ path(Segment) { operationGroupId =>
              complete(ApiOperations.fetchOperationGroup(operationGroupId) map {
                operationGroup => JsonUtil.toJson(operationGroup)
              })
            }
          } ~ pathPrefix("operations") {
            path("avgFees") {
              ApiOperations.fetchAverageFees(filter) match {
                case Success(fees) => complete(JsonUtil.toJson(fees))
                case Failure(e) => failWith(e)
              }
            } ~ pathEnd {
              ApiOperations.fetchOperations(filter) match {
                case Success(operations) => complete(JsonUtil.toJson(operations))
                case Failure(e) => failWith(e)
              }
            }
          }
        }

      }
    }
  }
}