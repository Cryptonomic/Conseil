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
import scala.io.Source
import com.typesafe.config.ConfigFactory
import scala.util.Try

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
class DataEndpointsClientProbe private (configfile: String, syncNetwork: Option[String]) {

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
        command = Seq("sbt", s"runLorre -v -d 10000 -h BLc7tKfzia9hnaY1YTMS6RkDniQBoApM4EjKFRLucsuHbiy3eqt $network"),
        cwd = None,
        extraEnv = "SBT_OPTS" -> s"-Dconfig.file=$configfile"
      )
    private val runConseil =
      Process(
        command = Seq("sbt", "runConseil -v"),
        cwd = None,
        extraEnv = ("SBT_OPTS" -> s"-Dconfig.file=$configfile")
      )
    private def grepConseilPID(): String = (Seq("jps", "-m") #| Seq("grep", "runConseil")).!!.split(" ").head
    private def stopPid(pid: String) = Seq("kill", "-2", pid).!

    private def syncData(network: String) =
      IO((runLorre(network).!).ensuring(_ == 0, "lorre failed to load correctly"))

    private def startConseil: IO[Process] =
      for {
        _ <- if (syncNetwork.nonEmpty) syncData(syncNetwork.get) else IO(0)
        proc <- IO(runConseil.run())
        _ <- IO(println("waiting for conseil to start"))
        _ <- timer.sleep(30.seconds)
      } yield proc

    def usingConseil[A](testBlock: => IO[A]) =
      startConseil.bracket(
        use = _ => testBlock
      )(
        release = _ =>
          for {
            pid <- IO(grepConseilPID())
            _ <- IO(println(s"Stopping conseil at PID $pid"))
            _ <- IO(stopPid(pid))
          } yield ()
      )

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
    Setup.usingConseil {
      infoEndpoint *>
        blockHeadEndpoint *>
        groupingPredicatesQueryEndpoint
    }.flatMap {
      case Left(error) => IO(println(s"$RED Regression test failed: ${error.getMessage()}$RESET"))
      case Right(value) => IO(println(s"$GREEN Regression test passed: OK$RESET"))
    }

  /** info */
  def infoEndpoint: IO[Either[Throwable, Json]] = {

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
  def blockHeadEndpoint: IO[Either[Throwable, Json]] = {

    val expected: Json = parser
      .parse(
        """{
    |  "baker": "tz3RDC3Jdn4j15J7bBHZd29EUee9gVB1CxD9",
    |  "chainId": "NetXdQprcVkpaWU",
    |  "consumedGas": 0,
    |  "context": "CoUnq1qGxUtidFCdcaCWXEQdefFDSdBTpjnYVcrHJ1cKYqL6HLiA",
    |  "expectedCommitment": false,
    |  "fitness": "00,000000000004fff6",
    |  "hash": "BLc7tKfzia9hnaY1YTMS6RkDniQBoApM4EjKFRLucsuHbiy3eqt",
    |  "level": 10000,
    |  "metaCycle": 2,
    |  "metaCyclePosition": 1807,
    |  "metaLevel": 10000,
    |  "metaLevelPosition": 9999,
    |  "metaVotingPeriod": 0,
    |  "metaVotingPeriodPosition": 9999,
    |  "operationsHash": "LLob71uMBRtLaKGj3sDJmAT7VEdGTtEoogrbFFnPjxXiYfDmUQrgr",
    |  "periodKind": "proposal",
    |  "predecessor": "BMG7bSzAh1is2896bUkK7RnUREqqN4BjcH4J7YgkFKcNHWNe4cM",
    |  "priority": 0,
    |  "proto": 1,
    |  "protocol": "PtCJ7pwoxe8JasnHY8YonnLYjcVHmhiARPJvqcC6VfHT5s8k8sY",
    |  "signature": "sigRg6mM8oEt5y7nzSwi34P3UEoNDYjHF2Nik9s2f7xFGzMbbgmVYrc3uXdAKPF3ayDLv7vaEN4U2ZeDC69EJp4keYphw9WQ",
    |  "timestamp": 1530983187000,
    |  "validationPass": 4
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

  def groupingPredicatesQueryEndpoint = {
    val callBody = parser
      .parse(Source.fromResource("groupingPredicatesQuery.body.json").mkString)
      .ensuring(_.isRight)
      .right
      .get

    val expected = parser
      .parse(Source.fromResource("groupingPredicatesQuery.response.json").mkString)
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
