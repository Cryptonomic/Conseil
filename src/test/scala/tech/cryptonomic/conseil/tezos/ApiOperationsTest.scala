package tech.cryptonomic.conseil.tezos

import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.generic.chain.QueryProtocolTypes.{OperationType, Predicate}

class ApiOperationsTest extends WordSpec with Matchers {

  "ApiOperations" should {
    val examplePredicates = List(
      Predicate(
        field = "some_field",
        operation = OperationType.in,
        set = List(
          "valid",
          "valid_value",
          "invalid*value",
          "another;invalid,value",
          "yet.another.value"
        )
      )
    )

    "correctly sanitize values for SQL" in {
      val results = ApiOperations.sanitizePredicates(examplePredicates).head.set
      results should contain allElementsOf List("valid", "valid_value", "invalidvalue", "anotherinvalidvalue", "yet.another.value")
      results.size shouldBe 5
    }
  }
}
