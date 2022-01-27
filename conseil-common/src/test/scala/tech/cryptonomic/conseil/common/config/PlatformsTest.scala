package tech.cryptonomic.conseil.common.config

import com.typesafe.config.ConfigFactory
import tech.cryptonomic.conseil.common.config.Platforms.{
  BitcoinBatchFetchConfiguration,
  BitcoinConfiguration,
  BitcoinNodeConfiguration,
  PlatformsConfiguration,
  TezosConfiguration,
  TezosNodeConfiguration
}
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class PlatformsTest extends ConseilSpec {

  private val dbCfg = ConfigFactory.parseString("""
        |    {
        |      dataSourceClass: "org.postgresql.ds.PGSimpleDataSource"
        |      properties {
        |        user: "foo"
        |        password: "bar"
        |        url: "jdbc:postgresql://localhost:5432/postgres"
        |      }
        |      numThreads: 10
        |      maxConnections: 10
        |    }
        """.stripMargin)

  private val configTezosNode = TezosNodeConfiguration("host", 0, "protocol")
  private val configTezos =
    TezosConfiguration("mainnet", enabled = true, configTezosNode, BigDecimal.decimal(8000), dbCfg, None)
  private val configBitcoinNode = BitcoinNodeConfiguration("host", 0, "protocol", "username", "password")
  private val configBitcoinBatching = BitcoinBatchFetchConfiguration(1, 1, 1, 1, 1)
  private val configBitcoin =
    BitcoinConfiguration("testnet", enabled = false, configBitcoinNode, dbCfg, configBitcoinBatching)
  private val config = PlatformsConfiguration(List(configTezos, configBitcoin))

  private val platformTezos = PlatformDiscoveryTypes.Platform("tezos", "Tezos")
  private val platformBitcoin = PlatformDiscoveryTypes.Platform("bitcoin", "Bitcoin")

  private val networkTezos = PlatformDiscoveryTypes.Network("mainnet", "Mainnet", "tezos", "mainnet")
  private val networkBitcoin = PlatformDiscoveryTypes.Network("testnet", "Testnet", "bitcoin", "testnet")

  "Platforms.PlatformsConfiguration" should {
      "return enabled platforms, by default" in {
        config.getPlatforms() should contain only platformTezos
      }

      "return allow to ask for disabled platforms" in {
        config.getPlatforms(enabled = false) should contain only platformBitcoin
      }

      "return networks for enabled platforms and specific name, by default" in {
        config.getNetworks("tezos") should contain only networkTezos
      }

      "return allow to ask for networks, for disabled platforms and specific name" in {
        config.getNetworks("tezos", enabled = false) shouldBe empty
        config.getNetworks("bitcoin", enabled = false) should contain only networkBitcoin
      }

      "return configs for enabled platforms" in {
        config.getDbConfig("tezos", "mainnet") shouldBe dbCfg
      }

      "return databases for enabled platforms" in {
        config.getDatabases().keys.toList should contain theSameElementsAs List(("tezos", "mainnet"))
      }
    }
}
