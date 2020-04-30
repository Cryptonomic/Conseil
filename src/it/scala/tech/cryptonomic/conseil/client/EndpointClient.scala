package tech.cryptonomic.conseil.client

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.sys.process._
import scala.Console.{GREEN, RED, RESET}
import org.http4s.client.blaze._
import org.http4s.Header
import org.http4s.headers._
import org.http4s.MediaType
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.Uri
import io.circe.{parser, Json}
import cats.effect.IO
import cats.syntax.all._
import com.typesafe.config.ConfigFactory
import scala.util.Try
import cats.effect.Resource

object DataEndpointsClientProbe {

  /** Creates a valid client probe or fails trying
    *
    * @param configfile should point to a valid configuration for Lorre/Conseil
    * @param syncNetwork can be a valid configuration entry for network to sync to
    * @return a client to run regression tests
    */
  def apply(configfile: String, syncNetwork: Option[String] = None): IO[DataEndpointsClientProbe] =
    IO(new DataEndpointsClientProbe(configfile, syncNetwork))

}

/** Currently can be used to test any conseil instance that loaded blocks levels 1 to 1000
  * against predefined expectations on the responses
  */
class DataEndpointsClientProbe private (configfile: String, syncNetwork: Option[String]) extends RegressionFixtures {

  /* We're currently assuming running on carthage up to a given level
   * we should eventually pass-in everything as a composite argument, like a csv?
   */
  private val referenceBlockHash = "BKosYnQbd4zhakKey6YdjyC96sZJ8K11yw8FJWbHAgfJ1yG4EFC" // block at level 5000
  private val depth = "5000"

  //this is supposed to throw an error if there's anything wrong, and be catched by the companion smart constructor
  private val apiKey = {
    val key = Try(
      ConfigFactory.load().getStringList("conseil.security.apiKeys.keys").get(0)
    )
    key.failed.foreach(_ => println("No apiKey found in configuration, I can't test conseil api without"))
    key.get
  }

  object Setup {
    implicit val ioShift = IO.contextShift(scala.concurrent.ExecutionContext.global)
    val timer = IO.timer(scala.concurrent.ExecutionContext.global)

    /* We might wanna start with only 10k blocks */
    private def runLorre(network: String) =
      Process(
        command = Seq("sbt", s"runLorre -v -d $depth -h $referenceBlockHash $network"),
        cwd = None,
        extraEnv = "SBT_OPTS" -> s"-Dconfig.file=$configfile"
      )
    private val runConseil =
      Process(
        command = Seq("sbt", "runConseil -v"),
        cwd = None,
        extraEnv = ("SBT_OPTS" -> s"-Dconfig.file=$configfile")
      )

    private def syncData(network: String) =
      IO((runLorre(network).!).ensuring(_ == 0, "lorre failed to load correctly"))

    private def startConseil: IO[Process] =
      for {
        _ <- if (syncNetwork.nonEmpty) syncData(syncNetwork.get) else IO(0)
        proc <- IO(runConseil.run())
        _ <- IO(println("waiting for conseil to start"))
        _ <- timer.sleep(15.seconds)
      } yield proc

    val conseilProcess = Resource.make(startConseil) { conseil =>
      IO(println(s"Stopping conseil process $conseil")) *>
        IO(conseil.destroy())
    }

  }

  private val pool = Executors.newCachedThreadPool()
  private val clientExecution: ExecutionContext = ExecutionContext.fromExecutor(pool)
  implicit private val shift = IO.contextShift(clientExecution)

  private val clientBuild = BlazeClientBuilder[IO](clientExecution)

  /** will run the regression suite against the given endpoints
    * @param configfile the file path to the configuration file used to run the regressions
    * @param syncToNetwork if a network name is provided, a Lorre instance will load data to the local db from the specified network
    */
  def runRegressionSuite: IO[Unit] =
    Setup.conseilProcess.use { _ =>
      infoEndpoint *> blockHeadEndpoint *> groupingPredicatesQueryEndpoint
    }.flatMap {
      case Left(error) => IO(println(s"$RED Regression test failed: ${error.getMessage()}$RESET"))
      case Right(value) => IO(println(s"$GREEN Regression test passed: OK$RESET"))
    }

  /** info */
  val infoEndpoint: IO[Either[Throwable, Json]] = {

    val expected =
      Json.obj(
        "application" -> Json.fromString("Conseil"),
        "version" -> Json.fromString("A non empty version string")
      )

    val endpoint = Uri.uri("http://localhost:1337/info")

    clientBuild.resource.use { client =>
      val req = GET(
        endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header("apiKey", apiKey)
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

    val endpoint = Uri.uri("http://localhost:1337/v2/data/tezos/mainnet/blocks/head")

    clientBuild.resource.use { client =>
      val req = GET(
        endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header("apiKey", apiKey)
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

    val endpoint = Uri.uri("http://localhost:1337/v2/data/tezos/mainnet/operations")

    clientBuild.resource.use { client =>
      val req = POST(
        body = callBody,
        uri = endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header("apiKey", apiKey)
      )

      IO(println("Running test on /v2/data/tezos/mainnet/operations for grouped predicates query")) *>
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
