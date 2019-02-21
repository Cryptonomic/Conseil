package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, BlocksRow}
import tech.cryptonomic.conseil.generic.chain.DataTypes._
import tech.cryptonomic.conseil.util.RandomSeed

import scala.util.Random

class TezosDatabaseOperationsTest
  extends WordSpec
    with MockFactory
    with TezosDataGeneration
    with InMemoryDatabase
    with Matchers
    with ScalaFutures
    with OptionValues
    with LazyLogging
    with IntegrationPatience {

  "The database api" should {

    //needed for most tezos-db operations
    import scala.concurrent.ExecutionContext.Implicits.global

    val sut = TezosDatabaseOperations
    val feesToConsider = 1000

    "write fees" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val expectedCount = 5
      val generatedFees = generateFees(expectedCount, testReferenceTimestamp)

      val writeAndGetRows = for {
        written <- sut.writeFees(generatedFees)
        rows <- Tables.Fees.result
      } yield (written, rows)

      val (stored, dbFees) = dbHandler.run(writeAndGetRows.transactionally).futureValue

      stored.value shouldEqual expectedCount

      dbFees should have size expectedCount

      import org.scalatest.Inspectors._

      forAll(dbFees zip generatedFees) { case (row, fee) =>
        row.low shouldEqual fee.low
        row.medium shouldEqual fee.medium
        row.high shouldEqual fee.high
        row.timestamp shouldEqual fee.timestamp
        row.kind shouldEqual fee.kind
      }
    }

    "write blocks" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val basicBlocks = generateBlocks(5, testReferenceDateTime)
      val generatedBlocks = basicBlocks.zipWithIndex map {
        case (block, idx) =>
          //need to use different seeds to generate unique hashes for groups
          val group = generateOperationGroup(block, generateOperations = true)(randomSeed + idx)
          block.copy(operationGroups = List(group))
      }

      whenReady(dbHandler.run(sut.writeBlocks(generatedBlocks))) {
        _ =>
          //read and check what's on db
          val dbBlocks = dbHandler.run(Tables.Blocks.result).futureValue

          dbBlocks should have size (generatedBlocks.size)

          import org.scalatest.Inspectors._

          forAll(dbBlocks zip generatedBlocks) {
            case (row, block) =>
              row.level shouldEqual block.data.header.level
              row.proto shouldEqual block.data.header.proto
              row.predecessor shouldEqual block.data.header.predecessor.value
              row.timestamp shouldEqual Timestamp.from(block.data.header.timestamp.toInstant)
              row.validationPass shouldEqual block.data.header.validation_pass
              row.fitness shouldEqual block.data.header.fitness.mkString(",")
              row.context.value shouldEqual block.data.header.context
              row.signature shouldEqual block.data.header.signature
              row.protocol shouldEqual block.data.protocol
              row.chainId shouldEqual block.data.chain_id
              row.hash shouldEqual block.data.hash.value
              row.operationsHash shouldEqual block.data.header.operations_hash
          }

          val dbBlocksAndGroups =
            dbHandler.run {
              val query = for {
                g <- Tables.OperationGroups
                b <- g.blocksFk
              } yield (b, g)
              query.result
            }.futureValue

          dbBlocksAndGroups should have size (generatedBlocks.size)

          forAll(dbBlocksAndGroups) {
            case (blockRow, groupRow) =>
              val blockForGroup = generatedBlocks.find(_.data.hash.value == blockRow.hash).value
              val group = blockForGroup.operationGroups.head
              groupRow.hash shouldEqual group.hash.value
              groupRow.blockId shouldEqual blockForGroup.data.hash.value
              groupRow.chainId shouldEqual group.chain_id.map(_.id)
              groupRow.branch shouldEqual group.branch.value
              groupRow.signature shouldEqual group.signature.map(_.value)
          }

          /* we read operations as mappings to a case class for ease of comparison vs.
           * having to check un-tagged field values from a HList
           */
          val dbOperations =
           dbHandler.run {
             val query = for {
              o <- Tables.Operations
              g <- o.operationGroupsFk
            } yield (g, o.mapTo[DBTableMapping.Operation])
            query.result
           }.futureValue

          val generatedGroups = generatedBlocks.map(_.operationGroups.head)

          dbOperations should have size (generatedGroups.map(_.contents.size).sum)

          forAll(dbOperations) {
            case (groupRow, opRow) =>
            val operationBlock = generatedBlocks.find(_.operationGroups.head.hash.value == groupRow.hash).value
            val operationGroup = generatedGroups.find(_.hash.value == groupRow.hash).value
            //figure out common fields
            opRow.operationId should be > -1
            opRow.operationGroupHash shouldEqual operationGroup.hash.value
            opRow.blockHash shouldEqual operationBlock.data.hash.value
            opRow.timestamp shouldEqual Timestamp.from(operationBlock.data.header.timestamp.toInstant)
            //figure out the correct sub-type
            val operationMatch = opRow.kind match {
              case "endorsement" =>
                operationGroup.contents.find(_.isInstanceOf[Endorsement])
              case "seed_nonce_revelation" =>
                operationGroup.contents.find(_.isInstanceOf[SeedNonceRevelation])
              case "activate_account" =>
                operationGroup.contents.find(_.isInstanceOf[ActivateAccount])
              case "reveal" =>
                operationGroup.contents.find(_.isInstanceOf[Reveal])
              case "transaction" =>
                operationGroup.contents.find(_.isInstanceOf[Transaction])
              case "origination" =>
                operationGroup.contents.find(_.isInstanceOf[Origination])
              case "delegation" =>
                operationGroup.contents.find(_.isInstanceOf[Delegation])
              case "double_endorsement_evidence" =>
                operationGroup.contents.find(_ == DoubleEndorsementEvidence)
              case "double_baking_evidence" =>
                operationGroup.contents.find(_ == DoubleBakingEvidence)
              case "proposals" =>
                operationGroup.contents.find(_ == Proposals)
              case "ballot" =>
                operationGroup.contents.find(_ == Ballot)
              case _ => None
            }

            operationMatch shouldBe 'defined

            val operation = operationMatch.value

            /* Convert both the generated and loaded operations to a tables row representation
             * Comparing those for correctness makes sense as long as we guarantee with testing elsewhere
             * that the conversion itself is correct
             */
            import DatabaseConversions._
            import tech.cryptonomic.conseil.util.Conversion.Syntax._
            //used as a constraint to read balance updates from operations
            import tech.cryptonomic.conseil.tezos.OperationBalances._
            import tech.cryptonomic.conseil.tezos.SymbolSourceDescriptor.Show._

            val generatedConversion = (operationBlock, operationGroup.hash, operation).convertTo[Tables.OperationsRow]
            val dbConversion = opRow.convertTo[Tables.OperationsRow]
            //skip the id, to take into account that it's only generated on save
            generatedConversion.tail shouldEqual dbConversion.tail

            /* check stored balance updates */
            //convert and set the real stored operation id
            val generatedUpdateRows =
              operation.convertToA[List, Tables.BalanceUpdatesRow]
                .map(_.copy(sourceId = Some(opRow.operationId)))

            //reset the generated id for matching
            val dbUpdateRows = dbHandler.run(
              Tables.BalanceUpdates.filter(_.sourceId === opRow.operationId).result
            ).futureValue
            .map(_.copy(id = 0))

            dbUpdateRows should contain theSameElementsAs generatedUpdateRows

          }
      }

    }

    "write metadata balance updates along with the blocks" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val basicBlocks = generateBlocks(2, testReferenceDateTime)
      val generatedBlocks = basicBlocks.zipWithIndex.map {
        case (block, idx) =>
        block.copy(
          data = block.data.copy(
            metadata = block.data.metadata.copy(
              balance_updates = Option(generateBalanceUpdates(2)(randomSeed + idx))
            )
          )
        )
      }

      whenReady(dbHandler.run(sut.writeBlocks(generatedBlocks))) {
        _ =>
        val dbUpdatesRows = dbHandler.run(Tables.BalanceUpdates.result).futureValue

        dbUpdatesRows should have size 6 //2 updates x 3 blocks

        /* Convert both the generated blocks data to balance updates table row representation
         * Comparing those for correctness makes sense as long as we guarantee with testing elsewhere
         * that the conversion itself is correct
         */
        import DatabaseConversions._
        import tech.cryptonomic.conseil.util.Conversion.Syntax._
        //used as a constraint to read balance updates from block data
        import tech.cryptonomic.conseil.tezos.BlockBalances._
        import tech.cryptonomic.conseil.tezos.SymbolSourceDescriptor.Show._

        val generatedUpdateRows =
          generatedBlocks.flatMap(
            _.data.convertToA[List, Tables.BalanceUpdatesRow]
          )

        //reset the generated id for matching
        dbUpdatesRows.map(_.copy(id = 0)) should contain theSameElementsAs generatedUpdateRows
      }

    }

    "write accounts for a single block" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val expectedCount = 3

      val block = generateBlockRows(1, testReferenceTimestamp).head
      val accountsInfo = generateAccounts(expectedCount, BlockHash(block.hash), block.level)

      val writeAndGetRows = for {
        _ <- Tables.Blocks += block
        written <- sut.writeAccounts(List(accountsInfo))
        rows <- Tables.Accounts.result
      } yield (written, rows)

      val (stored, dbAccounts) = dbHandler.run(writeAndGetRows.transactionally).futureValue

      stored shouldBe expectedCount

      dbAccounts should have size expectedCount

      import org.scalatest.Inspectors._

      forAll(dbAccounts zip accountsInfo.accounts) {
        case (row, (id, account)) =>
          row.accountId shouldEqual id.id
          row.blockId shouldEqual block.hash
          row.manager shouldEqual account.manager
          row.spendable shouldEqual account.spendable
          row.delegateSetable shouldEqual account.delegate.setable
          row.delegateValue shouldEqual account.delegate.value
          row.counter shouldEqual account.counter
          row.script shouldEqual account.script.map(_.toString)
          row.balance shouldEqual account.balance
          row.blockLevel shouldEqual block.level
      }

    }

    "fail to write accounts if the reference block is not stored" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val accountsInfo = generateAccounts(howMany = 1, blockHash = BlockHash("no-block-hash"), blockLevel = 1)

      val resultFuture = dbHandler.run(sut.writeAccounts(List(accountsInfo)))

      whenReady(resultFuture.failed) {
          _ shouldBe a [java.sql.SQLException]
      }
    }

    "update accounts if they exists already" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(2, testReferenceTimestamp)
      val account = generateAccountRows(1, blocks.head).head

      val populate =
        DBIO.seq(
          Tables.Blocks ++= blocks,
          Tables.Accounts += account
        )

      dbHandler.run(populate)

      //prepare new accounts
      val accountChanges = 2
      val (hashUpdate, levelUpdate) = (blocks(1).hash, blocks(1).level)
      val accountsInfo = generateAccounts(accountChanges, BlockHash(hashUpdate), levelUpdate)

      //check for same identifier
      accountsInfo.accounts.keySet.map(_.id) should contain (account.accountId)

      //do the updates
      val writeUpdatedAndGetRows = for {
        written <- sut.writeAccounts(List(accountsInfo))
        rows <- Tables.Accounts.result
      } yield (written, rows)

      val (updates, dbAccounts) = dbHandler.run(writeUpdatedAndGetRows.transactionally).futureValue

      //number of db changes
      updates shouldBe accountChanges

      //total number of rows on db (1 update and 1 insert expected)
      dbAccounts should have size accountChanges

      import org.scalatest.Inspectors._

      //both rows on db should refer to updated data
      forAll(dbAccounts zip accountsInfo.accounts) {
        case (row, (id, account)) =>
          row.accountId shouldEqual id.id
          row.blockId shouldEqual hashUpdate
          row.manager shouldEqual account.manager
          row.spendable shouldEqual account.spendable
          row.delegateSetable shouldEqual account.delegate.setable
          row.delegateValue shouldEqual account.delegate.value
          row.counter shouldEqual account.counter
          row.script shouldEqual account.script.map(_.toString)
          row.balance shouldEqual account.balance
          row.blockLevel shouldEqual levelUpdate
      }

    }

    "store checkpoint account ids with block reference" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)
      //custom hash generator with predictable seed
      val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

      val maxLevel = 1
      val idPerBlock = 3
      val expectedCount = (maxLevel + 1) * idPerBlock

      //generate data
      val blocks = generateBlockRows(toLevel = maxLevel, testReferenceTimestamp)
      val ids = blocks.map(block => (BlockHash(block.hash), block.level, List.fill(idPerBlock)(AccountId(generateHash(5)))))

      //store and write
      val populateAndFetch = for {
        _ <- Tables.Blocks ++= blocks
        written <- sut.writeAccountsCheckpoint(ids)
        rows <- Tables.AccountsCheckpoint.result
      } yield (written, rows)

      val (stored, checkpointRows) = dbHandler.run(populateAndFetch).futureValue

      //number of changes
      stored.value shouldBe expectedCount
      checkpointRows should have size expectedCount

      import org.scalatest.Inspectors._

      val flattenedIdsData = ids.flatMap{ case (hash, level, accounts) => accounts.map((hash, level, _))}

      forAll(checkpointRows.zip(flattenedIdsData)) {
        case (row, (hash, level, accountId)) =>
          row.blockId shouldEqual hash.value
          row.blockLevel shouldBe level
          row.accountId shouldEqual accountId.id
      }

    }

    "clean the checkpoints with no selection" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

      //store required blocks for FK
      dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)

      val accountIds = Array("a0", "a1", "a2", "a3", "a4", "a5", "a6")
      val blockIds = blocks.map(_.hash)

      //create test data:
      val checkpointRows = Array(
        Tables.AccountsCheckpointRow(accountIds(1), blockIds(1), blockLevel = 1),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(1), blockLevel = 1),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(1), blockLevel = 1),
        Tables.AccountsCheckpointRow(accountIds(4), blockIds(2), blockLevel = 2),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(2), blockLevel = 2),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(3), blockLevel = 3),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(4), blockLevel = 4),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(4), blockLevel = 4),
        Tables.AccountsCheckpointRow(accountIds(6), blockIds(5), blockLevel = 5)
        )

        val populateAndTest = for {
          stored <- Tables.AccountsCheckpoint ++= checkpointRows
          cleaned <- sut.cleanAccountsCheckpoint()
          rows <- Tables.AccountsCheckpoint.result
        } yield (stored, cleaned, rows)

        val (initialCount, deletes, survivors) = dbHandler.run(populateAndTest.transactionally).futureValue
        initialCount.value shouldBe checkpointRows.size
        deletes shouldBe checkpointRows.size
        survivors shouldBe empty
      }

      "clean the checkpoints with a partial id selection" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

      //store required blocks for FK
      dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)

      val accountIds = Array("a0", "a1", "a2", "a3", "a4", "a5", "a6")
      val blockIds = blocks.map(_.hash)

      //create test data:
      val checkpointRows = Array(
        Tables.AccountsCheckpointRow(accountIds(1), blockIds(1), blockLevel = 1),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(1), blockLevel = 1),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(1), blockLevel = 1),
        Tables.AccountsCheckpointRow(accountIds(4), blockIds(2), blockLevel = 2),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(2), blockLevel = 2),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(3), blockLevel = 3),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(4), blockLevel = 4),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(4), blockLevel = 4),
        Tables.AccountsCheckpointRow(accountIds(6), blockIds(5), blockLevel = 5)
        )

      val inSelection = Set(accountIds(1), accountIds(2), accountIds(3), accountIds(4))

      val selection = inSelection.map(AccountId)

      val expected = checkpointRows.filterNot(row => inSelection(row.accountId))

      val populateAndTest = for {
        stored <- Tables.AccountsCheckpoint ++= checkpointRows
        cleaned <- sut.cleanAccountsCheckpoint(Some(selection))
        rows <- Tables.AccountsCheckpoint.result
      } yield (stored, cleaned, rows)

      val (initialCount, deletes, survivors) = dbHandler.run(populateAndTest.transactionally).futureValue
      initialCount.value shouldBe checkpointRows.size
      deletes shouldEqual checkpointRows.filter(row => inSelection(row.accountId)).size
      survivors should contain theSameElementsAs expected

    }

    "read latest account ids from checkpoint" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

      //store required blocks for FK
      dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)
      val accountIds = Array("a0", "a1", "a2", "a3", "a4", "a5", "a6")
      val blockIds = blocks.map(_.hash)

      //create test data:
      val checkpointRows = Array(
        Tables.AccountsCheckpointRow(accountIds(1), blockIds(1), blockLevel = 1),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(1), blockLevel = 1),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(1), blockLevel = 1),
        Tables.AccountsCheckpointRow(accountIds(4), blockIds(2), blockLevel = 2),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(2), blockLevel = 2),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(3), blockLevel = 3),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(4), blockLevel = 4),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(4), blockLevel = 4),
        Tables.AccountsCheckpointRow(accountIds(6), blockIds(5), blockLevel = 5)
      )

      def entry(accountAtIndex: Int, atLevel: Int) =
        AccountId(accountIds(accountAtIndex)) -> (BlockHash(blockIds(atLevel)), atLevel)

        //expecting only the following to remain
      val expected =
        Map(
          entry(accountAtIndex = 1, atLevel = 1),
          entry(accountAtIndex = 2, atLevel = 3),
          entry(accountAtIndex = 3, atLevel = 4),
          entry(accountAtIndex = 4, atLevel = 2),
          entry(accountAtIndex = 5, atLevel = 4),
          entry(accountAtIndex = 6, atLevel = 5)
      )

      val populateAndFetch = for {
        stored <- Tables.AccountsCheckpoint ++= checkpointRows
        rows <- sut.getLatestAccountsFromCheckpoint
      } yield (stored, rows)

      val (initialCount, latest) = dbHandler.run(populateAndFetch.transactionally).futureValue
      initialCount.value shouldBe checkpointRows.size

      latest.toSeq should contain theSameElementsAs expected.toSeq

    }

    "fetch nothing if looking up a non-existent operation group by hash" in {
      dbHandler.run(sut.operationsForGroup("no-group-here")).futureValue shouldBe None
    }

    "fetch existing operations with their group on a existing hash" in {
      import DatabaseConversions._
      import tech.cryptonomic.conseil.util.Conversion.Syntax._

      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val block = generateBlockRows(1, testReferenceTimestamp).head
      val group = generateOperationGroupRows(block).head
      val ops = generateOperationsForGroup(block, group)

      val populateAndFetch = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops.map(_.convertTo[Tables.OperationsRow])
        result <- sut.operationsForGroup(group.hash)
      } yield (result, ids)

      val (Some((groupRow, operationRows)), operationIds) = dbHandler.run(populateAndFetch).futureValue

      //we now have only a generic HList repr. so columns are accessed by position
      val operationId: Tables.OperationsRow => Int = _.apply(0)

      groupRow.hash shouldEqual group.hash
      operationRows should have size ops.size
      operationRows.map(operationId).toList should contain theSameElementsAs operationIds

    }

    "compute correct average fees from stored operations" in {
      import DatabaseConversions._
      import tech.cryptonomic.conseil.util.Conversion.Syntax._
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)
      val block = generateBlockRows(1, testReferenceTimestamp).head
      val group = generateOperationGroupRows(block).head

      // mu = 152.59625
      // std-dev = 331.4
      // the sample std-dev should be 354.3, using correction formula
      val fees = Seq(
        Some(BigDecimal(35.23)), Some(BigDecimal(12.01)), Some(BigDecimal(2.22)), Some(BigDecimal(150.01)), None, Some(BigDecimal(1020.30)), Some(BigDecimal(1.00)), None
      )
      val ops = wrapFeesWithOperations(fees, block, group)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops.map(_.convertTo[Tables.OperationsRow])
      } yield ids

      dbHandler.run(populate).futureValue should have size (fees.size)

      //expectations
      val (mu, sigma) = (153, 332)
      val latest = new Timestamp(ops.map(_.timestamp.getTime).max)

      val expected = AverageFees(
        low = 0,
        medium = mu,
        high = mu + sigma,
        timestamp = latest,
        kind = ops.head.kind
      )

      //check
      val feesCalculation = sut.calculateAverageFees(ops.head.kind, feesToConsider)

      dbHandler.run(feesCalculation).futureValue.value shouldEqual expected

    }

    "return None when computing average fees for a kind with no data" in {
      import DatabaseConversions._
      import tech.cryptonomic.conseil.util.Conversion.Syntax._
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)
      val block = generateBlockRows(1, testReferenceTimestamp).head
      val group = generateOperationGroupRows(block).head

      val fees = Seq.fill(3)(Some(BigDecimal(1)))
      val ops = wrapFeesWithOperations(fees, block, group)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops.map(_.convertTo[Tables.OperationsRow])
      } yield ids

      dbHandler.run(populate).futureValue should have size (fees.size)

      //check
      val feesCalculation = sut.calculateAverageFees("undefined", feesToConsider)

      dbHandler.run(feesCalculation).futureValue shouldBe None

    }

    "compute average fees only using the selected operation kinds" in {
      import DatabaseConversions._
      import tech.cryptonomic.conseil.util.Conversion.Syntax._
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)
      val block = generateBlockRows(1, testReferenceTimestamp).head
      val group = generateOperationGroupRows(block).head

      val (selectedFee, ignoredFee) = (Some(BigDecimal(1)), Some(BigDecimal(1000)))

      val fees = Seq(selectedFee, selectedFee, ignoredFee, ignoredFee)

      //change kind for fees we want to ignore
      val ops = wrapFeesWithOperations(fees, block, group).map {
        case op if op.fee == ignoredFee => op.copy(kind = op.kind + "ignore")
        case op => op
      }

      val selection = ops.filter(_.fee == selectedFee)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops.map(_.convertTo[Tables.OperationsRow])
      } yield ids

      dbHandler.run(populate).futureValue should have size (fees.size)

      //expectations
      val mu = 1
      val latest = new Timestamp(selection.map(_.timestamp.getTime).max)

      val expected = AverageFees(
        low = mu,
        medium = mu,
        high = mu,
        timestamp = latest,
        kind = ops.head.kind
      )
      //check
      val feesCalculation = sut.calculateAverageFees(selection.head.kind, feesToConsider)

      dbHandler.run(feesCalculation).futureValue.value shouldEqual expected

    }

    "return the default when fetching the latest block level and there's no block stored" in {
      val expected = -1
      val maxLevel = dbHandler.run(
        sut.fetchMaxBlockLevel
      ).futureValue

      maxLevel should equal(expected)
    }

    "fetch the latest block level when blocks are available" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val expected = 5
      val populateAndFetch = for {
        _ <- Tables.Blocks ++= generateBlockRows(expected, testReferenceTimestamp)
        result <- sut.fetchMaxBlockLevel
      } yield result

      val maxLevel = dbHandler.run(populateAndFetch.transactionally).futureValue

      maxLevel should equal(expected)
    }

    "correctly verify when a block exists" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val blocks = generateBlockRows(1, testReferenceTimestamp)
      val opGroups = generateOperationGroupRows(blocks: _*)
      val testHash = BlockHash(blocks.last.hash)

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocks
        _ <- Tables.OperationGroups ++= opGroups
        existing <- sut.blockExists(testHash)
        nonExisting <- sut.blockExists(BlockHash("bogus-hash"))
      } yield (existing, nonExisting)

      val (hit, miss) = dbHandler.run(populateAndTest.transactionally).futureValue

      hit shouldBe true
      miss shouldBe false

    }

    "say a block doesn't exist if it has no associated operation group" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val blocks = generateBlockRows(1, testReferenceTimestamp)
      val testHash = BlockHash(blocks.last.hash)

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocks
        found <- sut.blockExists(testHash)
      } yield found

      val exists = dbHandler.run(populateAndTest.transactionally).futureValue
      exists shouldBe false

    }

    val blocksTmp = List(
      BlocksRow(0,1,"genesis",new Timestamp(0),0,"fitness",Some("context0"),Some("sigqs6AXPny9K"),"protocol",Some("YLBMy"),"R0NpYZuUeF",None),
      BlocksRow(1,1,"R0NpYZuUeF",new Timestamp(1),0,"fitness",Some("context1"),Some("sigTZ2IB879wD"),"protocol",Some("YLBMy"),"aQeGrbXCmG",None)
    )

    "get all values from the table with nulls as nones" in {

      val columns = List()

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, List.empty, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map(
          "operations_hash" -> None,
          "timestamp" -> Some(new Timestamp(0)),
          "context" -> Some("context0"),
          "proto" -> Some(1),
          "signature" -> Some("sigqs6AXPny9K"),
          "hash" -> Some("R0NpYZuUeF"),
          "fitness" -> Some("fitness"),
          "validation_pass" -> Some(0),
          "protocol" -> Some("protocol"),
          "predecessor" -> Some("genesis"),
          "chain_id" -> Some("YLBMy"),
          "level" -> Some(0)
        ),
        Map(
          "operations_hash" -> None,
          "timestamp" -> Some(new Timestamp(1)),
          "context" -> Some("context1"),
          "proto" -> Some(1),
          "signature" -> Some("sigTZ2IB879wD"),
          "hash" -> Some("aQeGrbXCmG"),
          "fitness" -> Some("fitness"),
          "validation_pass" -> Some(0),
          "protocol" -> Some("protocol"),
          "predecessor" -> Some("R0NpYZuUeF"),
          "chain_id" -> Some("YLBMy"),
          "level" -> Some(1)
        )
      )
    }



    "get values where context is null" in {
      val blocksTmp = List(
        BlocksRow(0,1,"genesis",new Timestamp(0),0,"fitness",None,Some("sigqs6AXPny9K"),"protocol",Some("YLBMy"),"R0NpYZuUeF",None),
        BlocksRow(1,1,"R0NpYZuUeF",new Timestamp(1),0,"fitness",Some("context1"),Some("sigTZ2IB879wD"),"protocol",Some("YLBMy"),"aQeGrbXCmG",None)
      )
      val columns = List("level", "proto", "context", "hash", "operations_hash")
      val predicates = List(
        Predicate(
          field = "context",
          operation = OperationType.isnull,
          set = List.empty,
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "context" -> None, "hash" -> Some("R0NpYZuUeF"), "operations_hash" -> None)
      )
    }

    "get values where context is NOT null" in {
      val blocksTmp = List(
        BlocksRow(0,1,"genesis",new Timestamp(0),0,"fitness",None,Some("sigqs6AXPny9K"),"protocol",Some("YLBMy"),"R0NpYZuUeF",None),
        BlocksRow(1,1,"R0NpYZuUeF",new Timestamp(1),0,"fitness",Some("context1"),Some("sigTZ2IB879wD"),"protocol",Some("YLBMy"),"aQeGrbXCmG",None)
      )
      val columns = List("level", "proto", "context", "hash", "operations_hash")
      val predicates = List(
        Predicate(
          field = "context",
          operation = OperationType.isnull,
          set = List.empty,
          inverse = true
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "context" -> Some("context1"), "hash" -> Some("aQeGrbXCmG"), "operations_hash" -> None)
      )
    }

    "get null values from the table as none" in {

      val columns = List("level", "proto", "protocol", "hash", "operations_hash")

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, List.empty, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"), "operations_hash" -> None),
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"), "operations_hash" -> None)
      )
    }

    "get map from a block table" in {

      val columns = List("level", "proto", "protocol", "hash")

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, List.empty, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get map from a block table with predicate" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.in,
          set = List("R0NpYZuUeF"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
      )
    }

    "get map from a block table with inverse predicate" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.in,
          set = List("R0NpYZuUeF"),
          inverse = true
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get map from a block table with multiple predicates" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.in,
          set = List("R0NpYZuUeF"),
          inverse = true
        ),
        Predicate(
          field = "hash",
          operation = OperationType.in,
          set = List("aQeGrbXCmG"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get empty map from empty table" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List.empty

      val populateAndTest = for {
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe 'empty
    }

    "get map from a block table with eq predicate" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.eq,
          set = List("aQeGrbXCmG"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get map from a block table with eq predicate on numeric type" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "level",
          operation = OperationType.eq,
          set = List(1),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }


    "get map from a block table with like predicate when starts with pattern" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.like,
          set = List("aQeGr"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get map from a block table with like predicate when ends with pattern" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.like,
          set = List("rbXCmG"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get map from a block table with like predicate when pattern is in the middle" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.like,
          set = List("rbX"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get map from a block table with less than predicate when one element fulfils it" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "level",
          operation = OperationType.lt,
          set = List(1),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
      )
    }

    "get empty map from a block table with less than predicate when no elements fulfil it" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "level",
          operation = OperationType.lt,
          set = List(0),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe 'empty
    }

    "get map from a block table with between predicate when two element fulfill it" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "level",
          operation = OperationType.between,
          set = List(-10,10),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get map from a block table with between predicate when two element fulfill it but limited to 1 element" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "level",
          operation = OperationType.between,
          set = List(-10,10),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 1)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
      )
    }

    "get map from a block table with between predicate when one element fulfill it" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "level",
          operation = OperationType.between,
          set = List(1,10),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }
    "get map from a block table with datetime field" in {
      val columns = List("level", "proto", "protocol", "hash", "timestamp")
      val predicates = List(
        Predicate(
          field = "timestamp",
          operation = OperationType.gt,
          set = List(new Timestamp(0)),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"), "timestamp" -> Some(new Timestamp(1)))
      )
    }
    "get map from a block table with startsWith predicate" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.startsWith,
          set = List("R0Np"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
      )
    }
    "get empty map from a block table with startsWith predicate" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.startsWith,
          set = List("YZuUeF"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe 'empty
    }
    "get map from a block table with endsWith predicate" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.endsWith,
          set = List("ZuUeF"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
      )
    }
    "get empty map from a block table with endsWith predicate" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List(
        Predicate(
          field = "hash",
          operation = OperationType.endsWith,
          set = List("R0NpYZ"),
          inverse = false
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe 'empty
    }

    val accRow = AccountsRow(
      accountId = 1.toString,
      blockId = "R0NpYZuUeF",
      blockLevel = 0,
      manager = "manager",
      spendable = true,
      delegateSetable = false,
      delegateValue = None,
      counter = 0,
      script = None,
      balance = BigDecimal(1.45)
    )
    "get one element when correctly rounded value" in {
      val columns = List("account_id", "balance")
      val predicates = List(
        Predicate(
          field = "balance",
          operation = OperationType.eq,
          set = List(1.5),
          inverse = false,
          precision = Some(1)
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        _ <- Tables.Accounts += accRow
        found <- sut.selectWithPredicates(Tables.Accounts.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue.map(_.mapValues(_.toString))
      result shouldBe List(Map("account_id" -> "Some(1)", "balance" -> "Some(1.45)"))
    }
    "get empty list of elements when correctly rounded value does not match" in {
      val columns = List("account_id", "balance")
      val predicates = List(
        Predicate(
          field = "balance",
          operation = OperationType.eq,
          set = List(1.5),
          inverse = false,
          precision = Some(2)
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        _ <- Tables.Accounts += accRow
        found <- sut.selectWithPredicates(Tables.Accounts.baseTableRow.tableName, columns, predicates, List.empty, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe 'empty
    }

    "return the same results for the same query" in {
      import tech.cryptonomic.conseil.util.DatabaseUtil.QueryBuilder._
      val columns = List("level", "proto", "protocol", "hash")
      val tableName = Tables.Blocks.baseTableRow.tableName
      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        generatedQuery <- makeQuery(tableName, columns).as[AnyMap]
      } yield generatedQuery

      val generatedQueryResult = dbHandler.run(populateAndTest.transactionally).futureValue
      val expectedQueryResult = dbHandler.run(
        sql"""SELECT #${columns.head}, #${columns(1)}, #${columns(2)}, #${columns(3)} FROM #$tableName WHERE true""".as[AnyMap]
      ).futureValue
      generatedQueryResult shouldBe expectedQueryResult
    }


    "get map from a block table and sort by level in ascending order" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List()
      val sortBy = List(
        QueryOrdering(
          field = "level",
          direction = OrderDirection.asc
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get map from a block table and sort by level in descending order" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List()
      val sortBy = List(
        QueryOrdering(
          field = "level",
          direction = OrderDirection.desc
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
      )
    }

    "get map from a block table and sort by hash in descending order" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List()
      val sortBy = List(
        QueryOrdering(
          field = "hash",
          direction = OrderDirection.desc
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG")),
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF"))
      )
    }

    "get map from a block table and sort by hash in ascending order" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List()
      val sortBy = List(
        QueryOrdering(
          field = "hash",
          direction = OrderDirection.asc
        )
      )

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(0), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("R0NpYZuUeF")),
        Map("level" -> Some(1), "proto" -> Some(1), "protocol" -> Some("protocol"), "hash" -> Some("aQeGrbXCmG"))
      )
    }

    "get map from a block table and sort by proto in descending order and by level in ascending order" in {
      val columns = List("level", "proto")
      val predicates = List()
      val sortBy = List(
        QueryOrdering(
          field = "proto",
          direction = OrderDirection.desc
        ),
        QueryOrdering(
          field = "level",
          direction = OrderDirection.asc
        )
      )

      val blocksTmp2 = blocksTmp.head.copy(level = 2, proto = 2, hash = "aQeGrbXCmF") :: blocksTmp

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp2
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy, 3)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> Some(2), "proto" -> Some(2)),
        Map("level" -> Some(0), "proto" -> Some(1)),
        Map("level" -> Some(1), "proto" -> Some(1))
      )
    }
  }

 }
