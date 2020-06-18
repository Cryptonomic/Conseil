package tech.cryptonomic.conseil.common.bitcoin.rpc

import cats.effect.{Concurrent, Resource}
import com.typesafe.scalalogging.LazyLogging
import fs2.{Pipe, Stream}
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._

import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.bitcoin.rpc.BitcoinRpcCommands._
import tech.cryptonomic.conseil.common.bitcoin.rpc.json.{Block, Transaction, TransactionComponent}

class BitcoinClient[F[_]: Concurrent](
    client: RpcClient[F]
) extends LazyLogging {

  def getBlockHash(batchSize: Int): Pipe[F, Int, String] =
    _.map(GetBlockHash.request)
      .through(client.stream[GetBlockHash.Params, String](batchSize))

  def getBlockByHash(batchSize: Int): Pipe[F, String, Block] =
    _.map(GetBlock.request)
      .through(client.stream[GetBlock.Params, Block](batchSize))

  def getTransactionsFromBlock(batchSize: Int): Pipe[F, Block, Transaction] =
    _.map(_.tx)
      .flatMap(Stream.emits)
      .map(GetRawTransaction.request)
      .through(client.stream[GetRawTransaction.Params, Transaction](batchSize))

  def getTransactionComponents: Pipe[F, Transaction, TransactionComponent] =
    _.flatMap { transaction =>
      Stream.emits(transaction.vin.map(_.copy(txid = Some(transaction.txid)))) ++
        Stream.emits(transaction.vout.map(_.copy(txid = Some(transaction.txid))))
    }

}

object BitcoinClient {

  def resource[F[_]: Concurrent](client: RpcClient[F]): Resource[F, BitcoinClient[F]] =
    for {
      client <- Resource.pure(
        new BitcoinClient[F](client)
      )
    } yield client
}
