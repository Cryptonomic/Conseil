package tech.cryptonomic.conseil.tezos

import org.scalatest.Matchers
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.WordSpec
import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.util.RandomSeed
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  Contract,
  Decimal,
  Micheline,
  Operation,
  OperationsGroup,
  Origination,
  ScriptId,
  Transaction
}
import tech.cryptonomic.conseil.tezos.Tables.BigMapContentsRow
import tech.cryptonomic.conseil.tezos.Tables.BigMapsRow
import com.softwaremill.diffx.scalatest.DiffMatcher._
import tech.cryptonomic.conseil.tezos.Tables.OriginatedAccountMapsRow
import tech.cryptonomic.conseil.tezos.michelson.contracts.TokenContracts

class BigMapOperationsTest
    extends WordSpec
    with TezosDataGeneration
    with InMemoryDatabase
    with Matchers
    with ScalaFutures
    with LazyLogging
    with IntegrationPatience {

  "The big-maps operations" should {
      //simplify signatures
      type ListTransf[A] = List[A] => List[A]
      //needed for most tezos-db operations
      import scala.concurrent.ExecutionContext.Implicits.global

      val sut = BigMapsOperations(TezosDatabaseOperations.CustomPostgresProfile)

      "save big map diffs allocations contained in a list of blocks" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        //use combinators defined in the fixtures to update big_map_diff values within lists of operations
        val allocateMap: ListTransf[Operation] = Operations.updateOperationsWithBigMapAllocation {
          case (_: Origination) =>
            Contract.BigMapAlloc(
              action = "alloc",
              big_map = Decimal(1),
              key_type = Micheline("""{"prim":"address"}"""),
              value_type = Micheline("""{"prim":"nat"}""")
            )
        }

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = allocateMap(sampleOperations.contents)
          ) :: Nil

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //when
        val writeAndGetRows = sut.saveMaps(blockToSave :: Nil) andThen Tables.BigMaps.result

        val maps = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
        maps.size shouldBe 1

        maps(0) should matchTo(
          BigMapsRow(
            bigMapId = BigDecimal(1),
            keyType = Some("address"),
            valueType = Some("nat")
          )
        )
      }

      "save big map diffs updates contained in a list of blocks" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        //use combinators defined in the fixtures to update big_map_diff values within lists of operations
        val updateMap: ListTransf[Operation] = Operations.updateOperationsWithBigMapUpdate {
          case (_: Transaction) =>
            Contract.BigMapUpdate(
              action = "udpate",
              big_map = Decimal(1),
              key = Micheline("""{"bytes":"0000b2e19a9e74440d86c59f13dab8a18ff873e889ea"}"""),
              key_hash = ScriptId("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
              value = Some(Micheline("""{"prim":"Pair", "args": [{"int":"20"},[]]}"""))
            )
        }

        //we need this to be referred as a FK from the content record
        val initialBigMap = BigMapsRow(
          bigMapId = BigDecimal(1),
          keyType = Some("address"),
          valueType = Some("nat")
        )

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = updateMap(sampleOperations.contents)
          ) :: Nil

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //when
        val writeAndGetRows = for {
          _ <- Tables.BigMaps += initialBigMap
          _ <- sut.upsertContent(blockToSave :: Nil)
          contents <- Tables.BigMapContents.result
        } yield contents

        val contents = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
        contents.size shouldBe 1

        contents(0) should matchTo(
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}")
          )
        )

      }

      "save big map diffs references to originated accounts in a list of blocks" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        //use combinators defined in the fixtures to update big_map_diff values within lists of operations
        val allocateMap: ListTransf[Operation] = Operations.updateOperationsWithBigMapAllocation {
          case (_: Origination) =>
            Contract.BigMapAlloc(
              action = "alloc",
              big_map = Decimal(1),
              key_type = Micheline("""{"prim":"address"}"""),
              value_type = Micheline("""{"prim":"nat"}""")
            )
        }

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = allocateMap(sampleOperations.contents)
          ) :: Nil

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //when
        implicit val noTokenContract = TokenContracts.fromTokens(List.empty)
        val writeAndGetRows = sut.saveContractOrigin(blockToSave :: Nil) andThen Tables.OriginatedAccountMaps.result

        val accounts = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //the origination used for the generated sample is used to create the test big map
        val sampleAccountIds =
          Operations.sampleOrigination.metadata.operation_result.originated_contracts
            .fold(List.empty[String])(_.map(_.id))

        //then
        accounts.size shouldEqual sampleAccountIds.size

        accounts should contain theSameElementsAs sampleAccountIds.map(
          id =>
            OriginatedAccountMapsRow(
              bigMapId = BigDecimal(1),
              accountId = id
            )
        )

      }

      "allow content diff updates for existing keys in a big map" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val initialBigMap = BigMapsRow(
          bigMapId = BigDecimal(1),
          keyType = Some("address"),
          valueType = Some("nat")
        )
        val initialBigMapContent = BigMapContentsRow(
          bigMapId = BigDecimal(1),
          key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
          keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
          value = Some("Pair 20 {}")
        )

        val populate = for {
          mapAdded <- Tables.BigMaps += initialBigMap
          contentAdded <- Tables.BigMapContents += initialBigMapContent
        } yield (mapAdded, contentAdded)

        dbHandler.run(populate).futureValue shouldEqual ((1, 1))

        val updateMap: ListTransf[Operation] = Operations.updateOperationsWithBigMapUpdate {
          case (_: Transaction) =>
            Contract.BigMapUpdate(
              action = "udpate",
              big_map = Decimal(1),
              key = Micheline("""{"bytes":"0000b2e19a9e74440d86c59f13dab8a18ff873e889ea"}"""),
              key_hash = ScriptId("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
              value = Some(Micheline("""{"prim":"Pair", "args": [{"int":"50"},[]]}"""))
            )
        }

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = updateMap(sampleOperations.contents)
          ) :: Nil

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //when
        val writeAndGetRows = sut.upsertContent(blockToSave :: Nil) andThen Tables.BigMapContents.result

        val contents = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
        contents.size shouldBe 1

        contents(0) should matchTo(
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 50 {}")
          )
        )
      }

      "copy big map contents for diffs contained in a list of blocks" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        //we need 2 pre-existing big maps to transafer content between
        val initialBigMaps =
          1 :: 2 :: Nil map (
                  i =>
                    BigMapsRow(
                      bigMapId = BigDecimal(i),
                      keyType = None,
                      valueType = None
                    )
                )

        //the content to copy
        val initialBigMapContent =
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}")
          )

        //store the data
        val populate = for {
          maps <- Tables.BigMaps ++= initialBigMaps
          contents <- Tables.BigMapContents += initialBigMapContent
        } yield (maps, contents)

        dbHandler.run(populate.transactionally).futureValue shouldEqual ((Some(2), 1))

        //we want to copy content between the first and second map, and then remove the former
        val copyMap: ListTransf[Operation] = Operations.updateOperationsWithBigMapCopy {
          case (_: Transaction) =>
            Contract.BigMapCopy(
              action = "copy",
              source_big_map = Decimal(1),
              destination_big_map = Decimal(2)
            )
        }

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = copyMap(sampleOperations.contents)
          ) :: Nil

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //when
        val writeAndGetRows = for {
          _ <- sut.copyContent(blockToSave :: Nil)
          maps <- Tables.BigMaps.result
          contents <- Tables.BigMapContents.result
        } yield (maps, contents)

        val (maps, contents) = dbHandler.run(writeAndGetRows).futureValue

        //then
        maps.size shouldBe initialBigMaps.size

        contents.size shouldBe 2

        contents(1) should matchTo(
          BigMapContentsRow(
            bigMapId = BigDecimal(2),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}")
          )
        )

      }

      "delete all data in selected big maps for diffs contained in a list of blocks" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        //we need 2 pre-existing big maps to transafer content between
        val initialBigMaps =
          1 :: 2 :: Nil map (
                  i =>
                    BigMapsRow(
                      bigMapId = BigDecimal(i),
                      keyType = None,
                      valueType = None
                    )
                )

        //the content to copy
        val initialBigMapContent =
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}")
          )

        //the origination used for the generated sample is used to create the test big map
        val sampleAccountIds =
          Operations.sampleOrigination.metadata.operation_result.originated_contracts
            .fold(List.empty[String])(_.map(_.id))

        val initialOriginatedReferences = sampleAccountIds.map(
          id =>
            OriginatedAccountMapsRow(
              bigMapId = BigDecimal(1),
              accountId = id
            )
        )

        //store the data
        val populate = for {
          maps <- Tables.BigMaps ++= initialBigMaps
          contents <- Tables.BigMapContents += initialBigMapContent
          links <- Tables.OriginatedAccountMaps ++= initialOriginatedReferences
        } yield (maps, contents, links)

        dbHandler.run(populate.transactionally).futureValue shouldEqual ((Some(2), 1, Some(1)))

        val removeMap: ListTransf[Operation] = Operations.updateOperationsWithBigMapRemove {
          case (_: Transaction) =>
            Contract.BigMapRemove(
              action = "remove",
              big_map = Decimal(1)
            )
        }

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = removeMap(sampleOperations.contents)
          ) :: Nil

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //when
        val writeAndGetRows = for {
          _ <- sut.removeMaps(blockToSave :: Nil)
          maps <- Tables.BigMaps.result
          contents <- Tables.BigMapContents.result
          accountMapLinks <- Tables.OriginatedAccountMaps.result
        } yield (maps, contents, accountMapLinks)

        val (maps, contents, accounts) = dbHandler.run(writeAndGetRows).futureValue

        //then
        maps.size shouldEqual (initialBigMaps.size - 1)
        maps.map(_.bigMapId) should not contain BigDecimal(1)
        contents shouldBe 'empty
        accounts shouldBe 'empty

      }

    }

}
