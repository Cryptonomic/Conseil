package tech.cryptonomic.conseil.client

import java.util.concurrent.Executors
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import scala.sys.process._
import scala.Console.{GREEN, RED, RESET}
import org.http4s.{Header, MediaType, Uri}
import org.http4s.circe._
import org.http4s.headers._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.client.blaze._
import io.circe.{parser, Json}
import cats.effect.IO
import cats.syntax.all._
import scala.io.Source
import com.softwaremill.diffx.{Diff, Identical}
import com.typesafe.config.ConfigFactory

/** Currently can be used to test any conseil instance that loaded blocks levels 1 to 1000
  * against predefined expectations on the responses
  */
object DataEndpointsClientProbe {

  /* improvements: find how to correctly pass a custom IT config directly to a forked process
   * we would eventually prefer to define here the testing secret instead of relying on an outside
   * configuration file being available in the testing environment
   */
  private val apiKey = ConfigFactory.load().getStringList("conseil.security.apiKeys.keys").get(0)

  object Setup {
    implicit val ioShift = IO.contextShift(scala.concurrent.ExecutionContext.global)
    val timer = IO.timer(scala.concurrent.ExecutionContext.global)

    /* We might wanna start with only 10k blocks */
    private def runLorre(network: String) =
      Seq("sbt", s"runLorre -d 10000 -h BLc7tKfzia9hnaY1YTMS6RkDniQBoApM4EjKFRLucsuHbiy3eqt $network")
    private val runConseil = Process(Seq("sbt", "runConseil -v"))
    private def grepConseilPID(): String = (Seq("jps", "-m") #| Seq("grep", "runConseil")).!!.split(" ").head
    private def stopPid(pid: String) = Seq("kill", "-2", pid).!

    private def syncData(network: String) = IO((runLorre(network).!).ensuring(_ == 0, "lorre failed to load correctly"))

    private def startConseil(syncNetwork: Option[String]): IO[Process] =
      for {
        _ <- if (syncNetwork.nonEmpty) syncData(syncNetwork.get) else IO(0)
        proc <- IO(runConseil.run())
        _ <- IO(println("waiting for conseil to start"))
        _ <- timer.sleep(30.seconds)
      } yield proc

    def usingConseil[A](syncNetwork: Option[String] = None)(testBlock: => IO[A]) =
      startConseil(syncNetwork).bracket(
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
    * @param syncToNetwork if a network name is provided, a Lorre instance will load data to the local db from the specified network
    */
  def runRegressionSuite(syncToNetwork: Option[String] = None): IO[Unit] =
    Setup
      .usingConseil(syncToNetwork) {
        infoEndpoint *>
          blockHeadEndpoint *>
          groupingPredicatesQueryEndpoint *>
          bakingRightsQueryEndpoint *>
          endorsingRightsQueryEndpoint
      }
      .flatMap {
        case Left(error) => IO(println(s"$RED Regression tests failed: ${error.getMessage()}$RESET"))
        case Right(value) => IO(println(s"$GREEN Regression tests passed: OK$RESET"))
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
        case Right(received) =>
          Either.cond(
            received \\ "application" == expected \\ "application" && (received \\ "version").nonEmpty,
            right = received,
            left = new Exception(
              s"Failed to match on $endpoint: expected \n${expected.spaces2} \n but found \n ${received.spaces2}"
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
        case Right(received) =>
          Diff[Json].apply(received, expected) match {
            case Identical(value: Json) =>
              value.asRight[Throwable]
            case different =>
              new Exception(
                s"Failed to match on $endpoint: diff result is \n${different.show}"
              ).asLeft[Json]
          }
        case left => left
      }
    }

  }

  /** operations query using grouping in the predicates */
  def groupingPredicatesQueryEndpoint = {

    val callBody = parseJsonResource("groupingPredicatesQuery.body.json")
    val expected = parseJsonResource("groupingPredicatesQuery.response.json")
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
        case Right(received) =>
          Diff[Json].apply(ignoreOperationId(received), ignoreOperationId(expected)) match {
            case Identical(value) =>
              value.asRight[Throwable]
            case different =>
              new Exception(
                s"Failed to match on $endpoint: diff result is \n${different.show}"
              ).asLeft[Json]
          }
        case left => left
      }
    }

  }

  def bakingRightsQueryEndpoint = {

    val callBody = parseJsonResource("futureBakingRightsQuery.body.json")
    val expected = parseJsonResource("futureBakingRightsQuery.response.json")
    val endpoint = Uri.uri("http://localhost:1337/v2/data/tezos/mainnet/baking_rights")

    clientBuild.resource.use { client =>
      val req = POST(
        body = callBody,
        uri = endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header("apiKey", apiKey)
      )
      client.expect[Json](req).attempt.map {
        case Right(received) =>
          Diff[Json].apply(received, expected) match {
            case Identical(value) =>
              value.asRight[Throwable]
            case different =>
              new Exception(
                s"Failed to match on $endpoint: diff result is \n${different.show}"
              ).asLeft[Json]
          }
        case left => left
      }
    }
  }

  def endorsingRightsQueryEndpoint = {

    val callBody = parseJsonResource("futureEndorsingRightsQuery.body.json")
    val expected = parseJsonResource("futureEndorsingRightsQuery.response.json")
    val endpoint = Uri.uri("http://localhost:1337/v2/data/tezos/mainnet/endorsing_rights")

    clientBuild.resource.use { client =>
      val req = POST(
        body = callBody,
        uri = endpoint,
        `Content-Type`(MediaType.application.json),
        Accept(MediaType.application.json),
        Header("apiKey", apiKey)
      )
      client.expect[Json](req).attempt.map {
        case Right(received) =>
          Diff[Json].apply(received, expected) match {
            case Identical(value) =>
              value.asRight[Throwable]
            case different =>
              new Exception(
                s"Failed to match on $endpoint: diff result is \n${different.show}"
              ).asLeft[Json]
          }
        case left => left
      }
    }
  }

  private def parseJsonResource(fileName: String): Json =
    parser
      .parse(Source.fromResource(fileName).mkString)
      .ensuring(_.isRight)
      .right
      .get

  /** removes operation id fields (which are generated by the db) from the passed json */
  val ignoreOperationId: Json => Json = (in: Json) =>
    in.arrayOrObject(
      jsonObject = op => Json.fromJsonObject(op.remove("operation_id")),
      jsonArray = ops => Json.fromValues(ops.map(ignoreOperationId)),
      or = in
    )

}
