package tech.cryptonomic.conseil.tezos.repositories

import java.sql.Timestamp
import scala.util.Random
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.{InMemoryDatabase, Tables, TezosDataGeneration}
import tech.cryptonomic.conseil.tezos.Fees.AverageFees
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  discardGenesis,
  AccountId,
  BlockHash,
  ContractId,
  PositiveDecimal,
  ProtocolId,
  PublicKeyHash
}
import tech.cryptonomic.conseil.util.RandomSeed

class SlickRepositoriesTest
    extends WordSpec
    with TezosDataGeneration
    with InMemoryDatabase
    with ScalaFutures
    with OptionValues
    with Matchers
    with IntegrationPatience {

  //needed for most tezos-db operations
  import scala.concurrent.ExecutionContext.Implicits.global
  val repos = new SlickRepositories

  "The slick blocks repository" should {
      val sut = repos.blocksRepository

      "tell if there are any stored blocks at all" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        //generate data
        val blocks = generateBlockRows(toLevel = 5, testReferenceTimestamp)

        //check initial condition
        dbHandler.run(sut.anyBlockAvailable).futureValue shouldBe false

        //when
        dbHandler.run(Tables.Blocks ++= blocks).futureValue shouldBe Some(blocks.size)

        //then
        dbHandler.run(sut.anyBlockAvailable).futureValue shouldBe true

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
              val metadata = discardGenesis.lift(block.data.metadata)

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
        }
      }

      "return an empty value when fetching the latest block level with no block stored" in {
        val maxLevel = dbHandler
          .run(
            sut.fetchMaxBlockLevel
          )
          .futureValue

        maxLevel shouldBe None
      }

      "fetch the latest block level when blocks are available" in {
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val expected = 5
        val populateAndFetch = for {
          _ <- Tables.Blocks ++= generateBlockRows(expected, testReferenceTimestamp)
          result <- sut.fetchMaxBlockLevel
        } yield result

        val maxLevel = dbHandler.run(populateAndFetch.transactionally).futureValue

        maxLevel.value should equal(expected)
      }

      "correctly verify when a block exists" in {
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val blocks = generateBlockRows(1, testReferenceTimestamp)
        val testHash = BlockHash(blocks.last.hash)

        val populateAndTest = for {
          _ <- Tables.Blocks ++= blocks
          existing <- sut.blockExists(testHash)
          nonExisting <- sut.blockExists(BlockHash("bogus-hash"))
        } yield (existing, nonExisting)

        val (hit, miss) = dbHandler.run(populateAndTest.transactionally).futureValue

        hit shouldBe true
        miss shouldBe false

      }

    }

  "The slick operations repository" should {

      val sut = repos.operationsRepository

      "write operations groups" in {
        import org.scalatest.Inspectors._

        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val blockRows = generateBlockRows(5, testReferenceTimestamp)
        val generatedGroups = (blockRows.zipWithIndex map {
            case (block, idx) =>
              //need to use different seeds to generate unique hashes for groups
              val groups = generateOperationGroupRows(block)(randomSeed + idx)
              block -> groups
          }).toMap

        //when
        val writeData =
          for {
            _ <- Tables.Blocks ++= blockRows
            _ <- sut.writeOperationsGroups(generatedGroups.values.flatten.toList)
          } yield ()

        whenReady(dbHandler.run(writeData)) { _ =>
          //then
          val dbBlocksAndGroups =
            dbHandler.run {
              val query = for {
                g <- Tables.OperationGroups
                b <- g.blocksFk
              } yield (b, g)
              query.result
            }.futureValue

          dbBlocksAndGroups should have size (generatedGroups.size)

          forAll(dbBlocksAndGroups) {
            case (blockRow, groupRow) =>
              val groups = generatedGroups.get(blockRow).value
              forAll(groups) { group =>
                groupRow.hash shouldEqual group.hash.value
                groupRow.blockId shouldEqual blockRow.hash
                groupRow.chainId shouldEqual group.chainId
                groupRow.branch shouldEqual group.branch
                groupRow.signature shouldEqual group.signature
                groupRow.protocol shouldEqual group.protocol
              }
          }

        }

      }

      "write operations" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val block = generateBlockRows(1, testReferenceTimestamp).last
        val group = generateOperationGroupRows(block).head

        val generatedOperation = generateOperationsForGroup(block, group, howMany = 1).head

        //when
        val writeData =
          for {
            _ <- Tables.Blocks += block
            _ <- Tables.OperationGroups += group
          } yield ()

        whenReady(dbHandler.run(writeData)) { _ =>
          //then
          val writeAndFetchFromDb = (for {
            generatedId <- sut.writeOperationWithNewId(generatedOperation)
            rows <- Tables.Operations.result
          } yield (generatedId, rows)).transactionally

          val (id, dbOperations) = dbHandler.run(writeAndFetchFromDb).futureValue

          dbOperations should have size 1
          val operation = dbOperations.head

          operation.operationId shouldBe id
          operation.operationGroupHash shouldEqual group.hash.value
          operation.blockHash shouldEqual block.hash
          operation.timestamp shouldEqual block.timestamp

        }

      }

      "fetch existing operations with their group on a existing hash" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val block = generateBlockRows(1, testReferenceTimestamp).last
        val group = generateOperationGroupRows(block).head
        val operations = generateOperationsForGroup(block, group, howMany = 3)

        //when
        val writeData =
          for {
            _ <- Tables.Blocks += block
            _ <- Tables.OperationGroups += group
            _ <- Tables.Operations ++= operations
          } yield ()

        whenReady(dbHandler.run(writeData)) { _ =>
          import org.scalatest.Inspectors._
          //then
          val (dbGroup, dbOperations) = dbHandler.run(sut.operationsForGroup(group.hash)).futureValue.value

          dbOperations should have size 3

          dbGroup shouldBe group

          forAll(dbOperations) { operation =>
            operation.operationId should be > -1
            operation.operationGroupHash shouldEqual group.hash
            operation.blockHash shouldEqual block.hash
            operation.timestamp shouldEqual block.timestamp
          }

        }

      }

      "fetch nothing if looking up a non-existent operation group by hash" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val block = generateBlockRows(1, testReferenceTimestamp).last
        val group = generateOperationGroupRows(block).head
        val operations = generateOperationsForGroup(block, group, howMany = 1)

        val noGroupsHash = "bogus-hash"

        //when
        val writeData =
          for {
            _ <- Tables.Blocks += block
            _ <- Tables.OperationGroups += group
            _ <- Tables.Operations ++= operations
          } yield ()

        //then
        whenReady(dbHandler.run(writeData)) { _ =>
          dbHandler.run(sut.operationsForGroup(noGroupsHash)).futureValue shouldBe 'empty
        }

      }

      "write balance updates for block's metadata" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val (maxLevel, updatePerBlock) = (2, 2)

        val blockRows = generateBlockRows(maxLevel, testReferenceTimestamp)
        val generatedUpdates = blockRows.zipWithIndex.flatMap {
          case (block, idx) => generateBalanceUpdatesRows(updatePerBlock, Left(block))(randomSeed + idx)
        }

        whenReady(dbHandler.run(Tables.Blocks ++= blockRows)) { _ =>
          //when
          val writeAndFetchUpdates = for {
            stored <- sut.writeUpdates(generatedUpdates)
            updates <- Tables.BalanceUpdates.result
          } yield (stored, updates)

          val (dbWrites, dbUpdatesRows) = dbHandler.run(writeAndFetchUpdates).futureValue

          //expectations
          val totalExpected = (maxLevel + 1) * updatePerBlock // blocks are generated from 0 to level

          //then
          dbWrites.value shouldBe (totalExpected)

          dbUpdatesRows should have size (totalExpected)

          //reset the generated id for matching
          dbUpdatesRows.map(_.copy(id = 0)) should contain theSameElementsAs generatedUpdates

          import org.scalatest.Inspectors._

          val allowedHashes = blockRows.map(_.hash)

          forAll(dbUpdatesRows) { row =>
            row.sourceId shouldBe 'empty
            allowedHashes should contain(row.sourceHash.value)
            row.source shouldBe "Block"

          }
        }

      }

      "write balance updates for operations" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val block = generateBlockRows(1, testReferenceTimestamp).last
        val group = generateOperationGroupRows(block).head
        val operation = generateOperationsForGroup(block, group, howMany = 1).head

        val updatePerOperation = 2
        val generatedUpdates = generateBalanceUpdatesRows(updatePerOperation, Right(operation))

        //when
        val writeData =
          for {
            _ <- Tables.Blocks += block
            _ <- Tables.OperationGroups += group
            _ <- Tables.Operations += operation
          } yield ()

        whenReady(dbHandler.run(writeData.transactionally)) { _ =>
          //when
          val writeAndFetchUpdates = for {
            stored <- sut.writeUpdates(generatedUpdates, sourceOperation = Some(operation.operationId))
            updates <- Tables.BalanceUpdates.result
          } yield (stored, updates)

          val (dbWrites, dbUpdatesRows) = dbHandler.run(writeAndFetchUpdates).futureValue

          //expectations
          val totalExpected = updatePerOperation

          //then
          dbWrites.value shouldBe (totalExpected)

          dbUpdatesRows should have size (totalExpected)

          //reset the generated id and the missing operation source id for matching
          dbUpdatesRows.map(_.copy(id = 0, sourceId = None)) should contain theSameElementsAs generatedUpdates

          import org.scalatest.Inspectors._

          forAll(dbUpdatesRows) { row =>
            row.sourceId.value shouldBe (operation.operationId)
            row.sourceHash shouldBe 'empty
            row.source shouldBe "Operation"

          }
        }

      }

    }

  "The slick voting repository" should {
      import tech.cryptonomic.conseil.tezos.DatabaseConversions._
      import tech.cryptonomic.conseil.util.Conversion.Syntax._

      val sut = repos.votingRepository

      "write voting proposal" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val block = generateSingleBlock(atLevel = 1, atTime = testReferenceDateTime)
        val proposals = Voting.generateProposals(howMany = 3, forBlock = block)

        //when
        val writeAndGetRows = for {
          _ <- Tables.Blocks += block.convertTo[Tables.BlocksRow]
          written <- sut.writeVotingProposals(proposals)
          rows <- Tables.Proposals.result
        } yield (written, rows)

        val (stored, dbProposals) = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
        val expectedWrites = proposals.map(_.protocols.size).sum
        stored.value shouldEqual expectedWrites
        dbProposals should have size expectedWrites

        import org.scalatest.Inspectors._

        val allProtocols = proposals.flatMap(_.protocols).toMap

        forAll(dbProposals) { proposalRow =>
          val rowProtocol = ProtocolId(proposalRow.protocolHash)
          allProtocols.keySet should contain(rowProtocol)
          allProtocols(rowProtocol) shouldBe proposalRow.supporters.value
          proposalRow.blockId shouldBe block.data.hash.value
          proposalRow.blockLevel shouldBe block.data.header.level
        }
      }

      "write voting bakers rolls" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val block = generateSingleBlock(atLevel = 1, atTime = testReferenceDateTime)
        val rolls = Voting.generateBakersRolls(howMany = 3)

        //when
        val writeAndGetRows = for {
          _ <- Tables.Blocks += block.convertTo[Tables.BlocksRow]
          written <- sut.writeVotingRolls(rolls, block)
          rows <- Tables.Rolls.result
        } yield (written, rows)

        val (stored, dbRolls) = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
        stored.value shouldEqual rolls.size
        dbRolls should have size rolls.size

        import org.scalatest.Inspectors._

        forAll(dbRolls) { rollsRow =>
          val generated = rolls.find(_.pkh.value == rollsRow.pkh).value
          rollsRow.rolls shouldEqual generated.rolls
          rollsRow.blockId shouldBe block.data.hash.value
          rollsRow.blockLevel shouldBe block.data.header.level
        }

      }

      "write voting ballots" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val block = generateSingleBlock(atLevel = 1, atTime = testReferenceDateTime)
        val ballots = Voting.generateBallots(howMany = 3)

        //when
        val writeAndGetRows = for {
          _ <- Tables.Blocks += block.convertTo[Tables.BlocksRow]
          written <- sut.writeVotingBallots(ballots, block)
          rows <- Tables.Ballots.result
        } yield (written, rows)

        val (stored, dbBallots) = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
        stored.value shouldEqual ballots.size
        dbBallots should have size ballots.size

        import org.scalatest.Inspectors._

        forAll(dbBallots) { ballotRow =>
          val generated = ballots.find(_.pkh.value == ballotRow.pkh).value
          ballotRow.ballot shouldEqual generated.ballot.value
          ballotRow.blockId shouldBe block.data.hash.value
          ballotRow.blockLevel shouldBe block.data.header.level
        }

      }
    }

  "The slick accounts repository" should {

      val sut = repos.accountsRepository

      "write accounts for a single block" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val expectedCount = 3

        val block = generateBlockRows(1, testReferenceTimestamp).head
        val accountsInfo = generateAccounts(expectedCount, BlockHash(block.hash), block.level)

        //when
        val writeAndGetRows = for {
          _ <- Tables.Blocks += block
          written <- sut.updateAccounts(List(accountsInfo))
          rows <- Tables.Accounts.result
        } yield (written, rows)

        val (stored, dbAccounts) = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
        stored shouldBe expectedCount

        dbAccounts should have size expectedCount

        import org.scalatest.Inspectors._

        forAll(dbAccounts zip accountsInfo.content) {
          case (row, (id, account)) =>
            row.accountId shouldEqual id.id
            row.blockId shouldEqual block.hash
            row.manager shouldEqual account.manager.value
            row.spendable shouldEqual account.spendable
            row.delegateSetable shouldEqual account.delegate.setable
            row.delegateValue shouldEqual account.delegate.value.map(_.value)
            row.counter shouldEqual account.counter
            row.script shouldEqual account.script.map(_.code.expression)
            row.storage shouldEqual account.script.map(_.storage.expression)
            row.balance shouldEqual account.balance
            row.blockLevel shouldEqual block.level
        }

      }

      "fail to write accounts if the reference block is not stored" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val accountsInfo = generateAccounts(howMany = 1, blockHash = BlockHash("no-block-hash"), blockLevel = 1)

        //wehen
        val resultFuture = dbHandler.run(sut.updateAccounts(List(accountsInfo)))

        //then
        whenReady(resultFuture.failed) {
          _ shouldBe a[java.sql.SQLException]
        }
      }

      "update accounts if they exists already" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val blocks @ (second :: first :: genesis :: Nil) =
          generateBlockRows(toLevel = 2, startAt = testReferenceTimestamp)
        val account = generateAccountRows(1, first).head

        val populate =
          DBIO.seq(
            Tables.Blocks ++= blocks,
            Tables.Accounts += account
          )

        whenReady(dbHandler.run(populate)) { _ =>
          //prepare new accounts
          val accountChanges = 2
          val (hashUpdate, levelUpdate) = (second.hash, second.level)
          val accountsInfo = generateAccounts(accountChanges, BlockHash(hashUpdate), levelUpdate)

          //double-check for the identifier existence
          accountsInfo.content.keySet.map(_.id) should contain(account.accountId)

          //when
          //do the updates
          val writeUpdatedAndGetRows = for {
            written <- sut.updateAccounts(List(accountsInfo))
            rows <- Tables.Accounts.result
          } yield (written, rows)

          val (updates, dbAccounts) = dbHandler.run(writeUpdatedAndGetRows.transactionally).futureValue

          //then
          //number of db changes
          updates shouldBe accountChanges

          //total number of rows on db (1 update and 1 insert expected)
          dbAccounts should have size accountChanges

          import org.scalatest.Inspectors._

          //both rows on db should refer to updated data
          forAll(dbAccounts zip accountsInfo.content) {
            case (row, (id, account)) =>
              row.accountId shouldEqual id.id
              row.blockId shouldEqual hashUpdate
              row.manager shouldEqual account.manager.value
              row.spendable shouldEqual account.spendable
              row.delegateSetable shouldEqual account.delegate.setable
              row.delegateValue shouldEqual account.delegate.value.map(_.value)
              row.counter shouldEqual account.counter
              row.script shouldEqual account.script.map(_.code.expression)
              row.storage shouldEqual account.script.map(_.storage.expression)
              row.balance shouldEqual account.balance
              row.blockLevel shouldEqual levelUpdate
          }

        }

      }

      "store checkpoint account ids with block reference" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)
        //custom hash generator with predictable seed
        val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

        val maxLevel = 1
        val idPerBlock = 3
        val expectedCount = (maxLevel + 1) * idPerBlock

        //generate data
        val blocks = generateBlockRows(toLevel = maxLevel, testReferenceTimestamp)
        val ids =
          blocks.map(block => (BlockHash(block.hash), block.level, List.fill(idPerBlock)(AccountId(generateHash(5)))))

        //when
        //store and write
        val populateAndFetch = for {
          _ <- Tables.Blocks ++= blocks
          written <- sut.writeAccountsCheckpoint(ids)
          rows <- Tables.AccountsCheckpoint.result
        } yield (written, rows)

        val (stored, checkpointRows) = dbHandler.run(populateAndFetch).futureValue

        //then
        //number of changes
        stored.value shouldBe expectedCount
        checkpointRows should have size expectedCount

        import org.scalatest.Inspectors._

        val flattenedIdsData = ids.flatMap { case (hash, level, accounts) => accounts.map((hash, level, _)) }

        forAll(checkpointRows.zip(flattenedIdsData)) {
          case (row, (hash, level, accountId)) =>
            row.blockId shouldEqual hash.value
            row.blockLevel shouldBe level
            row.accountId shouldEqual accountId.id
        }

      }

      "clean the accounts checkpoints with no selection" in {
        //given
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

        //when
        val populateAndTest = for {
          stored <- Tables.AccountsCheckpoint ++= checkpointRows
          cleaned <- sut.cleanAccountsCheckpoint()
          rows <- Tables.AccountsCheckpoint.result
        } yield (stored, cleaned, rows)

        val (initialCount, deletes, survivors) = dbHandler.run(populateAndTest.transactionally).futureValue

        //then
        initialCount.value shouldBe checkpointRows.size
        deletes shouldBe checkpointRows.size
        survivors shouldBe empty
      }

      "clean the accounts checkpoints with a partial id selection" in {
        //given
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

        //when
        val populateAndTest = for {
          stored <- Tables.AccountsCheckpoint ++= checkpointRows
          cleaned <- sut.cleanAccountsCheckpoint(Some(selection))
          rows <- Tables.AccountsCheckpoint.result
        } yield (stored, cleaned, rows)

        val (initialCount, deletes, survivors) = dbHandler.run(populateAndTest.transactionally).futureValue

        //then
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

    }

  "The slick delegates repository" should {

      val sut = repos.delegatesRepository

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
          written <- sut.updateDelegates(List(delegatesInfo))
          delegatesRows <- Tables.Delegates.result
          contractsRows <- Tables.DelegatedContracts.result
        } yield (written, delegatesRows, contractsRows)

        val (stored, dbDelegates, dbContracts) = dbHandler.run(writeAndGetRows.transactionally).futureValue

        stored shouldBe expectedCount

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

        val resultFuture = dbHandler.run(sut.updateDelegates(List(delegatesInfo)))

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
            Tables.Delegates += delegate
          )

        whenReady(dbHandler.run(populate)) { _ =>
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
            written <- sut.updateDelegates(List(updatedDelegates))
            rows <- Tables.Delegates.result
          } yield (written, rows)

          val (updates, dbDelegates) = dbHandler.run(writeUpdatedAndGetRows.transactionally).futureValue

          //number of db changes
          updates shouldBe changes

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
      }

      "copy accounts data to corresponding delegate contracts" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val expectedCount = 3

        val block = generateBlockRows(1, testReferenceTimestamp).head
        val delegatedAccounts = generateAccountRows(howMany = expectedCount, block)

        val populate = for {
          _ <- Tables.Blocks += block
          _ <- Tables.Accounts ++= delegatedAccounts
        } yield ()

        whenReady(dbHandler.run(populate.transactionally)) { _ =>
          //when
          val contractIds = delegatedAccounts.map(account => ContractId(account.accountId)).toSet

          val copyAndRead = for {
            copied <- sut.copyAccountsAsDelegateContracts(contractIds)
            contracts <- Tables.DelegatedContracts.result
          } yield (copied, contracts)

          val (dbCopies, dbContracts) = dbHandler.run(copyAndRead).futureValue

          //then
          dbCopies.value shouldBe expectedCount
          dbContracts should have size expectedCount

          import org.scalatest.Inspectors._

          forAll(dbContracts zip delegatedAccounts) {
            case (contract, account) =>
              contract.accountId shouldEqual account.accountId
              contract.delegateValue shouldEqual account.delegateValue
          }

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
          block => (BlockHash(block.hash), block.level, List.fill(pkPerBlock)(PublicKeyHash(generateHash(5))))
        )

        //store and write
        val populateAndFetch = for {
          _ <- Tables.Blocks ++= blocks
          written <- sut.writeDelegatesCheckpoint(keys)
          rows <- Tables.DelegatesCheckpoint.result
        } yield (written, rows)

        val (stored, checkpointRows) = dbHandler.run(populateAndFetch).futureValue

        //number of changes
        stored.value shouldBe expectedCount
        checkpointRows should have size expectedCount

        import org.scalatest.Inspectors._

        val flattenedKeysData = keys.flatMap { case (hash, level, keys) => keys.map((hash, level, _)) }

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
          Tables.DelegatesCheckpointRow(delegateKeyHashes(1), blockIds(1), blockLevel = 1),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(2), blockIds(1), blockLevel = 1),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(3), blockIds(1), blockLevel = 1),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(4), blockIds(2), blockLevel = 2),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(5), blockIds(2), blockLevel = 2),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(2), blockIds(3), blockLevel = 3),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(3), blockIds(4), blockLevel = 4),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(5), blockIds(4), blockLevel = 4),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(6), blockIds(5), blockLevel = 5)
        )

        val populateAndTest = for {
          stored <- Tables.DelegatesCheckpoint ++= checkpointRows
          cleaned <- sut.cleanDelegatesCheckpoint()
          rows <- Tables.DelegatesCheckpoint.result
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
          Tables.DelegatesCheckpointRow(delegateKeyHashes(1), blockIds(1), blockLevel = 1),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(2), blockIds(1), blockLevel = 1),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(3), blockIds(1), blockLevel = 1),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(4), blockIds(2), blockLevel = 2),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(5), blockIds(2), blockLevel = 2),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(2), blockIds(3), blockLevel = 3),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(3), blockIds(4), blockLevel = 4),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(5), blockIds(4), blockLevel = 4),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(6), blockIds(5), blockLevel = 5)
        )

        val inSelection = Set(delegateKeyHashes(1), delegateKeyHashes(2), delegateKeyHashes(3), delegateKeyHashes(4))

        val selection = inSelection.map(PublicKeyHash)

        val expected = checkpointRows.filterNot(row => inSelection(row.delegatePkh))

        val populateAndTest = for {
          stored <- Tables.DelegatesCheckpoint ++= checkpointRows
          cleaned <- sut.cleanDelegatesCheckpoint(Some(selection))
          rows <- Tables.DelegatesCheckpoint.result
        } yield (stored, cleaned, rows)

        val (initialCount, deletes, survivors) = dbHandler.run(populateAndTest.transactionally).futureValue
        initialCount.value shouldBe checkpointRows.size
        deletes shouldEqual checkpointRows.filter(row => inSelection(row.delegatePkh)).size
        survivors should contain theSameElementsAs expected

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
          Tables.DelegatesCheckpointRow(delegateKeyHashes(1), blockIds(1), blockLevel = 1),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(2), blockIds(1), blockLevel = 1),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(3), blockIds(1), blockLevel = 1),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(4), blockIds(2), blockLevel = 2),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(5), blockIds(2), blockLevel = 2),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(2), blockIds(3), blockLevel = 3),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(3), blockIds(4), blockLevel = 4),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(5), blockIds(4), blockLevel = 4),
          Tables.DelegatesCheckpointRow(delegateKeyHashes(6), blockIds(5), blockLevel = 5)
        )

        def entry(delegateAtIndex: Int, atLevel: Int) =
          PublicKeyHash(delegateKeyHashes(delegateAtIndex)) -> (BlockHash(blockIds(atLevel)), atLevel)

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
          stored <- Tables.DelegatesCheckpoint ++= checkpointRows
          rows <- sut.getLatestDelegatesFromCheckpoint
        } yield (stored, rows)

        val (initialCount, latest) = dbHandler.run(populateAndFetch.transactionally).futureValue
        initialCount.value shouldBe checkpointRows.size

        latest.toSeq should contain theSameElementsAs expected.toSeq

      }

    }

  "The slick fees repository" should {
      //used on fees averaging
      val feesToConsider = 1000

      val sut = repos.feesRepository

      "write fees" in {
        //given
        val expectedCount = 5

        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val generatedFees = generateFees(expectedCount, testReferenceTimestamp)

        //when
        val writeAndGetRows = for {
          written <- sut.writeFees(generatedFees)
          rows <- Tables.Fees.result
        } yield (written, rows)

        val (stored, dbFees) = dbHandler.run(writeAndGetRows.transactionally).futureValue

        //then
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

      "compute correct average fees from stored operations" in {
        //given
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
          kind = ops.head.kind
        )

        //when
        val feesCalculation = sut.calculateAverageFees(ops.head.kind, feesToConsider)

        //then
        dbHandler.run(feesCalculation).futureValue.value shouldEqual expected

      }

      "return None when computing average fees for a kind with no data" in {
        //given
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

        //when
        val feesCalculation = sut.calculateAverageFees("undefined", feesToConsider)

        //then
        dbHandler.run(feesCalculation).futureValue shouldBe None

      }

      "compute average fees only using the selected operation kinds" in {
        //given
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
          kind = ops.head.kind
        )
        //when
        val feesCalculation = sut.calculateAverageFees(selection.head.kind, feesToConsider)

        //then
        dbHandler.run(feesCalculation).futureValue.value shouldEqual expected

      }

    }

}
