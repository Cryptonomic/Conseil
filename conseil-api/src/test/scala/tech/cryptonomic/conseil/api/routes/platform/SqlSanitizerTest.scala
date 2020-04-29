package tech.cryptonomic.conseil.api.routes.platform

import org.scalatest.{Matchers, WordSpec}

class SqlSanitizerTest extends WordSpec with Matchers {

  "SqlSanitizer" should {
      "sanitizeForSql alphanumeric string" in {
        // given
        val input = "xyz123"

        // when
        val result = Sanitizer.sanitizeForSql(input)

        // then
        result shouldBe "xyz123"
      }

      "sanitizeForSql alphanumeric string with supported characters" in {
        // given
        val input = "xyz+123_abc: pqr"

        // when
        val result = Sanitizer.sanitizeForSql(input)

        // then
        result shouldBe "xyz+123_abc: pqr"
      }

      "sanitizeForSql alphanumeric string with unsupported characters" in {
        // given
        val input = ";xyz$*)("

        // when
        val result = Sanitizer.sanitizeForSql(input)

        // then
        result shouldBe "xyz)("
      }

      "sanitizeForSql and escape characters for SQL" in {
        // given
        val input = "\";xyz$%*)("

        // when
        val result = Sanitizer.sanitizeForSql(input)

        // then
        result shouldBe "\"xyz\\%)("
      }

      "sanitizeDatePartAggregation and leave all valid characters" in {
        // given
        val input = "DD-MM-YYYY"

        // when
        val result = Sanitizer.sanitizeDatePartAggregation(input)

        // then
        result shouldBe "DD-MM-YYYY"
      }

      "sanitizeDatePartAggregation and remove invalid characters" in {
        // given
        val input = "xyz "

        // when
        val result = Sanitizer.sanitizeDatePartAggregation(input)

        // then
        result shouldBe ""
      }

    }
}
