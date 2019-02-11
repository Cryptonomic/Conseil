package tech.cryptonomic.conseil.routes

import java.sql.Timestamp

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import endpoints.algebra.Documentation
import endpoints.{InvariantFunctor, akkahttp}
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.generic.chain.DataPlatform
import tech.cryptonomic.conseil.generic.chain.DataTypes.QueryValidationError
import tech.cryptonomic.conseil.routes.openapi.{Endpoints, QueryStringListsServer, Validation}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
import tech.cryptonomic.conseil.tezos.{ApiOperations, Tables}
import tech.cryptonomic.conseil.util.{ConfigUtil, RouteHandling}

import scala.concurrent.{ExecutionContext, Future}

/** Companion object providing apply implementation */
object Data {
  def apply(config: PlatformsConfiguration)(implicit ec: ExecutionContext): Data = new Data(config, DataPlatform())
}

/**
  * Platform discovery routes.
  *
  * @param queryProtocolPlatform QueryProtocolPlatform object which checks if platform exists and executes query
  * @param apiExecutionContext   is used to call the async operations exposed by the api service
  */
class Data(config: PlatformsConfiguration, queryProtocolPlatform: DataPlatform)(implicit apiExecutionContext: ExecutionContext)
  extends LazyLogging with RouteHandling with DatabaseApiFiltering with FailFastCirceSupport with akkahttp.server.Endpoints
    with akkahttp.server.JsonSchemaEntities with Endpoints with QueryStringListsServer with Validation {

  import cats.implicits._

  /*
   * reuse the same context as the one for ApiOperations calls
   * as long as it doesn't create issues or performance degradation
   */
  override val asyncApiFiltersExecutionContext: ExecutionContext = apiExecutionContext

  /** Route for the POST query */
  val postRoute: Route = queryEndpoint.implementedByAsync {
    case ((platform, network, entity), apiQuery, _) =>
      apiQuery.validate(entity).map { valQuery =>
        optionFutureOps {
          ConfigUtil.getNetworks(config, platform).find(_.network == network).flatMap { _ =>
            queryProtocolPlatform.queryWithPredicates(platform, entity, valQuery)
          }
        }
      }.left.map(Future.successful).bisequence.map(eitherOptionOps)
  }


  val blocksRoute: Route = blocksEndpoint.implementedByAsync {
    case ((platform, network, filter), _) => {
      optionFutureOps {
        ConfigUtil.getNetworks(config, platform).find(_.network == network).flatMap { _ =>
          queryProtocolPlatform.queryWithPredicates(platform, "blocks", filter.toQuery)
        }
      }
    }
  }

  val blocksHeadRoute: Route = blocksHeadEndpoint.implementedByAsync {
    case (platform, network, _) => {
      optionFutureOps {
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          ApiOperations.fetchLatestBlock()
        }
      }.map(_.flatten)
    }
  }

  val blockByHashRoute: Route = blockByHashEndpoint.implementedByAsync {
    case ((platform, network, hash), _) => {
      optionFutureOps {
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          ApiOperations.fetchBlock(BlockHash(hash))
        }
      }.map(_.flatten)
    }
  }


  val accountsRoute: Route = accountsEndpoint.implementedByAsync {
    case ((platform, network, filter), _) => {
      optionFutureOps {
        ConfigUtil.getNetworks(config, platform).find(_.network == network).flatMap { _ =>
          queryProtocolPlatform.queryWithPredicates(platform, "accounts", filter.toQuery)
        }
      }
    }
  }

  val accountByIdRoute: Route = accountByIdEndpoint.implementedByAsync {
    case ((platform, network, accountId), _) =>
      optionFutureOps {
        ConfigUtil.getNetworks(config, platform).find(_.network == network).map { _ =>
          ApiOperations.fetchAccount(AccountId(accountId))
        }
      }.map(_.flatten)
  }


  val getRoutes: Route = concat(
    blocksHeadRoute,
    blockByHashRoute,
    blocksRoute,
    accountByIdRoute,
    accountsRoute
  )


  //              } ~ pathPrefix("operation_groups") {
  //                pathEnd {
  //                  completeWithJsonOrNotFound(
  //                    queryProtocolPlatform.queryWithPredicates(platform, "operation_groups", filter.toQuery)
  //                  )
  //                } ~ path(Segment) { operationGroupId =>
  //                  complete(
  //                    handleNoneAsNotFound(ApiOperations.fetchOperationGroup(operationGroupId))
  //                  )
  //                }
  //              } ~ pathPrefix("operations") {
  //                path("avgFees") {
  //                  complete(
  //                    handleNoneAsNotFound(ApiOperations.fetchAverageFees(filter))
  //                  )
  //                } ~ pathEnd {
  //                  completeWithJsonOrNotFound(
  //                    queryProtocolPlatform.queryWithPredicates(platform, "operations", filter.toQuery)
  //                  )
  //                }
  //              }
  //            }
  //          }

  def validated[A](response: A => Route, invalidDocs: Documentation): Either[List[QueryValidationError], A] => Route = {
    case Left(errors) =>
      complete(StatusCodes.BadRequest -> s"Errors: \n${errors.mkString("\n")}")
    case Right(success) =>
      response(success)
  }

  private def optionFutureOps[M](x: Option[Future[M]]): Future[Option[M]] =
    Future.sequence(Option.option2Iterable(x)).map(_.headOption)

  import io.circe._
  import io.circe.syntax._

  private def eitherOptionOps[A, B](x: Either[A, Option[B]]): Option[Either[A, B]] = x match {
    case Left(value) => Some(Left(value))
    case Right(Some(value)) => Some(Right(value))
    case Right(None) => None
  }

  def anyEncoder: Encoder[Any] = (a: Any) => a match {
    case x: java.lang.String => Json.fromString(x)
    case x: java.lang.Integer => Json.fromInt(x)
    case x: java.sql.Timestamp => Json.fromBigInt(x.getTime)
    case x: java.lang.Boolean => Json.fromBoolean(x)
    case x: scala.collection.immutable.Vector[Tables.OperationGroupsRow] => x.map(_.asJson(operationGroupsRowSchema.encoder)).asJson
    case x: Tables.BlocksRow => x.asJson(blocksRowSchema.encoder)
    case x =>
      println(x.getClass.getName)
      Json.fromString(x.toString)
  }

  override implicit def anySchema: JsonSchema[Any] = new JsonSchema[Any] {
    override def encoder: Encoder[Any] = anyEncoder

    override def decoder: Decoder[Any] =
      (c: HCursor) => {
        Right(c.value)
      }
  }

  override implicit def queryResponseSchema: JsonSchema[List[Map[String, Option[Any]]]] =
    new JsonSchema[List[Map[String, Option[Any]]]] {
      override def encoder: Encoder[List[Map[String, Option[Any]]]] = (a: List[Map[String, Option[Any]]]) =>
        a.map { myMap =>
          Json.obj(myMap.map(field => (field._1, field._2 match {
            case Some(y) => y.asJson(anyEncoder)
            case None => Json.Null
          })).toList: _*)
        }.asJson

      override def decoder: Decoder[List[Map[String, Option[Any]]]] = ???
    }

  override implicit def qsInvFunctor: InvariantFunctor[QueryString] = new InvariantFunctor[QueryString] {
    override def xmap[From, To](f: QueryString[From], map: From => To, contramap: To => From): QueryString[To] = new QueryString[To](
      f.directive.map(map)
    )
  }

  override implicit def blocksByHashSchema: JsonSchema[Map[String, Any]] = new JsonSchema[Map[String, Any]] {
    override def encoder: Encoder[Map[String, Any]] = (a: Map[String, Any]) => Json.obj(a.map {
      case (k, v) => (k, v.asJson(anyEncoder))
    }.toList: _*)

    override def decoder: Decoder[Map[String, Any]] = ???
  }

  override implicit def timestampSchema: JsonSchema[Timestamp] = new JsonSchema[Timestamp] {
    override def encoder: Encoder[Timestamp] = (a: Timestamp) => a.getTime.asJson

    override def decoder: Decoder[Timestamp] = ???
  }
}
