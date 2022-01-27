package tech.cryptonomic.conseil.smoke.tests.suites

import cats.effect.IO
import io.circe.{parser, Json}
import org.http4s.{Header, MediaType}
import org.http4s.circe._
import org.http4s.blaze.client._
import org.http4s.client.dsl.io._
import org.http4s.implicits._
import org.http4s.dsl.io._
import org.http4s.headers._
import tech.cryptonomic.conseil.smoke.tests.RegressionFixtures

import java.util.concurrent.Executors
import scala.Console.{GREEN, RED, RESET}
import scala.concurrent.ExecutionContext
import org.typelevel.ci.CIString

class EthereumRegressionSuite(val configfile: String, val syncNetwork: Option[String])
    extends RegressionSuite
    with RegressionFixtures {

  val syncPlatform: String = "ethereum"
  val referenceBlockHash = "0x5f91e535ee4d328725b869dd96f4c42059e3f2728dfc452c32e5597b28ce68d6"
  val depth = "5000"

  private val pool = Executors.newCachedThreadPool()
  private val clientExecution: ExecutionContext = ExecutionContext.fromExecutor(pool)

  private val clientBuild = BlazeClientBuilder[IO].withExecutionContext(clientExecution)

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
          |  "state_root" : "0x6d9631ccd8ed5f982377f89d3bd11449c1dc2d12299a6c3e4b31d5d8e7d004f3",
          |  "difficulty" : 178040237088,
          |  "timestamp" : 1438286864000,
          |  "size" : 546,
          |  "gas_used" : 0,
          |  "transactions_root" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
          |  "nonce" : "0x3959ec7623b538b0",
          |  "logs_bloom" : "0x00000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000000",
          |  "sha3_uncles" : "0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347",
          |  "mix_hash" : "0xd64e30b1ef7f302873665e9dd8857b849f16b84be3ae8db027224729bf689a51",
          |  "uncles" : null,
          |  "miner" : "0x48040276e9c17ddbe5c8d2976245dcd0235efa43",
          |  "extra_data" : "0x476574682f76312e302e302d30636463373634372f6c696e75782f676f312e34",
          |  "hash" : "0x5f91e535ee4d328725b869dd96f4c42059e3f2728dfc452c32e5597b28ce68d6",
          |  "parent_hash" : "0x82e95c1ee3a98cd0646225b5ae6afc0b0229367b992df97aeb669c898657a4bb",
          |  "receipts_root" : "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421",
          |  "total_difficulty" : 355553772216163,
          |  "gas_limit" : 5000,
          |  "level" : 5000
          }""".stripMargin
      )
      .ensuring(_.isRight)
      .right
      .get

    val endpoint = uri"http://localhost:1337/v2/data/ethereum/mainnet/blocks/head"

    clientBuild.resource.use { client =>
      val req = GET(
        endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header.Raw(CIString("apiKey"), apiKey)
      )

      IO(println("Running test on /v2/data/ethereum/mainnet/blocks/head")) *>
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
