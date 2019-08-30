package tech.cryptonomic.conseil.client

import org.http4s.client.blaze._
import scala.concurrent.ExecutionContext
import java.util.concurrent.Executors
import cats.effect.IO
import io.circe
import org.http4s.Header
import org.http4s.headers._
import org.http4s.MediaType
import org.http4s.circe._
import org.http4s.dsl.io._
import org.http4s.client.dsl.io._
import org.http4s.Uri
import io.circe.Json

/** Currently can be used to test any conseil instance that loaded blocks levels 1 to 1000
  * against predefined expectations on the responses
  */
object DataEndpointsClientProbe {

  /*
   * We might wanna start with
   * runLorre -v -d 1000 -h BMbVtqhnzWcXVCLEZvcVVVPYdLLBrFcd2j3bufVY67WWRY8FVuN prodnet
   */

  val pool = Executors.newCachedThreadPool()
  val clientExecution: ExecutionContext = ExecutionContext.fromExecutor(pool)
  implicit val shift = IO.contextShift(clientExecution)

  val clientBuild = BlazeClientBuilder[IO](clientExecution)

  def infoEndpoint: IO[Either[Throwable, Json]] = {

    val expected =
      Json.obj(
        "application" -> Json.fromString("Conseil"),
        "version" -> Json.fromString("A non empty version string")
      )

    val endpoint = Uri.uri("http://localhost:1337/info")

    clientBuild.resource.use {
      client =>
        val req = GET(
          endpoint,
          `Content-Type`(MediaType.application.json),
          Accept(MediaType.application.json),
          Header("apiKey", "hooman")
        )
        client.expect[Json](req)
          .attempt
          .map {
            case Right(json) =>
              Either.cond(
                json \\ "application" == expected \\ "application" && (json \\ "version").nonEmpty,
                right = json,
                left = new Exception(s"Failed to match on $endpoint: expected \n${expected.spaces2} \n but found \n ${json.spaces2}")
              )
            case left => left
          }
    }
  }

}
