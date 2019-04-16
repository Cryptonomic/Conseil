package tech.cryptonomic.conseil.tezos

import org.scalamock.scalatest.MockFactory
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}
import tech.cryptonomic.conseil.generic.chain.DataTypes._
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, KeyType}

import scala.concurrent.Future

class DataTypesTest extends WordSpec with Matchers with ScalaFutures with MockFactory {
import scala.concurrent.ExecutionContext.Implicits.global

  val tpdo = stub[TezosPlatformDiscoveryOperations]

  "DataTypes" should {
    "validate correct query field" in {
      (tpdo.isAttributeValid _).when("test", "valid").returns(Future.successful(true))

      val query = ApiQuery(
        fields = Some(List("valid")),
        predicates = None,
        orderBy = None,
        limit = None,
        output = None,
        aggregation = None
      )

      val result = query.validate("test", tpdo)

      result.futureValue.right.get shouldBe Query(fields = List("valid"))

    }

    "return error with incorrect query fields" in {
      (tpdo.isAttributeValid _).when("test", "invalid").returns(Future.successful(false))

      val query = ApiQuery(
        fields = Some(List("invalid")),
        predicates = None,
        orderBy = None,
        limit = None,
        output = None,
        aggregation = None
      )

      val result = query.validate("test", tpdo)

      result.futureValue.left.get shouldBe List(InvalidQueryField("invalid"))
    }

    "validate correct predicate field" in {
      val attribute = Attribute(
        name = "valid",
        displayName = "valid",
        dataType = DataType.Int,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "test"
      )
      (tpdo.isAttributeValid _).when("test", "valid").returns(Future.successful(true))
      (tpdo.getTableAttributesWithoutUpdatingCache _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = None,
        predicates = Some(List(ApiPredicate("valid", OperationType.in))),
        orderBy = None,
        limit = None,
        output = None,
        aggregation = None
      )

      val result = query.validate("test", tpdo)

      result.futureValue.right.get shouldBe Query(predicates = List(Predicate("valid", OperationType.in)))
    }

    "return error with incorrect predicate fields" in {
      val attribute = Attribute(
        name = "valid",
        displayName = "valid",
        dataType = DataType.Int,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "test"
      )
      (tpdo.isAttributeValid _).when("test", "invalid").returns(Future.successful(false))
      (tpdo.getTableAttributesWithoutUpdatingCache _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = None,
        predicates = Some(List(ApiPredicate("invalid", OperationType.in))),
        orderBy = None,
        limit = None,
        output = None,
        aggregation = None
      )

      val result = query.validate("test", tpdo)

      result.futureValue.left.get shouldBe List(InvalidPredicateField("invalid"))
    }

    "validate correct orderBy field" in {
      (tpdo.isAttributeValid _).when("test", "valid").returns(Future.successful(true))

      val query = ApiQuery(
        fields = None,
        predicates = None,
        orderBy = Some(List(QueryOrdering("valid", OrderDirection.asc))),
        limit = None,
        output = None,
        aggregation = None
      )

      val result = query.validate("test", tpdo)

      result.futureValue.right.get shouldBe Query(orderBy = List(QueryOrdering("valid", OrderDirection.asc)))
    }

    "return error with incorrect orderBy fields" in {
      (tpdo.isAttributeValid _).when("test", "invalid").returns(Future.successful(false))

      val query = ApiQuery(
        fields = None,
        predicates = None,
        orderBy = Some(List(QueryOrdering("invalid", OrderDirection.asc))),
        limit = None,
        output = None,
        aggregation = None
      )

      val result = query.validate("test", tpdo)

      result.futureValue.left.get shouldBe List(InvalidOrderByField("invalid"))
    }

    "validate correct aggregation field which exists in DB" in {
      val attribute = Attribute(
        name = "valid",
        displayName = "valid",
        dataType = DataType.Int,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "test"
      )

      (tpdo.isAttributeValid _).when("test", "valid").returns(Future.successful(true))
      (tpdo.getTableAttributesWithoutUpdatingCache _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = Some(List("valid")),
        predicates = None,
        orderBy = None,
        limit = None,
        output = None,
        aggregation = Some(ApiAggregation(field = "valid"))
      )

      val result = query.validate("test", tpdo)

      result.futureValue.right.get shouldBe Query(fields = List("valid"), aggregation = Some(Aggregation(field = "valid")))
    }

    "return error with incorrect aggregation field which exists in DB" in {
      val attribute = Attribute(
        name = "invalid",
        displayName = "Invalid",
        dataType = DataType.String,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "test"
      )

      (tpdo.isAttributeValid _).when("test", "invalid").returns(Future.successful(true))
      (tpdo.getTableAttributesWithoutUpdatingCache _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = Some(List("invalid")),
        predicates = None,
        orderBy = None,
        limit = None,
        output = None,
        aggregation = Some(ApiAggregation(field = "invalid"))
      )

      val result = query.validate("test", tpdo)

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
      (tpdo.isAttributeValid _).when("test", "valid").returns(Future.successful(true)) once()
      (tpdo.isAttributeValid _).when("test", "invalid").returns(Future.successful(false)) once()
      (tpdo.getTableAttributesWithoutUpdatingCache _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = Some(List("valid")),
        predicates = None,
        orderBy = None,
        limit = None,
        output = None,
        aggregation = Some(ApiAggregation(field = "invalid"))
      )

      val result = query.validate("test", tpdo)

      result.futureValue.left.get should contain theSameElementsAs List(InvalidAggregationField("invalid"), InvalidAggregationFieldForType("invalid"))
    }

    "successfully validates aggregation for any dataType when COUNT function is used" in {
      val attribute = Attribute(
        name = "valid",
        displayName = "Valid",
        dataType = DataType.String, // only COUNT function can be used on types other than numeric and DateTime
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "test"
      )

      (tpdo.isAttributeValid _).when("test", "valid").returns(Future.successful(true))
      (tpdo.getTableAttributesWithoutUpdatingCache _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = Some(List("valid")),
        predicates = None,
        orderBy = None,
        limit = None,
        output = None,
        aggregation = Some(ApiAggregation(field = "valid", function = AggregationType.count))
      )

      val result = query.validate("test", tpdo)

      result.futureValue.right.get shouldBe Query(fields = List("valid"), aggregation = Some(Aggregation(field = "valid", function = AggregationType.count)))
    }

    "correctly transform predicate DateTime field as Long into ISO" in {
      val attribute = Attribute(
        name = "valid",
        displayName = "Valid",
        dataType = DataType.DateTime, // only COUNT function can be used on types other than numeric and DateTime
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "test"
      )

      (tpdo.isAttributeValid _).when("test", "valid").returns(Future.successful(true))
      (tpdo.getTableAttributesWithoutUpdatingCache _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = None,
        predicates = Some(List(ApiPredicate(field = "valid", operation = OperationType.in, set = Some(List(123456789000L))))),
        orderBy = None,
        limit = None,
        output = None,
        aggregation = None
      )

      val result = query.validate("test", tpdo)

      result.futureValue.right.get shouldBe Query(predicates = List(Predicate(field = "valid", operation = OperationType.in, set = List("1973-11-29T21:33:09Z"))))
    }
  }

}
