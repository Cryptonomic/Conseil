package tech.cryptonomic.conseil.api.routes.info

import akka.http.scaladsl.model.{ContentTypes, StatusCodes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.common.util.JsonUtil._

class AppInfoRouteTest extends WordSpec with Matchers with ScalatestRouteTest {

  "The application info route" should {

      /* we need to embed in a complete route the fragment to test it
       * (see https://doc.akka.io/docs/akka-http/current/routing-dsl/testkit.html#testing-route-fragments)
       */
      val sut = AppInfo.route

      "expose an endpoint to get the current application version" in {
        Get("/info") ~> addHeader("apiKey", "hooman") ~> sut ~> check {
          status shouldEqual StatusCodes.OK
          contentType shouldBe ContentTypes.`application/json`
          val info: Map[String, Any] = toMap[Any](responseAs[String])
          info("application") shouldBe "Conseil"
          info("version").toString should fullyMatch regex """^0\.\d{4}\.\d{4}(-SNAPSHOT)?"""
          info("git").asInstanceOf[Map[String, String]]("commitHash") should fullyMatch regex """^[0-9a-f]+$"""
        }
      }

      "reject any other http method than GET" in {
        /* we need to seal the route so that rejections are handled as http errors
         * (see https://doc.akka.io/docs/akka-http/current/routing-dsl/testkit.html#testing-sealed-routes)
         */
        val sealedSut = Route.seal(sut)

        val notAllowed = Options :: Post :: Patch :: Put :: Head :: Delete :: Nil

        import org.scalatest.Inspectors._

        forAll(notAllowed) { method =>
          method("/info") ~> sealedSut ~> check {
            status shouldEqual StatusCodes.MethodNotAllowed
          }
        }
      }

    }

}
