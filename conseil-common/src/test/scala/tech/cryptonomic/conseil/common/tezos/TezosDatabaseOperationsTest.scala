package tech.cryptonomic.conseil.common.tezos

import java.sql.Timestamp
import java.time.Instant

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalatest.{Matchers, OptionValues, WordSpec}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.common.testkit.InMemoryDatabase
import tech.cryptonomic.conseil.common.testkit.util.RandomSeed
import tech.cryptonomic.conseil.common.tezos.michelson.contracts.{TNSContract, TokenContracts}
import tech.cryptonomic.conseil.common.tezos.FeeOperations.AverageFees
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import Tables.ProcessedChainEventsRow

import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Random

class TezosDatabaseOperationsTest
  extends WordSpec
    with TezosDataGeneration
    with InMemoryDatabase
    with TezosInMemoryDatabaseSetup
    with Matchers
    with ScalaFutures
    with OptionValues
    with LazyLogging
    with IntegrationPatience {

  "The database api" should {

    //needed for most tezos-db operations
    import scala.concurrent.ExecutionContext.Implicits.global
    implicit val noTokenContracts = TokenContracts.fromConfig(List.empty)
    implicit val noTNSContracts = TNSContract.noContract

    val sut = TezosDatabaseOperations
    val feesToConsider = 1000

    "use the right collation" in {
      val ordered =
        dbHandler.run(sql"SELECT val FROM unnest(ARRAY['a', 'b', 'A', 'B']) val ORDER BY val".as[String]).futureValue
      ordered should contain inOrderOnly ("a", "A", "b", "B")
    }

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

      forAll(dbFees zip generatedFees) {
        case (row, fee) =>
          row.low shouldEqual fee.low
          row.medium shouldEqual fee.medium
          row.high shouldEqual fee.high
          row.timestamp shouldEqual fee.timestamp
          row.kind shouldEqual fee.kind
      }
    }

    "tell if there are any stored blocks" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

      //check initial condition
      dbHandler.run(sut.doBlocksExist()).futureValue shouldBe false

      //store some blocks
      dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)

      //check final condition
      dbHandler.run(sut.doBlocksExist()).futureValue shouldBe true

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

      whenReady(dbHandler.run(sut.writeBlocks(generatedBlocks))) { _ =>
        //read and check what's on db
        val dbBlocks = dbHandler.run(Tables.Blocks.result).futureValue

        dbBlocks should have size (generatedBlocks.size)

        import org.scalatest.Inspectors._

        forAll(dbBlocks zip generatedBlocks) {
          case (row, block) =>
            val metadata = discardGenesis(block.data.metadata)

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
            row.periodKind shouldEqual metadata.map(_.voting_period_kind.toString)
            row.currentExpectedQuorum shouldEqual block.votes.quorum
            row.activeProposal shouldEqual block.votes.active.map(_.id)
            row.baker shouldEqual metadata.map(_.baker.value)
            row.consumedGas shouldEqual metadata.map(_.consumed_gas).flatMap {
              case PositiveDecimal(value) => Some(value)
              case _ => None
            }
            row.priority.value shouldEqual block.data.header.priority.value
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
            } yield (g, o)
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
                operationGroup.contents.find(_.isInstanceOf[Proposals])
              case "ballot" =>
                operationGroup.contents.find(_.isInstanceOf[Ballot])
              case _ => None
            }

            operationMatch shouldBe 'defined

            val operation = operationMatch.value

            /* Convert both the generated operation to a tables row representation
             * Comparing those for correctness makes sense as long as we guarantee with testing elsewhere
             * that the conversion itself is correct
             */
            import DatabaseConversions._
            import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
            //used as a constraint to read balance updates from operations
            import tech.cryptonomic.conseil.common.tezos.OperationBalances._
            import tech.cryptonomic.conseil.common.tezos.SymbolSourceLabels.Show._

            val generatedConversion = (operationBlock, operationGroup.hash, operation).convertTo[Tables.OperationsRow]
            //skip the id, to take into account that it's only generated on save
            generatedConversion shouldEqual opRow.copy(operationId = 0)

            /* check stored balance updates */
            //convert and set the real stored operation id
            val generatedUpdateRows =
            BlockTagged
              .fromBlockData(operationBlock.data, operation)
              .convertToA[List, Tables.BalanceUpdatesRow]
              .map(_.copy(sourceId = Some(opRow.operationId), operationGroupHash = Some(opRow.operationGroupHash)))

            //reset the generated id for matching
            val dbUpdateRows = dbHandler
              .run(
                Tables.BalanceUpdates.filter(_.sourceId === opRow.operationId).result
              )
              .futureValue
              .map(_.copy(id = 0))

            dbUpdateRows should contain theSameElementsAs generatedUpdateRows

        }
      }

    }

    "write metadata balance updates along with the blocks" in {
      import TezosOptics.Blocks._

      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val basicBlocks = generateBlocks(2, testReferenceDateTime)
      val generatedBlocks = basicBlocks.zipWithIndex.map {
        case (block, idx) =>
          val randomUpdates = generateBalanceUpdates(2)(randomSeed + idx)
          setBalances(randomUpdates)(block)
      }

      whenReady(dbHandler.run(sut.writeBlocks(generatedBlocks))) { _ =>
        val dbUpdatesRows = dbHandler.run(Tables.BalanceUpdates.result).futureValue

        dbUpdatesRows should have size 4 //2 updates x 2 blocks, not considering genesis which has no balances

        /* Convert both the generated blocks data to balance updates table row representation
         * Comparing those for correctness makes sense as long as we guarantee with testing elsewhere
         * that the conversion itself is correct
         */
        import DatabaseConversions._
        import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
        //used as a constraint to read balance updates from block data
        import tech.cryptonomic.conseil.common.tezos.BlockBalances._
        import tech.cryptonomic.conseil.common.tezos.SymbolSourceLabels.Show._

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

      stored.value shouldBe expectedCount

      dbAccounts should have size expectedCount

      import org.scalatest.Inspectors._

      forAll(dbAccounts zip accountsInfo.content) {
        case (row, (id, account)) =>
          row.accountId shouldEqual id.id
          row.blockId shouldEqual block.hash
          row.counter shouldEqual account.counter
          row.script shouldEqual account.script.map(_.code.expression)
          row.storage shouldEqual account.script.map(_.storage.expression)
          row.balance shouldEqual account.balance
          row.blockLevel shouldEqual block.level
      }

    }

    "fail to write accounts if the reference block is not stored" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val accountsInfo = generateAccounts(howMany = 1, blockHash = BlockHash("no-block-hash"), blockLevel = 1)

      val resultFuture = dbHandler.run(sut.writeAccounts(List(accountsInfo)))

      whenReady(resultFuture.failed) {
        _ shouldBe a[java.sql.SQLException]
      }
    }

    "update accounts if they exists already" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks @ (second :: first :: genesis :: Nil) =
        generateBlockRows(toLevel = 2, startAt = testReferenceTimestamp)
      val account = generateAccountRows(1, first).head

      val populate =
        DBIO.seq(
          Tables.Blocks ++= blocks,
          Tables.Accounts += account
        )

      dbHandler.run(populate).isReadyWithin(5 seconds) shouldBe true

      //prepare new accounts
      val accountChanges = 2
      val (hashUpdate, levelUpdate) = (second.hash, second.level)
      val accountsInfo = generateAccounts(accountChanges, BlockHash(hashUpdate), levelUpdate)

      //double-check for the identifier existence
      accountsInfo.content.keySet.map(_.id) should contain(account.accountId)

      //do the updates
      val writeUpdatedAndGetRows = for {
        written <- sut.writeAccounts(List(accountsInfo))
        rows <- Tables.Accounts.result
      } yield (written, rows)

      val (updates, dbAccounts) = dbHandler.run(writeUpdatedAndGetRows.transactionally).futureValue

      //number of db changes
      updates.value shouldBe accountChanges

      //total number of rows on db (1 update and 1 insert expected)
      dbAccounts should have size accountChanges

      import org.scalatest.Inspectors._

      //both rows on db should refer to updated data
      forAll(dbAccounts zip accountsInfo.content) {
        case (row, (id, account)) =>
          row.accountId shouldEqual id.id
          row.blockId shouldEqual hashUpdate
          row.counter shouldEqual account.counter
          row.script shouldEqual account.script.map(_.code.expression)
          row.storage shouldEqual account.script.map(_.storage.expression)
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
      val time = Instant.ofEpochMilli(0)
      val ids =
        blocks.map(
          block =>
            (BlockHash(block.hash), block.level, Some(time), None, List.fill(idPerBlock)(AccountId(generateHash(5))))
        )

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

      val flattenedIdsData = ids.flatMap {
        case (hash, level, time, cycle, accounts) => accounts.map((hash, level, _))
      }

      forAll(checkpointRows.zip(flattenedIdsData)) {
        case (row, (hash, level, accountId)) =>
          row.blockId shouldEqual hash.value
          row.blockLevel shouldBe level
          row.accountId shouldEqual accountId.id
      }

    }

    "clean the accounts checkpoints with no selection" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

      //store required blocks for FK
      dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)

      val accountIds = Array("a0", "a1", "a2", "a3", "a4", "a5", "a6")
      val blockIds = blocks.map(_.hash)

      //create test data:
      val checkpointRows = Array(
        Tables.AccountsCheckpointRow(accountIds(1), blockIds(1), blockLevel = 1, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(1), blockLevel = 1, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(1), blockLevel = 1, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(4), blockIds(2), blockLevel = 2, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(2), blockLevel = 2, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(3), blockLevel = 3, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(4), blockLevel = 4, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(4), blockLevel = 4, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(6), blockIds(5), blockLevel = 5, testReferenceTimestamp)
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

    "clean the accounts checkpoints with a partial id selection" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

      //store required blocks for FK
      dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)

      val accountIds = Array("a0", "a1", "a2", "a3", "a4", "a5", "a6")
      val blockIds = blocks.map(_.hash)

      //create test data:
      val checkpointRows = Array(
        Tables.AccountsCheckpointRow(accountIds(1), blockIds(1), blockLevel = 1, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(1), blockLevel = 1, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(1), blockLevel = 1, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(4), blockIds(2), blockLevel = 2, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(2), blockLevel = 2, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(3), blockLevel = 3, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(4), blockLevel = 4, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(4), blockLevel = 4, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(6), blockIds(5), blockLevel = 5, testReferenceTimestamp)
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

    "write delegates for a single block" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val expectedCount = 3

      val block = generateBlockRows(1, testReferenceTimestamp).head
      val delegatedAccounts = generateAccountRows(howMany = expectedCount, block)
      val delegatesInfo =
        generateDelegates(delegatedHashes = delegatedAccounts.map(_.accountId), BlockHash(block.hash), block.level)

      val writeAndGetRows = for {
        _ <- Tables.Blocks += block
        _ <- Tables.Accounts ++= delegatedAccounts
        written <- sut.writeBakersAndCopyContracts(List(delegatesInfo))
        delegatesRows <- Tables.Bakers.result
      } yield (written, delegatesRows)

      val (stored, dbDelegates) = dbHandler.run(writeAndGetRows.transactionally).futureValue

      stored.value shouldBe expectedCount

      dbDelegates should have size expectedCount

      import org.scalatest.Inspectors._

      forAll(dbDelegates zip delegatesInfo.content) {
        case (row, (pkh, delegate)) =>
          row.pkh shouldEqual pkh.value
          row.balance shouldEqual (delegate.balance match {
            case PositiveDecimal(value) => Some(value)
            case _ => None
          })
          row.delegatedBalance shouldEqual (delegate.delegated_balance match {
            case PositiveDecimal(value) => Some(value)
            case _ => None
          })
          row.frozenBalance shouldEqual (delegate.frozen_balance match {
            case PositiveDecimal(value) => Some(value)
            case _ => None
          })
          row.stakingBalance shouldEqual (delegate.staking_balance match {
            case PositiveDecimal(value) => Some(value)
            case _ => None
          })
          row.gracePeriod shouldEqual delegate.grace_period
          row.deactivated shouldBe delegate.deactivated
          row.blockId shouldEqual block.hash
          row.blockLevel shouldEqual block.level
      }

    }

    "fail to write delegates if the reference block is not stored" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val block = generateBlockRows(1, testReferenceTimestamp).head
      val delegatedAccounts = generateAccountRows(howMany = 1, block)
      val delegatesInfo = generateDelegates(
        delegatedHashes = delegatedAccounts.map(_.accountId),
        blockHash = BlockHash("no-block-hash"),
        blockLevel = 1
      )

      val resultFuture = dbHandler.run(sut.writeBakersAndCopyContracts(List(delegatesInfo)))

      whenReady(resultFuture.failed) {
        _ shouldBe a[java.sql.SQLException]
      }
    }

    "update delegates if they exists already" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks @ (second :: first :: genesis :: Nil) =
        generateBlockRows(toLevel = 2, startAt = testReferenceTimestamp)
      val account = generateAccountRows(1, first).head
      val delegate = generateDelegateRows(1, first).head

      val populate =
        DBIO.seq(
          Tables.Blocks ++= blocks,
          Tables.Accounts += account,
          Tables.Bakers += delegate
        )

      dbHandler.run(populate).isReadyWithin(5 seconds) shouldBe true

      //prepare new delegates
      val changes = 2
      val (hashUpdate, levelUpdate) = (second.hash, second.level)
      val delegatedKeys =
        generateAccounts(howMany = changes, BlockHash(hashUpdate), levelUpdate).content.keySet.map(_.id)
      val delegatesInfo = generateDelegates(
        delegatedHashes = delegatedKeys.toList,
        blockHash = BlockHash(hashUpdate),
        blockLevel = levelUpdate
      )

      //rewrite one of the keys to make it update the previously stored delegate row
      val delegateMap = delegatesInfo.content
      val pkh = delegateMap.keySet.head
      val updatedMap = (delegateMap - pkh) + (PublicKeyHash(delegate.pkh) -> delegateMap(pkh))
      val updatedDelegates = delegatesInfo.copy(content = updatedMap)

      //do the updates
      val writeUpdatedAndGetRows = for {
        written <- sut.writeBakersAndCopyContracts(List(updatedDelegates))
        rows <- Tables.Bakers.result
      } yield (written, rows)

      val (updates, dbDelegates) = dbHandler.run(writeUpdatedAndGetRows.transactionally).futureValue

      //number of db changes
      updates.value shouldBe changes

      //total number of rows on db (1 update and 1 insert expected)
      dbDelegates should have size changes

      import org.scalatest.Inspectors._

      //both rows on db should refer to updated data
      forAll(dbDelegates zip updatedDelegates.content) {
        case (row, (pkh, delegate)) =>
          row.pkh shouldEqual pkh.value
          row.balance shouldEqual (delegate.balance match {
            case PositiveDecimal(value) => Some(value)
            case _ => None
          })
          row.delegatedBalance shouldEqual (delegate.delegated_balance match {
            case PositiveDecimal(value) => Some(value)
            case _ => None
          })
          row.frozenBalance shouldEqual (delegate.frozen_balance match {
            case PositiveDecimal(value) => Some(value)
            case _ => None
          })
          row.stakingBalance shouldEqual (delegate.staking_balance match {
            case PositiveDecimal(value) => Some(value)
            case _ => None
          })
          row.gracePeriod shouldEqual delegate.grace_period
          row.deactivated shouldBe delegate.deactivated
          row.blockId should (equal(first.hash) or equal(second.hash))
          row.blockLevel should (equal(first.level) or equal(second.level))
      }

    }

    "store checkpoint delegate key hashes with block reference" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)
      //custom hash generator with predictable seed
      val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

      val maxLevel = 1
      val pkPerBlock = 3
      val expectedCount = (maxLevel + 1) * pkPerBlock

      //generate data
      val blocks = generateBlockRows(toLevel = maxLevel, testReferenceTimestamp)
      val keys = blocks.map(
        block =>
          (
            BlockHash(block.hash),
            block.level,
            Some(testReferenceTimestamp.toInstant),
            None,
            List.fill(pkPerBlock)(PublicKeyHash(generateHash(5)))
          )
      )

      //store and write
      val populateAndFetch = for {
        _ <- Tables.Blocks ++= blocks
        written <- sut.writeBakersCheckpoint(keys)
        rows <- Tables.BakersCheckpoint.result
      } yield (written, rows)

      val (stored, checkpointRows) = dbHandler.run(populateAndFetch).futureValue

      //number of changes
      stored.value shouldBe expectedCount
      checkpointRows should have size expectedCount

      import org.scalatest.Inspectors._

      val flattenedKeysData = keys.flatMap { case (hash, level, time, cycle, keys) => keys.map((hash, level, _)) }

      forAll(checkpointRows.zip(flattenedKeysData)) {
        case (row, (hash, level, keyHash)) =>
          row.blockId shouldEqual hash.value
          row.blockLevel shouldBe level
          row.delegatePkh shouldEqual keyHash.value
      }

    }

    "clean the delegates checkpoints with no selection" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

      //store required blocks for FK
      dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)

      val delegateKeyHashes = Array("pkh0", "pkh1", "pkh2", "pkh3", "pkh4", "pkh5", "pkh6")
      val blockIds = blocks.map(_.hash)

      //create test data:
      val checkpointRows = Array(
        Tables.BakersCheckpointRow(delegateKeyHashes(1), blockIds(1), blockLevel = 1),
        Tables.BakersCheckpointRow(delegateKeyHashes(2), blockIds(1), blockLevel = 1),
        Tables.BakersCheckpointRow(delegateKeyHashes(3), blockIds(1), blockLevel = 1),
        Tables.BakersCheckpointRow(delegateKeyHashes(4), blockIds(2), blockLevel = 2),
        Tables.BakersCheckpointRow(delegateKeyHashes(5), blockIds(2), blockLevel = 2),
        Tables.BakersCheckpointRow(delegateKeyHashes(2), blockIds(3), blockLevel = 3),
        Tables.BakersCheckpointRow(delegateKeyHashes(3), blockIds(4), blockLevel = 4),
        Tables.BakersCheckpointRow(delegateKeyHashes(5), blockIds(4), blockLevel = 4),
        Tables.BakersCheckpointRow(delegateKeyHashes(6), blockIds(5), blockLevel = 5)
      )

      val populateAndTest = for {
        stored <- Tables.BakersCheckpoint ++= checkpointRows
        cleaned <- sut.cleanBakersCheckpoint()
        rows <- Tables.BakersCheckpoint.result
      } yield (stored, cleaned, rows)

      val (initialCount, deletes, survivors) = dbHandler.run(populateAndTest.transactionally).futureValue
      initialCount.value shouldBe checkpointRows.size
      deletes shouldBe checkpointRows.size
      survivors shouldBe empty
    }

    "clean the delegates checkpoints with a partial key hash selection" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

      //store required blocks for FK
      dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)

      val delegateKeyHashes = Array("pkh0", "pkh1", "pkh2", "pkh3", "pkh4", "pkh5", "pkh6")
      val blockIds = blocks.map(_.hash)

      //create test data:
      val checkpointRows = Array(
        Tables.BakersCheckpointRow(delegateKeyHashes(1), blockIds(1), blockLevel = 1),
        Tables.BakersCheckpointRow(delegateKeyHashes(2), blockIds(1), blockLevel = 1),
        Tables.BakersCheckpointRow(delegateKeyHashes(3), blockIds(1), blockLevel = 1),
        Tables.BakersCheckpointRow(delegateKeyHashes(4), blockIds(2), blockLevel = 2),
        Tables.BakersCheckpointRow(delegateKeyHashes(5), blockIds(2), blockLevel = 2),
        Tables.BakersCheckpointRow(delegateKeyHashes(2), blockIds(3), blockLevel = 3),
        Tables.BakersCheckpointRow(delegateKeyHashes(3), blockIds(4), blockLevel = 4),
        Tables.BakersCheckpointRow(delegateKeyHashes(5), blockIds(4), blockLevel = 4),
        Tables.BakersCheckpointRow(delegateKeyHashes(6), blockIds(5), blockLevel = 5)
      )

      val inSelection = Set(delegateKeyHashes(1), delegateKeyHashes(2), delegateKeyHashes(3), delegateKeyHashes(4))

      val selection = inSelection.map(PublicKeyHash)

      val expected = checkpointRows.filterNot(row => inSelection(row.delegatePkh))

      val populateAndTest = for {
        stored <- Tables.BakersCheckpoint ++= checkpointRows
        cleaned <- sut.cleanBakersCheckpoint(Some(selection))
        rows <- Tables.BakersCheckpoint.result
      } yield (stored, cleaned, rows)

      val (initialCount, deletes, survivors) = dbHandler.run(populateAndTest.transactionally).futureValue
      initialCount.value shouldBe checkpointRows.size
      deletes shouldEqual checkpointRows.filter(row => inSelection(row.delegatePkh)).size
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
        Tables.AccountsCheckpointRow(accountIds(1), blockIds(1), blockLevel = 1, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(1), blockLevel = 1, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(1), blockLevel = 1, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(4), blockIds(2), blockLevel = 2, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(2), blockLevel = 2, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(2), blockIds(3), blockLevel = 3, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(3), blockIds(4), blockLevel = 4, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(5), blockIds(4), blockLevel = 4, testReferenceTimestamp),
        Tables.AccountsCheckpointRow(accountIds(6), blockIds(5), blockLevel = 5, testReferenceTimestamp)
      )

      def entry(accountAtIndex: Int, atLevel: Int, time: Timestamp) =
        AccountId(accountIds(accountAtIndex)) -> (BlockHash(blockIds(atLevel)), atLevel, Some(time.toInstant), None, None)

      //expecting only the following to remain
      val expected =
        Map(
          entry(accountAtIndex = 1, atLevel = 1, time = testReferenceTimestamp),
          entry(accountAtIndex = 2, atLevel = 3, time = testReferenceTimestamp),
          entry(accountAtIndex = 3, atLevel = 4, time = testReferenceTimestamp),
          entry(accountAtIndex = 4, atLevel = 2, time = testReferenceTimestamp),
          entry(accountAtIndex = 5, atLevel = 4, time = testReferenceTimestamp),
          entry(accountAtIndex = 6, atLevel = 5, time = testReferenceTimestamp)
        )

      val populateAndFetch = for {
        stored <- Tables.AccountsCheckpoint ++= checkpointRows
        rows <- sut.getLatestAccountsFromCheckpoint
      } yield (stored, rows)

      val (initialCount, latest) = dbHandler.run(populateAndFetch.transactionally).futureValue
      initialCount.value shouldBe checkpointRows.size

      latest.toSeq should contain theSameElementsAs expected.toSeq

    }

    "read latest delegate key hashes from checkpoint" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      //generate data
      val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

      //store required blocks for FK
      dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)
      val delegateKeyHashes = Array("pkh0", "pkh1", "pkh2", "pkh3", "pkh4", "pkh5", "pkh6")
      val blockIds = blocks.map(_.hash)

      //create test data:
      val checkpointRows = Array(
        Tables.BakersCheckpointRow(delegateKeyHashes(1), blockIds(1), blockLevel = 1),
        Tables.BakersCheckpointRow(delegateKeyHashes(2), blockIds(1), blockLevel = 1),
        Tables.BakersCheckpointRow(delegateKeyHashes(3), blockIds(1), blockLevel = 1),
        Tables.BakersCheckpointRow(delegateKeyHashes(4), blockIds(2), blockLevel = 2),
        Tables.BakersCheckpointRow(delegateKeyHashes(5), blockIds(2), blockLevel = 2),
        Tables.BakersCheckpointRow(delegateKeyHashes(2), blockIds(3), blockLevel = 3),
        Tables.BakersCheckpointRow(delegateKeyHashes(3), blockIds(4), blockLevel = 4),
        Tables.BakersCheckpointRow(delegateKeyHashes(5), blockIds(4), blockLevel = 4),
        Tables.BakersCheckpointRow(delegateKeyHashes(6), blockIds(5), blockLevel = 5)
      )

      def entry(delegateAtIndex: Int, atLevel: Int) =
        PublicKeyHash(delegateKeyHashes(delegateAtIndex)) -> (BlockHash(blockIds(atLevel)), atLevel, None, None, None)

      //expecting only the following to remain
      val expected =
        Map(
          entry(delegateAtIndex = 1, atLevel = 1),
          entry(delegateAtIndex = 2, atLevel = 3),
          entry(delegateAtIndex = 3, atLevel = 4),
          entry(delegateAtIndex = 4, atLevel = 2),
          entry(delegateAtIndex = 5, atLevel = 4),
          entry(delegateAtIndex = 6, atLevel = 5)
        )

      val populateAndFetch = for {
        stored <- Tables.BakersCheckpoint ++= checkpointRows
        rows <- sut.getLatestBakersFromCheckpoint
      } yield (stored, rows)

      val (initialCount, latest) = dbHandler.run(populateAndFetch.transactionally).futureValue
      initialCount.value shouldBe checkpointRows.size

      latest.toSeq should contain theSameElementsAs expected.toSeq

    }

    "fetch nothing if looking up a non-existent operation group by hash" in {
      dbHandler.run(sut.operationsForGroup("no-group-here")).futureValue shouldBe None
    }

    "fetch existing operations with their group on a existing hash" in {
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val block = generateBlockRows(1, testReferenceTimestamp).head
      val group = generateOperationGroupRows(block).head
      val ops = generateOperationsForGroup(block, group)

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
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)
      val block = generateBlockRows(1, testReferenceTimestamp).head
      val group = generateOperationGroupRows(block).head

      // mu = 152.59625
      // std-dev = 331.4
      // the sample std-dev should be 354.3, using correction formula
      val fees = Seq(
        Some(BigDecimal(35.23)),
        Some(BigDecimal(12.01)),
        Some(BigDecimal(2.22)),
        Some(BigDecimal(150.01)),
        None,
        Some(BigDecimal(1020.30)),
        Some(BigDecimal(1.00)),
        None
      )
      val ops = wrapFeesWithOperations(fees, block, group)

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
        kind = ops.head.kind,
        cycle = None,
        level = Some(block.level)
      )

      //check
      val feesCalculation = sut.calculateAverageFees(ops.head.kind, feesToConsider)

      dbHandler.run(feesCalculation).futureValue.value shouldEqual expected

    }

    "return None when computing average fees for a kind with no data" in {
      //generate data
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)
      val block = generateBlockRows(1, testReferenceTimestamp).head
      val group = generateOperationGroupRows(block).head

      val fees = Seq.fill(3)(Some(BigDecimal(1)))
      val ops = wrapFeesWithOperations(fees, block, group)

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
        kind = ops.head.kind,
        cycle = None,
        level = Some(0)
      )
      //check
      val feesCalculation = sut.calculateAverageFees(selection.head.kind, feesToConsider)

      dbHandler.run(feesCalculation).futureValue.value shouldEqual expected

    }

    "return the default when fetching the latest block level and there's no block stored" in {
      val expected = -1
      val maxLevel = dbHandler
        .run(
          sut.fetchMaxBlockLevel
        )
        .futureValue

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

    "read the custom update events processed from the db" in {
      //given
      val events = (1 to 3).map(ProcessedChainEventsRow(_, "event")).toList

      val populate = dbHandler.run(Tables.ProcessedChainEvents ++= events)
      populate.isReadyWithin(5.seconds) shouldBe true

      //when
      val results = dbHandler.run(sut.fetchProcessedEventsLevels("event")).futureValue

      results should contain theSameElementsAs (1 to 3)
    }

    "write new custom update events to the processed table on db" in {
      //given
      val values = (1 to 3).map(BigDecimal(_)).toList

      //when
      val populate = dbHandler.run(sut.writeProcessedEventsLevels("event", values))

      //then
      populate.isReadyWithin(5.seconds) shouldBe true

      populate.futureValue.value shouldBe 3

      val stored = dbHandler.run(Tables.ProcessedChainEvents.result).futureValue

      stored should contain theSameElementsAs (1 to 3).map(ProcessedChainEventsRow(_, "event"))

    }

    "read all distinct account ids and add entries for each in the checkpoint" in {
      //given
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val expectedCount = 3

      val block = generateBlockRows(1, testReferenceTimestamp).head
      val accountsInfo = generateAccounts(expectedCount, BlockHash(block.hash), block.level)

      val populate =
        (Tables.Blocks += block) >>
          sut.writeAccounts(List(accountsInfo))

      val write = dbHandler.run(populate.transactionally)

      write.isReadyWithin(5.seconds) shouldBe true

      //when
      val dbAction =
        sut.refillAccountsCheckpointFromExisting(
          BlockHash(block.hash),
          block.level,
          block.timestamp.toInstant,
          block.metaCycle
        )

      val results = dbHandler.run(dbAction).futureValue
      results.value shouldBe 3

      //then
      val checkpoint = dbHandler.run(sut.getLatestAccountsFromCheckpoint).futureValue

      checkpoint.keys should contain theSameElementsAs accountsInfo.content.keys

      import org.scalatest.Inspectors._
      forAll(checkpoint.values) {
        case (hash, level, instantOpt, cycleOpt, periodOpt) =>
          hash.value shouldEqual block.hash
          level shouldEqual block.level
          instantOpt.value shouldEqual block.timestamp.toInstant
          cycleOpt shouldEqual block.metaCycle
      }
    }

    "read selected distinct account ids via regex and add entries for each in the checkpoint" in {
      //given
      implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

      val expectedCount = 3
      val matchingId = AccountId("tz19alkdjf83aadkcl")

      val block = generateBlockRows(1, testReferenceTimestamp).head
      val BlockTagged(hash, level, ts, cycle, period, accountsContent) =
        generateAccounts(expectedCount, BlockHash(block.hash), block.level)
      val updatedContent = accountsContent.map {
        case (AccountId(id), account) if id == "1" => (matchingId, account)
        case any => any
      }

      val accountsInfo = BlockTagged(hash, level, ts, cycle, period, updatedContent)

      val populate =
        (Tables.Blocks += block) >>
          sut.writeAccounts(List(accountsInfo))

      val write = dbHandler.run(populate.transactionally)

      write.isReadyWithin(5.seconds) shouldBe true

      //when
      val dbAction =
        sut.refillAccountsCheckpointFromExisting(
          BlockHash(block.hash),
          block.level,
          block.timestamp.toInstant,
          block.metaCycle,
          Set("tz1.+")
        )

      val results = dbHandler.run(dbAction).futureValue
      results.value shouldBe 1

      //then
      val checkpoint = dbHandler.run(sut.getLatestAccountsFromCheckpoint).futureValue

      checkpoint.keys.size shouldBe 1
      checkpoint.keySet should contain only matchingId

      import org.scalatest.Inspectors._
      forAll(checkpoint.values) {
        case (hash, level, instantOpt, cycleOpt, periodOpt) =>
          hash.value shouldEqual block.hash
          level shouldEqual block.level
          instantOpt.value shouldEqual block.timestamp.toInstant
          cycleOpt shouldEqual block.metaCycle
      }
    }

  }

}