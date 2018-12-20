package tech.cryptonomic.conseil.util

import org.scalatest.{Matchers, WordSpec}
import JsonUtil._
import com.stephenn.scalatest.jsonassert.JsonMatchers

class JsonUtilTest extends WordSpec with Matchers with JsonMatchers {

  "AccountIds unapply object" should {

    val jsonOperations =
      """{
        |  "branch": "BL2b35WaV3dkWanK6pA1dgAWvmYCn36GQyd13JZNaJc8cYv4uDX",
        |  "chain_id": "NetXSzLHKwSumh7",
        |  "contents": [
        |      {
        |          "kind": "endorsement",
        |          "level": 44706,
        |          "metadata": {
        |              "balance_updates": [
        |                  {
        |                      "change": "-64000000",
        |                      "contract": "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ",
        |                      "kind": "contract"
        |                  },
        |                  {
        |                      "category": "deposits",
        |                      "change": "64000000",
        |                      "delegate": "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ",
        |                      "kind": "freezer",
        |                      "level": 349
        |                  },
        |                  {
        |                      "category": "rewards",
        |                      "change": "2000000",
        |                      "delegate": "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ",
        |                      "kind": "freezer",
        |                      "level": 349
        |                  }
        |              ],
        |              "delegate": "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ",
        |              "slots": [
        |                  31
        |              ]
        |          }
        |      }
        |  ],
        |}""".stripMargin

    val signature =
      """"signature": "sigRgp414XtmHUE2Zg2sSHLnekhWUbPS1PgUvhwL7Hdz9gw1tz3ptiEzKwRcbw1xiBfLzCBxUuvDxqUR6mE8Fp2tcHGCC7Lr"}"""

    "match a valid account id" in {
      val singleHash = """"tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ""""
      singleHash match {
        case AccountIds(first) =>
          first shouldBe "tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ"
        case _ =>
          fail("Account id hash wasn't matched")
      }
    }

    "extract the valid account ids from a json string" in {
      jsonOperations match {
        case AccountIds(first, rest @ _*) =>
          Set(first) ++ rest should contain theSameElementsAs Set("tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ")
        case _ =>
          fail("Account ids were not matched")
      }
    }

    "not extract a valid hash format as part of another value in a json string" in {
      jsonOperations ++ signature match {
        case AccountIds(first, rest @ _*) =>
          Set(first) ++ rest should contain theSameElementsAs Set("tz1UmPE44pqWrEgW8sTRs6ED1DgwF7k43ncQ")
        case _ =>
          fail("Account ids were not matched")
      }
    }

    "convert a simple map to json" in {
      val result = JsonUtil.toJson(Map("key1" -> "value1", "key2" -> "value2")).json
      result should matchJson("""{"key1": "value1", "key2": "value2"}""")
    }

    "convert a complex map to json" in {
      val result = JsonUtil.toJson(Map("a" -> "b", "c" -> Map("d" -> "e", "f" -> "g"))).json
      result should matchJson("""{"a": "b", "c": {"d": "e", "f": "g"}}""")
    }

    "convert a simple json to a map" in {
      val result = JsonUtil.toMap[String]("""{"key1": "value1", "key2": "value2"}""")
      result should be(Map("key1" -> "value1", "key2" -> "value2"))
    }

    "convert a compex json to a map" in {
      val result = JsonUtil.toMap[Any]("""{"a": "b", "c": {"d": "e", "f": "g"}}""")
      result should be(Map("a" -> "b", "c" -> Map("d" -> "e", "f" -> "g")))
    }
  }

}