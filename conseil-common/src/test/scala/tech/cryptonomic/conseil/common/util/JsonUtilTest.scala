package tech.cryptonomic.conseil.common.util

import org.scalatest.Inspectors._
import JsonUtil._
import JsonUtil.JsonString.InvalidJsonString
import com.stephenn.scalatest.jsonassert.JsonMatchers
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class JsonUtilTest extends ConseilSpec with JsonMatchers {

  "JsonUtil" should {

      "expose a JsonString empty object" in {
        JsonString.emptyObject shouldBe a[JsonString]
        JsonString.emptyObject.json shouldBe "{}"
      }

      "allow wrapping of valid json into a JsonString" in {
        //basic json types
        val base = List("{}", """"text"""", "1", "1.9", "null", "true")
        //all base types wrapped in one object
        val objects = base.map(json => s"""{"field": $json}""")
        //arrays of both basic types and objects
        val arrays = base.mkString("[", ",", "]") :: objects.mkString("[", ",", "]") :: Nil

        forAll(base ++ objects ++ arrays) { json =>
          val jsonTry = JsonString.fromString(json)
          jsonTry shouldBe 'success
          jsonTry.get shouldBe a[JsonString]
        }
      }

      "fail when wrapping invalid json into a JsonString" in {

        val invalid =
          List(
            "{", //incomplete
            "{field : true}", //missing quotes
            """{"field" : trues}""", // invalid field value
            """{"field" : true, }""" // incomplete object fields
          )

        forAll(invalid) { string =>
          val jsonTry = JsonString.fromString(string)
          jsonTry shouldBe 'failure
          jsonTry.failed.get shouldBe a[InvalidJsonString[_]]
        }
      }

      "sanitize unwanted input, by removing any ISO control char from the json string" in {
        //include the most prominent control chars
        val invalid: String =
          (Set(0x00 to 0x1F: _*) + 0x7F ++ Set(0x80 to 0x9F: _*))
            .map(_.toChar)
            .foldLeft("")(_ + _)

        //double check
        invalid should have size 65

        forAll(invalid)(_.isControl)

        (JsonString sanitize invalid) shouldBe 'empty

      }

      "sanitize unwanted input, by removing any string-encoded unicode chars from the json string" in {

        val invalid_control = """demo\\u0000"""
        JsonString.sanitize(invalid_control) shouldBe "demou0000"

        val invalid_hangul = """\\ucd5c\\ub3d9\\ud6c8"""
        JsonString.sanitize(invalid_hangul) shouldBe "ucd5cub3d9ud6c8"
      }

      "convert a simple map to json" in {
        val result = JsonUtil.toJson(Map("key1" -> "value1", "key2" -> "value2")).json
        result should matchJson("""{"key1": "value1", "key2": "value2"}""")
      }

      "convert a nested generic shapeless Record to json" in {
        import shapeless.record.Record
        /* this does the trick of encoding any Record whose fields have encoders themeselves */
        import io.circe.generic.encoding.ReprAsObjectEncoder._

        val record = Record(a = "b", c = Record(d = "e", f = "g"))
        val result = JsonUtil.toJson(record).json
        result should matchJson("""{"a": "b", "c": {"d": "e", "f": "g"}}""")
      }

      "allow lenient parsing of json with duplicate object keys, actually deserialized or not" in {
        case class TestObject(field: String = "")
        object TestObject {
          import io.circe.Decoder
          import io.circe.generic.semiauto._
          /* required by Circe to decode from json */
          implicit val testObjectDecode: Decoder[TestObject] = deriveDecoder
        }

        val valid = """{"field": "value"}"""

        JsonUtil.fromJson[TestObject](valid).get shouldBe TestObject(field = "value")

        val duplicateKey = """{"field": "value", "field": "dup"}"""

        JsonUtil.fromJson[TestObject](duplicateKey).get shouldBe TestObject(field = "dup")

        val duplicateInnerKey = """{"field": "value", "inner": {"field":"inner", "field": "dup", "another": 10}}"""

        JsonUtil.fromJson[TestObject](duplicateInnerKey).get shouldBe TestObject(field = "value")
      }

    }

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

      "not match when no valid hash was found" in {
        "invalid-hash" match {
          case AccountIds(first) =>
            fail("Account ids were matched incorrectly")
          case _ =>
            succeed
        }
      }

    }

}
