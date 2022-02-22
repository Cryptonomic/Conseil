package tech.cryptonomic.conseil.api

import cats.effect.IO

import tech.cryptonomic.conseil.api.ConseilApi.NoNetworkEnabledError
import tech.cryptonomic.conseil.api.config.ConseilAppConfig.CombinedConfiguration
import tech.cryptonomic.conseil.api.config.NautilusCloudConfiguration
import tech.cryptonomic.conseil.api.platform.data.bitcoin.BitcoinDataOperations
import tech.cryptonomic.conseil.api.platform.data.bitcoin.BitcoinDataRoutes
import tech.cryptonomic.conseil.api.platform.data.ethereum.EthereumDataOperations
import tech.cryptonomic.conseil.api.platform.data.ethereum.EthereumDataRoutes
import tech.cryptonomic.conseil.api.platform.data.ethereum.QuorumDataRoutes
import tech.cryptonomic.conseil.api.platform.data.tezos.TezosDataOperations
import tech.cryptonomic.conseil.api.platform.data.tezos.TezosDataRoutes
import tech.cryptonomic.conseil.api.platform.discovery.GenericPlatformDiscoveryOperations
import tech.cryptonomic.conseil.api.platform.metadata.AttributeValuesCacheConfiguration
import tech.cryptonomic.conseil.api.platform.metadata.MetadataService
import tech.cryptonomic.conseil.api.platform.metadata.UnitTransformation
import tech.cryptonomic.conseil.common.cache.MetadataCaching
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.config.Platforms.BlockchainPlatform
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.sql.DatabaseRunner
import tech.cryptonomic.conseil.common.util.syntax._

import scala.concurrent.ExecutionContext

object ConseilApi {

  /** Exception, which is thrown when no network is enabled in configuration */
  case class NoNetworkEnabledError(message: String) extends Exception(message)

  /** Creates Conseil API based on a given configuration */
  def create(config: CombinedConfiguration): IO[ConseilApi] = IO(new ConseilApi(config))
}

class ConseilApi(config: CombinedConfiguration) extends ConseilLogSupport {

  import tech.cryptonomic.conseil.api.info.model.currentInfo

  private val transformation = new UnitTransformation(config.metadata)
  private val cacheOverrides = new AttributeValuesCacheConfiguration(config.metadata)

  config.nautilusCloud match {
    // FIXME: update security; or do it at the implementation/definition time
    case ncc @ NautilusCloudConfiguration(true, _, _, _, _, delay, interval) => ncc
    case _ => ()
  }

  config.platforms.platforms.foreach { conf =>
    if (conf.enabled)
      logger.info(
        s"Configuring endpoints to expose data for platform: ${conf.platform.name} and network ${conf.network}."
      )
    else
      logger.warn(
        s"Configuration for platform: ${conf.platform.name} and network: ${conf.network} is disabled, skipping..."
      )
  }

  // this val is not lazy to force to fetch metadata and trigger logging at the start of the application
  implicit private val ec: ExecutionContext = ExecutionContext.global

  lazy val metadataService =
    new MetadataService(config.platforms, transformation, cacheOverrides, ApiCache.cachedDiscoveryOperations)

  lazy val tezosOperations = new TezosDataOperations(
    config.platforms.getDbConfig(Platforms.Tezos.name, config.platforms.getNetworks(Platforms.Tezos.name).head.name)
  )
  lazy val tezosDataRoutes =
    TezosDataRoutes(metadataService, config.metadata, tezosOperations, config.server.maxQueryResultSize)

  lazy val bitcoinOperations = new BitcoinDataOperations(
    config.platforms.getDbConfig(Platforms.Bitcoin.name, config.platforms.getNetworks(Platforms.Bitcoin.name).head.name)
  )
  lazy val bitcoindDataRoutes =
    BitcoinDataRoutes(metadataService, config.metadata, bitcoinOperations, config.server.maxQueryResultSize)

  lazy val ethereumOperations = new EthereumDataOperations(
    dbConfig = config.platforms
      .getDbConfig(Platforms.Ethereum.name, config.platforms.getNetworks(Platforms.Ethereum.name).head.name)
  )
  lazy val ethereumDataRoutes =
    EthereumDataRoutes(metadataService, config.metadata, ethereumOperations, config.server.maxQueryResultSize)

  lazy val appInfoRoute = protocol.appInfo.serverLogicSuccess(_ => currentInfo)

  // TODO: add [[platform.data]] routes
  lazy val route = appInfoRoute :: tezosDataRoutes.getRoute ++ bitcoindDataRoutes.getRoute ++ ethereumDataRoutes.getRoute

  /**
    * Object, which initializes and holds all of the APIs (blockchain-specific endpoints) in the map.
    *
    * Note that only APIs that are visible (configured via metadata config file)
    * will be initialized and eventually exposed.
    */
  private object ApiCache {
    private lazy val cache = forVisiblePlatforms {
      case Platforms.Tezos =>
        // FIXME: investigate here + aren't [[operations]] & [[routes]] duplicated?
        val operations = new TezosDataOperations(
          config.platforms
            .getDbConfig(Platforms.Tezos.name, config.platforms.getNetworks(Platforms.Tezos.name).head.name)
        )
        TezosDataRoutes(metadataService, config.metadata, operations, config.server.maxQueryResultSize)

      case Platforms.Bitcoin =>
        val operations = new BitcoinDataOperations(
          config.platforms
            .getDbConfig(Platforms.Bitcoin.name, config.platforms.getNetworks(Platforms.Bitcoin.name).head.name)
        )
        BitcoinDataRoutes(metadataService, config.metadata, operations, config.server.maxQueryResultSize)

      case Platforms.Ethereum =>
        val operations = new EthereumDataOperations(
          Platforms.Ethereum.name,
          config.platforms
            .getDbConfig(Platforms.Ethereum.name, config.platforms.getNetworks(Platforms.Ethereum.name).head.name)
        )
        EthereumDataRoutes(metadataService, config.metadata, operations, config.server.maxQueryResultSize)

      case Platforms.Quorum =>
        val operations = new EthereumDataOperations(
          Platforms.Quorum.name,
          config.platforms
            .getDbConfig(Platforms.Quorum.name, config.platforms.getNetworks(Platforms.Quorum.name).head.name)
        )
        QuorumDataRoutes(metadataService, config.metadata, operations, config.server.maxQueryResultSize)
    }

    private val cacheOverrides = new AttributeValuesCacheConfiguration(config.metadata)
    private val metadataCaching = MetadataCaching.empty[IO]

    private val metadataOperations: Map[(String, String), DatabaseRunner] =
      config.platforms.getDatabases().mapValues(db => () => db)

    lazy val cachedDiscoveryOperations =
      new GenericPlatformDiscoveryOperations(
        metadataOperations,
        metadataCaching,
        cacheOverrides,
        config.server.cacheTTL,
        config.server.highCardinalityLimit
      )

    /**
      * Map, that contains list of available `ApiDataRoutes` accessed by platform name.
      *
      * @see `tech.cryptonomic.conseil.common.config.Platforms` to get list of possible platforms.
      */
    lazy val cachedDataEndpoints = cache.map { case (key, value) => key.name -> value }

    private val visiblePlatforms = transformation.overridePlatforms(config.platforms.getPlatforms())

    /**
      * Function, that while used is going to execute function `init` for every platform found (and visible) in configuration.
      *
      * @param init function, which is going to initialize type `T` for given blockchain's platform.
      * @tparam T the type of the entity that is going to be created for every blockchain's platform.
      * @return a map with blockchain's platform and type `T`.
      */
    private def forVisiblePlatforms[T](init: BlockchainPlatform => T): Map[BlockchainPlatform, T] =
      visiblePlatforms.map { platform =>
        val blockchainPlatform = BlockchainPlatform.fromString(platform.name)
        blockchainPlatform -> init(blockchainPlatform)
      }.toMap

    private val visibleNetworks = for {
      platform <- visiblePlatforms
      network <- transformation.overrideNetworks(platform.path, config.platforms.getNetworks(platform.name))
    } yield platform -> network

    def initMetadataCaching() =
      cachedDiscoveryOperations
        .init(visibleNetworks)
        .flatMap(_ => IO("Pre-caching successful!").debug.void)
        .handleErrorWith(_ => IO.raiseError(new RuntimeException("Pre-caching metadata failed")).debug.void)

    def initAttributesCaching() =
      cachedDiscoveryOperations
        .initAttributesCache(visibleNetworks)
        .flatMap(_ => IO("Pre-caching attributes successful!").debug.void)
        .handleErrorWith(_ => IO.raiseError(new RuntimeException("Pre-caching attributes failed")).debug.void)

    // At least one blockchain is enabled
    if (visibleNetworks.nonEmpty) initMetadataCaching >> initAttributesCaching
    else
      IO.raiseError(
        new NoNetworkEnabledError(
          """|Pre-caching can't be done, because there is no enabled block-chain defined.
           | This probably means the application is NOT properly configured.
           | The API needs a platform section to be defined, which lists at least one enabled chain and network.
           | Please double-check the configuration file and start the API service again.
           |""".stripMargin
        )
      )
  }

}
