package tech.cryptonomic.conseil.util

import akka.http.scaladsl.marshalling.{PredefinedToEntityMarshallers, ToEntityMarshaller, ToResponseMarshallable, ToResponseMarshaller}
import akka.http.scaladsl.model.{MediaTypes, StatusCodes}
import akka.http.scaladsl.server.Directives.{complete, _}
import akka.http.scaladsl.server.{Directive, Directive0, Directive1, StandardRoute}
import tech.cryptonomic.conseil.config.Platforms.PlatformsConfiguration
import tech.cryptonomic.conseil.generic.chain.DataTypes.Query
import tech.cryptonomic.conseil.tezos.TezosPlatformDiscoveryOperations
import tech.cryptonomic.conseil.util.ConfigUtil.getNetworks
import tech.cryptonomic.conseil.util.JsonUtil.{JsonString, toJson}

import scala.concurrent.{ExecutionContext, Future}

/** Trait containing utilities for routes handling and Json serialization */
trait RouteHandling {

  /** Implicit Json marshaller for data serialization */
  implicit protected val jsonMarshaller: ToEntityMarshaller[JsonString] =
    PredefinedToEntityMarshallers.StringMarshaller
      .compose((_: JsonString).json)
      .wrap(MediaTypes.`application/json`)(identity _)

  /**
   * Allow generic handling of optional results, embedded in async computations.
   * In addition to converting any missing result to a NotFound http code, it allows to convert the existing content
   * to something which is marshallable as a response
   * @param operation is the computation that will provide, as soon as available, an optional result
   * @param converter a final conversion function to turn the original T, when available to a marshallable result,
   *        by default the function converts to a [[JsonString]]
   * @param T the type of the possible result of the async computation
   * @param R the final outcome, which must be compatible with an available [[ToResponseMarshaller]]
   */
  protected def handleNoneAsNotFound[T, R: ToResponseMarshaller](operation: => Future[Option[T]], converter: T => R = toJson[T] _)
    (implicit ec: ExecutionContext): Future[ToResponseMarshallable] =
    operation.map {
      case Some(content) => converter(content)
      case None => StatusCodes.NotFound
    }

  /**
    * Directive handling validation errors as bad requests(400)
    * @param fieldQuery field query before validation
    * @return Either validated query or bad request if validation failed
    */
  protected def validateQueryOrBadRequest(fieldQuery: Query): Directive1[Query] = Directive { inner =>
    fieldQuery.validate match {
      case Some(value) => inner(Tuple1(value))
      case None => complete(StatusCodes.BadRequest)
    }
  }

  /** converts the future value to [[JsonString]] and completes the call */
  protected def completeWithJson[T](futureValue: Future[T])(implicit ec: ExecutionContext): StandardRoute =
    complete(futureValue.map(toJson[T]))

  /** converts the Option[future] value to [[JsonString]] and completes the call or completes with NotFound */
  protected def completeWithJsonOrNotFound[T](optionValue: Option[Future[T]])(implicit ec: ExecutionContext): StandardRoute = {
    val response: ToResponseMarshallable = optionValue match {
      case Some(futureValue) => futureValue.map(toJson[T])
      case None => StatusCodes.NotFound
    }
    complete(response)
  }
  /** validates platform and network */
  protected def validatePlatformAndNetwork(config: PlatformsConfiguration, platform: String, network: String): Directive0 = {
    getNetworks(config, platform).find(_.network == network) match {
      case Some(_) => pass
      case None => complete(StatusCodes.NotFound)
    }
  }

  /** validates entity if it exists */
  protected def validateEntity(entity: String): Directive0 = {
    if (TezosPlatformDiscoveryOperations.isEntityValid(entity)) {
      pass
    } else {
      complete(StatusCodes.NotFound)
    }
  }

  /** validates attribute if it exists in the given entity */
  protected def validateAttributes(entity: String, attribute: String): Directive0 = {
    if (TezosPlatformDiscoveryOperations.isAttributeValid(entity, attribute)) {
      pass
    } else {
      complete(StatusCodes.NotFound)
    }
  }
}
