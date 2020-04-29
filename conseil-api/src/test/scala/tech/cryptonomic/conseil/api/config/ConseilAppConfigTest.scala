package tech.cryptonomic.conseil.api.config

import com.typesafe.config.ConfigFactory
import org.scalatest.{EitherValues, Matchers, WordSpec}
import tech.cryptonomic.conseil.api.config.ConseilAppConfig.Implicits._
import tech.cryptonomic.conseil.common.config.Platforms

class ConseilAppConfigTest extends WordSpec with Matchers with EitherValues {

  "ConseilAppConfig" should {
      "extract the correct configuration map for Tezos platform's networks" in {
        import Platforms._

        val cfg = ConfigFactory.parseString("""
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

        platforms.keys should contain only Tezos

        platforms.values.flatten should contain only (
          TezosConfiguration(
            "alphanet",
            TezosNodeConfiguration(hostname = "localhost", port = 8732, protocol = "http"),
            None
          ),
          TezosConfiguration(
            "alphanet-staging",
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

      "extract a configuration map that includes a unknown platforms" in {
        import Platforms._

        val cfg = ConfigFactory.parseString("""
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
            TezosNodeConfiguration(hostname = "localhost", port = 8732, protocol = "http"),
            None
          ),
          UnknownPlatformConfiguration("some-network")
        )

      }

      "adapt multiple pureconfig reader failures to a single reason" in {
        import java.nio.file.Paths

        import pureconfig.error._

        val failure1: ConfigReaderFailure = CannotParse("cannot parse", location = None)
        val failure2: ConfigReaderFailure = CannotReadFile(path = Paths.get("no/path/to/exit"), reason = None)
        val failure3: ConfigReaderFailure = ThrowableFailure(new Exception("generic failure"), location = None)
        val failures = ConfigReaderFailures(failure1, failure2 :: failure3 :: Nil)

        val reason = reasonFromReadFailures(failures)
        reason shouldBe a[FailureReason]
        reason.description shouldBe "Unable to parse the configuration: cannot parse. Unable to read file no/path/to/exit. generic failure."
      }

      "fold many parse results into a single failure if any is present" in {
        import cats.syntax.either._
        import pureconfig.error._

        val reason1 = CannotConvert(value = "this", toType = "that", because = "reasons")
        val reason2 = EmptyStringFound("something")
        val success = "did it!"

        val results: List[Either[FailureReason, String]] = reason1
            .asLeft[String] :: success.asRight[FailureReason] :: reason2.asLeft[String] :: Nil
        val folded = foldReadResults(results)(_.mkString(""))

        folded shouldBe 'left
        val leftValue = folded.left.value
        leftValue shouldBe a[FailureReason]
        leftValue.description shouldBe "Cannot convert 'this' to that: reasons. Empty string found when trying to convert to something."

      }

      "extract the correct platforms type" in {
        import Platforms._

        import scala.collection.JavaConverters._

        val cfg = ConfigFactory.parseString("""
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

        cfg
          .getObject("platforms")
          .keySet
          .asScala
          .map(BlockchainPlatform.fromString) should contain only (Tezos, UnknownPlatform("ethereum"))
      }
    }

}
