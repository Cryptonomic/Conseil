package tech.cryptonomic.conseil.indexer.bitcoin.streams

import cats.effect.{Async, Concurrent}
import com.typesafe.scalalogging.LazyLogging
import fs2.Stream
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._

import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.indexer.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.indexer.bitcoin.domain.Block

class BlocksStream[F[_]: Async: Concurrent](
    client: RpcClient[F]
) extends LazyLogging {
  def stream(from: Int, to: Int): Stream[F, Unit] =
    for {
      _ <- client
        // Convert block numbers to hashes, as Bitcoin rpc requires it, batch it by 500 items
        .stream[BitcoinClient.GetBlockHash.Params, String](500)(
          Stream
            .range(from, to)
            .map(BitcoinClient.GetBlockHash.request)
        )
        .map(BitcoinClient.GetBlock.request)
        // Get Blocks in batches of 100
        .through(client.stream[BitcoinClient.GetBlock.Params, Block](100)(_))
        .chunkN(500) // batch it by 500 to save it in bulk inserts (in the future)
        .evalTap { blocks =>
          Async[F].delay(
            logger.info(s"Save blocks in a batch of: ${blocks.size} (tx count: ${blocks.map(_.tx.size).toList.sum})")
          )
        }

    } yield ()
}
