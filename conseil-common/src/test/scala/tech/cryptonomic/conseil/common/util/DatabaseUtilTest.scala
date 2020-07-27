package tech.cryptonomic.conseil.common.util

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.Predicate
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.OperationType

class DatabaseUtilTest extends AnyWordSpec with Matchers {

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

      "concatenate groups of predicates with OR" in {
        val predicates =
          Predicate("fa", OperationType.eq, List("a"), false, None, group = Some("1")) ::
              Predicate("fb", OperationType.eq, List("b"), false, None, group = Some("1")) ::
              Predicate("fc", OperationType.eq, List("c"), false, None, group = Some("2")) ::
              Predicate("fd", OperationType.eq, List("d"), false, None, group = Some("2")) ::
              Nil

        val first :: rest = sut.makePredicates(predicates)
        val fragment = sut.concatenateSqlActions(first, rest: _*).queryParts.mkString
        fragment shouldBe "AND (True AND fa = 'a' AND fb = 'b') OR (True AND fc = 'c' AND fd = 'd') "
      }
    }

}
