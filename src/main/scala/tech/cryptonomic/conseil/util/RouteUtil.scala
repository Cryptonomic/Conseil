package tech.cryptonomic.conseil.util

import java.util.UUID

import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.model.{HttpEntity, HttpResponse, RemoteAddress, StatusCodes}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.directives.BasicDirectives
import akka.http.scaladsl.server.{Directive, ExceptionHandler, RequestContext, Route}
import akka.stream.Materializer
import cats.effect.concurrent.MVar
import cats.effect.{Concurrent, IO}
import com.typesafe.scalalogging.LazyLogging
import org.slf4j.MDC

/** Utility class for recording responses */
class RouteUtil(implicit concurrent: Concurrent[IO]) extends LazyLogging {

  private val requestInfoMap: MVar[IO, Map[UUID, RequestValues]] =
    MVar.of[IO, Map[UUID, RequestValues]](Map.empty[UUID, RequestValues]).unsafeRunSync()

  /** Directive adding recorded values to the MDC */
  def recordResponseValues(
      ip: RemoteAddress
  )(implicit materializer: Materializer, correlationId: UUID): Directive[Unit] =
    BasicDirectives.extractRequestContext.flatMap { ctx =>
      (for {
        requestMap <- requestInfoMap.take
        value = RequestValues.fromCtxAndIp(ctx, ip)
        _ <- requestInfoMap.put(requestMap.updated(correlationId, value))
      } yield ()).unsafeRunSync()

      val response = BasicDirectives.mapResponse { resp =>
        (for {
          requestMap <- requestInfoMap.take
          _ <- requestInfoMap.put(requestMap.filterNot(_._1 == correlationId))
        } yield requestMap(correlationId).logResponse(resp)).unsafeRunAsyncAndForget()
        resp
      }
      response
    }

  /** Custom exception handler with MDC logging */
  def loggingExceptionHandler(implicit correlationId: UUID): ExceptionHandler =
    ExceptionHandler {
      case e: Throwable =>
        val response = HttpResponse(InternalServerError)

        (for {
          requestMap <- requestInfoMap.take
          _ <- requestInfoMap.put(requestMap.filterNot(_._1 == correlationId))
        } yield requestMap(correlationId).logResponse(response)).unsafeRunAsyncAndForget()
        e.printStackTrace()
        complete(response)
    }

  /** Providing handling of the requests that timed out */
  def timeoutHandler(route: => Route)(implicit correlationId: UUID): Route =
    withRequestTimeoutResponse { _ =>
      val response = HttpResponse(StatusCodes.ServiceUnavailable, entity = HttpEntity("Request timeout"))
      (for {
        requestMap <- requestInfoMap.take
        _ <- requestInfoMap.put(requestMap.filterNot(_._1 == correlationId))
      } yield requestMap(correlationId).logResponse(response)).unsafeRunAsyncAndForget()
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
      val requestEndTime = System.currentTimeMillis()
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
    def fromCtxAndIp(ctx: RequestContext, ip: RemoteAddress)(implicit materializer: Materializer): RequestValues = {
      import scala.concurrent.duration._
      RequestValues(
        httpMethod = ctx.request.method.value,
        requestBody = ctx.request.entity.toStrict(1000.millis).value.get.get.data.utf8String,
        clientIp = ip.toOption.map(_.toString).getOrElse("unknown"),
        path = ctx.request.uri.path.toString(),
        apiVersion = if (ctx.request.uri.path.toString().startsWith("/v2")) "v2" else "v1",
        apiKey = ctx.request.headers.find(_.is("apikey")).map(_.value()).getOrElse(""),
        startTime = System.currentTimeMillis()
      )
    }
  }
}
