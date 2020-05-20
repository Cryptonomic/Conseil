package tech.cryptonomic.conseil.indexer

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.common.config.Platforms.{TezosConfiguration, UnknownPlatformConfiguration}
import tech.cryptonomic.conseil.indexer.config.LorreAppConfig.LORRE_FAILURE_IGNORE_VAR
import tech.cryptonomic.conseil.indexer.config.LorreAppConfig
import tech.cryptonomic.conseil.indexer.logging.LorreInfoLogging
import tech.cryptonomic.conseil.indexer.tezos.TezosIndexer

import scala.concurrent.Await
import scala.concurrent.duration._

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends App with LazyLogging with LorreAppConfig with LorreInfoLogging {

  //reads all configuration upstart, will only complete if all values are found
  val config = loadApplicationConfiguration(args)

  //stop if conf is not available
  config.left.foreach { _ =>
    sys.exit(1)
  }

  //unsafe call, will only be reached if loadedConf is a Right, otherwise the merge will fail
  val LorreAppConfig.CombinedConfiguration(
    lorreConf,
    platformConf,
    callsConf,
    streamingClientConf,
    batchingConf,
    verbose
  ) = config.merge

  //whatever happens we try to clean up
  sys.addShutdownHook(shutdown())

  //creates the indexer based on the given configuration, which is picked based on platform and network from argument variables
  val indexer = platformConf match {
    case conf: TezosConfiguration =>
      logger.info("Initializing indexer for Tezos Blockchain.")
      TezosIndexer.fromConfig(lorreConf, conf, callsConf, streamingClientConf, batchingConf)
    case _: UnknownPlatformConfiguration =>
      logger.error("Could not initialize indexer. Unsupported platform has been read from configuration file.")
      sys.exit(1)
  }

  try {
    //displaying information for better debugging
    displayInfo(indexer.platform.name, platformConf.network)
    if (verbose.on)
      displayConfiguration(
        indexer.platform,
        platformConf,
        lorreConf,
        (LORRE_FAILURE_IGNORE_VAR, sys.env.get(LORRE_FAILURE_IGNORE_VAR))
      )

    //actual start of the indexer
    indexer.start()
  } finally shutdown()

  private def shutdown(): Unit = {
    logger.info("Doing clean-up.")
    Await.result(indexer.stop(), 10.seconds)
    logger.info("All things closed.")
  }

}
