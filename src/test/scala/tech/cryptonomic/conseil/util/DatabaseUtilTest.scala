package tech.cryptonomic.conseil.util

import org.scalatest.{Matchers, WordSpec}

class DatabaseUtilTest extends WordSpec with Matchers {

  "Database utils query builder" should {
      val sut = DatabaseUtil.QueryBuilder

      "concatenate multiple values in a sql action" in {
        sut.insertValuesIntoSqlAction(Seq("a", "b", "c")).queryParts.mkString("") shouldBe "('a','b','c')"
        sut.insertValuesIntoSqlAction(Seq(1, 2, 3)).queryParts.mkString("") shouldBe "('1','2','3')"
      }
      "concatenate an empty sequence of values in a sql action" in {
        sut.insertValuesIntoSqlAction(Seq.empty[Any]).queryParts.mkString("") shouldBe "()"
      }
      "concatenate a singleton sequence in a sql action" in {
        sut.insertValuesIntoSqlAction(Seq("single")).queryParts.mkString("") shouldBe "('single')"
      }

    }

}
