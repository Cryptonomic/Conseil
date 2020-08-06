package tech.cryptonomic.conseil.common.config

import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Network, Platform}

/** defines configuration types for conseil available platforms */
object Platforms {

  /** a trait defining existing platforms */
  sealed trait BlockchainPlatform extends Product with Serializable {

    /** a name usually used in configurations to identify the platform */
    def name: String
  }

  /** Represents blockchain platform for Tezos */
  case object Tezos extends BlockchainPlatform {
    val name = "tezos"
  }

  /** Represents blockchain platform for Bitcoin */
  case object Bitcoin extends BlockchainPlatform {
    val name = "bitcoin"
  }

  /** Represents blockchain platform for Ethereum */
  case object Ethereum extends BlockchainPlatform {
    val name: String = "ethereum"
  }

  /** Represents blockchain platform for Quorum */
  case object Quorum extends BlockchainPlatform {
    val name: String = "quorum"
  }

  object BlockchainPlatform {

    /** maps a generic string to a typed BlockchainPlatform */
    def fromString(s: String): BlockchainPlatform = s match {
      // Note that we are not handling match-error,
      // due to the fact that unknown platforms will be handled at configuration reading level
      case Tezos.name => Tezos
      case Bitcoin.name => Bitcoin
      case Ethereum.name => Ethereum
      case Quorum.name => Quorum
    }
  }

  /**
    * Collects all platforms configuration in a list.
    *
    * To access specific type of the configuration,
    * - match the inner `BlockchainPlatform` type over one of supported platforms,
    * - match the outer `PlatformConfiguration` type over one of specified platform's configuration.
    *
    * Keep in mind, that specific platform's configuration can be enabled or disabled.
    */
  case class PlatformsConfiguration(platforms: List[PlatformConfiguration]) {

    /*** Extracts platforms from configuration */
    def getPlatforms(enabled: Boolean = true): List[Platform] =
      platforms
        .filter(_.enabled == enabled)
        .map(_.platform)
        .map(platform => Platform(platform.name, platform.name.capitalize))

    /*** Extracts networks from configuration */
    def getNetworks(platformName: String, enabled: Boolean = true): List[Network] =
      platforms
        .filter(v => v.platform.name == platformName && v.enabled == enabled)
        .map { config =>
          Network(config.network, config.network.capitalize, config.platform.name, config.network)
        }
  }

  /** configurations to describe a tezos node */
  final case class TezosNodeConfiguration(
      hostname: String,
      port: Int,
      protocol: String,
      pathPrefix: String = "",
      chainEnv: String = "main"
  )

  /** Defines chain-specific values to identify the TNS (Tezos Naming Service) smart contract */
  final case class TNSContractConfiguration(name: String, contractType: String, accountId: String)

  /** generic trait for any platform configuration, where each instance corresponds to a network available on that chain */
  sealed trait PlatformConfiguration extends Product with Serializable {

    /** Defines the blockchain platform that configuration belongs to */
    def platform: BlockchainPlatform

    /** Defines whether this specific configuration is enabled */
    def enabled: Boolean

    /** Defines the name of the network for specific blockchain */
    def network: String
  }

  /** collects all config related to a tezos network */
  final case class TezosConfiguration(
      network: String,
      enabled: Boolean,
      node: TezosNodeConfiguration,
      tns: Option[TNSContractConfiguration]
  ) extends PlatformConfiguration {
    override val platform: BlockchainPlatform = Tezos
  }

  /** configurations to describe a bitcoin node */
  final case class BitcoinNodeConfiguration(
      hostname: String,
      port: Int,
      protocol: String,
      username: String,
      password: String
  ) {
    val url = s"$protocol://$hostname:$port"
  }

  /** configurations to describe a bitcoin batch fetch */
  final case class BitcoinBatchFetchConfiguration(
      indexerThreadsCount: Int,
      httpFetchThreadsCount: Int,
      hashBatchSize: Int,
      blocksBatchSize: Int,
      transactionsBatchSize: Int
  )

  /** collects all config related to a bitcoin network */
  final case class BitcoinConfiguration(
      network: String,
      enabled: Boolean,
      node: BitcoinNodeConfiguration,
      batching: BitcoinBatchFetchConfiguration
  ) extends PlatformConfiguration {
    override val platform: BlockchainPlatform = Bitcoin
  }

  /** configurations to describe a ethereum node */
  final case class EthereumNodeConfiguration(
      hostname: String,
      port: Int,
      protocol: String
  ) {
    val url = s"$protocol://$hostname:$port"
  }

  /** configurations to describe a ethereum batch fetch */
  final case class EthereumBatchFetchConfiguration(
      indexerThreadsCount: Int,
      httpFetchThreadsCount: Int,
      blocksBatchSize: Int,
      transactionsBatchSize: Int
  )

  /** collects all config related to a ethereum network */
  final case class EthereumConfiguration(
      network: String,
      enabled: Boolean,
      node: EthereumNodeConfiguration,
      batching: EthereumBatchFetchConfiguration
  ) extends PlatformConfiguration {
    override val platform: BlockchainPlatform = Ethereum
  }

  /** collects all config related to a quorum network */
  final case class QuorumConfiguration(
      network: String,
      enabled: Boolean,
      node: EthereumNodeConfiguration,
      batching: EthereumBatchFetchConfiguration
  ) extends PlatformConfiguration {
    override val platform: BlockchainPlatform = Quorum

    lazy val toEthereumConfiguration = EthereumConfiguration(network, enabled, node, batching)
  }

}
