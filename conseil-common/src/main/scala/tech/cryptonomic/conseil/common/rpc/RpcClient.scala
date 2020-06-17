package tech.cryptonomic.conseil.common.rpc

import scala.util.control.NoStackTrace

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import org.http4s.{EntityDecoder, EntityEncoder, Header, Method, Uri}
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl

import tech.cryptonomic.conseil.common.rpc.RpcClient._

// JSON-RPC client according to specification: https://www.jsonrpc.org/specification
class RpcClient[F[_]: Concurrent](
    endpoint: String,
    maxConcurrent: Int,
    httpClient: Client[F],
    headers: Header*
) extends Http4sClientDsl[F]
    with LazyLogging {

  def stream[P, R](batchSize: Int)(request: Stream[F, RpcRequest[P]])(
      implicit decode: EntityDecoder[F, Seq[RpcResponse[R]]],
      encode: EntityEncoder[F, List[RpcRequest[P]]]
  ): Stream[F, R] =
    request
      .chunkN(batchSize)
      .evalTap { requests =>
        Concurrent[F].delay(logger.debug(s"Call RPC in a batch of: ${requests.size}"))
      }
      .mapAsyncUnordered(maxConcurrent) { chunks =>
        httpClient
          .expect[Seq[RpcResponse[R]]](
            Method.POST(chunks.toList, Uri.unsafeFromString(endpoint), headers: _*)
          )
      }
      .flatMap(Stream.emits)
      .flatMap {
        case RpcResponse(_, _, Some(error), _) =>
          Stream.raiseError[F](error)
        case RpcResponse(_, Some(result), _, _) =>
          Stream(result)
        case _ =>
          Stream.raiseError[F](
            new UnsupportedOperationException(
              s"Unexpected response from JSON-RPC server at $endpoint"
            )
          )
      }

}

object RpcClient {

  def resource[F[_]: Concurrent](
      endpoint: String,
      maxConcurrent: Int,
      httpClient: Client[F],
      headers: Header*
  ): Resource[F, RpcClient[F]] =
    for {
      client <- Resource.pure(
        new RpcClient[F](
          endpoint,
          maxConcurrent,
          httpClient,
          headers: _*
        )
      )
    } yield client

  case class RpcException(
      code: Int,
      message: String,
      data: Option[String]
  ) extends RuntimeException(s"Json-RPC error $code: $message ${data.getOrElse("")}")
      with NoStackTrace

  case class RpcRequest[P](
      jsonrpc: String,
      method: String,
      params: P,
      id: String
  )

  case class RpcResponse[R](
      jsonrpc: Option[String],
      result: Option[R],
      error: Option[RpcException],
      id: String
  )
}
