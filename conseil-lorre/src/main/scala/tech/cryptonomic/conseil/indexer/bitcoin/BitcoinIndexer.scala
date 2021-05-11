package tech.cryptonomic.conseil.indexer.bitcoin

import java.util.concurrent.Executors

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration.FiniteDuration

import cats.effect.{IO, Resource}
import org.http4s.headers.Authorization
import org.http4s.BasicCredentials
import org.http4s.client.blaze.BlazeClientBuilder
import slick.jdbc.PostgresProfile.api._
import slickeffect.Transactor
import slickeffect.transactor.{config => transactorConfig}

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.util.DatabaseUtil
import tech.cryptonomic.conseil.indexer.config.LorreConfiguration
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.config.Platforms.BitcoinConfiguration
import tech.cryptonomic.conseil.indexer.LorreIndexer
import tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging
import tech.cryptonomic.conseil.common.rpc.RpcClient

/**
  * Class responsible for indexing data for Bitcoin Blockchain.
  *
  * @param lorreConf Lorre configuration
  * @param bitcoinConf Bitcoin configuration
  */
class BitcoinIndexer(
    lorreConf: LorreConfiguration,
    bitcoinConf: BitcoinConfiguration
) extends LorreIndexer
    with LorreProgressLogging
    with ConseilLogSupport {

  /**
    * Executor for the rpc client, timer and to handle stop method.
    */
  private val indexerExecutor = Executors.newFixedThreadPool(bitcoinConf.batching.indexerThreadsCount)

  /**
    * Dedicated executor for the http4s.
    */
  private val httpExecutor = Executors.newFixedThreadPool(bitcoinConf.batching.httpFetchThreadsCount)

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

  override def platform: Platforms.BlockchainPlatform = Platforms.Bitcoin

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
        bitcoinOperations =>
          repeatEvery(lorreConf.sleepInterval) {

            /**
              * Place with all the computations for the Bitcoin.
              * Currently, it only contains the blocks. But it can be extended to
              * handle multiple computations.
              */
            IO.delay(logger.info("Start Lorre for Bitcoin")) *>
              bitcoinOperations.loadBlocks(lorreConf.depth, lorreConf.headHash).compile.drain
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
    * Lorre indexer or the Bitcoin. This method creates all the dependencies and wraps it into the [[cats.Resource]].
    */
  private def indexer: Resource[IO, BitcoinOperations[IO]] =
    for {
      httpClient <- BlazeClientBuilder[IO](httpEC).resource

      rpcClient <- RpcClient.resource(
        bitcoinConf.node.url,
        maxConcurrent = bitcoinConf.batching.indexerThreadsCount,
        httpClient, // TODO: wrap it into retry and logger middleware
        Authorization(BasicCredentials(bitcoinConf.node.username, bitcoinConf.node.password))
      )

      tx <- Transactor
        .fromDatabase[IO](IO.delay(DatabaseUtil.lorreDb))
        .map(_.configure(transactorConfig.transactionally)) // run operations in transaction

      bitcoinOperations <- BitcoinOperations.resource(rpcClient, tx, bitcoinConf.batching)
    } yield bitcoinOperations
}

object BitcoinIndexer {

  /**
    * Creates the Indexer which is dedicated for Bitcoin Blockchain
    *
    * @param lorreConf Lorre configuration
    * @param bitcoinConf Bitcoin configuration
    */
  def fromConfig(
      lorreConf: LorreConfiguration,
      bitcoinConf: BitcoinConfiguration
  ): LorreIndexer =
    new BitcoinIndexer(lorreConf, bitcoinConf)
}
