package tech.cryptonomic.conseil.routes

import org.scalatest.{WordSpec, Matchers}
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.http.scaladsl.model.{StatusCodes, ContentTypes}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.server.Directives._

class AppInfoRouteTest extends WordSpec with Matchers with ScalatestRouteTest {

  "The application info route" should {

    /* we need to embed in a complete route the fragment to test it
     * (see https://doc.akka.io/docs/akka-http/current/routing-dsl/testkit.html#testing-route-fragments)
     */
    val sut = pathPrefix("info") { AppInfo.route }

    "expose an endpoint to get the current application version" in {
      Get("/info") ~> sut ~> check {
        status shouldEqual StatusCodes.OK
        contentType shouldBe ContentTypes.`application/json`

      }
    }

    "reject any other http method than GET" in {
      /* we need to seal the route so that rejections are handled as http errors
      * (see https://doc.akka.io/docs/akka-http/current/routing-dsl/testkit.html#testing-sealed-routes)
      */
      val sealedSut = Route.seal(sut)

      val notAllowed = Options :: Post :: Patch :: Put :: Head :: Delete :: Nil

      import org.scalatest.Inspectors._

      forAll(notAllowed) {
        method =>
          method("/info") ~> sealedSut ~> check {
            status shouldEqual StatusCodes.MethodNotAllowed
          }
      }
    }

  }

}