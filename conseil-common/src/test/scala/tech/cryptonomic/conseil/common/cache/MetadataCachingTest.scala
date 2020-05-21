package tech.cryptonomic.conseil.common.cache

import cats.effect._
import com.rklaehn.radixtree.RadixTree
import org.scalatest.{Matchers, OneInstancePerTest, OptionValues, WordSpec}
import tech.cryptonomic.conseil.common.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, Entity, KeyType}
import tech.cryptonomic.conseil.common.cache.MetadataCaching._

import scala.concurrent.ExecutionContext

class MetadataCachingTest extends WordSpec with Matchers with OneInstancePerTest with OptionValues {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

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
        val emptyAttributesCache: AttributesCache = Map(CacheKey("testNetwork") -> CacheEntry(0L, List()))
        sut.fillAttributesCache(emptyAttributesCache).unsafeRunSync()

        sut.getAttributes("not valid").unsafeRunSync() shouldBe None
        sut.getAttributes("testNetwork").unsafeRunSync() shouldBe Some(CacheEntry(0, List()))
      }

      "init entities cache" in {
        val emptyEntitiesCache: EntitiesCache = Map(CacheKey("testNetwork") -> CacheEntry(0L, List()))
        sut.fillEntitiesCache(emptyEntitiesCache).unsafeRunSync()

        sut.getEntities("not valid").unsafeRunSync() shouldBe None
        sut.getEntities("testNetwork").unsafeRunSync() shouldBe Some(CacheEntry(0, List()))
      }

      "init attribute values cache" in {
        val attributeValuesCache: AttributeValuesCache =
          Map(CacheKey("table.column") -> CacheEntry(0L, RadixTree[String, String]()))
        sut.fillAttributeValuesCache(attributeValuesCache).unsafeRunSync()

        sut.getAttributeValues("not valid", "not valid either").unsafeRunSync() shouldBe None
        val CacheEntry(_, result) = sut.getAttributeValues("table", "column").unsafeRunSync().value
        result.values.toList shouldBe List.empty
      }

      "insert/update values in entities cache" in {
        val emptyEntitiesCache: EntitiesCache = Map(CacheKey("testNetwork") -> CacheEntry(0L, List()))
        val entitiesList = List(Entity("a", "b", 0))
        val updatedEntityList = List(Entity("x", "y", 0))
        sut.fillEntitiesCache(emptyEntitiesCache).unsafeRunSync()

        // insert
        sut.putEntities("differentTestNetwork", entitiesList).unsafeRunSync()
        val CacheEntry(_, insertResult) = sut.getEntities("differentTestNetwork").unsafeRunSync().value
        insertResult shouldBe entitiesList

        // update
        sut.putEntities("differentTestNetwork", updatedEntityList).unsafeRunSync()
        val CacheEntry(_, updateResult) = sut.getEntities("differentTestNetwork").unsafeRunSync().value
        updateResult shouldBe updatedEntityList
        sut.getAllEntities.unsafeRunSync().mapValues(_.value) shouldBe Map(
          CacheKey("testNetwork") -> List(),
          CacheKey("differentTestNetwork") -> List(Entity("x", "y", 0, None))
        )
      }

      "insert/update values in attributes cache" in {
        val emptyAttributesCache: AttributesCache = Map(CacheKey("testEntity") -> CacheEntry(0L, List()))
        val attributesList = List(Attribute("a", "b", DataType.String, None, KeyType.NonKey, "c"))
        val updatedAttributesList = List(Attribute("x", "y", DataType.String, None, KeyType.NonKey, "z"))
        sut.fillAttributesCache(emptyAttributesCache).unsafeRunSync()

        // insert
        sut.putAttributes("differentTestEntity", attributesList).unsafeRunSync()
        val CacheEntry(_, insertResult) = sut.getAttributes("differentTestEntity").unsafeRunSync().value
        insertResult shouldBe attributesList

        // update
        sut.putAttributes("differentTestEntity", updatedAttributesList).unsafeRunSync()
        val CacheEntry(_, updateResult) = sut.getAttributes("differentTestEntity").unsafeRunSync().get
        updateResult shouldBe updatedAttributesList
        sut.getAllAttributes.unsafeRunSync().mapValues(_.value) shouldBe
          Map(
            CacheKey("testEntity") -> List(),
            CacheKey("differentTestEntity") -> List(Attribute("x", "y", DataType.String, None, KeyType.NonKey, "z"))
          )
      }

      "insert/update values in attribute values cache" in {
        val emptyAttributeValuesCache: AttributeValuesCache =
          Map(CacheKey("table.column") -> CacheEntry(0L, RadixTree[String, String]()))
        val attributeValuesTree = RadixTree[String, String]("a" -> "a")
        val updatedAttributeValuesTree = RadixTree[String, String]("b" -> "b")
        sut.fillAttributeValuesCache(emptyAttributeValuesCache).unsafeRunSync()

        // insert
        sut.putAttributeValues("table2", "column2", attributeValuesTree).unsafeRunSync()
        val CacheEntry(_, insertResult) = sut.getAttributeValues("table2", "column2").unsafeRunSync().get
        insertResult.values.toList shouldBe List("a")

        // update
        sut.putAttributeValues("table2", "column2", updatedAttributeValuesTree).unsafeRunSync()
        val CacheEntry(_, updateResult) = sut.getAttributeValues("table2", "column2").unsafeRunSync().get
        updateResult.values.toList shouldBe List("b")
      }
    }

}
