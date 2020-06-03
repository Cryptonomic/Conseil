package tech.cryptonomic.conseil.indexer.bitcoin

import scala.concurrent.{ExecutionContext, Future}

import cats.effect.{ContextShift, ExitCode, IO}
import com.typesafe.scalalogging.LazyLogging
import org.http4s.headers.Authorization
import org.http4s.BasicCredentials
import org.http4s.client.blaze.BlazeClientBuilder

import tech.cryptonomic.conseil.indexer.config.{
  BatchFetchConfiguration,
  HttpStreamingConfiguration,
  LorreConfiguration,
  NetworkCallsConfiguration
}
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.config.Platforms.BitcoinConfiguration
import tech.cryptonomic.conseil.indexer.LorreIndexer
import tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging

import tech.cryptonomic.conseil.common.rpc.RpcClient
import cats.effect.Resource
import tech.cryptonomic.conseil.indexer.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.indexer.bitcoin.persistence.BitcoinPersistence

/** * Class responsible for indexing data for Bitcoin BlockChain */
class BitcoinIndexer(
    lorreConf: LorreConfiguration,
    bitcoinConf: BitcoinConfiguration,
    callsConf: NetworkCallsConfiguration,
    streamingClientConf: HttpStreamingConfiguration,
    batchingConf: BatchFetchConfiguration
) extends LazyLogging
    with LorreIndexer
    with LorreProgressLogging {

  // Use global execution context only for the PoC
  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)
  private val httpEC = ExecutionContext.global

  def resource: Resource[IO, Unit] =
    for {
      httpClient <- BlazeClientBuilder[IO](httpEC).resource

      rpcClient <- RpcClient.resource(
        bitcoinConf.nodeConfig.url,
        maxConcurrent = 8, // TODO: move to the configuration
        httpClient, // TODO: wrap it into retry and logger middleware
        Authorization(BasicCredentials(bitcoinConf.nodeConfig.username, bitcoinConf.nodeConfig.password))
      )

      bitcoinOperations <- BitcoinOperations.resource(rpcClient)

      _ <- bitcoinOperations.blockStream(1 to 100000).compile.resource.drain
    } yield ()

  override def platform: Platforms.BlockchainPlatform = Platforms.Bitcoin

  override def start(): Unit = {
    resource.use(_ => IO.unit.map(_ => ExitCode.Success)).unsafeRunSync()
    ()
  }

  override def stop(): Future[LorreIndexer.ShutdownComplete] = Future.successful(LorreIndexer.ShutdownComplete)
}

object BitcoinIndexer {

  /** * Creates the Indexer which is dedicated for Bitcoin BlockChain */
  def fromConfig(
      lorreConf: LorreConfiguration,
      conf: BitcoinConfiguration,
      callsConf: NetworkCallsConfiguration,
      streamingClientConf: HttpStreamingConfiguration,
      batchingConf: BatchFetchConfiguration
  ): LorreIndexer =
    new BitcoinIndexer(lorreConf, conf, callsConf, streamingClientConf, batchingConf)
}
