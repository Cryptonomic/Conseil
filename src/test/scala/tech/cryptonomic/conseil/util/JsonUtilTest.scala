package tech.cryptonomic.conseil.util

import org.scalatest.{WordSpec, Matchers}
import org.scalatest.Inspectors._
import JsonUtil.JsonString

class JsonUtilTest extends WordSpec with Matchers {

  "JsonUtil" should {

    "expose a JsonString empty object" in {
      JsonString.emptyObject shouldBe a [JsonString]
      JsonString.emptyObject.json shouldBe "{}"
    }

    "allow wrapping of valid json into a JsonString" in {
      //basic json types
      val base = List("{}", """"text"""", "1", "1.9", "null", "true")
      //all base types wrapped in one object
      val objects = base.map(json => s"""{"field": $json}""")
      //arrays of both basic types and objects
      val arrays = base.mkString("[", ",", "]") :: objects.mkString("[", ",", "]") :: Nil


      forAll(base ++ objects ++ arrays) {
        json =>
          val jsonTry = JsonString.wrapString(json)
          jsonTry shouldBe 'success
          jsonTry.get shouldBe a [JsonString]
      }
    }

    "fail when wrapping invalid json into a JsonString" in {

      import com.fasterxml.jackson.core.JsonParseException

      val invalid =
        List(
          "{", //incomplete
          "{field : true}", //missing quotes
          """{"field" : trues}""", // invalid field value
          """{"field" : true, }""", // incomplete object fields
          """{"field" : true, "field" : 1}""" // duplicate field
        )

      forAll(invalid) {
        string =>
          val jsonTry = JsonString.wrapString(string)
          jsonTry shouldBe 'failure
          jsonTry.failed.get shouldBe a [JsonParseException]
      }
    }

  }
}