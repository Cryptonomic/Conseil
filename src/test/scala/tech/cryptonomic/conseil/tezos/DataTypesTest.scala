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
      (tpdo.areFieldsValid _).when("test", Set("valid")).returns(Future.successful(true))

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
      (tpdo.areFieldsValid _).when("test", Set("invalid")).returns(Future.successful(false))

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
      (tpdo.areFieldsValid _).when("test", Set("valid")).returns(Future.successful(true))

      val query = ApiQuery(
        fields = None,
        predicates = Some(List(Predicate("valid", OperationType.in))),
        orderBy = None,
        limit = None,
        output = None,
        aggregation = None
      )

      val result = query.validate("test", tpdo)

      result.futureValue.right.get shouldBe Query(predicates = List(Predicate("valid", OperationType.in)))
    }

    "return error with incorrect predicate fields" in {
      (tpdo.areFieldsValid _).when("test", Set("invalid")).returns(Future.successful(false))

      val query = ApiQuery(
        fields = None,
        predicates = Some(List(Predicate("invalid", OperationType.in))),
        orderBy = None,
        limit = None,
        output = None,
        aggregation = None
      )

      val result = query.validate("test", tpdo)

      result.futureValue.left.get shouldBe List(InvalidPredicateField("invalid"))
    }

    "validate correct orderBy field" in {
      (tpdo.areFieldsValid _).when("test", Set("valid")).returns(Future.successful(true))

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
      (tpdo.areFieldsValid _).when("test", Set("invalid")).returns(Future.successful(false))

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

      (tpdo.areFieldsValid _).when("test", Set("valid")).returns(Future.successful(true))
      (tpdo.getTableAttributesWithoutCounts _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = Some(List("valid")),
        predicates = None,
        orderBy = None,
        limit = None,
        output = None,
        aggregation = Some(Aggregation(field = "valid"))
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

      (tpdo.areFieldsValid _).when("test", Set("invalid")).returns(Future.successful(true))
      (tpdo.getTableAttributesWithoutCounts _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = Some(List("invalid")),
        predicates = None,
        orderBy = None,
        limit = None,
        output = None,
        aggregation = Some(Aggregation(field = "invalid"))
      )

      val result = query.validate("test", tpdo)

      result.futureValue.left.get shouldBe List(InvalidAggregationFieldForType("invalid"))
    }

    "return two errors with incorrect aggregation field and with field that does not exist in query fields" in {
      val attribute = Attribute(
        name = "invalid",
        displayName = "Invalid",
        dataType = DataType.String,
        cardinality = None,
        keyType = KeyType.NonKey,
        entity = "test"
      )
      (tpdo.areFieldsValid _).when("test", Set("valid")).returns(Future.successful(true)) once()
      (tpdo.areFieldsValid _).when("test", Set("invalid")).returns(Future.successful(false)) once()
      (tpdo.getTableAttributesWithoutCounts _).when("test").returns(Future.successful(Some(List(attribute))))

      val query = ApiQuery(
        fields = Some(List("valid")),
        predicates = None,
        orderBy = None,
        limit = None,
        output = None,
        aggregation = Some(Aggregation(field = "invalid"))
      )

      val result = query.validate("test", tpdo)

      result.futureValue.left.get should contain allElementsOf List(InvalidAggregationField("invalid"), InvalidAggregationFieldForType("invalid"))
    }
  }

}
