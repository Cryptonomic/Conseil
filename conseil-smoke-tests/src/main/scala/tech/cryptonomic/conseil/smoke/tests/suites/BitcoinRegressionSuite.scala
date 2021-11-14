package tech.cryptonomic.conseil.smoke.tests.suites

import cats.effect.IO
import io.circe.{parser, Json}
import org.http4s.circe._
import org.http4s.blaze.client._
import org.http4s.client.dsl.io._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.implicits._
import org.http4s.{Header, MediaType}
import tech.cryptonomic.conseil.smoke.tests.RegressionFixtures

import java.util.concurrent.Executors
import scala.Console.{GREEN, RED, RESET}
import scala.concurrent.ExecutionContext
import org.typelevel.ci.CIString

class BitcoinRegressionSuite(val configfile: String, val syncNetwork: Option[String])
    extends RegressionSuite
    with RegressionFixtures {

  val syncPlatform: String = "bitcoin"
  val referenceBlockHash = "000000004d78d2a8a93a1d20a24d721268690bebd2b51f7e80657d57e226eef9"
  val depth = "5000"

  private val pool = Executors.newCachedThreadPool()
  private val clientExecution: ExecutionContext = ExecutionContext.fromExecutor(pool)

  private val clientBuild = BlazeClientBuilder[IO](clientExecution)

  /** will run the regression suite against the given endpoints */
  def runRegressionSuite: IO[Unit] =
    Setup.conseilProcess.use { _ =>
      infoEndpoint *> blockHeadEndpoint
    }.flatMap {
      case Left(error) => IO(println(s"$RED Regression test failed: ${error.getMessage}$RESET"))
      case Right(_) => IO(println(s"$GREEN Regression test passed: OK$RESET"))
    }

  /** info */
  val infoEndpoint: IO[Either[Throwable, Json]] = {

    val expected =
      Json.obj(
        "application" -> Json.fromString("Conseil"),
        "version" -> Json.fromString("A non empty version string")
      )

    val endpoint = uri"http://localhost:1337/info"

    clientBuild.resource.use { client =>
      val req = GET(
        endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header.Raw(CIString("apiKey"), apiKey)
      )

      IO(println("Running test on /info")) *>
        client.expect[Json](req).attempt.map {
          case Right(json) =>
            Either.cond(
              json \\ "application" == expected \\ "application" && (json \\ "version").nonEmpty,
              right = json,
              left = new Exception(
                s"Failed to match on $endpoint: expected \n${expected.spaces2} \n but found \n ${json.spaces2}"
              )
            )
          case left => left
        }
    }
  }

  val blockHeadEndpoint: IO[Either[Throwable, Json]] = {

    val expected: Json = parser
      .parse(
        """{
          |  "weight" : 864,
          |  "difficulty" : 1,
          |  "size" : 216,
          |  "nonce" : 3600108085,
          |  "height" : 5000,
          |  "median_time" : 1235133137000,
          |  "version" : 1,
          |  "hash" : "000000004d78d2a8a93a1d20a24d721268690bebd2b51f7e80657d57e226eef9",
          |  "next_block_hash" : "00000000284bcd658fd7a76f5a88ee526f18592251341a05fd7f3d7abaf0c3ec",
          |  "stripped_size" : 216,
          |  "n_tx" : 1,
          |  "previous_block_hash" : "00000000c9a61ea18fbf06b03e10033355e6eab3de038d975f40af9babbe0658",
          |  "version_hex" : "00000001",
          |  "merkle_root" : "b0e585927e1737d07bd8157a2ba9f7615ef8ecd2af6d03523e51b4d23e134b6a",
          |  "time" : 1235135895000,
          |  "bits" : "1d00ffff",
          |  "chain_work" : "0000000000000000000000000000000000000000000000000000138913891389"
          }""".stripMargin
      )
      .ensuring(_.isRight)
      .right
      .get

    val endpoint = uri"http://localhost:1337/v2/data/bitcoin/mainnet/blocks/head"

    clientBuild.resource.use { client =>
      val req = GET(
        endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header.Raw(CIString("apiKey"), apiKey)
      )

      IO(println("Running test on /v2/data/bitcoin/mainnet/blocks/head")) *>
        client.expect[Json](req).attempt.map {
          case Right(json) =>
            Either.cond(
              json == expected,
              right = json,
              left = new Exception(
                s"Failed to match on $endpoint: expected \n${expected.spaces2} \n but found \n ${json.spaces2}"
              )
            )
          case left => left
        }
    }
  }
}
