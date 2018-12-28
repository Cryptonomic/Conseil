package tech.cryptonomic.conseil.config

import org.scalatest.{WordSpec, Matchers}
import com.typesafe.config.ConfigFactory

class ConfigUtilTest extends WordSpec with Matchers {

  "ConfigUtil" should {

    "extract the correct platforms type" in {
      import Platforms._
      import scala.collection.JavaConverters._

      val cfg = ConfigFactory.parseString(
        """
          | platforms.tezos : {
          |  alphanet: {
          |    node: {
          |      protocol: "http",
          |      hostname: "localhost",
          |      port: 8732
          |      pathPrefix: ""
          |    }
          |  }
          | }
          | platforms.ethereum : {
          |   some-network: {
          |     custom: "configuration"
          |   }
          | }
        """.stripMargin)

      cfg.getObject("platforms").keySet.asScala.map(BlockchainPlatform.fromString) should contain only (Tezos, UnknownPlatform("ethereum"))
    }

    "extract the correct configuration map for Tezos platform's networks" in {
      import Platforms._
      import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

      val cfg = ConfigFactory.parseString(
        """
          | platforms.tezos : {
          |  alphanet: {
          |    node: {
          |      protocol: "http",
          |      hostname: "localhost",
          |      port: 8732
          |      pathPrefix: ""
          |    }
          |  }
          |  alphanet-staging : {
          |    node: {
          |      protocol: "https"
          |      hostname: "nautilus.cryptonomic.tech",
          |      port: 8732
          |      pathPrefix: "tezos/alphanet/"
          |    }
          |  }
          | }
        """.stripMargin)

      val typedConfig = pureconfig.loadConfig[PlatformsConfiguration](conf = cfg, namespace = "platforms")
      typedConfig shouldBe 'right

      val Right(PlatformsConfiguration(platforms)) = typedConfig

      platforms.keys should contain only (Tezos)

      platforms.values.flatten should contain only (
        TezosConfiguration(
          "alphanet",
          Newest,
          TezosNodeConfiguration(hostname = "localhost", port = 8732, protocol = "http")
        ),
        TezosConfiguration(
          "alphanet-staging",
          Newest,
          TezosNodeConfiguration(hostname = "nautilus.cryptonomic.tech", port = 8732, protocol = "https", pathPrefix = "tezos/alphanet/")
        )
      )

    }

    "extract a configuration map that includes a unknown platforms" in {
      import Platforms._
      import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

      val cfg = ConfigFactory.parseString(
        """
          | platforms.tezos : {
          |  alphanet: {
          |    node: {
          |      protocol: "http",
          |      hostname: "localhost",
          |      port: 8732
          |      pathPrefix: ""
          |    }
          |  }
          | }
          | platforms.ethereum : {
          |   some-network: {
          |     custom: "configuration"
          |   }
          | }
        """.stripMargin)

      val typedConfig = pureconfig.loadConfig[PlatformsConfiguration](conf = cfg, namespace = "platforms")
      typedConfig shouldBe 'right

      val Right(PlatformsConfiguration(platforms)) = typedConfig

      platforms.keys should contain only (Tezos, UnknownPlatform("ethereum"))

      platforms.values.flatten should contain only (
        TezosConfiguration(
          "alphanet",
          Newest,
          TezosNodeConfiguration(hostname = "localhost", port = 8732, protocol = "http")
        ),
        UnknownPlatformConfiguration("some-network")
      )

    }

    "extract the client host pool configuration for streaming http" in {
      import scala.collection.JavaConverters._
      import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

      val typedConfig = loadAkkaStreamingClientConfig(namespace = "akka.tezos-streaming-client")
      typedConfig shouldBe 'right

      val Right(HttpStreamingConfiguration(pool)) = typedConfig

      //verify expected entries in the pool config
      val configKeys = pool.getConfig("akka.http.host-connection-pool").entrySet.asScala.map(_.getKey)

      configKeys should contain allOf (
        "min-connections",
        "max-connections",
        "max-retries",
        "max-open-requests",
        "pipelining-limit",
        "idle-timeout",
        "pool-implementation",
        "response-entity-subscription-timeout"
        )

      }

      "fail to extract the client host pool configuration with the wrong namespace" in {
        import pureconfig.error.ThrowableFailure
        import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

        val typedConfig = loadAkkaStreamingClientConfig(namespace = "tezos-streaming-client")
        typedConfig shouldBe 'left

        val Left(failures) = typedConfig

        failures.toList should have size 1

        failures.head shouldBe a [ThrowableFailure]

        failures.head.asInstanceOf[ThrowableFailure].throwable shouldBe a [com.typesafe.config.ConfigException.Missing]

      }

  }

}