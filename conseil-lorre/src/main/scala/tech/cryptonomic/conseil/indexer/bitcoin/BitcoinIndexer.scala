package tech.cryptonomic.conseil.indexer.bitcoin

import java.util.concurrent.Executors

import scala.concurrent.{ExecutionContext, Future}

import cats.effect.{IO, Resource}
// import org.http4s.headers.Authorization
// import org.http4s.BasicCredentials
import org.http4s.blaze.client.BlazeClientBuilder
import slick.jdbc.PostgresProfile.api._
import slickeffect.Transactor
import slickeffect.transactor.{config => transactorConfig}

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.indexer.config.LorreConfiguration
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.config.Platforms.BitcoinConfiguration
import tech.cryptonomic.conseil.indexer.LorreIndexer
import tech.cryptonomic.conseil.indexer.logging.LorreProgressLogging
import tech.cryptonomic.conseil.common.rpc.RpcClient

import cats.effect.unsafe.implicits.global

/**
  * Class responsible for indexing data for Bitcoin Blockchain.
  *
  * @param lorreConf Lorre configuration
  * @param bitcoinConf Bitcoin configuration
  */
class BitcoinIndexer(
    lorreConf: LorreConfiguration,
    bitcoinConf: BitcoinConfiguration,
    db: Database
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
    * Dedicated [[ExecutionContext]] for the http4s.
    */
  private val httpEC = ExecutionContext.fromExecutor(httpExecutor)

  override def platform: Platforms.BlockchainPlatform = Platforms.Bitcoin

  // TODO: Handle the cancelation in the right way, now it's imposible to use `ctrl-C`
  //       to stop the mainLoop.
  override def start(): Unit =
    /**
      * Repeat [[cats.IO]] after the specified interval.
      *
      * @param interval finite duration interval
      * @param f [[cats.IO]] to repeat
      */
    indexer
      .use(
        bitcoinOperations =>
          /**
            * Place with all the computations for the Bitcoin.
            * Currently, it only contains the blocks. But it can be extended to
            * handle multiple computations.
            */
          (IO(logger.info("Start Lorre for Bitcoin")) *>
              bitcoinOperations.loadBlocks(lorreConf.depth, lorreConf.headHash).compile.drain *>
              IO.sleep(lorreConf.sleepInterval)).foreverM
      )
      .unsafeRunSync()

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
        httpClient // TODO: wrap it into retry and logger middleware
        // Authorization(BasicCredentials(bitcoinConf.node.username, bitcoinConf.node.password))
      )

      tx <- Transactor
        .fromDatabase[IO](IO.delay(db))
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
      bitcoinConf: BitcoinConfiguration,
      db: Database
  ): LorreIndexer =
    new BitcoinIndexer(lorreConf, bitcoinConf, db)
}
