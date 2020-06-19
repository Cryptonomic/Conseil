package tech.cryptonomic.conseil.indexer.bitcoin

import java.util.concurrent.Executors
import scala.concurrent.{ExecutionContext, Future}

import cats.effect.{Blocker, ContextShift, ExitCode, IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import org.http4s.headers.Authorization
import org.http4s.BasicCredentials
import org.http4s.client.blaze.BlazeClientBuilder
import slickeffect.Transactor

import tech.cryptonomic.conseil.common.util.DatabaseUtil
import tech.cryptonomic.conseil.indexer.config.LorreConfiguration
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.config.Platforms.BitcoinConfiguration
import tech.cryptonomic.conseil.indexer.LorreIndexer
import tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging
import tech.cryptonomic.conseil.common.rpc.RpcClient

/** * Class responsible for indexing data for Bitcoin BlockChain */
class BitcoinIndexer(
    lorreConf: LorreConfiguration,
    bitcoinConf: BitcoinConfiguration
) extends LazyLogging
    with LorreIndexer
    with LorreProgressLogging {

  private val executor = Executors.newFixedThreadPool(16)
  implicit private val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.fromExecutor(executor))
  // implicit val timer = IO.timer(ExecutionContext.global)
  implicit private val httpEC: ExecutionContext = ExecutionContext.fromExecutor(executor)

  def resource: Resource[IO, Unit] =
    for {
      httpClient <- BlazeClientBuilder[IO](httpEC).resource

      rpcClient <- RpcClient.resource(
        bitcoinConf.nodeConfig.url,
        maxConcurrent = 8, // TODO: move to the configuration
        httpClient, // TODO: wrap it into retry and logger middleware
        Authorization(BasicCredentials(bitcoinConf.nodeConfig.username, bitcoinConf.nodeConfig.password))
      )

      tx <- Transactor
        .fromDatabase[IO](IO.delay(DatabaseUtil.lorreDb))

      bitcoinOperations <- BitcoinOperations.resource(rpcClient, tx)

      _ <- bitcoinOperations.loadBlocksWithTransactions(99000 to 100000).compile.resource.drain
    } yield ()

  override def platform: Platforms.BlockchainPlatform = Platforms.Bitcoin

  override def start(): Unit = {
    resource.use(_ => IO.delay(ExitCode.Success)).unsafeRunSync()
    ()
  }

  override def stop(): Future[LorreIndexer.ShutdownComplete] =
    Future {
      executor.shutdownNow()
      LorreIndexer.ShutdownComplete
    }
}

object BitcoinIndexer {

  /** * Creates the Indexer which is dedicated for Bitcoin BlockChain */
  def fromConfig(
      lorreConf: LorreConfiguration,
      bitcoinConf: BitcoinConfiguration,
  ): LorreIndexer =
    new BitcoinIndexer(lorreConf, bitcoinConf)
}
