package tech.cryptonomic.conseil.routes

import java.sql.Timestamp

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import cats.Functor
import endpoints.akkahttp
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.generic.chain.DataTypes.{AnyMap, QueryValidationError}
import tech.cryptonomic.conseil.routes.openapi.{DataEndpoints, QueryStringListsServer, Validation}
import tech.cryptonomic.conseil.tezos.Tables

import scala.concurrent.{ExecutionContext, Future}

trait DataHelpers extends QueryStringListsServer with Validation with akkahttp.server.Endpoints
  with akkahttp.server.JsonSchemaEntities with DataEndpoints {

  import io.circe._
  import io.circe.syntax._

  override def validated[A](response: A => Route, invalidDocs: Documentation): Either[List[QueryValidationError], A] => Route = {
    case Left(errors) =>
      complete(StatusCodes.BadRequest -> s"Errors: \n${errors.mkString("\n")}")
    case Right(success) =>
      response(success)
  }

  protected def optionFutureOps[M](x: Option[Future[M]])(implicit ec: ExecutionContext): Future[Option[M]] =
    Future.sequence(Option.option2Iterable(x)).map(_.headOption)

  protected def eitherOptionOps[A, B](x: Either[A, Option[B]]): Option[Either[A, B]] = x match {
    case Left(value) => Some(Left(value))
    case Right(Some(value)) => Some(Right(value))
    case Right(None) => None
  }

  override implicit def qsFunctor: Functor[QueryString] = new Functor[QueryString] {
    override def map[From, To](f: QueryString[From])(map: From => To): QueryString[To] = new QueryString[To](
      f.directive.map(map)
    )
  }

  def anyEncoder: Encoder[Any] = (a: Any) => a match {
    case x: java.lang.String => Json.fromString(x)
    case x: java.lang.Integer => Json.fromInt(x)
    case x: java.sql.Timestamp => Json.fromBigInt(x.getTime)
    case x: java.lang.Boolean => Json.fromBoolean(x)
    case x: scala.collection.immutable.Vector[Tables.OperationGroupsRow] => x.map(_.asJson(operationGroupsRowSchema.encoder)).asJson
    case x: Tables.BlocksRow => x.asJson(blocksRowSchema.encoder)
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

  override implicit def blocksByHashSchema: JsonSchema[AnyMap] = new JsonSchema[AnyMap] {
    override def encoder: Encoder[AnyMap] = (a: AnyMap) => Json.obj(a.map {
      case (k, v) => (k, v.asJson(anyEncoder))
    }.toList: _*)

    override def decoder: Decoder[AnyMap] = ???
  }

  override implicit def timestampSchema: JsonSchema[Timestamp] = new JsonSchema[Timestamp] {
    override def encoder: Encoder[Timestamp] = (a: Timestamp) => a.getTime.asJson

    override def decoder: Decoder[Timestamp] = ???
  }
}
