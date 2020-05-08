package tech.cryptonomic.conseil.common.util

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives
import akka.http.scaladsl.server.{Directive, ExceptionHandler, Route}
import akka.stream.Materializer
import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, IO}
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MDC

/** Utility class for recording responses */
class RecordingDirectives(implicit concurrent: Concurrent[IO]) extends LazyLogging {

  type RequestMap = Map[UUID, RequestValues]
  private val requestInfoMap: MVar[IO, Map[UUID, RequestValues]] =
    MVar.of[IO, RequestMap](Map.empty[UUID, RequestValues]).unsafeRunSync()

  private def requestMapModify[A](
      modify: RequestMap => RequestMap
  )(useValues: RequestValues => A)(implicit correlationId: UUID) =
    for {
      map <- requestInfoMap.take
      _ <- requestInfoMap.put(modify(map))
    } yield useValues(map(correlationId))

  /** Directive adding recorded values to the MDC */
  def recordResponseValues(
      ip: RemoteAddress
  )(implicit materializer: Materializer, correlationId: UUID): Directive[Unit] =
    BasicDirectives.extractRequest.flatMap { request =>
      (for {
        requestMap <- requestInfoMap.take
        value = RequestValues.fromHttpRequestAndIp(request, ip)
        _ <- requestInfoMap.put(requestMap.updated(correlationId, value))
      } yield ()).unsafeRunSync()

      requestMapModify(map => map.updated(correlationId, RequestValues.fromHttpRequestAndIp(request, ip)))(_ => ())
        .unsafeRunSync()

      val response = BasicDirectives.mapResponse { resp =>
        requestMapModify(
          modify = _.filterNot(_._1 == correlationId)
        ) { values =>
          values.logResponse(resp)
        }.unsafeRunAsyncAndForget()
        resp
      }
      response
    }

  /** Custom exception handler with MDC logging */
  def loggingExceptionHandler(implicit correlationId: UUID): ExceptionHandler =
    ExceptionHandler {
      case e: Throwable =>
        val response = HttpResponse(InternalServerError)

        requestMapModify(
          modify = _.filterNot(_._1 == correlationId)
        ) { values =>
          values.logResponse(response)
        }.unsafeRunAsyncAndForget()
        complete(response)
    }

  /** Providing handling of the requests that timed out */
  def timeoutHandler(route: => Route)(implicit correlationId: UUID): Route =
    withRequestTimeoutResponse { _ =>
      val response = HttpResponse(StatusCodes.ServiceUnavailable, entity = HttpEntity("Request timeout"))
      requestMapModify(
        modify = _.filterNot(_._1 == correlationId)
      ) { values =>
        values.logResponse(response)
      }.unsafeRunAsyncAndForget()
      response
    }(route)

  /** Representation of request things to log */
  case class RequestValues(
      httpMethod: String,
      requestBody: String,
      clientIp: String,
      path: String,
      apiVersion: String,
      apiKey: String,
      startTime: Long
  ) {

    /** Logging response with MDC */
    def logResponse(response: HttpResponse): Unit = {
      MDC.put("httpMethod", httpMethod)
      MDC.put("requestBody", requestBody)
      MDC.put("clientIp", clientIp)
      MDC.put("path", path)
      MDC.put("apiVersion", apiVersion)
      MDC.put("apiKey", apiKey)
      val requestEndTime = System.nanoTime()
      val responseTime = requestEndTime - startTime
      MDC.put("responseTime", responseTime.toString)
      MDC.put("responseCode", response.status.intValue().toString)
      logger.info("HTTP request")
      MDC.clear()
    }

  }

  /** Companion object for RequestValues */
  object RequestValues {

    /** Extracts Request values from request context and ip address */
    def fromHttpRequestAndIp(request: HttpRequest, ip: RemoteAddress)(
        implicit materializer: Materializer
    ): RequestValues = {
      import scala.concurrent.duration._
      RequestValues(
        httpMethod = request.method.value,
        requestBody = request.entity.toStrict(1000.millis).value.get.get.data.utf8String,
        clientIp = ip.toOption.map(_.toString).getOrElse("unknown"),
        path = request.uri.path.toString(),
        apiVersion = if (request.uri.path.toString().startsWith("/v2")) "v2" else "v1",
        apiKey = request.headers.find(_.is("apikey")).map(_.value()).getOrElse(""),
        startTime = System.nanoTime()
      )
    }
  }
}
