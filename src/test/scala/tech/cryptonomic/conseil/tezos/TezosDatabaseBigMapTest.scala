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

class TezosDatabaseBigMapTest
    extends WordSpec
    with TezosDataGeneration
    with InMemoryDatabase
    with Matchers
    with ScalaFutures
    with LazyLogging
    with IntegrationPatience {

  "The database api" should {
      //simplify signatures
      type ListTransf[A] = List[A] => List[A]
      //needed for most tezos-db operations
      import scala.concurrent.ExecutionContext.Implicits.global

      val sut = TezosDatabaseOperations

      "save big map diffs allocations and updates contained in a list of blocks" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        //use combinators defined in the fixtures to update big_map_diff values within lists of operations
        val allocateMap: ListTransf[Operation] = Operations.updateOperationToBigMapAllocation {
          case (_: Origination) =>
            Contract.BigMapAlloc(
              action = "alloc",
              big_map = Decimal(1),
              key_type = Micheline("""{"prim":"address"}"""),
              value_type = Micheline("""{"prim":"nat"}""")
            )
        } _

        val updateMap: ListTransf[Operation] = Operations.updateOperationToBigMapUpdate {
          case (_: Transaction) =>
            Contract.BigMapUpdate(
              action = "udpate",
              big_map = Decimal(1),
              key = Micheline("""{"bytes":"0000b2e19a9e74440d86c59f13dab8a18ff873e889ea"}"""),
              key_hash = ScriptId("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
              value = Some(Micheline("""{"prim":"Pair", "args": [{"int":"20"},[]]}"""))
            )
        } _

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = (allocateMap andThen updateMap)(sampleOperations.contents)
          ) :: Nil

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //when
        val writeAndGetRows = for {
          _ <- sut.saveBigMaps(blockToSave :: Nil)
          maps <- Tables.BigMaps.result
          contents <- Tables.BigMapContents.result
          accountMapLinks <- Tables.OriginatedAccountMaps.result
        } yield (maps, contents, accountMapLinks)

        val (maps, contents, accounts) = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //the origination used for the generated sample is used to create the test big map
        val sampleAccountIds =
          Operations.sampleOrigination.metadata.operation_result.originated_contracts
            .fold(List.empty[String])(_.map(_.id))

        //then
        maps.size shouldBe 1
        contents.size shouldBe 1
        accounts.size shouldEqual sampleAccountIds.size

        maps(0) should matchTo(
          BigMapsRow(
            bigMapId = BigDecimal(1),
            keyType = Some("address"),
            valueType = Some("nat")
          )
        )
        contents(0) should matchTo(
          BigMapContentsRow(
            bigMapId = Some(BigDecimal(1)),
            key = Some("0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea"),
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("(Pair 20 {})")
          )
        )

        accounts should contain theSameElementsAs sampleAccountIds.map(
          id =>
            OriginatedAccountMapsRow(
              bigMapId = BigDecimal(1),
              accountId = id
            )
        )

      }

      "copy big map content for diffs contained in a list of blocks" in pending

      "delete big maps for diffs contained in a list of blocks" in pending

    }

}
