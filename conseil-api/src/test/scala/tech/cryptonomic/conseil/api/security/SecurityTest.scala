package tech.cryptonomic.conseil.api.security

import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest.time.{Millis, Seconds, Span}
import tech.cryptonomic.conseil.api.security.Security.SecurityApi
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class SecurityTest extends ConseilSpec with ScalatestRouteTest {

  implicit override val patienceConfig = PatienceConfig(timeout = Span(2, Seconds), interval = Span(20, Millis))

  "The SecurityApi" should {

    "valid itself" in {
      SecurityApi(Set.empty, None).isValid shouldBe false
      SecurityApi(Set.empty, Some(false)).isValid shouldBe false

      SecurityApi(Set("some-key"), Some(false)).isValid shouldBe true
      SecurityApi(Set("some-key"), None).isValid shouldBe true
      SecurityApi(Set.empty, Some(true)).isValid shouldBe true
      SecurityApi(Set("some-key"), Some(true)).isValid shouldBe true
    }

    "validate a given key" in {
      SecurityApi(Set("some-key"), None).validateApiKey(Some("some-key")).futureValue shouldBe true
      SecurityApi(Set("some-key"), Some(true)).validateApiKey(Some("some-key")).futureValue shouldBe true

      SecurityApi(Set.empty, None).validateApiKey(Some("some-key")).futureValue shouldBe false
      SecurityApi(Set.empty, Some(true)).validateApiKey(Some("some-key")).futureValue shouldBe false

      SecurityApi(Set.empty, None).validateApiKey(None).futureValue shouldBe false
      SecurityApi(Set.empty, Some(true)).validateApiKey(None).futureValue shouldBe true
    }

  }
}
