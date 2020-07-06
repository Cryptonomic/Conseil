package tech.cryptonomic.conseil.api.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{EitherValues, Matchers, WordSpec}
import pureconfig.error.ConvertFailure
import pureconfig.generic.auto._
import tech.cryptonomic.conseil.api.config.ConseilAppConfig._
import tech.cryptonomic.conseil.common.config.Platforms

import scala.concurrent.duration._
import scala.language.postfixOps

class ConseilAppConfigTest extends WordSpec with Matchers with EitherValues {

  "ConseilAppConfig" should {
      "extract the correct configuration list for Tezos platform's networks using default readers" in {
        import Platforms._

        val cfg = ConfigFactory.parseString("""
                                            |platforms: [
                                            |  {
                                            |    name: "tezos"
                                            |    network: "alphanet"
                                            |    enabled: true,
                                            |    node: {
                                            |      protocol: "http",
                                            |      hostname: "localhost",
                                            |      port: 8732
                                            |      path-prefix: ""
                                            |    }
                                            |  },
                                            |  {
                                            |    name: "tezos"
                                            |    network: "alphanet-staging"
                                            |    enabled: false,
                                            |    node: {
                                            |      protocol: "https"
                                            |      hostname: "nautilus.cryptonomic.tech",
                                            |      port: 8732
                                            |      path-prefix: "tezos/alphanet/"
                                            |    }
                                            |  }
                                            |]
        """.stripMargin)

        val typedConfig = pureconfig.loadConfig[PlatformsConfiguration](conf = cfg, namespace = "platforms")

        typedConfig.right.value.platforms should contain only (
          TezosConfiguration(
            "alphanet",
            enabled = true,
            TezosNodeConfiguration(hostname = "localhost", port = 8732, protocol = "http"),
            None
          ),
          TezosConfiguration(
            "alphanet-staging",
            enabled = false,
            TezosNodeConfiguration(
              hostname = "nautilus.cryptonomic.tech",
              port = 8732,
              protocol = "https",
              pathPrefix = "tezos/alphanet/"
            ),
            None
          )
        )

      }

      "extract a configuration list that includes a unknown platforms" in {
        import Platforms._

        val cfg = ConfigFactory.parseString("""
                                              |platforms: [
                                              |  {
                                              |    name: "tezos"
                                              |    network: "alphanet"
                                              |    enabled: true,
                                              |    node: {
                                              |      protocol: "http",
                                              |      hostname: "localhost",
                                              |      port: 8732
                                              |      pathPrefix: ""
                                              |    }
                                              |  },
                                              |  {
                                              |    name: "ethereum"
                                              |    network: "alphanet-staging"
                                              |    enabled: false,
                                              |    node: {
                                              |      protocol: "https"
                                              |      hostname: "nautilus.cryptonomic.tech",
                                              |      port: 8732
                                              |      pathPrefix: "tezos/alphanet/"
                                              |    }
                                              |  }
                                              |]
        """.stripMargin)

        val typedConfig = pureconfig.loadConfig[PlatformsConfiguration](conf = cfg, namespace = "platforms")
        typedConfig.left.value.toList should have size 1
        typedConfig.left.value.toList.head shouldBe an[ConvertFailure]
      }

      "extract None, when configuration for Nautilus Cloud does not exist" in {
        val config = ConfigFactory.parseString("")
        val typedConfig = pureconfig.loadConfig[Option[NautilusCloudConfiguration]](conf = config, namespace = "nautilus-cloud")

        typedConfig.right.value shouldBe empty
      }

      "extract Some, when configuration for Nautilus Cloud exists" in {
        val config = ConfigFactory.parseString("""
            |nautilus-cloud {
            |  host: "http://localhost"
            |  port: 1234
            |  path: "apiKeys/dev" // here should be an environment name after '/'
            |  key: "exampleApiKeyDev"
            |  delay: 10 seconds
            |  interval: 30 seconds
            |}
            |""".stripMargin)
        val typedConfig = pureconfig.loadConfig[Option[NautilusCloudConfiguration]](conf = config, namespace = "nautilus-cloud")

        typedConfig.right.value shouldBe Some(
          NautilusCloudConfiguration(
            "http://localhost",
            1234,
            "apiKeys/dev",
            "exampleApiKeyDev",
            10 seconds,
            30 seconds
          )
        )
      }
    }

}
