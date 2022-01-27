package tech.cryptonomic.conseil.api.config

import com.typesafe.config.ConfigFactory
import pureconfig.error.ConvertFailure
import pureconfig.generic.auto._
import tech.cryptonomic.conseil.api.config.ConseilAppConfig._
import tech.cryptonomic.conseil.common.config.Platforms
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

import scala.concurrent.duration._
import scala.language.postfixOps

class ConseilAppConfigTest extends ConseilSpec {

  "ConseilAppConfig" should {
      "extract the correct configuration list for Tezos platform's networks using default readers" in {
        import Platforms._

        val cfg = ConfigFactory.parseString("""
                                            |platforms: [
                                            |  {
                                            |    name: "tezos"
                                            |    baker-rolls-size: 8000
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
                                            |  },
                                            |  {
                                            |    name: "tezos"
                                            |    baker-rolls-size: 8000
                                            |    network: "alphanet-staging"
                                            |    enabled: false,
                                            |    node: {
                                            |      protocol: "https"
                                            |      hostname: "nautilus.cryptonomic.tech",
                                            |      port: 8732
                                            |      path-prefix: "tezos/alphanet-staging/"
                                            |      trace-calls: "true"
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

        val typedConfig = pureconfig.loadConfig[PlatformsConfiguration](conf = cfg)

        typedConfig.right.value.platforms should contain only (
          TezosConfiguration(
            "alphanet",
            enabled = true,
            TezosNodeConfiguration("localhost", 8732, "http", "tezos/alphanet/"),
            BigDecimal.decimal(8000),
            cfg.getConfigList("platforms").get(0).getConfig("db"),
            None
          ),
          TezosConfiguration(
            "alphanet-staging",
            enabled = false,
            TezosNodeConfiguration(
              "nautilus.cryptonomic.tech",
              8732,
              "https",
              "tezos/alphanet-staging/",
              traceCalls = true
            ),
            BigDecimal.decimal(8000),
            cfg.getConfigList("platforms").get(1).getConfig("db"),
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
                                              |    baker-rolls-size: 8000
                                              |    network: "alphanet"
                                              |    enabled: true,
                                              |    node: {
                                              |      protocol: "http",
                                              |      hostname: "localhost",
                                              |      port: 8732
                                              |      path-prefix: ""
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
                                              |  },
                                              |  {
                                              |    name: "OpenChain"
                                              |    network: "alphanet-staging"
                                              |    enabled: false,
                                              |    node: {
                                              |      protocol: "https"
                                              |      hostname: "nautilus.cryptonomic.tech",
                                              |      port: 8732
                                              |      path-prefix: "openchain/alphanet/"
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

        val typedConfig = pureconfig.loadConfig[PlatformsConfiguration](conf = cfg)
        typedConfig.left.value.toList should have size 1
        typedConfig.left.value.toList.head shouldBe an[ConvertFailure]
      }

      "extract None, when configuration for Nautilus Cloud does not exist" in {
        val config = ConfigFactory.parseString("")
        val typedConfig =
          pureconfig.loadConfig[Option[NautilusCloudConfiguration]](conf = config, namespace = "nautilus-cloud")

        typedConfig.value shouldBe empty
      }

      "extract Some, when configuration for Nautilus Cloud exists" in {
        val config = ConfigFactory.parseString("""
            |nautilus-cloud {
            |  enabled: true
            |  host: "http://localhost"
            |  port: 1234
            |  path: "apiKeys/dev" // here should be an environment name after '/'
            |  key: "exampleApiKeyDev"
            |  delay: 10 seconds
            |  interval: 30 seconds
            |}
            |""".stripMargin)
        val typedConfig =
          pureconfig.loadConfig[Option[NautilusCloudConfiguration]](conf = config, namespace = "nautilus-cloud")

        typedConfig.right.value.value shouldBe NautilusCloudConfiguration(
          true,
          "http://localhost",
          1234,
          "apiKeys/dev",
          "exampleApiKeyDev",
          10 seconds,
          30 seconds
        )
      }
    }

}
