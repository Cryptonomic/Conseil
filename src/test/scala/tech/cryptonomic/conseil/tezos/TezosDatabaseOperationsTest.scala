package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp
import java.time.{LocalDate, ZoneOffset}

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.IntegrationPatience
import org.scalatest.concurrent.ScalaFutures
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.Tables.{AccountsRow, BlocksRow, OperationGroupsRow, OperationsRow}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.generic.chain.DataTypes.{OperationType, OrderDirection, Predicate, QueryOrdering}

import scala.util.Random

/* use this to make random generation implicit but deterministic */
case class RandomSeed(seed: Long) extends AnyVal with Product with Serializable {
  def +(extra: Long): RandomSeed = RandomSeed(seed + extra)
}

class TezosDatabaseOperationsTest
  extends WordSpec
    with MockFactory
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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val expectedCount = 5
      val generatedFees = generateFees(expectedCount, testReferenceTime)

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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val basicBlocks = generateBlocks(5, testReferenceTime)
      val generatedBlocks = basicBlocks.zipWithIndex map {
        case (block, idx) =>
          //need to use different seeds to generate unique hashes for groups
          val group = generateOperationGroup(block, operations = 1)(randomSeed + idx)
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
              row.level shouldEqual block.metadata.header.level
              row.proto shouldEqual block.metadata.header.proto
              row.predecessor shouldEqual block.metadata.header.predecessor.value
              row.timestamp shouldEqual block.metadata.header.timestamp
              row.validationPass shouldEqual block.metadata.header.validationPass
              row.fitness shouldEqual block.metadata.header.fitness.mkString(",")
              row.context.value shouldEqual block.metadata.header.context
              row.signature shouldEqual block.metadata.header.signature
              row.protocol shouldEqual block.metadata.protocol
              row.chainId shouldEqual block.metadata.chain_id
              row.hash shouldEqual block.metadata.hash.value
              row.operationsHash shouldEqual block.metadata.header.operations_hash
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
              val blockForGroup = generatedBlocks.find(_.metadata.hash.value == blockRow.hash).value
              val group = blockForGroup.operationGroups.head
              groupRow.hash shouldEqual group.hash.value
              groupRow.blockId shouldEqual blockForGroup.metadata.hash.value
              groupRow.chainId shouldEqual group.chain_id
              groupRow.branch shouldEqual group.branch
              groupRow.signature shouldEqual group.signature
          }

          val dbOperations =
           dbHandler.run {
             val query = for {
              o <- Tables.Operations
              g <- o.operationGroupsFk
            } yield (g, o)
            query.result
           }.futureValue

          val generatedGroups = generatedBlocks.map(_.operationGroups.head)

          dbOperations should have size (generatedGroups.map(_.contents.map(_.size).getOrElse(0)).sum)

          forAll(dbOperations) {
            case (groupRow, opRow) =>
              val operationBlock = generatedBlocks.find(_.operationGroups.head.hash.value == groupRow.hash).value
              val operationGroup = generatedGroups.find(_.hash.value == groupRow.hash).value
              val operation = operationGroup.contents.value.head
              opRow.kind shouldEqual operation.kind
              opRow.source shouldEqual operation.source
              opRow.fee shouldEqual operation.fee
              opRow.storageLimit shouldEqual operation.storageLimit
              opRow.gasLimit shouldEqual operation.gasLimit
              opRow.amount shouldEqual operation.amount
              opRow.destination shouldEqual operation.destination
              opRow.pkh shouldEqual operation.pkh
              opRow.delegate shouldEqual operation.delegate
              opRow.balance shouldEqual operation.balance
              opRow.operationGroupHash shouldEqual operationGroup.hash.value
              opRow.blockHash shouldEqual operationBlock.metadata.hash.value
              opRow.blockLevel shouldEqual operationBlock.metadata.header.level
              opRow.timestamp shouldEqual operationBlock.metadata.header.timestamp
          }
      }


    }

    "write accounts for a single block" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val expectedCount = 3

      val block = generateBlockRows(1, testReferenceTime).head
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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val accountsInfo = generateAccounts(howMany = 1, blockHash = BlockHash("no-block-hash"), blockLevel = 1)

      val resultFuture = dbHandler.run(sut.writeAccounts(List(accountsInfo)))

      whenReady(resultFuture.failed) {
          _ shouldBe a [java.sql.SQLException]
      }
    }

    "update accounts if they exists already" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      //generate data
      val blocks = generateBlockRows(2, testReferenceTime)
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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)
      //custom hash generator with predictable seed
      val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

      val maxLevel = 1
      val idPerBlock = 3
      val expectedCount = (maxLevel + 1) * idPerBlock

      //generate data
      val blocks = generateBlockRows(toLevel = maxLevel, testReferenceTime)
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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTime)

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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTime)

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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTime)

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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val block = generateBlockRows(1, testReferenceTime).head
      val group = generateOperationGroupRows(block).head
      val ops = generateOperationRowsForGroup(block, group)

      val populateAndFetch = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
        result <- sut.operationsForGroup(group.hash)
      } yield (result, ids)

      val (Some((groupRow, operationRows)), operationIds) = dbHandler.run(populateAndFetch).futureValue

      groupRow.hash shouldEqual group.hash
      operationRows should have size ops.size
      operationRows.map(_.operationId).toList should contain theSameElementsAs operationIds

    }

    "compute correct average fees from stored operations" in {
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)
      val block = generateBlockRows(1, testReferenceTime).head
      val group = generateOperationGroupRows(block).head

      // mu = 152.59625
      // std-dev = 331.4
      // the sample std-dev should be 354.3, using correction formula
      val fees = Seq(
        Some("35.23"), Some("12.01"), Some("2.22"), Some("150.01"), None, Some("1020.30"), Some("1.00"), None
      )
      val ops = wrapFeesWithOperationRows(fees, block, group)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
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

    "handle invalid fee data using the default value" in {
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)
      val block = generateBlockRows(1, testReferenceTime).head
      val group = generateOperationGroupRows(block).head

      // mu = 152.59625
      // std-dev = 331.4
      // the sample std-dev should be 354.3, using correction formula
      val fees = Seq(
        Some("35.23"), Some("12.01"), Some("2.22"), Some("150.01"), Some("1-00"), Some("1020.30"), Some("1.00"), Some("inv4lid")
      )
      val ops = wrapFeesWithOperationRows(fees, block, group)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
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
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)
      val block = generateBlockRows(1, testReferenceTime).head
      val group = generateOperationGroupRows(block).head

      val fees = Seq.fill(3)(Some("1.0"))
      val ops = wrapFeesWithOperationRows(fees, block, group)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
      } yield ids

      dbHandler.run(populate).futureValue should have size (fees.size)

      //check
      val feesCalculation = sut.calculateAverageFees("undefined", feesToConsider)

      dbHandler.run(feesCalculation).futureValue shouldBe None

    }

    "compute average fees only using the selected operation kinds" in {
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)
      val block = generateBlockRows(1, testReferenceTime).head
      val group = generateOperationGroupRows(block).head

      val (selectedFee, ignoredFee) = (Some("1.0"), Some("1000.0"))

      val fees = Seq(selectedFee, selectedFee, ignoredFee, ignoredFee)

      //change kind for fees we want to ignore
      val ops = wrapFeesWithOperationRows(fees, block, group).map {
        case op if op.fee == ignoredFee => op.copy(kind = op.kind + "ignore")
        case op => op
      }

      val selection = ops.filter(_.fee == selectedFee)

      val populate = for {
        _ <- Tables.Blocks += block
        _ <- Tables.OperationGroups += group
        ids <- Tables.Operations returning Tables.Operations.map(_.operationId) ++= ops
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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val expected = 5
      val populateAndFetch = for {
        _ <- Tables.Blocks ++= generateBlockRows(expected, testReferenceTime)
        result <- sut.fetchMaxBlockLevel
      } yield result

      val maxLevel = dbHandler.run(populateAndFetch.transactionally).futureValue

      maxLevel should equal(expected)
    }

    "correctly verify when a block exists" in {
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val blocks = generateBlockRows(1, testReferenceTime)
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
      implicit val randomSeed = RandomSeed(testReferenceTime.getTime)

      val blocks = generateBlockRows(1, testReferenceTime)
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

    "get map from a block table" in {

      val columns = List("level", "proto", "protocol", "hash")

      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, List.empty, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF"),
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
      )
    }

    "get empty map from empty table" in {
      val columns = List("level", "proto", "protocol", "hash")
      val predicates = List.empty

      val populateAndTest = for {
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF"),
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG", "timestamp" -> new Timestamp(1))
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, List.empty)
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
        found <- sut.selectWithPredicates(Tables.Accounts.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue.map(_.mapValues(_.toString))
      result shouldBe List(Map("account_id" -> "1", "balance" -> "1.45"))
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
        found <- sut.selectWithPredicates(Tables.Accounts.baseTableRow.tableName, columns, predicates, List.empty)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe 'empty
    }

    "return the same results for the same query" in {
      import tech.cryptonomic.conseil.util.DatabaseUtil._
      val columns = List("level", "proto", "protocol", "hash")
      val tableName = Tables.Blocks.baseTableRow.tableName
      val populateAndTest = for {
        _ <- Tables.Blocks ++= blocksTmp
        generatedQuery <- sut.makeQuery(tableName, columns).as[Map[String, Any]]
      } yield generatedQuery

      val generatedQueryResult = dbHandler.run(populateAndTest.transactionally).futureValue
      val expectedQueryResult = dbHandler.run(
        sql"""SELECT #${columns.head}, #${columns(1)}, #${columns(2)}, #${columns(3)} FROM #$tableName WHERE true""".as[Map[String, Any]]
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF"),
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG"),
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG"),
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF")
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 0, "proto" -> 1, "protocol" -> "protocol", "hash" -> "R0NpYZuUeF"),
        Map("level" -> 1, "proto" -> 1, "protocol" -> "protocol", "hash" -> "aQeGrbXCmG")
      )
    }
///////////////
    "get map from a block table and sort by hash in ascending order and by hash in ascending order" in {
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
        found <- sut.selectWithPredicates(Tables.Blocks.baseTableRow.tableName, columns, predicates, sortBy)
      } yield found

      val result = dbHandler.run(populateAndTest.transactionally).futureValue
      result shouldBe List(
        Map("level" -> 2, "proto" -> 2),
        Map("level" -> 0, "proto" -> 1),
        Map("level" -> 1, "proto" -> 1)
      )
    }
  }

  //a stable timestamp reference if needed
  private lazy val testReferenceTime =
    new Timestamp(
      LocalDate.of(2018, 1, 1)
        .atStartOfDay
        .toEpochSecond(ZoneOffset.UTC)
    )

  //creates pseudo-random strings of given length, based on an existing [[Random]] generator
  private val alphaNumericGenerator =
    (random: Random) => random.alphanumeric.take(_: Int).mkString

  /* randomly populate a number of fees */
  private def generateFees(howMany: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[AverageFees] = {
    require(howMany > 0, "the test can generates a positive number of fees, you asked for a non positive value")

    val rnd = new Random(randomSeed.seed)

    (1 to howMany).map {
      current =>
        val low = rnd.nextInt(10)
        val medium = rnd.nextInt(10) + 10
        val high = rnd.nextInt(10) + 20
        AverageFees(
          low = low,
          medium = medium,
          high = high,
          timestamp = new Timestamp(startAt.getTime + current),
          kind = "kind"
        )
    }.toList
  }

  /* randomly generates a number of accounts with associated block data */
  private def generateAccounts(howMany: Int, blockHash: BlockHash, blockLevel: Int)(implicit randomSeed: RandomSeed): BlockAccounts = {
    require(howMany > 0, "the test can generates a positive number of accounts, you asked for a non positive value")

    val rnd = new Random(randomSeed.seed)

    val accounts = (1 to howMany).map {
      currentId =>
        (AccountId(String valueOf currentId),
          Account(
            manager = "manager",
            balance = rnd.nextInt,
            spendable = true,
            delegate = AccountDelegate(setable = false, value = Some("delegate-value")),
            script = Some("script"),
            counter = currentId
          )
        )
    }.toMap

    BlockAccounts(blockHash, blockLevel, accounts)
  }

  /* randomly populate a number of blocks based on a level range */
  private def generateBlocks(toLevel: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[Block] = {
    require(toLevel > 0, "the test can generate blocks up to a positive chain level, you asked for a non positive value")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    //same for all blocks
    val chainHash = generateHash(5)

    val startMillis = startAt.getTime

    def generateOne(level: Int, predecessorHash: BlockHash): Block =
      Block(
        BlockMetadata(
          "protocol",
          Some(chainHash),
          BlockHash(generateHash(10)),
          BlockHeader(
            level = level,
            proto = 1,
            predecessor = predecessorHash,
            timestamp = new Timestamp(startMillis + level),
            validationPass = 0,
            operations_hash = None,
            fitness = Seq.empty,
            context = s"context$level",
            signature = Some(s"sig${generateHash(10)}")
          )),
        operationGroups = List.empty
      )

    //we need a block to start
    val genesis = generateOne(0, BlockHash("genesis"))

    //use a fold to pass the predecessor hash, to keep a plausibility of sort
    (1 to toLevel).foldLeft(List(genesis)) {
      case (chain, lvl) =>
        val currentBlock = generateOne(lvl, chain.head.metadata.hash)
        currentBlock :: chain
    }.reverse

  }


  /* randomly populate a number of blocks based on a level range */
  private def generateBlockRows(toLevel: Int, startAt: Timestamp)(implicit randomSeed: RandomSeed): List[Tables.BlocksRow] = {
    require(toLevel > 0, "the test can generate blocks up to a positive chain level, you asked for a non positive value")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    //same for all blocks
    val chainHash = generateHash(5)

    val startMillis = startAt.getTime

    def generateOne(level: Int, predecessorHash: String): BlocksRow =
      BlocksRow(
      level = level,
      proto = 1,
      predecessor = predecessorHash,
      timestamp = new Timestamp(startMillis + level),
      validationPass = 0,
      fitness = "fitness",
      protocol = "protocol",
      context = Some(s"context$level"),
      signature = Some(s"sig${generateHash(10)}"),
      chainId = Some(chainHash),
      hash = generateHash(10)
    )

    //we need somewhere to start with
    val genesis = generateOne(0, "genesis")

    //use a fold to pass the predecessor hash, to keep a plausibility of sort
    (1 to toLevel).foldLeft(List(genesis)) {
      case (chain, lvl) =>
        val currentBlock = generateOne(lvl, chain.head.hash)
        currentBlock :: chain
    }.reverse

  }

  /* create an operation group for each block passed in, using random values, with the requested copies of operations */
  private def generateOperationGroup(block: Block, operations: Int)(implicit randomSeed: RandomSeed): OperationGroup = {
    require(operations >= 0, "the test won't generate a negative number of operations")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    def fillOperations(): Option[List[Operation]] = {
      val ops = List.fill(operations) {
        Operation(
          kind = "kind",
          block = Some(block.metadata.hash.value),
          level = Some(block.metadata.header.level),
          slots = Some(List(0)),
          nonce = Some(generateHash(10)),
          op1 = None,
          op2 = None,
          bh1 = None,
          bh2 = None,
          pkh = Some("pkh"),
          secret = Some("secret"),
          proposals = Some(List("proposal")),
          period = Some("period"),
          source = Some("source"),
          proposal = Some("proposal"),
          ballot = Some("ballot"),
          fee = Some("fee"),
          counter = Some(0),
          gasLimit = Some("gasLimit"),
          storageLimit = Some("storageLimit"),
          publicKey = Some("publicKey"),
          amount = Some("amount"),
          destination = Some("destination"),
          parameters = Some("parameters"),
          managerPubKey = Some("managerPubKey"),
          balance = Some("balance"),
          spendable = Some(true),
          delegatable = Some(true),
          delegate = Some("delegate")
        )
      }
      if (ops.isEmpty) None else Some(ops)
    }


    OperationGroup(
      protocol = "protocol",
      chain_id = block.metadata.chain_id,
      hash = OperationHash(generateHash(10)),
      branch = generateHash(10),
      signature = Some(s"sig${generateHash(10)}"),
      contents = fillOperations()
    )
  }


  /* create an empty operation group for each block passed in, using random values */
  private def generateOperationGroupRows(blocks: BlocksRow*)(implicit randomSeed: RandomSeed): List[Tables.OperationGroupsRow] = {
    require(blocks.nonEmpty, "the test won't generate any operation group without a block to start with")

    //custom hash generator with predictable seed
    val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

    blocks.map(
      block =>
        Tables.OperationGroupsRow(
          protocol = "protocol",
          chainId = block.chainId,
          hash = generateHash(10),
          branch = generateHash(10),
          signature = Some(s"sig${generateHash(10)}"),
          blockId = block.hash
        )
    ).toList
  }

  /* create operations related to a specific group, with random data */
  private def generateOperationRowsForGroup(block: BlocksRow, group: OperationGroupsRow, howMany: Int = 3): List[Tables.OperationsRow] =
   if (howMany > 0) {

     (1 to howMany).map {
       counting =>
         Tables.OperationsRow(
           kind = "operation-kind",
           operationGroupHash = group.hash,
           operationId = -1,
           blockHash = block.hash,
           timestamp = block.timestamp,
           blockLevel = block.level
         )
     }.toList
   } else List.empty

  /* create operation rows to hold the given fees */
  private def wrapFeesWithOperationRows(
    fees: Seq[Option[String]],
    block: BlocksRow,
    group: OperationGroupsRow) = {

    fees.zipWithIndex.map {
      case (fee, index) =>
        OperationsRow(
          kind = "kind",
          operationGroupHash = group.hash,
          operationId = -1,
          fee = fee,
          blockHash = block.hash,
          timestamp = new Timestamp(block.timestamp.getTime + index),
          blockLevel = block.level
        )
    }

  }

  /* randomly generates a number of account rows for some block */
  private def generateAccountRows(howMany: Int, block: BlocksRow): List[AccountsRow] = {
    require(howMany > 0, "the test can generates a positive number of accounts, you asked for a non positive value")

    (1 to howMany).map {
      currentId =>
        AccountsRow(
          accountId = String valueOf currentId,
          blockId = block.hash,
          manager = "manager",
          spendable = true,
          delegateSetable = false,
          delegateValue = None,
          counter = 0,
          script = None,
          balance = 0
        )
    }.toList

  }


}
