package tech.cryptonomic.conseil.tezos

import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import scala.concurrent.duration._
import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.stream.ActorMaterializer
import tech.cryptonomic.conseil.generic.rpc.RpcHandler
import cats.effect.IO
import com.typesafe.config.ConfigFactory
import tech.cryptonomic.conseil.util.JsonUtil

class TezosRemoteInstancesTest
    extends TestKit(ActorSystem("LocalWebServer"))
    with WordSpecLike
    with Matchers
    with BeforeAndAfterAll {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
    super.afterAll()
  }

  "The IOEff RpcHander" should {
      import TezosRemoteInstances.Cats.IOEff._

      "GET json data from a web server" in withServerReturning(dummyJsonObject) {
        //given
        import LocalNodeContext._

        //when
        val result: String = RpcHandler.runGet("anypath").unsafeRunSync()

        //then
        result shouldEqual dummyJsonObject
      }

      "POST json data from a web server" in withServerReturning(dummyJsonObject) {
        //given
        import LocalNodeContext._

        val payload = JsonUtil.JsonString.wrapString("""{"number": 10}""").toOption.ensuring(_.nonEmpty)

        //when
        val result: String = RpcHandler.runPost("anypath", payload).unsafeRunSync()

        //then
        result shouldEqual dummyJsonObject
      }
    }

  private val dummyJsonObject = """{"name":"dummy"}"""

  /* Provides the contact point info to the test node
   * Import LocalNodeContext._ to have a TezosNodeContext implicitly in scope
   */
  private object LocalNodeContext {
    import TezosRemoteInstances.Akka.TezosNodeContext
    import tech.cryptonomic.conseil.config.{HttpStreamingConfiguration, NetworkTimeoutConfiguration, Platforms}

    val tezosConf =
      Platforms.TezosConfiguration(
        "local",
        Platforms.TezosNodeConfiguration("localhost", 9999, "http")
      )

    val timeouts =
      NetworkTimeoutConfiguration(GETResponseEntityTimeout = 1.second, POSTResponseEntityTimeout = 1.second)

    val streamingConf = HttpStreamingConfiguration(ConfigFactory.load())

    implicit val nodeContext: TezosNodeContext = TezosNodeContext(tezosConf, timeouts, streamingConf)
  }

  /* provides a running web server within the test */
  private def withServerReturning(json: String)(body: => Any) = {
    import akka.http.scaladsl.model._
    import akka.http.scaladsl.Http
    import cats.syntax.apply._
    import cats.syntax.functor._

    implicit val materializer = ActorMaterializer()

    val server = IO.fromFuture(
        IO(
          Http().bindAndHandleSync(
            handler = request =>
              HttpResponse(
                entity = HttpEntity(ContentTypes.`application/json`, json)
              ),
            interface = "localhost",
            port = 9999
          )
        )
      ) <* IO(println("Test server started"))

    server
      .bracket(
        use = _ => IO(body)
      )(
        release =
          binding => IO.fromFuture(IO(binding.terminate(10.seconds))).void <* IO(println("Test server terminated"))
      )
      .unsafeRunTimed(20.seconds)
  }

}
