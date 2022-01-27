package tech.cryptonomic.conseil.indexer.ethereum

import java.util.concurrent.Executors

import cats.effect.unsafe.implicits.global
import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

import cats.effect.{IO, Resource}
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.client.middleware.Retry
import slick.jdbc.PostgresProfile.api._
import slickeffect.Transactor
import slickeffect.transactor.{config => transactorConfig}

import tech.cryptonomic.conseil.common.rpc.RpcClient
import tech.cryptonomic.conseil.common.util.DatabaseUtil
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.config.Platforms.EthereumConfiguration
import tech.cryptonomic.conseil.indexer.LorreIndexer
import tech.cryptonomic.conseil.indexer.config.LorreConfiguration
import tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging

/**
  * Class responsible for indexing data for Ethereum Blockchain.
  *
  * @param lorreConf Lorre configuration
  * @param ethereumConf Ethereum configuration
  */
class EthereumIndexer(
    lorreConf: LorreConfiguration,
    ethereumConf: EthereumConfiguration,
    db: Database
) extends LorreIndexer
    with LorreProgressLogging {

  /**
    * Executor for the rpc client, timer and to handle stop method.
    */
  private val indexerExecutor = Executors.newFixedThreadPool(ethereumConf.batching.indexerThreadsCount)

  /**
    * Dedicated executor for the http4s.
    */
  private val httpExecutor = Executors.newFixedThreadPool(ethereumConf.batching.httpFetchThreadsCount)

  /**
    * [[ExecutionContext]] for the Lorre indexer.
    */
  private val indexerEC = ExecutionContext.fromExecutor(indexerExecutor)

  /**
    * Dedicated [[ExecutionContext]] for the http4s.
    */
  private val httpEC = ExecutionContext.fromExecutor(httpExecutor)

  override def platform: Platforms.BlockchainPlatform = Platforms.Ethereum

  // TODO: Handle the cancelation in the right way, now it's imposible to use `ctrl-C`
  //       to stop the mainLoop.
  override def start(): Unit = {

    /**
      * Repeat [[cats.IO]] after the specified interval.
      *
      * @param interval finite duration interval
      * @param f [[cats.IO]] to repeat
      */
    def repeatEvery[A](interval: FiniteDuration)(operations: IO[A]): IO[Unit] =
      for {
        _ <- operations
        _ <- IO.sleep(interval)
        _ <- repeatEvery(interval)(operations)
      } yield ()

    indexer
      .use(
        ethereumOperations =>
          repeatEvery(lorreConf.sleepInterval) {

            /**
              * Place with all the computations for the Ethereum.
              * Currently, it only contains the blocks. But it can be extended to
              * handle multiple computations.
              */
            IO.delay(logger.info("Start Lorre for Ethereum")) *>
              ethereumOperations.loadBlocksAndLogs(lorreConf.depth, lorreConf.headHash).compile.drain
          }
      )
      .unsafeRunSync()
  }

  override def stop(): Future[LorreIndexer.ShutdownComplete] =
    IO.delay {
      indexerExecutor.shutdown()
      httpExecutor.shutdown()
      LorreIndexer.ShutdownComplete
    }.unsafeToFuture

  /**
    * Lorre indexer for the Ethereum. This method creates all the dependencies and wraps it into the [[cats.Resource]].
    */
  private def indexer: Resource[IO, EthereumOperations[IO]] =
    for {
      httpClient <- BlazeClientBuilder[IO](httpEC).resource

      rpcClient <- RpcClient.resource(
        ethereumConf.node.toString,
        maxConcurrent = ethereumConf.batching.indexerThreadsCount,
        Retry(RpcClient.exponentialRetryPolicy(ethereumConf.retry.maxWait, ethereumConf.retry.maxRetry))(httpClient)
      )

      tx <- Transactor
        .fromDatabase[IO](IO.delay(db))
        .map(_.configure(transactorConfig.transactionally)) // run operations in transaction

      ethereumOperations <- EthereumOperations.resource(rpcClient, tx, ethereumConf.batching)
    } yield ethereumOperations
}

object EthereumIndexer {

  /**
    * Creates the Indexer which is dedicated for Ethereum blockchain.
    *
    * @param lorreConf Lorre configuration
    * @param ethereumConf Ethereum configuration
    */
  def fromConfig(
      lorreConf: LorreConfiguration,
      ethereumConf: EthereumConfiguration,
      db: Database
  ): LorreIndexer =
    new EthereumIndexer(lorreConf, ethereumConf, db: Database)
}
