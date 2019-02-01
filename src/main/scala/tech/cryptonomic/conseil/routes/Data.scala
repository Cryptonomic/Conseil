package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import endpoints.akkahttp
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.generic.chain.DataPlatform
import tech.cryptonomic.conseil.generic.chain.DataTypes.ApiQuery
import tech.cryptonomic.conseil.tezos.ApiOperations
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockHash}
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
  extends LazyLogging with RouteHandling with DatabaseApiFiltering with JacksonSupport with akkahttp.server.Endpoints
    with akkahttp.server.JsonSchemaEntities with Endpoints {

  import Tezos._

  /*
   * reuse the same context as the one for ApiOperations calls
   * as long as it doesn't create issues or performance degradation
   */
  override val asyncApiFiltersExecutionContext: ExecutionContext = apiExecutionContext

  val postRoute: Route = queryEndpoint.implementedByAsync {
    case ((platform, network, entity), apiQuery, header) =>
      println(apiQuery)
      ConfigUtil.getNetworks(config, platform).find(_.network == network).flatMap { net =>
        apiQuery.validate(entity).toOption.flatMap { validatedQuery =>
          println(validatedQuery)
          queryProtocolPlatform.queryWithPredicates(platform, entity, validatedQuery)
        }
      }.get

    case x => {
      println(x)
      Future.successful(List.empty)
    }
  }


  /** Route for the POST query */
//  val postRoute: Route =
//    post {
//      commonRoute { (platform, network) =>
//        pathPrefix(Segment) { ent =>
//          validateEntity(ent) {
//            entity(as[ApiQuery]) { apiQuery: ApiQuery =>
//              validateQueryOrBadRequest(ent, apiQuery) { validatedQuery =>
//                completeWithJsonOrNotFound(queryProtocolPlatform.queryWithPredicates(platform, ent, validatedQuery))
//              }
//            }
//          }
//        }
//      }
//    }

//  /** Route for the GET query with query parameters filtering */
//  val getRoute: Route =
//    get {
//      commonRoute {
//        (platform, network) =>
//          gatherConseilFilter { filter =>
//            validate(filter.limit.forall(_ <= 10000), "Cannot ask for more than 10000 entries") {
//              pathPrefix("blocks") {
//                pathEnd {
//                  completeWithJsonOrNotFound(
//                    queryProtocolPlatform.queryWithPredicates(platform, "blocks", filter.toQuery)
//                  )
//                } ~ path("head") {
//                  completeWithJson(
//                    ApiOperations.fetchLatestBlock()
//                  )
//                } ~ path(Segment).as(BlockHash) { blockId =>
//                  complete(
//                    handleNoneAsNotFound(
//                      ApiOperations.fetchBlock(blockId)
//                    )
//                  )
//                }
//              } ~ pathPrefix("accounts") {
//                pathEnd {
//                  completeWithJsonOrNotFound(
//                    queryProtocolPlatform.queryWithPredicates(platform, "accounts", filter.toQuery)
//                  )
//                } ~ path(Segment).as(AccountId) { accountId =>
//                  complete(
//                    handleNoneAsNotFound(
//                      ApiOperations.fetchAccount(accountId)
//                    )
//                  )
//                }
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
//
//      }
//    }

  /** common route builder with platform and network validation */
  private def commonRoute(routeBuilder: (String, String) => Route): Route =
    pathPrefix(Segment) { platform =>
      pathPrefix(Segment) { network =>
        validatePlatformAndNetwork(config, platform, network) {
            routeBuilder(platform, network)
        }
      }
    }

  import io.circe._, io.circe.syntax._


  override implicit def anySchema: JsonSchema[Any] = new JsonSchema[Any] {
    override def encoder: Encoder[Any] = new Encoder[Any] {
      override def apply(a: Any): Json = a match {
        case x: java.lang.String => Json.fromString(x)
        case x: java.lang.Integer => Json.fromInt(x)
        case x: java.sql.Timestamp => Json.fromBigInt(x.getTime)
        case x: java.lang.Boolean => Json.fromBoolean(x)
        case x => Json.fromString(x.toString)
      }
    }

    override def decoder: Decoder[Any] = ???
//      new Decoder[Any] {
//      override def apply(c: HCursor): Result[Any] = {
//        Right(c.toString)
//      }
//    }
  }

//  override implicit def mapRespSchema[A: JsonSchema : Encoder ]: JsonSchema[Map[String, Option[A]]] = new JsonSchema[Map[String, Option[A]]] {
//    override def encoder: Encoder[Map[String, Option[A]]] = new Encoder[Map[String, Option[A]]] {
//      override def apply(a: Map[String, Option[A]]): Json = a.map(x => Json.obj((x._1, x._2 match { case Some(y) => y.asJson case None => Json.Null} ))).asJson
//    }
//
//    override def decoder: Decoder[Map[String, Option[A]]] = ???
//  }

  override implicit def queryResponseSchema: JsonSchema[List[Map[String, Option[Any]]]] =
    new JsonSchema[List[Map[String, Option[Any]]]] {
      implicit def encoderr: Encoder[Any] = new Encoder[Any] {
        override def apply(a: Any): Json = a match {
          case x: java.lang.String => Json.fromString(x)
          case x: java.lang.Integer => Json.fromInt(x)
          case x: java.sql.Timestamp => Json.fromBigInt(x.getTime)
          case x: java.lang.Boolean => Json.fromBoolean(x)
          case x => Json.fromString(x.toString)
        }
      }

      override def encoder: Encoder[List[Map[String, Option[Any]]]] = new Encoder[List[Map[String, Option[Any]]]] {
        override def apply(a: List[Map[String, Option[Any]]]): Json = a.map {
          b =>
            Json.obj(b.map(x => (x._1, x._2 match { case Some(y) => y.asJson case None => Json.Null} )).toList:_*)
        }.asJson
      }

      override def decoder: Decoder[List[Map[String, Option[Any]]]] = ???
    }
}
