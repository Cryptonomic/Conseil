package tech.cryptonomic.conseil.config

import org.scalatest.{EitherValues, Matchers, OptionValues, WordSpec}
import com.typesafe.config.ConfigFactory
import tech.cryptonomic.conseil.util.ConfigUtil
import tech.cryptonomic.conseil.tezos.Tables

class ConfigUtilTest extends WordSpec with Matchers with EitherValues with OptionValues {

  "the config.Natural matcher" should {

      "match a valid positive integer string" in {
        "10" match {
          case Natural(value) => value shouldBe 10
          case _ => fail("the matcher didn't correcly identify an integer")
        }
      }

      "refuse a zero integer string" in {
        "0" match {
          case Natural(value) => fail(s"a zero string shouldn't match as $value")
          case _ =>
        }
      }

      "refuse a negatige integer string" in {
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

  "ConfigUtil" should {

      "adapt multiple pureconfig reader failures to a single reason" in {
        import tech.cryptonomic.conseil.util.{ConfigUtil => sut}
        import pureconfig.error._
        import java.nio.file.Paths

        val failure1: ConfigReaderFailure = CannotParse("cannot parse", location = None)
        val failure2: ConfigReaderFailure = CannotReadFile(path = Paths.get("no/path/to/exit"), reason = None)
        val failure3: ConfigReaderFailure = ThrowableFailure(new Exception("generic failure"), location = None)
        val failures = ConfigReaderFailures(failure1, failure2 :: failure3 :: Nil)

        val reason = sut.Pureconfig.reasonFromReadFailures(failures)
        reason shouldBe a[FailureReason]
        reason.description shouldBe "Unable to parse the configuration: cannot parse. Unable to read file no/path/to/exit. generic failure."
      }

      "fold many parse results into a single failure if any is present" in {
        import tech.cryptonomic.conseil.util.{ConfigUtil => sut}
        import pureconfig.error._
        import cats.syntax.either._

        val reason1 = CannotConvert(value = "this", toType = "that", because = "reasons")
        val reason2 = EmptyStringFound("something")
        val success = "did it!"

        val results: List[Either[FailureReason, String]] = reason1
            .asLeft[String] :: success.asRight[FailureReason] :: reason2.asLeft[String] :: Nil
        val folded = sut.Pureconfig.foldReadResults(results)(_.mkString(""))

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

      "extract the correct configuration map for Tezos platform's networks" in {
        import Platforms._
        import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

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

        platforms.keys should contain only (Tezos)

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
        import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

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

      "extract a configuration map that includes a known TNS contract" in {
        import Platforms._
        import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

        val cfg = ConfigFactory.parseString("""
          | platforms.tezos : {
          |  alphanet: {
          |    node: {
          |      protocol: "http",
          |      hostname: "localhost",
          |      port: 8732
          |      pathPrefix: ""
          |    },
          |    tns: {
          |      name: "Cryptonomic TNS",
          |      contractType: "TNS",
          |      accountId: "KT1tns"
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
            TezosNodeConfiguration(hostname = "localhost", port = 8732, protocol = "http"),
            Some(TNSContractConfiguration("Cryptonomic TNS", "TNS", "KT1tns"))
          )
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

        failures.head shouldBe a[ThrowableFailure]

        failures.head.asInstanceOf[ThrowableFailure].throwable shouldBe a[com.typesafe.config.ConfigException.Missing]

      }

    }

  "ConfigUtil.Csv" should {
      import kantan.csv.generic._
      import shapeless._

      val sut = ConfigUtil.Csv

      "read a csv source file and map to the corresponding rows for token contracts" in {

        //will provide the HList representation of the row, to be passed as a type for the method
        val genericRow = Generic[Tables.RegisteredTokensRow]

        val rows: List[Tables.RegisteredTokensRow] =
          sut
            .readRowsFromCsv[Tables.RegisteredTokensRow, genericRow.Repr](
              csvSource = getClass.getResource("/registered_tokens/testnet.csv"),
              separator = '|'
            )
            .value

        rows should have size 1

        rows.head shouldBe Tables.RegisteredTokensRow(1, "USDTez", "FA1.2", "tz1eEnQhbwf6trb8Q8mPb2RaPkNk2rN7BKi9")
      }

      "use a database table to find the csv file and map to the corresponding rows for token contracts" in {

        val rows: List[Tables.RegisteredTokensRow] =
          sut
            .readTableRowsFromCsv(
              table = Tables.RegisteredTokens,
              network = "testnet",
              separator = '|'
            )
            .value

        rows should have size 1

        rows.head shouldBe Tables.RegisteredTokensRow(1, "USDTez", "FA1.2", "tz1eEnQhbwf6trb8Q8mPb2RaPkNk2rN7BKi9")
      }

      "fail to read the csv data if the network doesn't have a matching config file" in {

        val rows: Option[List[Tables.RegisteredTokensRow]] =
          sut.readTableRowsFromCsv(table = Tables.RegisteredTokens, network = "nonsense")

        rows shouldBe empty

      }

      "fail to read the csv data if the file doesn't match the expected tabular format" in {

        //will provide the HList representation of the row, to be passed as a type for the method
        val genericRow = Generic[Tables.RegisteredTokensRow]

        val rows: List[Tables.RegisteredTokensRow] =
          sut
            .readRowsFromCsv[Tables.RegisteredTokensRow, genericRow.Repr](
              csvSource = getClass.getResource("/registered_tokens/testnet.csv")
            )
            .value

        rows shouldBe empty

      }

    }
}
