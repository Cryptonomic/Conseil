package tech.cryptonomic.conseil.util

import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.generic.chain.Trie

class TrieTest extends WordSpec with Matchers {

  "Trie" should {
    val sut = new Trie

    sut.put("aaaa")
    sut.put("bbbb")
    sut.put("aabb")
    sut.put("a")
    sut.put("xccb")
    "correctly find elements" in {
      sut.getAllWithPrefix("a") should contain theSameElementsAs Seq("aaaa", "aabb", "a")
      sut.getAllWithPrefix("aa") should contain theSameElementsAs Seq("aaaa", "aabb")
    }
    "not find any elements" in {
      sut.getAllWithPrefix("cc") shouldBe Seq.empty
    }
    "not store duplicates" in {
      val sut2 = sut
      sut2.put("aaaa")
      sut2.put("aaaa")
      val result = sut2.getAllWithPrefix("aaaa")
      result should contain theSameElementsAs Seq("aaaa")
      result.size shouldBe 1
    }
  }

}
