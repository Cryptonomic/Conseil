package tech.cryptonomic.conseil.common.rpc

import scala.util.control.NoStackTrace

import cats.effect.Concurrent
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import org.http4s.client._
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.{EntityDecoder, EntityEncoder, Method, Uri, Header}

import tech.cryptonomic.conseil.common.rpc.RpcClient._

class RpcClient[F[_]: Concurrent](
    endpoint: String,
    maxConcurrent: Int,
    client: Client[F],
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
        Concurrent[F].delay(logger.info(s"Call RPC in a batch of: ${requests.size}"))

      }
      .mapAsyncUnordered(maxConcurrent) { chunks =>
        client
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
              "Empty response result as well as error information."
            )
          )
      }

}

object RpcClient {
  case class RpcException(
      code: Int,
      message: String,
      data: Option[String]
  ) extends RuntimeException(s"Json-RPC error $code: $message")
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
