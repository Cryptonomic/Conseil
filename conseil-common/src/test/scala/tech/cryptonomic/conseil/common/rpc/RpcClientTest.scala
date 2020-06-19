package tech.cryptonomic.conseil.common.rpc

import scala.concurrent.ExecutionContext

import cats.effect._
import org.http4s._
import org.http4s.client.Client
import fs2.Stream
import io.circe.generic.auto._
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.circe.CirceEntityEncoder._
import org.scalatest.{Matchers, WordSpec}

import tech.cryptonomic.conseil.common.rpc.RpcClient._

class RpcClientTest extends WordSpec with Matchers {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  "Rpc Client" should {
      "return a case class for valid json" in new HttpClientFixtures {
        val response = """[{
        |  "result": { "hash": "abc" },
        |  "id": "requestId"
        |}]""".stripMargin
        val request = Stream(RpcRequest("2.0", "getBlock", Params(1), "requestId"))

        getBlocks(request, response, 1).unsafeRunSync() shouldBe List(Block("abc"))
      }

      "allow to batch requests" in new HttpClientFixtures {
        val response = """[
        |  { "result": { "hash": "abc" }, "id": "1"},
        |  { "result": { "hash": "def" }, "id": "2"},
        |  { "result": { "hash": "ghj" }, "id": "3"}
        |]""".stripMargin
        val request = Stream(
          RpcRequest("2.0", "getBlock", Params(1), "1"),
          RpcRequest("2.0", "getBlock", Params(2), "2"),
          RpcRequest("2.0", "getBlock", Params(3), "3")
        )

        getBlocks(request, response, 3).unsafeRunSync() shouldBe List(
              Block("abc"),
              Block("def"),
              Block("ghj")
            )
      }

      "throw an exception for invalid json" in new HttpClientFixtures {
        // the json is invalid because of the empty object, it should be a `Block`
        val response = """[{
        |  "result": {},
        |  "id": "requestId"
        |}]""".stripMargin
        val request = Stream(RpcRequest("2.0", "getBlock", Params(1), "requestId"))

        val exception = the[InvalidMessageBodyFailure] thrownBy getBlocks(request, response, 1).unsafeRunSync()
        exception.getMessage() should include("Invalid message body")
      }

      "throw exception for unexpected json in the response" in new HttpClientFixtures {
        val response = """[{
        |  "id": "requestId"
        |}]""".stripMargin
        val request = Stream(RpcRequest("2.0", "getBlock", Params(1), "requestId"))

        val exception = the[UnsupportedOperationException] thrownBy getBlocks(request, response, 1).unsafeRunSync()
        exception.getMessage() should include("Unexpected response from JSON-RPC server")
      }

      "propagate the error form json in the response" in new HttpClientFixtures {
        val response = """[{
        |  "error": {"code": -32601, "message": "Method not found"},
        |  "id": "requestId"
        |}]""".stripMargin
        val request = Stream(RpcRequest("2.0", "getBlock", Params(1), "requestId"))

        val exception = the[RpcException] thrownBy getBlocks(request, response, 1).unsafeRunSync()
        exception.getMessage() should include("Method not found")
      }
    }

  case class Block(hash: String)
  case class Params(height: Int)

  trait HttpClientFixtures {
    def getBlocks(
        request: Stream[IO, RpcRequest[Params]],
        responseJson: String,
        batchSize: Int
    ): IO[List[Block]] = {
      val response = Response[IO](
        Status.Ok,
        body = Stream(responseJson).through(fs2.text.utf8Encode)
      )
      val client = new RpcClient[IO]("https://api-endpoint.com", 1, httpClient(response))

      client.stream[Params, Block](batchSize)(request).compile.toList
    }

    def httpClient(response: Response[IO]): Client[IO] =
      Client.fromHttpApp(HttpApp.liftF(IO.pure(response)))
  }

}
