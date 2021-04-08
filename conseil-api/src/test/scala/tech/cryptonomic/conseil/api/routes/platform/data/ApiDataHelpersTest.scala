package tech.cryptonomic.conseil.api.routes.platform.data

import java.time.Instant

import tech.cryptonomic.conseil.common.testkit.ConseilSpec
import tech.cryptonomic.conseil.common.generic.chain.DataTypes.{OutputType, QueryResponseWithOutput}
import ujson.{Value => Json}
import endpoints.akkahttp.server.Endpoints

class ApiDataHelpersTest extends ConseilSpec {

  private case class TestEntity(value: String)

  private val sut = new ApiServerJsonSchema with Endpoints {
    override protected def customAnyEncoder: PartialFunction[Any, Json] = {
      case x: TestEntity => ujson.Str(x.value)
    }
  }

  "ApiDataHelpers" should {
      val encodeAny = sut.anySchema.encoder.encode _
      val encodeResponse = sut.queryResponseSchema.encoder.encode _
      val encodeResponseWithType = sut.queryResponseSchemaWithOutputType.encoder.encode _

      "encode 'Any' for default (common) data types conversion properly firstly" in {
        encodeAny(java.lang.Integer.valueOf(1)) shouldBe ujson.Num(1)
        encodeAny(java.lang.Long.valueOf(1L)) shouldBe ujson.Num(1)
        encodeAny(java.math.BigDecimal.ZERO) shouldBe ujson.Num(0)
        encodeAny("a") shouldBe ujson.Str("a")
        encodeAny(true) shouldBe ujson.True
        encodeAny(Vector(1, 2, 3)) shouldBe ujson.Arr(ujson.Num(1), ujson.Num(2), ujson.Num(3))
        val instant = Instant.parse("2007-12-03T10:15:30.00Z").toEpochMilli
        encodeAny(new java.sql.Timestamp(instant)) shouldBe ujson.Num(1196676930000L)
      }
      "convert 'Any' for custom data types conversion properly secondly" in {
        encodeAny(TestEntity("a")) shouldBe ujson.Str("a")
      }
      "fallback to default value if previous conversions are not satisfying" in {
        encodeAny(java.lang.Byte.MAX_VALUE) shouldBe ujson.Str("127")
        encodeAny(java.lang.Double.MAX_VALUE) shouldBe ujson.Str("1.7976931348623157E308")
      }
      "convert 'QueryResponse' entity to 'Json' properly" in {
        encodeResponse(
          Map(
            "field1" -> None,
            "field2" -> Some(true),
            "field3" -> Some(1)
          )
        ) shouldBe ujson.Obj(
          "field1" -> ujson.Null,
          "field2" -> ujson.True,
          "field3" -> ujson.Num(1)
        )
      }
      "convert 'QueryResponseWithOutput' entity to 'Json' properly" in {
        encodeResponseWithType(
          QueryResponseWithOutput(
            List(
              Map(
                "field1" -> None,
                "field2" -> Some(true),
                "field3" -> Some(1)
              )
            ),
            OutputType.json
          )
        ) shouldBe ujson.Arr(
          ujson.Obj(
            "field1" -> ujson.Null,
            "field2" -> ujson.True,
            "field3" -> ujson.Num(1)
          )
        )
      }
    }
}
