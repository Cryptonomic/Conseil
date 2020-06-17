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

  case class Block(hash: String)
  case class Params(height: Int)

  "Rpc Client" should {
      "return case class for valid json" in {
        val json = """[{
        |  "result": { "hash": "abc" },
        |  "id": "requestId"
        |}]""".stripMargin
        val request = Stream(RpcRequest("2.0", "getBlock", Params(1), "requestId"))
        val response = Response[IO](
          Status.Ok,
          body = Stream(json).through(fs2.text.utf8Encode)
        )
        val client = new RpcClient[IO]("https://api-endpoint.com", 1, httpClient(response))

        client.stream[Params, Block](batchSize = 1)(request).compile.toList.unsafeRunSync() shouldBe List(Block("abc"))
      }

      "allow to batch requests" in {
        val json = """[
        |  { "result": { "hash": "abc" }, "id": "1"},
        |  { "result": { "hash": "def" }, "id": "2"},
        |  { "result": { "hash": "ghj" }, "id": "3"}
        |]""".stripMargin
        val request = Stream(
          RpcRequest("2.0", "getBlock", Params(1), "1"),
          RpcRequest("2.0", "getBlock", Params(2), "2"),
          RpcRequest("2.0", "getBlock", Params(3), "3")
        )
        val response = Response[IO](
          Status.Ok,
          body = Stream(json).through(fs2.text.utf8Encode)
        )
        val client = new RpcClient[IO]("https://api-endpoint.com", 1, httpClient(response))

        client.stream[Params, Block](batchSize = 3)(request).compile.toList.unsafeRunSync() shouldBe List(
          Block("abc"),
          Block("def"),
          Block("ghj")
        )
      }

      "throw exception for invalid json" in {
        val json = """[{
        |  "result": {},
        |  "id": "requestId"
        |}]""".stripMargin
        val request = Stream(RpcRequest("2.0", "getBlock", Params(1), "requestId"))
        val response = Response[IO](
          Status.Ok,
          body = Stream(json).through(fs2.text.utf8Encode)
        )
        val client = new RpcClient[IO]("https://api-endpoint.com", 1, httpClient(response))

        an[InvalidMessageBodyFailure] should be thrownBy {
          client.stream[Params, Block](batchSize = 1)(request).compile.toList.unsafeRunSync()
        }
      }

      "throw exception for unexpected json" in {
        val json = """[{
        |  "id": "requestId"
        |}]""".stripMargin
        val request = Stream(RpcRequest("2.0", "getBlock", Params(1), "requestId"))
        val response = Response[IO](
          Status.Ok,
          body = Stream(json).through(fs2.text.utf8Encode)
        )
        val client = new RpcClient[IO]("https://api-endpoint.com", 1, httpClient(response))
        an[UnsupportedOperationException] should be thrownBy {
          client.stream[Params, Block](batchSize = 1)(request).compile.toList.unsafeRunSync()
        }
      }

      "propagate error form json" in {
        val json = """[{
        |  "error": {"code": -32601, "message": "Method not found"},
        |  "id": "requestId"
        |}]""".stripMargin
        val request = Stream(RpcRequest("2.0", "getBlock", Params(1), "requestId"))
        val response = Response[IO](
          Status.Ok,
          body = Stream(json).through(fs2.text.utf8Encode)
        )
        val client = new RpcClient[IO]("https://api-endpoint.com", 1, httpClient(response))

        an[RpcException] should be thrownBy {
          client.stream[Params, Block](batchSize = 1)(request).compile.toList.unsafeRunSync()
        }
      }
    }

  private def httpClient(response: Response[IO]): Client[IO] =
    Client.fromHttpApp(HttpApp.liftF(IO.pure(response)))

}
