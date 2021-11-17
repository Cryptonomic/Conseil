package tech.cryptonomic.conseil.indexer.tezos.bigmaps

import java.sql.Timestamp

import org.scalatest.concurrent.IntegrationPatience
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.sql.CustomProfileExtension
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.testkit.util.RandomSeed
import tech.cryptonomic.conseil.common.tezos.Tables.{
  BigMapContentsRow,
  BigMapsRow,
  OriginatedAccountMapsRow,
  TokenBalancesRow
}
import tech.cryptonomic.conseil.common.tezos.{Fork, Tables, TezosOptics, TezosTypes}
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.indexer.tezos.{TezosDatabaseOperationsTestFixtures, TezosInMemoryDatabaseSetup}
import com.softwaremill.diffx.scalatest.DiffShouldMatcher._
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class BigMapsOperationsTest
    extends ConseilSpec
    with TezosDatabaseOperationsTestFixtures
    with InMemoryDatabase
    with TezosInMemoryDatabaseSetup
    with IntegrationPatience {

  import com.softwaremill.diffx.generic.auto._

  "The big-maps operations" should {
      //simplify signatures
      type ListTransf[A] = List[A] => List[A]
      //needed for most tezos-db operations
      import scala.concurrent.ExecutionContext.Implicits.global

      val sut = BigMapsOperations(CustomProfileExtension)

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
            ) :: Nil
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

        maps(0) shouldMatchTo (
          BigMapsRow(
            bigMapId = BigDecimal(1),
            keyType = Some("address"),
            valueType = Some("nat"),
            forkId = Fork.mainForkId,
            blockLevel = Some(1)
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
            ) :: Nil
        }

        //we need this to be referred as a FK from the content record
        val initialBigMap = BigMapsRow(
          bigMapId = BigDecimal(1),
          keyType = Some("address"),
          valueType = Some("nat"),
          forkId = Fork.mainForkId
        )

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = updateMap(sampleOperations.contents)
          ) :: Nil
        val opGroupHash = sampleOperations.hash

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

        contents(0) shouldMatchTo (
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}"),
            valueMicheline = Some("""{"prim":"Pair", "args": [{"int":"20"},[]]}"""),
            operationGroupId = Some(opGroupHash.value),
            blockLevel = Some(block.data.header.level),
            timestamp = Some(Timestamp.from(block.data.header.timestamp.toInstant)),
            cycle = TezosOptics.Blocks.extractCycle(block),
            period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
            forkId = Fork.mainForkId
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
            ) :: Nil
        }

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = allocateMap(sampleOperations.contents)
          ) :: Nil

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //when
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

      "save token balance updates for corresponding big map updates on transactions to a token contract" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val tokenAddress = ContractId("KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux")
        val tokenMap = 1
        //this should match with the key data in the big map diff, after being encoded correctly
        val targetAccount = "tz1b2icJC4E7Y2ED1xsZXuqYpF7cxHDtduuP"

        //use combinators defined in the fixtures to update big_map_diff values and more within lists of operations
        val updateMap: ListTransf[Operation] = Operations.updateOperationsWithBigMapUpdate {
          case (_: Transaction) =>
            Contract.BigMapUpdate(
              action = "update",
              big_map = Decimal(tokenMap),
              key = Micheline("""{"bytes":"0000a8d45bdc966ddaaac83188a1e1c1fde2a3e05e5c"}"""),
              key_hash = ScriptId("exprvKTBQDAyXTMRc36TsLBsj9y5GXo1PD529MfF8zDV1pVzNNgehs"),
              value = Some(Micheline("""{"prim":"Pair", "args": [{"int":"50"},[]]}"""))
            ) :: Nil
        }

        //we're gonna direct any sample transaction to the token contract, referring to a test account
        val updateTransactionDestination: ListTransf[Operation] = Operations.modifyTransactions {
          case transaction =>
            transaction.copy(
              destination = tokenAddress,
              //we only need the address, as a valid json string, to be in the param string
              parameters = Some(Left(TezosTypes.Parameters(Micheline(s""""$targetAccount""""))))
            )
        }

        //we need this to be referred as a FK from the content record
        val initialBigMap = BigMapsRow(
          bigMapId = BigDecimal(tokenMap),
          keyType = Some("address"),
          valueType = Some("pair (nat :balance) (map :approvals (address :spender) (nat :value))"),
          forkId = Fork.mainForkId
        )

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = (updateTransactionDestination andThen updateMap)(sampleOperations.contents)
          ) :: Nil

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //prepare the token registry

        val registeredToken = Tables.RegisteredTokensRow(
          "name",
          "symbol",
          0,
          "[TZIP-7]",
          tokenAddress.id,
          None,
          1,
          "",
          "",
          "",
          "",
          false,
          false,
          None,
          None,
          None,
          None
        )

        implicit val fa12Tokens = TokenContracts.fromConfig(List(tokenAddress -> "[TZIP-7]"))
        fa12Tokens.setMapId(tokenAddress, BigDecimal(tokenMap))

        //when
        val writeAndGetRows = for {
          _ <- Tables.RegisteredTokens += registeredToken
          _ <- Tables.BigMaps += initialBigMap
          _ <- sut.updateTokenBalances(blockToSave :: Nil)
          contents <- Tables.TokenBalances.result
        } yield contents

        val tokenUpdates = dbHandler.run(writeAndGetRows).futureValue

        //then
        tokenUpdates should have size 1

        tokenUpdates(0) shouldMatchTo (
          TokenBalancesRow(
            tokenAddress = "KT1RmDuQ6LaTFfLrVtKNcBJkMgvnopEATJux",
            address = targetAccount,
            balance = BigDecimal(50),
            blockId = blockToSave.data.hash.value,
            blockLevel = blockToSave.data.header.level,
            asof = Timestamp.from(blockToSave.data.header.timestamp.toInstant),
            forkId = Fork.mainForkId
          )
        )

      }

      "allow content diff updates for existing keys in a big map" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val initialBigMap = BigMapsRow(
          bigMapId = BigDecimal(1),
          keyType = Some("address"),
          valueType = Some("nat"),
          forkId = Fork.mainForkId
        )
        val initialBigMapContent = BigMapContentsRow(
          bigMapId = BigDecimal(1),
          key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
          keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
          value = Some("Pair 20 {}"),
          forkId = Fork.mainForkId
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
            ) :: Nil
        }

        val block = generateSingleBlock(1, testReferenceDateTime)
        val sampleOperations = generateOperationGroup(block, generateOperations = true)
        val operationsWithDiffs: List[OperationsGroup] = sampleOperations.copy(
            contents = updateMap(sampleOperations.contents)
          ) :: Nil
        val opGroupHash = sampleOperations.hash

        val blockToSave = block.copy(operationGroups = operationsWithDiffs)

        //when
        val writeAndGetRows = sut.upsertContent(blockToSave :: Nil) andThen Tables.BigMapContents.result

        val contents = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
        contents.size shouldBe 1

        contents(0) shouldMatchTo (
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 50 {}"),
            valueMicheline = Some("""{"prim":"Pair", "args": [{"int":"50"},[]]}"""),
            operationGroupId = Some(opGroupHash.value),
            blockLevel = Some(block.data.header.level),
            timestamp = Some(Timestamp.from(block.data.header.timestamp.toInstant)),
            cycle = TezosOptics.Blocks.extractCycle(block),
            period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
            forkId = Fork.mainForkId
          )
        )
      }

      "update with only the latest big map content for diffs having the same target map and key: Issue#807" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val initialBigMap = BigMapsRow(
          bigMapId = BigDecimal(1),
          keyType = Some("address"),
          valueType = Some("nat"),
          forkId = Fork.mainForkId
        )
        val initialBigMapContent = BigMapContentsRow(
          bigMapId = BigDecimal(1),
          key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
          keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
          value = Some("Pair 20 {}"),
          forkId = Fork.mainForkId
        )

        val populate = for {
          mapAdded <- Tables.BigMaps += initialBigMap
          contentAdded <- Tables.BigMapContents += initialBigMapContent
        } yield (mapAdded, contentAdded)

        dbHandler.run(populate).futureValue shouldEqual ((1, 1))

        //here we want to generate multiple updates in different blocks
        def updatesMap(values: List[Int]): ListTransf[Operation] = Operations.updateOperationsWithBigMapUpdate {
          case (_: Transaction) =>
            values.map { value =>
              Contract.BigMapUpdate(
                action = "udpate",
                big_map = Decimal(1),
                key = Micheline("""{"bytes":"0000b2e19a9e74440d86c59f13dab8a18ff873e889ea"}"""),
                key_hash = ScriptId("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
                value = Some(Micheline(s"""{"prim":"Pair", "args": [{"int":"$value"},[]]}"""))
              )
            }
        }

        /* we make two blocks and define which are the big map values to update
         * therefore adding the diff to the corresponding operations
         */
        val blocks = generateBlocks(2, testReferenceDateTime).drop(1)

        val (operationsWithDiffs: List[List[OperationsGroup]], opGroupHashes: List[OperationHash]) = blocks.map {
          block =>
            val sampleOperations = generateOperationGroup(block, generateOperations = true)
            val opGroupHash = sampleOperations.hash
            val updateValue = block.data.header.level.toInt match {
              case 1 => List(10) //block-lvl 1 will carry one update
              case 2 => List(20, 50) //block-lvl 2 will carry both updates
              case _ => List.empty
            }
            (sampleOperations.copy(
              contents = updatesMap(values = updateValue)(sampleOperations.contents)
            ) :: Nil) -> opGroupHash
        }.unzip

        val blocksToSave =
          blocks.zip(operationsWithDiffs).map {
            case (block, ops) => block.copy(operationGroups = ops)
          }

        //we change the order of how blocks come in
        val reverted = blocksToSave.reverse

        //when
        val writeAndGetRows = sut.upsertContent(reverted) andThen Tables.BigMapContents.result

        val contents = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
        contents.size shouldBe 1

        // we expect only the latest update to take effect
        contents(0) shouldMatchTo (
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 50 {}"),
            valueMicheline = Some("""{"prim":"Pair", "args": [{"int":"50"},[]]}"""),
            operationGroupId = Some(opGroupHashes(0).value),
            blockLevel = Some(reverted.head.data.header.level),
            timestamp = Some(Timestamp.from(reverted.head.data.header.timestamp.toInstant)),
            cycle = TezosOptics.Blocks.extractCycle(reverted.head),
            period = TezosOptics.Blocks.extractPeriod(reverted.head.data.metadata),
            forkId = Fork.mainForkId
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
                      valueType = None,
                      forkId = Fork.mainForkId
                    )
                )

        //the content to copy
        val initialBigMapContent =
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}"),
            forkId = Fork.mainForkId
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
            ) :: Nil
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

        contents(1) shouldMatchTo (
          BigMapContentsRow(
            bigMapId = BigDecimal(2),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}"),
            forkId = Fork.mainForkId
          )
        )

      }

      "copy only the latest big map content for diffs copying to the same target map and key: Issue#807" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        //we need 3 pre-existing big maps to transafer content between
        val initialBigMaps =
          1 :: 2 :: 3 :: Nil map (
                  i =>
                    BigMapsRow(
                      bigMapId = BigDecimal(i),
                      keyType = None,
                      valueType = None,
                      forkId = Fork.mainForkId
                    )
                )

        //the content to copy
        val initialBigMapsContent = List(
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 10 {}"),
            forkId = Fork.mainForkId
          ),
          BigMapContentsRow(
            bigMapId = BigDecimal(2),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}"),
            forkId = Fork.mainForkId
          )
        )

        //store the data
        val populate = for {
          maps <- Tables.BigMaps ++= initialBigMaps
          contents <- Tables.BigMapContents ++= initialBigMapsContent
        } yield (maps, contents)

        dbHandler.run(populate.transactionally).futureValue shouldEqual ((Some(3), Some(2)))

        //we want to copy content between the first two maps and the third map, on the same key
        def copyMap(from: List[Int], to: Int): ListTransf[Operation] = Operations.updateOperationsWithBigMapCopy {
          case (_: Transaction) =>
            from.map { sourceId =>
              Contract.BigMapCopy(
                action = "copy",
                source_big_map = Decimal(sourceId),
                destination_big_map = Decimal(to)
              )

            }
        }

        /* we make two blocks and define which are the big map ids to copy
         * therefore adding the copy diff to the corresponding operations
         */
        val blocks = generateBlocks(2, testReferenceDateTime).drop(1)

        val operationsWithDiffs: List[List[OperationsGroup]] = blocks.map { block =>
          val sampleOperations = generateOperationGroup(block, generateOperations = true)
          val sourceIdsToCopy = block.data.header.level.toInt match {
            case 1 => List(1) //block-lvl 1 will copy contents from map 1
            case 2 => List(1, 2) //block-lvl 2 will copy contents from map 1 and 2 both
            case _ => List.empty
          }
          sampleOperations.copy(
            contents = copyMap(from = sourceIdsToCopy, to = 3)(sampleOperations.contents)
          ) :: Nil
        }

        val blocksToSave =
          blocks.zip(operationsWithDiffs).map {
            case (block, ops) => block.copy(operationGroups = ops)
          }

        //we change the order of how blocks come in
        val reverted = blocksToSave.reverse

        //when
        val writeAndGetRows = for {
          _ <- sut.copyContent(reverted)
          maps <- Tables.BigMaps.result
          contents <- Tables.BigMapContents.result
        } yield (maps, contents)

        val (maps, contents) = dbHandler.run(writeAndGetRows).futureValue

        //then
        maps.size shouldBe initialBigMaps.size

        contents.size shouldBe 3

        // we expect the destination map to only consider the very last copy applied
        contents(2) shouldMatchTo (
          BigMapContentsRow(
            bigMapId = BigDecimal(3),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}"),
            forkId = Fork.mainForkId
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
                      valueType = None,
                      forkId = Fork.mainForkId
                    )
                )

        //the content to copy
        val initialBigMapContent =
          BigMapContentsRow(
            bigMapId = BigDecimal(1),
            key = "0x0000b2e19a9e74440d86c59f13dab8a18ff873e889ea",
            keyHash = Some("exprv6UsC1sN3Fk2XfgcJCL8NCerP5rCGy1PRESZAqr7L2JdzX55EN"),
            value = Some("Pair 20 {}"),
            forkId = Fork.mainForkId
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
            ) :: Nil
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
