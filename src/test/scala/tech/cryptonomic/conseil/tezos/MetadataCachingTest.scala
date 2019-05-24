package tech.cryptonomic.conseil.tezos

import cats.effect._
import cats.effect.concurrent.MVar
import com.rklaehn.radixtree.RadixTree
import org.scalatest.{Matchers, OneInstancePerTest, WordSpec}
import tech.cryptonomic.conseil.generic.chain.PlatformDiscoveryTypes.{Attribute, DataType, Entity, KeyType}
import tech.cryptonomic.conseil.tezos.MetadataCaching._

import scala.concurrent.ExecutionContext

class MetadataCachingTest extends WordSpec with Matchers with OneInstancePerTest {

  implicit val contextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)


  val cachingStatus: MVar[IO, CachingStatus] = MVar[IO].of[CachingStatus](NotStarted).unsafeRunSync()
  val attributesCache: MVar[IO, AttributesCache] = MVar[IO].empty[AttributesCache].unsafeRunSync()
  val entitiesCache: MVar[IO, EntitiesCache] = MVar[IO].empty[EntitiesCache].unsafeRunSync()
  val attributeValuesCache: MVar[IO, AttributeValuesCache] = MVar[IO].empty[AttributeValuesCache].unsafeRunSync()

  val sut: MetadataCaching[IO] = new MetadataCaching[IO](cachingStatus, attributesCache, entitiesCache, attributeValuesCache)

  "Metadata caching" should {
    "get initial state of caching status" in {
      sut.getCachingStatus.unsafeRunSync() shouldBe NotStarted
    }

    "update cache status" in {
      sut.updateCachingStatus(InProgress).unsafeRunSync()

      sut.getCachingStatus.unsafeRunSync() shouldBe InProgress
    }

    "init attributes cache" in {
      val emptyAttributesCache: AttributesCache = Map("example" -> (0L, List()))
      sut.putAllAttributesIntoEmptyCache(emptyAttributesCache).unsafeRunSync()

      sut.getAttributes("not valid").unsafeRunSync() shouldBe None
      sut.getAttributes("example").unsafeRunSync() shouldBe Some(0L, List())
    }

    "init entities cache" in {
      val emptyEntitiesCache: EntitiesCache = Map("example" -> (0L, List()))
      sut.putAllEntitiesIntoEmptyCache(emptyEntitiesCache).unsafeRunSync()

      sut.getEntities("not valid").unsafeRunSync() shouldBe None
      sut.getEntities("example").unsafeRunSync() shouldBe Some(0L, List())
    }

    "init attribute values cache" in {
      val attributeValuesCache: AttributeValuesCache = Map("table.column" -> (0L, RadixTree[String, String]()))
      sut.putAllAttributeValuesIntoEmptyCache(attributeValuesCache).unsafeRunSync()

      sut.getAttributeValues("not valid", "not valid either").unsafeRunSync() shouldBe None
      val (_, result) = sut.getAttributeValues("table", "column").unsafeRunSync().get
      result.values.toList shouldBe List.empty
    }

    "insert/update values in entities cache" in {
      val emptyEntitiesCache: EntitiesCache = Map("example" -> (0L, List()))
      val entitiesList = List(Entity("a", "b", 0))
      val updatedEntityList = List(Entity("x", "y", 0))
      sut.putAllEntitiesIntoEmptyCache(emptyEntitiesCache).unsafeRunSync()

      // insert
      sut.putEntities("another example", entitiesList).unsafeRunSync()
      val (_, insertResult) = sut.getEntities("another example").unsafeRunSync().get
      insertResult shouldBe entitiesList

      // update
      sut.putEntities("another example", updatedEntityList).unsafeRunSync()
      val (_, updateResult) = sut.getEntities("another example").unsafeRunSync().get
      updateResult shouldBe updatedEntityList
      sut.getAllEntities.unsafeRunSync().mapValues(_._2) shouldBe Map("example" -> List(), "another example" -> List(Entity("x", "y", 0)))
    }

    "insert/update values in attributes cache" in {
      val emptyAttributesCache: AttributesCache = Map("example" -> (0L, List()))
      val attributesList = List(Attribute("a", "b", DataType.String, None, KeyType.NonKey, "c"))
      val updatedAttributesList = List(Attribute("x", "y", DataType.String, None, KeyType.NonKey, "z"))
      sut.putAllAttributesIntoEmptyCache(emptyAttributesCache).unsafeRunSync()

      // insert
      sut.putAttributes("another example", attributesList).unsafeRunSync()
      val (_, insertResult) = sut.getAttributes("another example").unsafeRunSync().get
      insertResult shouldBe attributesList

      // update
      sut.putAttributes("another example", updatedAttributesList).unsafeRunSync()
      val (_, updateResult) = sut.getAttributes("another example").unsafeRunSync().get
      updateResult shouldBe updatedAttributesList
      sut.getAllAttributes.unsafeRunSync().mapValues(_._2) shouldBe
        Map("example" -> List(), "another example" -> List(Attribute("x", "y", DataType.String, None, KeyType.NonKey, "z")))
    }

    "insert/update values in attribute values cache" in {
      val emptyAttributeValuesCache: AttributeValuesCache = Map("table.column" -> (0L, RadixTree[String, String]()))
      val attributeValuesTree = RadixTree[String, String]("a" -> "a")
      val updatedAttributeValuesTree = RadixTree[String, String]("b" -> "b")
      sut.putAllAttributeValuesIntoEmptyCache(emptyAttributeValuesCache).unsafeRunSync()

      // insert
      sut.putAttributeValues("table2", "column2", attributeValuesTree).unsafeRunSync()
      val (_, insertResult) = sut.getAttributeValues("table2", "column2").unsafeRunSync().get
      insertResult.values.toList shouldBe List("a")

      // update
      sut.putAttributeValues("table2", "column2", updatedAttributeValuesTree).unsafeRunSync()
      val (_, updateResult) = sut.getAttributeValues("table2", "column2").unsafeRunSync().get
      updateResult.values.toList shouldBe List("b")
    }
  }

}
