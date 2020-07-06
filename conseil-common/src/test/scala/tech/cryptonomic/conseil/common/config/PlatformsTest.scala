package tech.cryptonomic.conseil.common.config

import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.common.config.Platforms.{
  BitcoinConfiguration,
  PlatformsConfiguration,
  TezosConfiguration,
  TezosNodeConfiguration
}
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes

class PlatformsTest extends WordSpec with Matchers {

  private val configTezosNode = TezosNodeConfiguration("host", 0, "protocol")
  private val configTezos = TezosConfiguration("mainnet", enabled = true, configTezosNode, None)
  private val configBitcoin = BitcoinConfiguration("testnet", enabled = false)
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
    }
}
