package tech.cryptonomic.conseil.indexer.config

import com.typesafe.config.{Config, ConfigFactory}
import tech.cryptonomic.conseil.common.config.Platforms.{TezosConfiguration, TezosNodeConfiguration}
import tech.cryptonomic.conseil.indexer.config.LorreAppConfig.Loaders._
import tech.cryptonomic.conseil.indexer.config.ConfigDepthUtil.Natural
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class LorreAppConfigTest extends ConseilSpec {

  "LorreAppConfig.Natural" should {
      "match a valid positive integer string" in {
        "10" match {
          case Natural(value) => value shouldBe 10
          case _ => fail("the matcher didn't correctly identify an integer")
        }
      }

      "refuse a zero integer string" in {
        "0" match {
          case Natural(value) => fail(s"a zero string shouldn't match as $value")
          case _ =>
        }
      }

      "refuse a negative integer string" in {
        "-10" match {
          case Natural(value) => fail(s"a negative integer string shouldn't match as $value")
          case _ =>
        }
      }

      "refuse a non-numeric string" in {
        "abc10" match {
          case Natural(value) => fail(s"a generic string shouldn't match as $value")
          case _ =>
        }
      }
    }

  "LorreAppConfig.Loaders" should {
      "extract platforms configuration properly in Kebab-Case convention" in {
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
                                              |      path-prefix: "tezos/alphanet/"
                                              |    }
                                              |    db {
                                              |      dataSourceClass: "org.postgresql.ds.PGSimpleDataSource"
                                              |      properties {
                                              |        user: "foo"
                                              |        password: "bar"
                                              |        url: "jdbc:postgresql://localhost:5432/postgres"
                                              |      }
                                              |      numThreads: 10
                                              |      maxConnections: 10
                                              |    }
                                              |  }
                                              |]
        """.stripMargin)

        val typedConfig = loadPlatformConfiguration("tezos", "alphanet", config = Some(cfg))
        typedConfig.right.value shouldBe TezosConfiguration(
          "alphanet",
          enabled = true,
          TezosNodeConfiguration("localhost", 8732, "http", "tezos/alphanet/"),
          cfg.getConfigList("platforms").get(0).getConfig("db"),
          None
        )
      }

      "not extract fields for platforms configuration in CamelCase" in {
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
                                            |      pathPrefix: "tezos/alphanet/"
                                            |    }
                                            |    db {
                                            |      dataSourceClass: "org.postgresql.ds.PGSimpleDataSource"
                                            |      properties {
                                            |        user: "foo"
                                            |        password: "bar"
                                            |        url: "jdbc:postgresql://localhost:5432/postgres"
                                            |      }
                                            |      numThreads: 10
                                            |      maxConnections: 10
                                            |    }
                                            |  }
                                            |]
        """.stripMargin)

        val typedConfig = loadPlatformConfiguration("tezos", "alphanet", config = Some(cfg))
        typedConfig.right.value shouldBe TezosConfiguration(
          "alphanet",
          enabled = true,
          TezosNodeConfiguration("localhost", 8732, "http"),
          cfg.getConfigList("platforms").get(0).getConfig("db"),
          None
        )
      }

      "extract the client host pool configuration for streaming http" in {
        import scala.collection.JavaConverters._

        val typedConfig = loadAkkaStreamingClientConfig(namespace = "akka.streaming-client")
        typedConfig shouldBe 'right

        val Right(HttpStreamingConfiguration(pool)) = typedConfig

        //verify expected entries in the pool config
        val configKeys = pool.getConfig("akka.http.host-connection-pool").entrySet.asScala.map(_.getKey)

        configKeys should contain allOf (
          "min-connections",
          "max-connections",
          "max-retries",
          "max-open-requests",
          "max-connection-lifetime",
          "pipelining-limit",
          "base-connection-backoff",
          "max-connection-backoff",
          "idle-timeout",
          "response-entity-subscription-timeout"
        )

      }

      "fail to extract the client host pool configuration with the wrong namespace" in {
        import pureconfig.error.ThrowableFailure

        val typedConfig = loadAkkaStreamingClientConfig(namespace = "streaming-client")
        typedConfig shouldBe 'left

        val Left(failures) = typedConfig

        failures.toList should have size 1

        failures.head shouldBe a[ThrowableFailure]

        failures.head.asInstanceOf[ThrowableFailure].throwable shouldBe a[com.typesafe.config.ConfigException.Missing]

      }
    }

}
