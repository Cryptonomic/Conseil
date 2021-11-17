package tech.cryptonomic.conseil.smoke.tests.suites

import java.util.concurrent.Executors

import cats.effect.IO
import io.circe.{parser, Json}
import org.http4s.circe._
import org.http4s.client.dsl.io._
import org.http4s.dsl.io._
import org.http4s.headers._
import org.http4s.{Header, MediaType}
import tech.cryptonomic.conseil.smoke.tests.RegressionFixtures

import scala.Console.{GREEN, RED, RESET}
import scala.concurrent.ExecutionContext
import scala.annotation.tailrec
import io.circe.ACursor
import org.http4s.blaze.client.BlazeClientBuilder
import org.http4s.implicits._
import org.typelevel.ci.CIString

/** Currently can be used to test any conseil instance that loaded blocks levels 1 to 5000
  * against predefined expectations on the responses
  */
class TezosRegressionSuite(val configfile: String, val syncNetwork: Option[String])
    extends RegressionSuite
    with RegressionFixtures {

  /* We're currently assuming running on carthage up to a given level
   * we should eventually pass-in everything as a composite argument, like a csv?
   */
  val syncPlatform = "tezos"
  val referenceBlockHash = "BKosYnQbd4zhakKey6YdjyC96sZJ8K11yw8FJWbHAgfJ1yG4EFC" // block at level 5000
  val depth = "5000"

  private val pool = Executors.newCachedThreadPool()
  private val clientExecution: ExecutionContext = ExecutionContext.fromExecutor(pool)

  private val clientBuild = BlazeClientBuilder[IO].withExecutionContext(clientExecution)

  /** will run the regression suite against the given endpoints */
  override def runRegressionSuite: IO[Unit] =
    Setup.conseilProcess.use { _ =>
      infoEndpoint *> blockHeadEndpoint *> groupingPredicatesQueryEndpoint
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

  /** block head */
  val blockHeadEndpoint: IO[Either[Throwable, Json]] = {

    val expected: Json = parser
      .parse(
        """ {
          |  "level" : 5000,
          |  "proto" : 1,
          |  "predecessor" : "BLeaKFinHqxcpiU2azhRnp2bmfedTsJWTEP91EE2ArU2EHQp8gQ",
          |  "timestamp" : 1575181381000,
          |  "validationPass" : 4,
          |  "fitness" : "01,0000000000001387",
          |  "context" : "CoVb3qi8DzAqT7dxgtJvTdGCa3TyAyrEWksb3qnGBNmk9Nq9zaRB",
          |  "signature" : "sigZHCzMRk5teWj5u9GhAc4CoKg8XTy4N7HbCMBthasEAsGs2qyzvjUDxSYNmaMmvtz5uGbSqBpQdcoKDz98hS15NjkWApy8",
          |  "protocol" : "PsBabyM1eUXZseaJdmXFApDSBqj8YBfwELoxZHHW77EMcAbbwAS",
          |  "chainId" : "NetXjD3HPJJjmcd",
          |  "hash" : "BKosYnQbd4zhakKey6YdjyC96sZJ8K11yw8FJWbHAgfJ1yG4EFC",
          |  "operationsHash" : "LLoagDbF1dDEPWYrNw5HfPWHAY94MA75LZHbR8bgSHZ8EaTekif2p",
          |  "periodKind" : "proposal",
          |  "currentExpectedQuorum" : 5800,
          |  "baker" : "tz1RomaiWJV3NFDZWTMVR2aEeHknsn3iF5Gi",
          |  "consumedGas" : 0,
          |  "metaLevel" : 5000,
          |  "metaLevelPosition" : 4999,
          |  "metaCycle" : 2,
          |  "metaCyclePosition" : 903,
          |  "metaVotingPeriod" : 2,
          |  "metaVotingPeriodPosition" : 903,
          |  "expectedCommitment" : false,
          |  "priority" : 0,
          |  "utcYear" : 2019,
          |  "utcMonth" : 12,
          |  "utcDay" : 1,
          |  "utcTime" : "07:23:01"
          |}""".stripMargin
      )
      .ensuring(_.isRight)
      .right
      .get

    val endpoint = uri"http://localhost:1337/v2/data/tezos/mainnet/blocks/head"

    clientBuild.resource.use { client =>
      val req = GET(
        endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header.Raw(CIString("apiKey"), apiKey)
      )

      IO(println("Running test on /v2/data/tezos/mainnet/blocks/head")) *>
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

  def groupingPredicatesQueryEndpoint: IO[Either[Throwable, Json]] = {
    val callBody = parser
      .parse(GroupingPredicatesQuery.requestJsonPayload)
      .ensuring(_.isRight)
      .right
      .get

    val expected = parser
      .parse(GroupingPredicatesQuery.responseJsonContent)
      .ensuring(_.isRight)
      .right
      .get

    val endpoint = uri"http://localhost:1337/v2/data/tezos/mainnet/operations"

    /* Assuming a json array is passed-in, recursively remove the field from
     * all objects in it.
     * We use cursors to verify the presence of the field and remove it,
     * then we check at each recursion step if there's yet another sibling on the
     * array, else we return the last object.
     * Finding an object that doesn't have the field signals the recursion termination.
     * We return the whole array back
     */
    def stripObjectsField(fieldName: String, from: Json): Option[Json] = {
      @tailrec
      def loop(cursor: ACursor): Option[Json] = {
        val field = cursor.downField(fieldName)
        if (field.succeeded) {
          val stripped = field.delete
          loop(stripped.right.success.getOrElse(stripped))
        } else cursor.top
      }

      loop(from.hcursor.downArray)
    }

    clientBuild.resource.use { client =>
      val req = POST(
        body = callBody,
        uri = endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header.Raw(CIString("apiKey"), apiKey)
      )

      IO(println("Running test on /v2/data/tezos/mainnet/operations for grouped predicates query")) *>
        client.expect[Json](req).attempt.map {
          case Right(json) =>
            val normalized = stripObjectsField("operation_id", from = json).get
            Either.cond(
              normalized == expected,
              right = json,
              left = new Exception(
                s"Failed to match on $endpoint: expected \n${expected.spaces2} \n but found \n ${normalized.spaces2}"
              )
            )
          case left => left
        }
    }

  }

}
