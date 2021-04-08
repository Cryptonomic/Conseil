package tech.cryptonomic.conseil.api.routes.platform.data

import java.sql.Timestamp

import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterEach, OneInstancePerTest}
import org.scalatest.LoneElement._
import tech.cryptonomic.conseil.api.metadata.{
  AttributeValuesCacheConfiguration,
  MetadataService,
  TransparentUnitTransformation
}
import tech.cryptonomic.conseil.api.routes.platform.data.ApiDataTypes.ApiQuery
import tech.cryptonomic.conseil.api.routes.platform.discovery.TestPlatformDiscoveryOperations
import tech.cryptonomic.conseil.common.config.MetadataConfiguration
import tech.cryptonomic.conseil.common.config.Platforms._
import tech.cryptonomic.conseil.common.generic.chain.DataTypes._
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, Entity, KeyType}
import tech.cryptonomic.conseil.common.metadata._
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

import scala.concurrent.ExecutionContext.Implicits.global

class ApiDataTypesTest extends ConseilSpec with MockFactory with BeforeAndAfterEach with OneInstancePerTest {

  val platformDiscoveryOperations = new TestPlatformDiscoveryOperations
  val cacheOverrides: AttributeValuesCacheConfiguration = stub[AttributeValuesCacheConfiguration]
  val metadataConf: MetadataConfiguration = MetadataConfiguration(Map.empty)

  def createMetadataService(stubbing: => Unit = ()): MetadataService = {
    stubbing

    new MetadataService(
      PlatformsConfiguration(
        List(
          TezosConfiguration("alphanet", enabled = true, TezosNodeConfiguration("tezos-host", 123, "https://"), None)
        )
      ),
      TransparentUnitTransformation,
      cacheOverrides,
      platformDiscoveryOperations
    )
  }

  val testNetworkPath: NetworkPath = NetworkPath("alphanet", PlatformPath("tezos"))
  val testEntityPath: EntityPath = EntityPath("testEntity", testNetworkPath)
  val testEntity: Entity = Entity("testEntity", "Test Entity", 0)

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
        val metadataService = createMetadataService {
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
        }

        val query = ApiQuery(
          fields = Some(List(SimpleField("valid"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf).futureValue
        result.right.value shouldBe Query(fields = List(SimpleField("valid")))
      }

      "return error with incorrect query fields" in {
        platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)

        val query = ApiQuery(
          fields = Some(List(SimpleField("invalid"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, createMetadataService(), metadataConf)

        result.futureValue.left.value should contain theSameElementsAs List(InvalidQueryField("invalid"))
      }

      "validate correct predicate field" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "valid",
          dataType = DataType.Int,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
        }

        val query = ApiQuery(
          fields = None,
          predicates = Some(List(ApiPredicate("valid", OperationType.in))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf).futureValue

        result.right.value shouldBe Query(predicates = List(Predicate("valid", OperationType.in)))
      }

      "return error with incorrect predicate fields" in {
        platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)

        val query = ApiQuery(
          fields = None,
          predicates = Some(List(ApiPredicate("invalid", OperationType.in))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, createMetadataService(), metadataConf)

        result.futureValue.left.value should contain theSameElementsAs List(InvalidPredicateField("invalid"))
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

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
        }

        val query = ApiQuery(
          fields = None,
          predicates = None,
          orderBy = Some(List(QueryOrdering("valid", OrderDirection.asc))),
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.right.value shouldBe Query(orderBy = List(QueryOrdering("valid", OrderDirection.asc)))
      }

      "return error with incorrect orderBy fields" in {
        platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)

        val query = ApiQuery(
          fields = None,
          predicates = None,
          orderBy = Some(List(QueryOrdering("invalid", OrderDirection.asc))),
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, createMetadataService(), metadataConf)

        result.futureValue.left.value should contain theSameElementsAs List(InvalidOrderByField("invalid"))
      }

      "validate correct aggregation field which exists in DB" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "valid",
          dataType = DataType.Int,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(SimpleField("valid"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "valid")))
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.right.value shouldBe Query(
          fields = List(SimpleField("valid")),
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
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(SimpleField("invalid"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "invalid")))
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.left.value should contain theSameElementsAs List(InvalidAggregationFieldForType("invalid"))
      }

      "return two errors when we try to aggregate on non-aggregating field type and with field that does not exist in query fields" in {
        val attribute = Attribute(
          name = "invalid",
          displayName = "Invalid",
          dataType = DataType.String,
          cardinality = None,
          keyType = KeyType.NonKey,
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(SimpleField("valid"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "invalid")))
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.left.value should contain theSameElementsAs List(
          InvalidQueryField("valid"),
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
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity.copy(limitedQuery = Some(true)))
        }

        val query = ApiQuery(
          fields = None,
          predicates = Some(List(ApiPredicate("InvalidAttribute", OperationType.in))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.left.value.loneElement shouldBe a[InvalidPredicateFiltering]
      }

      "successfully validates aggregation for any dataType when COUNT function is used" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "Valid",
          dataType = DataType.String, // only COUNT function can be used on types other than numeric and DateTime
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(SimpleField("valid"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "valid", function = AggregationType.count)))
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.right.value shouldBe Query(
          fields = List(SimpleField("valid")),
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

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = None,
          predicates =
            Some(List(ApiPredicate(field = "valid", operation = OperationType.in, set = Some(List(123456789000L))))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.right.value shouldBe Query(
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

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(SimpleField("validAttribute"))),
          predicates = None,
          orderBy = Some(List(QueryOrdering("count_validAttribute", direction = OrderDirection.asc))),
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "validAttribute", function = AggregationType.count)))
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.right.value shouldBe Query(
          fields = List(SimpleField("validAttribute")),
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

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(SimpleField("validAttribute"))),
          predicates = Some(List(ApiPredicate("count_validAttribute", OperationType.in))),
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "validAttribute", function = AggregationType.count)))
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.right.value shouldBe Query(
          fields = List(SimpleField("validAttribute")),
          predicates = List(Predicate("count_validAttribute", operation = OperationType.in)),
          aggregation = List(Aggregation("validAttribute", AggregationType.count))
        )
      }
      "correctly aggregate field with currency data type" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "Valid",
          dataType = DataType.Currency,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(SimpleField("valid"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = Some(List(ApiAggregation(field = "valid")))
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.right.value shouldBe Query(
          fields = List(SimpleField("valid")),
          aggregation = List(Aggregation("valid", AggregationType.sum))
        )
      }

      "return validation error when formatting is being done on non-DateTime field" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "Valid",
          dataType = DataType.String,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(FormattedField("valid", FormatType.datePart, "YYYY-MM-DD"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.left.value.loneElement shouldBe a[InvalidQueryFieldFormatting]
      }

      "correctly validate field with format" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "Valid",
          dataType = DataType.DateTime,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(FormattedField("valid", FormatType.datePart, "YYYY-MM-DD"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.right.value shouldBe Query(
          fields = List(FormattedField("valid", FormatType.datePart, "YYYY-MM-DD"))
        )
      }

      "return invalid snapshot field" in {
        val attribute = Attribute(
          name = "valid",
          displayName = "Valid",
          dataType = DataType.DateTime,
          cardinality = None,
          keyType = KeyType.UniqueKey,
          entity = "testEntity"
        )

        val metadataService = createMetadataService {
          platformDiscoveryOperations.addAttribute(testEntityPath, attribute)
          platformDiscoveryOperations.addEntity(testNetworkPath, testEntity)
        }

        val query = ApiQuery(
          fields = Some(List(SimpleField("valid"))),
          predicates = None,
          orderBy = None,
          limit = None,
          output = None,
          aggregation = None,
          snapshot = Some(Snapshot("invalid", 1234))
        )

        val result = query.validate(testEntityPath, metadataService, metadataConf)

        result.futureValue.left.value.loneElement shouldBe a[InvalidSnapshotField]
      }

    }
}
