package tech.cryptonomic.conseil.indexer.ethereum

import java.util.concurrent.Executors

import scala.concurrent.{ExecutionContext, Future}
// import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._

import cats.effect.{IO, Resource}
import com.typesafe.scalalogging.LazyLogging
import org.http4s.client.blaze.BlazeClientBuilder
import org.http4s.Status._
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
import org.http4s.client.middleware.RetryPolicy
import org.http4s.client.middleware.Retry
import org.http4s.client.WaitQueueTimeoutException

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
    def repeatEvery[A](interval: FiniteDuration)(operations: IO[A]): IO[Unit] =
      for {
        _ <- operations
        _ <- IO.sleep(interval)
        // _ <- repeatEvery(interval)(operations)
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
              ethereumOperations.loadBlocksAndLogs(lorreConf.depth).compile.drain
          }
      )
      .unsafeRunSync()
  }

  override def stop(): Future[LorreIndexer.ShutdownComplete] =
    IO.delay {
      indexerExecutor.shutdown()
      httpExecutor.shutdown()
      LorreIndexer.ShutdownComplete
    }.unsafeToFuture()

  /**
    * Lorre indexer for the Ethereum. This method creates all the dependencies and wraps it into the [[cats.Resource]].
    */
  private def indexer: Resource[IO, EthereumOperations[IO]] =
    for {
      httpClient <- BlazeClientBuilder[IO](httpEC).resource

      retriableStatuses = Set(
        RequestTimeout,
        // TODO Leaving PayloadTooLarge out until we model Retry-After
        InternalServerError,
        ServiceUnavailable,
        BadGateway,
        GatewayTimeout,
        TooManyRequests
      )

      retryPolicy = RetryPolicy[IO](
        RetryPolicy.exponentialBackoff(2.seconds, 5),
        (_, result) =>
          result match {
            case Right(resp) => retriableStatuses(resp.status)
            case Left(WaitQueueTimeoutException) => false
            case _ => true
          }
      )

      rpcClient <- RpcClient.resource(
        ethereumConf.node.toString,
        maxConcurrent = ethereumConf.batching.indexerThreadsCount,
        Retry(retryPolicy)(httpClient) // TODO: wrap it into retry and logger middleware
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
