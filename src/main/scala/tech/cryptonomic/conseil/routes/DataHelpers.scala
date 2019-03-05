package tech.cryptonomic.conseil.routes

import java.sql.Timestamp

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.Route
import cats.Functor
import endpoints.akkahttp
import endpoints.algebra.Documentation
import tech.cryptonomic.conseil.generic.chain.DataTypes.{AnyMap, QueryResponse, QueryValidationError}
import tech.cryptonomic.conseil.routes.openapi.{DataEndpoints, QueryStringListsServer, Validation}
import tech.cryptonomic.conseil.tezos.Tables

/** Trait with helpers needed for data routes */
trait DataHelpers extends QueryStringListsServer with Validation with akkahttp.server.Endpoints
  with akkahttp.server.JsonSchemaEntities with DataEndpoints {

  import io.circe._
  import io.circe.syntax._

  /** Function validating request for the query endpoint */
  override def validated[A](response: A => Route, invalidDocs: Documentation): Either[List[QueryValidationError], A] => Route = {
    case Left(errors) =>
      complete(StatusCodes.BadRequest -> s"Errors: \n${errors.mkString("\n")}")
    case Right(success) =>
      response(success)
  }

  /** Function extracting option out of Right */
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

  override implicit def queryResponseSchemaWithCsv: JsonSchema[Either[List[QueryResponse], List[QueryResponse]]] =
    new JsonSchema[Either[List[QueryResponse], List[QueryResponse]]] {
      override def encoder: Encoder[Either[List[QueryResponse], List[QueryResponse]]] = new Encoder[Either[List[QueryResponse], List[QueryResponse]]] {
        override def apply(a: Either[List[QueryResponse], List[QueryResponse]]): Json =  a match {
          case Left(value) => value.asJson
          case Right(value) =>
            (value.headOption.map(_.keys.mkString(",")).getOrElse("") :: value.map { xxx =>
            xxx.values.map {
              case Some(xx) => xx
              case None => "null"
            }.mkString(",")
          }).mkString("\n").asJson
        }
      }

      override def decoder: Decoder[Either[List[QueryResponse], List[QueryResponse]]] = ???
    }

  /** Implementation of JSON encoder for Any */
  def anyEncoder: Encoder[Any] = (a: Any) => a match {
    case x: java.lang.String => Json.fromString(x)
    case x: java.lang.Integer => Json.fromInt(x)
    case x: java.sql.Timestamp => Json.fromLong(x.getTime)
    case x: java.lang.Boolean => Json.fromBoolean(x)
    case x: scala.collection.immutable.Vector[Tables.OperationGroupsRow] => x.map(_.asJson(operationGroupsRowSchema.encoder)).asJson
    case x: Tables.BlocksRow => x.asJson(blocksRowSchema.encoder)
    case x => Json.fromString(x.toString)
  }

  /** JSON schema implementation for Any */
  override implicit def anySchema: JsonSchema[Any] = new JsonSchema[Any] {
    override def encoder: Encoder[Any] = anyEncoder

    override def decoder: Decoder[Any] =
      (c: HCursor) => {
        Right(c.value)
      }
  }

  /** Query response JSON schema implementation */
  override implicit def queryResponseSchema: JsonSchema[List[QueryResponse]] =
    new JsonSchema[List[QueryResponse]] {
      override def encoder: Encoder[List[QueryResponse]] = (a: List[QueryResponse]) =>
        a.map { myMap =>
          Json.obj(myMap.map(field => (field._1, field._2 match {
            case Some(y) => y.asJson(anyEncoder)
            case None => Json.Null
          })).toList: _*)
        }.asJson

      override def decoder: Decoder[List[QueryResponse]] = ???
    }

  /** Blocks by hash JSON schema implementation */
  override implicit def blocksByHashSchema: JsonSchema[AnyMap] = new JsonSchema[AnyMap] {
    override def encoder: Encoder[AnyMap] = (a: AnyMap) => Json.obj(a.map {
      case (k, v) => (k, v.asJson(anyEncoder))
    }.toList: _*)

    override def decoder: Decoder[AnyMap] = ???
  }

  /** Timestamp JSON schema implementation */
  override implicit def timestampSchema: JsonSchema[Timestamp] = new JsonSchema[Timestamp] {
    override def encoder: Encoder[Timestamp] = (a: Timestamp) => a.getTime.asJson

    override def decoder: Decoder[Timestamp] = ???
  }
}
