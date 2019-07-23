package tech.cryptonomic.conseil.tezos

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit.TestKit
import tech.cryptonomic.conseil.generic.rpc.RpcHandler
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import tech.cryptonomic.conseil.util.JsonUtil
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._

class TezosRpcTest
    extends TestKit(ActorSystem("LocalWebServer"))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "The IOEff RpcHander" should {
      import TezosRpc.Cats.IOEff._

      "GET json data from a web server" in withMockServer { mockServer =>
        //given
        import LocalNodeContext._

        val testPath = "anypath"

        mockServer.stubFor(
          get(urlPathMatching(s"/.*/$testPath")) willReturn (
                aResponse() withBody (dummyJsonObject) withHeader ("Content-Type", "application/json")
              )
        )

        //when
        val result: String = RpcHandler.runGet(testPath).unsafeRunSync()

        //then
        result shouldEqual dummyJsonObject
      }

      "POST json data from a web server" in withMockServer { mockServer =>
        //given
        import LocalNodeContext._

        val testPath = "anypath"

        mockServer.stubFor(
          post(urlPathMatching(s"/.*/$testPath")) willReturn (
                aResponse() withBody (dummyJsonObject) withHeader ("Content-Type", "application/json")
              )
        )

        val payload = JsonUtil.JsonString.wrapString("""{"number": 10}""").toOption.ensuring(_.nonEmpty)

        //when
        val result: String = RpcHandler.runPost(testPath, payload).unsafeRunSync()

        //then
        result shouldEqual dummyJsonObject
      }
    }

  private val dummyJsonObject = """{"name":"dummy"}"""

  /* Provides the contact point info to the test node
   * Import LocalNodeContext._ to have a TezosNodeContext implicitly in scope
   */
  private object LocalNodeContext {
    import TezosRpc.Akka.TezosNodeContext
    import tech.cryptonomic.conseil.config.{HttpStreamingConfiguration, NetworkTimeoutConfiguration, Platforms}

    val tezosConf =
      Platforms.TezosConfiguration(
        "local",
        Platforms.TezosNodeConfiguration("localhost", 9999, "http")
      )

    val timeouts =
      NetworkTimeoutConfiguration(
        GETResponseEntityTimeout = 1.second,
        POSTResponseEntityTimeout = 1.second
      )

    val streamingConf = HttpStreamingConfiguration(ConfigFactory.load())

    implicit val nodeContext: TezosNodeContext = TezosNodeContext(tezosConf, timeouts, streamingConf)
  }

  /* provides a running web server within the test */
  private def withMockServer(body: WireMockServer => Any) = {
    import cats.syntax.apply._
    import cats.syntax.flatMap._

    IO(new WireMockServer(9999))
      .bracket(
        use = srv => IO(srv.start()) >> IO(println("Test server started")) >> IO(body(srv))
      )(
        release = srv => IO(srv.stop()) <* IO(println("Test server stopped"))
      )
      .unsafeRunTimed(20.seconds)
  }

}
