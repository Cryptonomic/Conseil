package tech.cryptonomic.conseil.util

import java.net.InetSocketAddress

import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes.InternalServerError
import akka.http.scaladsl.server.Directives.complete
import akka.http.scaladsl.server.{Directive, ExceptionHandler}
import akka.http.scaladsl.server.directives.BasicDirectives
import akka.stream.Materializer
import org.slf4j.{LoggerFactory, MDC}

import scala.util.Try

object RouteUtil {

  /** Async logger */
  private val asyncLogger = LoggerFactory.getLogger("ASYNC_LOGGER")

  /** Directive adding recorded values to the MDC */
  def recordResponseValues(inet: InetSocketAddress)(implicit materializer: Materializer): Directive[Unit] =
    BasicDirectives.extractRequestContext.flatMap { ctx =>
      import scala.concurrent.duration._
      val apiVersion = if (ctx.request.uri.path.toString().startsWith("/v2")) "v2" else "v1"
      MDC.put("httpMethod", ctx.request.method.value)
      MDC.put("requestBody", ctx.request.entity.toStrict(1000.millis).value.get.get.data.utf8String)
      MDC.put("clientIp", inet.getAddress.getHostAddress)
      MDC.put("path", ctx.request.uri.path.toString())
      MDC.put("apiVersion", apiVersion)
      MDC.put("apiKey", ctx.request.headers.find(_.is("apikey")).map(_.value()).getOrElse(""))
      val requestStartTime = System.currentTimeMillis()
      MDC.put("tmpStartTime", requestStartTime.toString)

      val response = BasicDirectives.mapResponse { resp =>
        val requestEndTime = System.currentTimeMillis()
        val responseTime = requestEndTime - requestStartTime
        MDC.remove("tmpStartTime")
        MDC.put("responseTime", responseTime.toString)
        MDC.put("responseCode", resp.status.value)
        asyncLogger.info("HTTP request")
        MDC.clear()
        resp
      }
      response
    }

  /** Custom exception handler with MDC logging */
  def loggingExceptionHandler: ExceptionHandler =
    ExceptionHandler {
      case e: Throwable =>
        val responseTime = Try(System.currentTimeMillis() - MDC.get("tmpStartTime").toLong).getOrElse(0)
        MDC.remove("tmpStartTime")
        MDC.put("responseTime", responseTime.toString)
        MDC.put("responseCode", "500")
        asyncLogger.info("HTTP request")
        MDC.clear()
        e.printStackTrace()
        complete(HttpResponse(InternalServerError))
    }
}
