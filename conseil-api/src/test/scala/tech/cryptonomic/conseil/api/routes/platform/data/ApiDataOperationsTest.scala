package tech.cryptonomic.conseil.api.routes.platform.data

import tech.cryptonomic.conseil.common.testkit.ConseilSpec
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{OperationType, Predicate}

class ApiDataOperationsTest extends ConseilSpec {

  "ApiDataOperations" should {
    "sanitizeFields" in {
      // given
      val input = List.empty

      // when
      val result = ApiDataOperations.sanitizeFields(input)

      // then
      result shouldBe List.empty
    }

    "sanitizePredicates" in {
      // given
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

      // when
      val results = ApiDataOperations.sanitizePredicates(examplePredicates).head.set

      // then
      results should contain allElementsOf List(
        "valid",
        "valid_value",
        "invalidvalue",
        "anotherinvalidvalue",
        "yet.another.value"
      )
      results.size shouldBe 5

    }
  }

}
