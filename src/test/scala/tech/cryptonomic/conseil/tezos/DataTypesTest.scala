package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.generic.chain.DataTypes._
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, Entity, KeyType}
import tech.cryptonomic.conseil.metadata.{EntityPath, MetadataService, NetworkPath, PlatformPath}

import scala.concurrent.Future

class DataTypesTest extends WordSpec with Matchers with ScalaFutures with MockFactory {
  import scala.concurrent.ExecutionContext.Implicits.global

  val ms = stub[MetadataService]
  val testEntityPath = EntityPath("testEntity", NetworkPath("testNetwork", PlatformPath("testPlatform")))
  val testEntity = Entity("testEntity", "Test Entity", 0)

  "DataTypes" should {
      "validate correct query field" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "Valid",
          dataType = DataType.Int,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "testEntity"
        )
        (ms.isAttributeValid _).when("testEntity", "valid").returns(Future.successful(true))
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = Some(List("valid")),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, ms)
        result.futureValue.right.get shouldBe Query(fields = List("valid"))

      }

      "return error with incorrect query fields" in {
        (ms.isAttributeValid _).when("testEntity", "invalid").returns(Future.successful(false))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = Some(List("invalid")),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.left.get shouldBe List(InvalidQueryField("invalid"))
      }

      "validate correct predicate field" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "valid",
          dataType = DataType.Int,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "test"
        )
        (ms.isAttributeValid _).when("testEntity", "valid").returns(Future.successful(true))
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = None,
          predicates = Some(List(ApiPredicate("valid", OperationType.in))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.right.get shouldBe Query(predicates = List(Predicate("valid", OperationType.in)))
      }

      "return error with incorrect predicate fields" in {
        val attribute = Attribute(
          name = "invalid",
          displayName = "invalid",
          dataType = DataType.Int,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "test"
        )
        (ms.isAttributeValid _).when("testEntity", "invalid").returns(Future.successful(false))
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
          .anyNumberOfTimes()
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = None,
          predicates = Some(List(ApiPredicate("invalid", OperationType.in))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.left.get shouldBe List(InvalidPredicateField("invalid"))
      }

      "validate correct orderBy field" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "Valid",
          dataType = DataType.Int,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "testEntity"
        )

        (ms.isAttributeValid _).when("testEntity", "valid").returns(Future.successful(true))
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
          .anyNumberOfTimes()
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = None,
          predicates = None,
          orderBy = Some(List(QueryOrdering("valid", OrderDirection.asc))),
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.right.get shouldBe Query(orderBy = List(QueryOrdering("valid", OrderDirection.asc)))
      }

      "return error with incorrect orderBy fields" in {
        (ms.isAttributeValid _).when("testEntity", "invalid").returns(Future.successful(false))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = None,
          predicates = None,
          orderBy = Some(List(QueryOrdering("invalid", OrderDirection.asc))),
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.left.get shouldBe List(InvalidOrderByField("invalid"))
      }

      "validate correct aggregation field which exists in DB" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "valid",
          dataType = DataType.Int,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "test"
        )

        (ms.isAttributeValid _).when("testEntity", "valid").returns(Future.successful(true))
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = Some(List("valid")),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "valid")))
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.right.get shouldBe Query(
          fields = List("valid"),
          aggregation = List(Aggregation(field = "valid"))
        )
      }

      "return error with incorrect aggregation field which exists in DB" in {
        val attribute = Attribute(
          name = "invalid",
          displayName = "Invalid",
          dataType = DataType.String,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "test"
        )

        (ms.isAttributeValid _).when("testEntity", "invalid").returns(Future.successful(true))
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = Some(List("invalid")),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "invalid")))
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.left.get shouldBe List(InvalidAggregationFieldForType("invalid"))
      }

      "return two errors when we try to aggregate on non-aggregating field type and with field that does not exist in query fields" in {
        val attribute = Attribute(
          name = "invalid",
          displayName = "Invalid",
          dataType = DataType.String,
          cardinality = None,
          keyType = KeyType.NonKey,
          entity = "test"
        )
        (ms.isAttributeValid _).when("testEntity", "valid").returns(Future.successful(true)) once ()
        (ms.isAttributeValid _).when("testEntity", "invalid").returns(Future.successful(false)) once ()
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = Some(List("valid")),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "invalid")))
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.left.get should contain theSameElementsAs List(
          InvalidAggregationField("invalid"),
          InvalidAggregationFieldForType("invalid")
        )
      }

      "return error with when none of the fields in predicates are valid for querying" in {
        val attribute = Attribute(
          name = "InvalidAttribute",
          displayName = "Invalid",
          dataType = DataType.String,
          cardinality = None,
          keyType = KeyType.NonKey,
          entity = "test"
        )

        (ms.isAttributeValid _).when("testEntity", "InvalidAttribute").returns(Future.successful(true))
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity.copy(limitedQuery = Some(true)))))

        val query = ApiQuery(
          fields = None,
          predicates = Some(List(ApiPredicate("InvalidAttribute", OperationType.in))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.left.get.head shouldBe a[InvalidPredicateFiltering]
      }

      "successfully validates aggregation for any dataType when COUNT function is used" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "Valid",
          dataType = DataType.String, // only COUNT function can be used on types other than numeric and DateTime
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "test"
        )

        (ms.isAttributeValid _).when("testEntity", "valid").returns(Future.successful(true))
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
          .anyNumberOfTimes()
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = Some(List("valid")),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "valid", function = AggregationType.count)))
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.right.get shouldBe Query(
          fields = List("valid"),
          aggregation = List(Aggregation(field = "valid", function = AggregationType.count))
        )
      }

      "correctly transform predicate DateTime field as Long into ISO" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "Valid",
          dataType = DataType.DateTime, // only COUNT function can be used on types other than numeric and DateTime
          cardinality = None,
          keyType = KeyType.NonKey,
          entity = "testEntity"
        )

        (ms.isAttributeValid _).when("testEntity", "valid").returns(Future.successful(true))
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(*)
          .returns(Future.successful(Some(List(attribute))))
          .anyNumberOfTimes()
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = None,
          predicates =
            Some(List(ApiPredicate(field = "valid", operation = OperationType.in, set = Some(List(123456789000L))))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.right.get shouldBe Query(
          predicates = List(
            Predicate(field = "valid", operation = OperationType.in, set = List(new Timestamp(123456789000L).toString))
          )
        )
      }

      "correctly validate query when aggregated field is used in orderBy" in {
        val attribute = Attribute(
          name = "validAttribute",
          displayName = "Valid attribute",
          dataType = DataType.String,
          cardinality = None,
          keyType = KeyType.NonKey,
          entity = "testEntity"
        )
        (ms.isAttributeValid _).when("testEntity", "validAttribute").returns(Future.successful(true)).anyNumberOfTimes()
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(testEntityPath)
          .returns(Future.successful(Some(List(attribute))))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = Some(List("validAttribute")),
          predicates = None,
          orderBy = Some(List(QueryOrdering("count_validAttribute", direction = OrderDirection.asc))),
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "validAttribute", function = AggregationType.count)))
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.right.get shouldBe Query(
          fields = List("validAttribute"),
          orderBy = List(QueryOrdering("count_validAttribute", OrderDirection.asc)),
          aggregation = List(Aggregation("validAttribute", AggregationType.count))
        )
      }

      "correctly validate query when aggregated field is used in predicate" in {
        val attribute = Attribute(
          name = "validAttribute",
          displayName = "Valid attribute",
          dataType = DataType.String,
          cardinality = None,
          keyType = KeyType.NonKey,
          entity = "testEntity"
        )
        (ms.isAttributeValid _).when("testEntity", "validAttribute").returns(Future.successful(true)).anyNumberOfTimes()
        (ms
          .getTableAttributesWithoutUpdatingCache(_: EntityPath))
          .when(testEntityPath)
          .returns(Future.successful(Some(List(attribute))))
        (ms
          .getEntities(_: NetworkPath))
          .when(*)
          .returns(Some(List(testEntity)))

        val query = ApiQuery(
          fields = Some(List("validAttribute")),
          predicates = Some(List(ApiPredicate("count_validAttribute", OperationType.in))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "validAttribute", function = AggregationType.count)))
        )

        val result = query.validate(testEntityPath, ms)

        result.futureValue.right.get shouldBe Query(
          fields = List("validAttribute"),
          predicates = List(Predicate("count_validAttribute", operation = OperationType.in)),
          aggregation = List(Aggregation("validAttribute", AggregationType.count))
        )
      }

    }

}
