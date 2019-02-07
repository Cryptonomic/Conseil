package tech.cryptonomic.conseil.routes

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpjackson.JacksonSupport
import endpoints.akkahttp
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.db.DatabaseApiFiltering
import tech.cryptonomic.conseil.generic.chain.DataPlatform
import tech.cryptonomic.conseil.generic.chain.DataTypes.{ApiQuery, QueryValidationError}
import tech.cryptonomic.conseil.routes.openapi.{Endpoints, Validation}
import tech.cryptonomic.conseil.tezos.ApiOperations
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

  import shapeless._
  import poly._
  import shapeless.ops.tuple.FlatMapper
  import syntax.std.tuple._

  trait LowPriorityFlatten extends Poly1 {
    implicit def default[T] = at[T](Tuple1(_))
  }
  object flatten extends LowPriorityFlatten {
    implicit def caseTuple[P <: Product](implicit fm: FlatMapper[P, flatten.type]) =
      at[P](_.flatMap(flatten))
  }

  val blocksRoute: Route = blocksEndpoint.implementedByAsync {
    case ((platform, network, cc), apiKey) => {
      val ccc = flatten(cc)
      val query = (ApiOperations.Filter.readParams _).tupled
      val sth = query(ccc)
      val sth2 = sth.toQuery
      optionFutureOps {
        ConfigUtil.getNetworks(config, platform).find(_.network == network).flatMap { _ =>
          queryProtocolPlatform.queryWithPredicates(platform, "blocks", sth2)
        }
      }
    }
  }

  private def optionFutureOps[M](x: Option[Future[M]]): Future[Option[M]] =
    Future.sequence(Option.option2Iterable(x)).map(_.headOption)

  private def eitherOptionOps[A, B](x: Either[A, Option[B]]): Option[Either[A, B]] = x match {
    case Left(value) => Some(Left(value))
    case Right(Some(value)) => Some(Right(value))
    case Right(None) => None
  }


//  /** common route builder with platform and network validation */
//  private def commonRoute(routeBuilder: (String, String) => Route): Route =
//    pathPrefix(Segment) { platform =>
//      pathPrefix(Segment) { network =>
//        validatePlatformAndNetwork(config, platform, network) {
//          routeBuilder(platform, network)
//        }
//      }
//    }

  import io.circe._
  import io.circe.syntax._

  def validated[A](response: A => Route, invalidDocs: Documentation): Either[List[QueryValidationError], A] => Route = {
    case Left(errors) =>
      complete(StatusCodes.BadRequest -> s"Errors: \n${errors.mkString("\n")}")
    case Right(success) =>
      response(success)
  }

  def anyEncoder: Encoder[Any] = (a: Any) => a match {
    case x: java.lang.String => Json.fromString(x)
    case x: java.lang.Integer => Json.fromInt(x)
    case x: java.sql.Timestamp => Json.fromBigInt(x.getTime)
    case x: java.lang.Boolean => Json.fromBoolean(x)
    case x => Json.fromString(x.toString)
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
}
