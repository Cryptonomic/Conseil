package tech.cryptonomic.conseil.indexer.ethereum

import java.util.concurrent.Executors

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import org.http4s.client.blaze.BlazeClientBuilder
import slick.jdbc.PostgresProfile.api._
import slickeffect.Transactor
import slickeffect.transactor.{config => transactorConfig}

import tech.cryptonomic.conseil.common.util.DatabaseUtil
import tech.cryptonomic.conseil.indexer.config.LorreConfiguration
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.config.Platforms.EthereumConfiguration
import tech.cryptonomic.conseil.indexer.LorreIndexer
import tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging
import tech.cryptonomic.conseil.common.rpc.RpcClient

/**
  * Class responsible for indexing data for Ethereum Blockchain.
  *
  * @param lorreConf Lorre configuration
  * @param ethereumConf Ethereum configuration
  */
class EthereumIndexer(
    lorreConf: LorreConfiguration,
    ethereumConf: EthereumConfiguration
) extends LazyLogging
    with LorreIndexer
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
    * [[cats.ContextShift]] is the equivalent to [[ExecutionContext]],
    * it's used by the Cats Effect related methods.
    */
  implicit private val contextShift = IO.contextShift(ExecutionContext.fromExecutor(indexerExecutor))

  /**
    * [[ExecutionContext]] for the Lorre indexer.
    */
  private val indexerEC = ExecutionContext.fromExecutor(indexerExecutor)

  /**
    * The timer to schedule continuous indexer runs.
    */
  implicit private val timer = IO.timer(indexerEC)

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
    def repeatEvery[A](interval: FiniteDuration)(f: IO[A]): IO[Unit] =
      for {
        _ <- f
        _ <- IO.sleep(interval)
        _ <- repeatEvery(interval)(f)
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
            IO.delay(logger.info(s"Start Lorre for Ethereum")) *>
              ethereumOperations.loadBlocks(lorreConf.depth).compile.drain
          }
      )
      .unsafeRunSync()
  }

  override def stop(): Future[LorreIndexer.ShutdownComplete] =
    Future {
      indexerExecutor.shutdown()
      httpExecutor.shutdown()
      LorreIndexer.ShutdownComplete
    }(ExecutionContext.global)

  /**
    * Lorre indexer for the Ethereum. This method creates all the dependencies and wraps it into the [[cats.Resource]].
    */
  private def indexer: Resource[IO, EthereumOperations[IO]] =
    for {
      httpClient <- BlazeClientBuilder[IO](httpEC).resource

      rpcClient <- RpcClient.resource(
        ethereumConf.node.url,
        maxConcurrent = ethereumConf.batching.indexerThreadsCount,
        httpClient, // TODO: wrap it into retry and logger middleware
      )

      tx <- Transactor
        .fromDatabase[IO](IO.delay(DatabaseUtil.lorreDb))
        .map(_.configure(transactorConfig.transactionally)) // run operations in transaction

      ethereumOperations <- EthereumOperations.resource(rpcClient, tx, ethereumConf.batching)
    } yield ethereumOperations
}

object EthereumIndexer {

  /**
    * Creates the Indexer which is dedicated for Ethereum blockchain
    *
    * @param lorreConf Lorre configuration
    * @param ethereumConf Ethereum configuration
    */
  def fromConfig(
      lorreConf: LorreConfiguration,
      ethereumConf: EthereumConfiguration
  ): LorreIndexer =
    new EthereumIndexer(lorreConf, ethereumConf)
}
