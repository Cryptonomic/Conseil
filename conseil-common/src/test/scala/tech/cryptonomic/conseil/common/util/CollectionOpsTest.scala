package tech.cryptonomic.conseil.common.util

import CollectionOps._
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class CollectionOpsTest extends ConseilSpec {

  "Collection operations" should {

    "allow grouping over sequences of tuples" in {

      val seq = Seq(
        1 -> "a",
        2 -> "aa",
        3 -> "aaa",
        1 -> "b",
        2 -> "bb"
      )

      groupByKey(seq) shouldEqual Map(
        1 -> Seq("a", "b"),
        2 -> Seq("aa", "bb"),
        3 -> Seq("aaa")
      )

    }

    "provide extension syntax to group over sequences of tuples" in {

      val seq = Seq(
        1 -> "a",
        2 -> "aa",
        3 -> "aaa",
        1 -> "b",
        2 -> "bb"
      )

      seq.byKey shouldEqual Map(
        1 -> Seq("a", "b"),
        2 -> Seq("aa", "bb"),
        3 -> Seq("aaa")
      )

    }

    "allow to define operations on bounds for a non-empty collection" in {
      val seq = Seq(
        "a",
        "aa",
        "aaa",
        "b",
        "bb"
      )

      applyOnBounds(seq)((first, last) => (first, last)) shouldEqual Some(("a", "bb"))

    }

    "allow to define operations on bounds for an empty collection, returning an empty value" in {
      val seq = Seq.empty[Any]

      applyOnBounds(seq)((first, last) => (first, last)) shouldBe 'empty

    }

    "provide extension syntax to call operations on bounds for a non-empty collection" in {
      val seq = Seq(
        "a",
        "aa",
        "aaa",
        "b",
        "bb"
      )

      seq.onBounds((first, last) => (first, last)) shouldEqual Some(("a", "bb"))

    }

    "provide extension syntax to call operations on bounds for an empty collection, returning an empty value" in {
      val seq = Seq.empty[Any]

      seq.onBounds((first, last) => (first, last)) shouldBe 'empty

    }

  }
}
