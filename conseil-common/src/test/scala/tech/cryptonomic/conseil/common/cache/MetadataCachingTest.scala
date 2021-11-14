package tech.cryptonomic.conseil.common.cache

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.rklaehn.radixtree.RadixTree
import org.scalatest.OneInstancePerTest
import tech.cryptonomic.conseil.common.cache.MetadataCaching._
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes._
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class MetadataCachingTest extends ConseilSpec with OneInstancePerTest {

  val sut: MetadataCaching[IO] = MetadataCaching.empty[IO].unsafeRunSync()

  "Metadata caching" should {
      "get initial state of caching status" in {
        sut.getCachingStatus.unsafeRunSync() shouldBe NotStarted
      }

      "update cache status" in {
        sut.updateCachingStatus(InProgress).unsafeRunSync()

        sut.getCachingStatus.unsafeRunSync() shouldBe InProgress
      }

      "init attributes cache" in {
        val emptyAttributesCache: AttributesCache =
          Map(AttributesCacheKey("testPlatform", "testNetwork", "testTable") -> CacheEntry(0L, List()))
        sut.fillAttributesCache(emptyAttributesCache).unsafeRunSync()

        sut.getAttributes(AttributesCacheKey("not valid", "testNetwork", "testTable")).unsafeRunSync() shouldBe None
        sut.getAttributes(AttributesCacheKey("testPlatform", "not valid", "testTable")).unsafeRunSync() shouldBe None
        sut.getAttributes(AttributesCacheKey("testPlatform", "testNetwork", "not valid")).unsafeRunSync() shouldBe None
        sut.getAttributes(AttributesCacheKey("testPlatform", "testNetwork", "")).unsafeRunSync() shouldBe None
        sut.getAttributes(AttributesCacheKey("", "", "")).unsafeRunSync() shouldBe None
        sut.getAttributes(AttributesCacheKey("testPlatform", "testNetwork", "testTable")).unsafeRunSync() shouldBe Some(
          CacheEntry(0L, List())
        )
      }

      "init entities cache" in {
        val emptyEntitiesCache: EntitiesCache =
          Map(EntitiesCacheKey("testPlatform", "testNetwork") -> CacheEntry(0L, List()))
        sut.fillEntitiesCache(emptyEntitiesCache).unsafeRunSync()

        sut.getEntities(EntitiesCacheKey("testPlatform", "not valid")).unsafeRunSync() shouldBe None
        sut.getEntities(EntitiesCacheKey("testPlatform", "testNetwork")).unsafeRunSync() shouldBe Some(
          CacheEntry(0, List())
        )
      }

      "init attribute values cache" in {
        val attributeValuesCache: AttributeValuesCache =
          Map(
            AttributeValuesCacheKey("platform", "network", "table", "column") -> CacheEntry(
                  0L,
                  RadixTree[String, String]()
                )
          )
        sut.fillAttributeValuesCache(attributeValuesCache).unsafeRunSync()

        sut
          .getAttributeValues(
            AttributeValuesCacheKey("not valid", "not valid", "not valid", "not valid either")
          )
          .unsafeRunSync() shouldBe None

        val CacheEntry(_, result) = sut
          .getAttributeValues(
            AttributeValuesCacheKey("platform", "network", "table", "column")
          )
          .unsafeRunSync()
          .value
        result.values.toList shouldBe List.empty
      }

      "insert/update values in entities cache" in {
        val emptyEntitiesCache: EntitiesCache =
          Map(EntitiesCacheKey("testPlatform", "testNetwork") -> CacheEntry(0L, List()))
        val entitiesList = List(Entity("a", "b", 0))
        val updatedEntityList = List(Entity("x", "y", 0))
        sut.fillEntitiesCache(emptyEntitiesCache).unsafeRunSync()

        // insert
        sut.putEntities(EntitiesCacheKey("testPlatform", "differentTestNetwork"), entitiesList).unsafeRunSync()
        val CacheEntry(_, insertResult) =
          sut.getEntities(EntitiesCacheKey("testPlatform", "differentTestNetwork")).unsafeRunSync().value
        insertResult shouldBe entitiesList

        // update
        sut.putEntities(EntitiesCacheKey("testPlatform", "differentTestNetwork"), updatedEntityList).unsafeRunSync()
        val CacheEntry(_, updateResult) =
          sut.getEntities(EntitiesCacheKey("testPlatform", "differentTestNetwork")).unsafeRunSync().value
        updateResult shouldBe updatedEntityList
        sut.getAllEntities.unsafeRunSync().mapValues(_.value) shouldBe Map(
          EntitiesCacheKey("testPlatform", "testNetwork") -> List(),
          EntitiesCacheKey("testPlatform", "differentTestNetwork") -> List(Entity("x", "y", 0, None))
        )
      }

      "insert/update values in attributes cache" in {
        val emptyAttributesCache: AttributesCache =
          Map(AttributesCacheKey("testPlatform", "testNetwork", "testEntity") -> CacheEntry(0L, List()))
        val attributesList = List(Attribute("a", "b", DataType.String, None, KeyType.NonKey, "c"))
        val updatedAttributesList = List(Attribute("x", "y", DataType.String, None, KeyType.NonKey, "z"))
        sut.fillAttributesCache(emptyAttributesCache).unsafeRunSync()

        // insert
        sut
          .putAttributes(AttributesCacheKey("testPlatform", "testNetwork", "differentTestEntity"), attributesList)
          .unsafeRunSync()
        val CacheEntry(_, insertResult) =
          sut
            .getAttributes(AttributesCacheKey("testPlatform", "testNetwork", "differentTestEntity"))
            .unsafeRunSync()
            .value
        insertResult shouldBe attributesList

        // update
        sut
          .putAttributes(
            AttributesCacheKey("testPlatform", "testNetwork", "differentTestEntity"),
            updatedAttributesList
          )
          .unsafeRunSync()

        sut
          .getAttributes(AttributesCacheKey("testPlatform", "testNetwork", "differentTestEntity"))
          .unsafeRunSync() match {
          case Some(CacheEntry(_, updateResult)) => updateResult shouldBe updatedAttributesList
          case None => fail("Expected some `CacheEntity`, but got None")
        }

        sut.getAllAttributes.unsafeRunSync().mapValues(_.value) shouldBe
          Map(
            AttributesCacheKey("testPlatform", "testNetwork", "testEntity") -> List(),
            AttributesCacheKey("testPlatform", "testNetwork", "differentTestEntity") -> List(
                  Attribute("x", "y", DataType.String, None, KeyType.NonKey, "z")
                )
          )
      }

      "insert/update values in attribute values cache" in {
        val emptyAttributeValuesCache: AttributeValuesCache =
          Map(
            AttributeValuesCacheKey("platform", "network", "table", "column") -> CacheEntry(
                  0L,
                  RadixTree[String, String]()
                )
          )
        val attributeValuesTree = RadixTree[String, String]("a" -> "a")
        val updatedAttributeValuesTree = RadixTree[String, String]("b" -> "b")
        sut.fillAttributeValuesCache(emptyAttributeValuesCache).unsafeRunSync()

        // insert
        sut
          .putAttributeValues(AttributeValuesCacheKey("platform", "network", "table2", "column2"), attributeValuesTree)
          .unsafeRunSync()

        sut
          .getAttributeValues(AttributeValuesCacheKey("platform", "network", "table2", "column2"))
          .unsafeRunSync() match {
          case Some(CacheEntry(_, insertResult)) => insertResult.values.toList shouldBe List("a")
          case None => fail("Expected some `CacheEntity`, but got None")
        }

        // update
        sut
          .putAttributeValues(
            AttributeValuesCacheKey("platform", "network", "table2", "column2"),
            updatedAttributeValuesTree
          )
          .unsafeRunSync()

        sut
          .getAttributeValues(AttributeValuesCacheKey("platform", "network", "table2", "column2"))
          .unsafeRunSync() match {
          case Some(CacheEntry(_, updateResult)) => updateResult.values.toList shouldBe List("b")
          case None => fail("Expected some `CacheEntity`, but got None")
        }
      }

      "handle entities for multiple platforms" in {
        val entitiesKey1 = EntitiesCacheKey("platform", "network")
        val entities1 = List(Entity("a", "a", 0))

        val entitiesKey2 = EntitiesCacheKey("platform-new", "network-new")
        val entities2 = List(Entity("b", "b", 0))

        val entities3 = List(Entity("c", "c", 0))

        val entitiesCache: EntitiesCache = Map(
          entitiesKey1 -> CacheEntry(0L, entities1),
          entitiesKey2 -> CacheEntry(0L, entities2)
        )

        // insert
        sut.fillEntitiesCache(entitiesCache).unsafeRunSync() shouldBe true

        // update
        sut.putEntities(entitiesKey1, entities3).unsafeRunSync()

        // get
        sut.getEntities(entitiesKey1).unsafeRunSync().value.value should contain theSameElementsAs entities3
        sut.getEntities(entitiesKey2).unsafeRunSync().value.value should contain theSameElementsAs entities2
      }

      "handle attributes for multiple platforms" in {
        val attributesKey1 = AttributesCacheKey("platform", "network", "table")
        val attributes1 = List(Attribute("a", "b", DataType.String, None, KeyType.NonKey, "c"))

        val attributesKey2 = AttributesCacheKey("platform-new", "network-new", "table-new")
        val attributes2 = List(Attribute("d", "e", DataType.String, None, KeyType.NonKey, "f"))

        val attributesCache: AttributesCache = Map(
          attributesKey1 -> CacheEntry(0L, attributes1),
          attributesKey2 -> CacheEntry(0L, attributes2)
        )

        sut.fillAttributesCache(attributesCache).unsafeRunSync() shouldBe true

        sut.getAttributes(attributesKey1).unsafeRunSync().value.value should contain theSameElementsAs attributes1
        sut.getAttributes(attributesKey2).unsafeRunSync().value.value should contain theSameElementsAs attributes2
      }

      "handle attribute values for multiple platforms" in {
        val attributesValueKey1 = AttributeValuesCacheKey("platform", "network", "table", "column")
        val attributesValue1 = RadixTree[String, String]("a" -> "a")

        val attributesValueKey2 = AttributeValuesCacheKey("platform-new", "network-new", "table-new", "column-new")
        val attributesValue2 = RadixTree[String, String]("b" -> "b")

        val attributesValueCache: AttributeValuesCache = Map(
          attributesValueKey1 -> CacheEntry(0L, attributesValue1),
          attributesValueKey2 -> CacheEntry(0L, attributesValue2)
        )

        sut.fillAttributeValuesCache(attributesValueCache).unsafeRunSync() shouldBe true

        sut.getAttributeValues(attributesValueKey1).unsafeRunSync().value.value shouldBe attributesValue1
        sut.getAttributeValues(attributesValueKey2).unsafeRunSync().value.value shouldBe attributesValue2
      }
    }

}
