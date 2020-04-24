package tech.cryptonomic.conseil.common.sql.postgres

import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{OperationType, Predicate}

class PostgresDataOperationsTest extends WordSpec with Matchers {

  "PostgresDataOperations" should {
      "sanitizeFields" in {
        // given
        val input = List.empty

        // when
        val result = PostgresDataOperations.sanitizeFields(input)

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
        val results = PostgresDataOperations.sanitizePredicates(examplePredicates).head.set

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
