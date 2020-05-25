package tech.cryptonomic.conseil.indexer.config

import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.indexer.config.LorreAppConfig.Natural
import tech.cryptonomic.conseil.indexer.config.LorreAppConfig.Loaders._

class LorreAppConfigTest extends WordSpec with Matchers {

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
          "pipelining-limit",
          "idle-timeout",
          "pool-implementation",
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
