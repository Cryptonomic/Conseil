package tech.cryptonomic.conseil.api.routes.platform.data.tezos

import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.util.ByteString
import cats.Functor
import endpoints.akkahttp.server
import endpoints.algebra.Documentation
import io.circe.Decoder.Result
import tech.cryptonomic.conseil.api.routes.validation.Validation
import tech.cryptonomic.conseil.common.generic.chain.DataTypes
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.common.tezos.Tables

/** Trait with helpers needed for data routes */
private[tezos] trait TezosDataHelpers
    extends Validation
    with server.Endpoints
    with server.JsonSchemaEntities
    with TezosDataEndpoints {

  import io.circe._
  import io.circe.syntax._
  import tech.cryptonomic.conseil.api.routes.platform.data.CsvConversions._
  import tech.cryptonomic.conseil.common.util.Conversion.Syntax._

  /** Function validating request for the query endpoint */
  override def validated[A](
      response: A => Route,
      invalidDocs: Documentation
  ): Either[List[QueryValidationError], A] => Route = {
    case Left(errors) =>
      complete(StatusCodes.BadRequest -> s"Errors: \n${errors.mkString("\n")}")
    case Right(QueryResponseWithOutput(queryResponse, OutputType.csv)) =>
      complete(HttpEntity.Strict(ContentTypes.`text/csv(UTF-8)`, ByteString(queryResponse.convertTo[String])))
    case Right(QueryResponseWithOutput(queryResponse, OutputType.sql)) =>
      complete(HttpEntity.Strict(ContentTypes.`text/plain(UTF-8)`, ByteString(queryResponse.head("sql").get.toString)))
    case Right(success) =>
      response(success)
  }

  implicit override val fieldSchema: JsonSchema[Field] = new JsonSchema[DataTypes.Field] {
    override def encoder: Encoder[Field] = new Encoder[Field] {
      override def apply(a: Field): Json = a match {
        case SimpleField(field) => field.asJson
        case ff: FormattedField => ff.asJson(formattedFieldSchema.encoder)
      }
    }

    override def decoder: Decoder[Field] = new Decoder[Field] {
      override def apply(c: HCursor): Result[Field] = c.value match {
        case jsonString if jsonString.isString => Right(SimpleField(jsonString.asString.get))
        case formattedField => formattedField.as[FormattedField](formattedFieldSchema.decoder)
      }
    }
  }

  /** Function extracting option out of Right */
  protected def eitherOptionOps[A, B](x: Either[A, Option[B]]): Option[Either[A, B]] = x match {
    case Left(value) => Some(Left(value))
    case Right(Some(value)) => Some(Right(value))
    case Right(None) => None
  }

  implicit override def qsFunctor: Functor[QueryString] = new Functor[QueryString] {
    override def map[From, To](f: QueryString[From])(map: From => To): QueryString[To] = new QueryString[To](
      f.directive.map(map)
    )
  }

  implicit override def queryResponseSchemaWithOutputType: JsonSchema[QueryResponseWithOutput] =
    new JsonSchema[QueryResponseWithOutput] {
      override def encoder: Encoder[QueryResponseWithOutput] =
        (a: QueryResponseWithOutput) => a.queryResponse.asJson(Encoder.encodeList(queryResponseSchema.encoder))

      override def decoder: Decoder[QueryResponseWithOutput] = ???
    }

  /** Implementation of JSON encoder for Any */
  def anyEncoder: Encoder[Any] =
    (a: Any) =>
      a match {
        case x: java.lang.String => Json.fromString(x)
        case x: java.lang.Integer => Json.fromInt(x)
        case x: java.sql.Timestamp => Json.fromLong(x.getTime)
        case x: java.lang.Boolean => Json.fromBoolean(x)
        case x: scala.collection.immutable.Vector[Any] =>
          x.map(_.asJson(anyEncoder)).asJson //Due to type erasure, a recursive call is made here.
        case x: Tables.BlocksRow => x.asJson(blocksRowSchema.encoder)
        case x: Tables.AccountsRow => x.asJson(accountsRowSchema.encoder)
        case x: Tables.OperationGroupsRow => x.asJson(operationGroupsRowSchema.encoder)
        case x: Tables.OperationsRow => x.asJson(operationsRowSchema.encoder)
        case x: java.math.BigDecimal => Json.fromBigDecimal(x)
        case x => Json.fromString(x.toString)
      }

  /** JSON schema implementation for Any */
  implicit override def anySchema: JsonSchema[Any] = new JsonSchema[Any] {
    override def encoder: Encoder[Any] = anyEncoder

    override def decoder: Decoder[Any] =
      (c: HCursor) => {
        // without this check strings are deserialized in double quotes for example "String" instead of String
        if (c.value.isString) {
          Right(c.value.asString.get)
        } else {
          Right(c.value)
        }
      }
  }

  /** Query response JSON schema implementation */
  implicit override def queryResponseSchema: JsonSchema[QueryResponse] =
    new JsonSchema[QueryResponse] {
      override def encoder: Encoder[QueryResponse] =
        (a: QueryResponse) =>
          Json.obj(
            a.map(
                field =>
                  (field._1, field._2 match {
                    case Some(y) => y.asJson(anyEncoder)
                    case None => Json.Null
                  })
              )
              .toList: _*
          )

      //Thisshouldn't actually be used anywhere, in any case we can go as far as
      //assume that anything in the value is coming from json, but nothing more
      override def decoder: Decoder[QueryResponse] =
        Decoder.decodeMap(
          KeyDecoder.decodeKeyString,
          Decoder.decodeOption(Decoder.decodeJson.map(_.asInstanceOf[Any]))
        )
    }

}
