package tech.cryptonomic.conseil.tezos

import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.{IntegrationPatience, ScalaFutures}
import org.scalamock.scalatest.MockFactory
import com.typesafe.config.ConfigFactory
import slick.jdbc.H2Profile.api._
import scala.util.Random
import tech.cryptonomic.conseil.util.RandomSeed
import tech.cryptonomic.conseil.tezos.repositories.BlocksRepository
import tech.cryptonomic.conseil.tezos.repositories.{
  AccountsRepository,
  DelegatesRepository,
  OperationsRepository,
  VotingRepository
}
import tech.cryptonomic.conseil.tezos.Tables.{BalanceUpdatesRow, OperationGroupsRow, OperationsRow}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, BlockTagged, ContractId}

class TezosDatastoreTest
    extends WordSpec
    with MockFactory
    with TezosDataGeneration
    with ScalaFutures
    with Matchers
    with IntegrationPatience {

  private def dbio[T](t: T): DBIO[T] = DBIO.successful(t)

  private val inMemoryDb = """
    | testdb = {
    |  url = "jdbc:h2:mem:conseil-test;MODE=PostgreSQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1"
    |  driver              = org.h2.Driver
    |  connectionPool      = disabled
    |  keepAliveConnection = true
    |}""".stripMargin

  private val testDb = Database.forConfig("testdb", config = ConfigFactory.parseString(inMemoryDb))

  "The Tezos Datastore Api" should {
      //needed for most tezos-db operations
      import scala.concurrent.ExecutionContext.Implicits.global

      //a function that constantly return ()
      def unitFunction[T] = Function.const[Unit, T](()) _

      /* creates a custom datastore where the underlying repos are mocks prepared with custom expecations */
      def prepareDatastoreMockWithExpectactions(
          blocksRepoExpectations: BlocksRepository[DBIO] => Any = unitFunction,
          accountsRepoExpectations: AccountsRepository[DBIO] => Any = unitFunction,
          votingRepoExpectations: VotingRepository[DBIO] => Any = unitFunction,
          delegatesRepoExpectations: DelegatesRepository[DBIO] => Any = unitFunction,
          operationsRepoExpectations: OperationsRepository[
            DBIO,
            OperationGroupsRow,
            OperationsRow,
            Int,
            BalanceUpdatesRow
          ] => Any = unitFunction
      ): TezosDatastore = {
        implicit val blocksRepo = mock[BlocksRepository[DBIO]]
        implicit val accountsRepo = mock[AccountsRepository[DBIO]]
        implicit val votingRepo = mock[VotingRepository[DBIO]]
        implicit val delegatesRepo = mock[DelegatesRepository[DBIO]]
        implicit val operationsRepo =
          mock[OperationsRepository[DBIO, OperationGroupsRow, OperationsRow, Int, BalanceUpdatesRow]]

        blocksRepoExpectations(blocksRepo)
        accountsRepoExpectations(accountsRepo)
        votingRepoExpectations(votingRepo)
        delegatesRepoExpectations(delegatesRepo)
        operationsRepoExpectations(operationsRepo)

        new TezosDatastore
      }

      "store blocks only" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val maxLevel = 5

        val generatedBlocks = generateBlocks(maxLevel, testReferenceDateTime)

        val blockAccountsMap = generatedBlocks.map { block =>
          block -> List.empty[AccountId]
        }.toMap

        //set expectations and build the datastore
        val sut = prepareDatastoreMockWithExpectactions(
          blocksRepoExpectations = repo => {
            (repo.writeBlocks _)
              .expects(blockAccountsMap.keys.toList)
              .returns(dbio(Some(generatedBlocks.size)))
              .once
          },
          operationsRepoExpectations = repo => {

            (repo.writeOperationsGroups _)
              .expects(
                where(
                  (groups: List[OperationGroupsRow]) => groups.isEmpty
                )
              )
              .returns(dbio(None))
              .once

            //once only for blocks-related updates
            (repo.writeUpdates _)
              .expects(List.empty[BalanceUpdatesRow], None)
              .returns(dbio(Option.empty[Int]))
              .once
          },
          accountsRepoExpectations = repo => {
            (repo.writeAccountsCheckpoint _)
              .expects(
                where(
                  (accountUpdates: List[(TezosTypes.BlockHash, Int, List[AccountId])]) =>
                    accountUpdates.size == generatedBlocks.size &&
                      accountUpdates.forall { case (_, _, accounts) => accounts.isEmpty }
                )
              )
              .returns(dbio(Some(0)))
          }
        )

        // val sut = new TezosDatastore()(blocksRepo, accountsRepo, opsRepo, votesRepo, delRepo)

        //when
        val action = sut.storeBlocksAndCheckpointAccounts(blockAccountsMap)

        //then
        testDb.run(action).futureValue shouldBe (Some(generatedBlocks.size), Some(0))
      }

      "store blocks with operations data" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val maxLevel = 5

        val basicBlocks = generateBlocks(maxLevel, testReferenceDateTime)
        val generatedBlocks = basicBlocks.zipWithIndex map {
              case (block, idx) =>
                //need to use different seeds to generate unique hashes for groups
                val group = generateOperationGroup(block, generateOperations = true)(randomSeed + idx)
                block.copy(operationGroups = List(group))
            }

        val blockAccountsMap = generatedBlocks.map { block =>
          block -> List.empty[AccountId]
        }.toMap

        val totalGroupsCount = generatedBlocks.map(_.operationGroups.size).sum
        //each operation group contains all sample operations
        val totalOperationsCount = Operations.sampleOperations.size * totalGroupsCount

        //set expectations and build the datastore
        val sut = prepareDatastoreMockWithExpectactions(
          blocksRepoExpectations = repo => {
            (repo.writeBlocks _)
              .expects(*)
              .returns(dbio(Some(generatedBlocks.size)))
              .once
          },
          operationsRepoExpectations = repo => {
            (repo.writeOperationsGroups _)
              .expects(*)
              .returns(dbio(None))
              .once

            var fakeOperationId = 0

            (repo.writeOperationWithNewId _)
              .expects(*)
              .onCall((_: OperationsRow) => {
                fakeOperationId += 1
                dbio(fakeOperationId)
              })
              .repeated(totalOperationsCount)
              .times

            //once for blocks-related updates and the rest for operations-related updates
            (repo.writeUpdates _)
              .expects(*, *)
              .returns(dbio(Option.empty[Int]))
              .repeated(totalOperationsCount + 1)
              .times

          },
          accountsRepoExpectations = repo => {
            (repo.writeAccountsCheckpoint _)
              .expects(
                where(
                  (accountUpdates: List[(TezosTypes.BlockHash, Int, List[AccountId])]) =>
                    accountUpdates.size == generatedBlocks.size &&
                      accountUpdates.forall { case (_, _, accounts) => accounts.isEmpty }
                )
              )
              .returns(dbio(Some(0)))

          }
        )

        //when
        val action = sut.storeBlocksAndCheckpointAccounts(blockAccountsMap)

        //then
        testDb.run(action).futureValue shouldBe (Some(generatedBlocks.size), Some(0))
      }

      "store blocks with account ids to checkpoint" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)
        //custom hash generator with predictable seed
        val generateHash: Int => String = alphaNumericGenerator(new Random(randomSeed.seed))

        val maxLevel = 5
        val accountsPerBlock = 3

        val generatedBlocks = generateBlocks(maxLevel, testReferenceDateTime)

        val blockAccountsMap = generatedBlocks.map { block =>
          block -> List.fill(accountsPerBlock)(AccountId(generateHash(5)))
        }.toMap

        //take the genesis into account
        val checkpointSize = (maxLevel + 1) * accountsPerBlock

        //set expectations and build the datastore
        val sut = prepareDatastoreMockWithExpectactions(
          blocksRepoExpectations = repo => {
            (repo.writeBlocks _)
              .expects(*)
              .returns(dbio(Some(generatedBlocks.size)))
              .once
          },
          operationsRepoExpectations = repo => {
            (repo.writeOperationsGroups _)
              .expects(
                where(
                  (groups: List[OperationGroupsRow]) => groups.isEmpty
                )
              )
              .returns(dbio(None))
              .once

            //once only for blocks-related updates
            (repo.writeUpdates _)
              .expects(List.empty[BalanceUpdatesRow], None)
              .returns(dbio(Option.empty[Int]))
              .once

          },
          accountsRepoExpectations = repo => {
            (repo.writeAccountsCheckpoint _)
              .expects(
                where(
                  (accountUpdates: List[(TezosTypes.BlockHash, Int, List[AccountId])]) =>
                    accountUpdates.size == generatedBlocks.size &&
                      accountUpdates.forall { case (_, _, ids) => ids.size == accountsPerBlock }
                )
              )
              .returns(dbio(Some(checkpointSize)))

          }
        )

        //when
        val action = sut.storeBlocksAndCheckpointAccounts(blockAccountsMap)

        //then
        testDb.run(action).futureValue shouldBe (Some(generatedBlocks.size), Some(checkpointSize))
      }

      "store all voting data for multiple blocks" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val (expectedProposals, expectedRolls, expectedBallots) = (3, 2, 4)

        val generatedBlock = generateSingleBlock(atLevel = 1, atTime = testReferenceDateTime)
        val proposalToStore = Voting.generateProposals(expectedProposals, forBlock = generatedBlock)
        val rollsToStore = (generatedBlock, Voting.generateBakersRolls(expectedRolls))
        val ballotsToStore = (generatedBlock, Voting.generateBallots(expectedBallots))

        //set expectations and build the datastore
        val sut = prepareDatastoreMockWithExpectactions(
          votingRepoExpectations = repo => {
            (repo.writeVotingProposals _)
              .expects(*)
              .returns(dbio(Some(expectedProposals)))
              .once

            (repo.writeVotingRolls _)
              .expects(*, generatedBlock)
              .returns(dbio(Some(expectedRolls)))
              .once

            (repo.writeVotingBallots _)
              .expects(*, generatedBlock)
              .returns(dbio(Some(expectedBallots)))
              .once
          }
        )

        //when
        val action = sut.storeBlocksVotingDetails(proposalToStore, rollsToStore :: Nil, ballotsToStore :: Nil)

        //then
        testDb.run(action).futureValue shouldBe (Some(expectedProposals + expectedRolls + expectedBallots))
      }

      "store accounts and checkpoint associated delegates" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val expectedAccountsCount = 3
        val expectedDelegateHashesCount = 3

        val block = generateSingleBlock(atLevel = 1, atTime = testReferenceDateTime)
        val accountsInfo = generateAccounts(expectedAccountsCount, block.data.hash, block.data.header.level)
        val delegateHashes = BlockTagged(
          block.data.hash,
          block.data.header.level,
          content = accountsInfo.content.values.flatMap(_.delegate.value).toList
        )

        //set expectations and build the datastore
        val sut = prepareDatastoreMockWithExpectactions(
          accountsRepoExpectations = repo => {
            (repo.updateAccounts _)
              .expects(*)
              .returns(dbio(expectedAccountsCount))
          },
          delegatesRepoExpectations = repo => {
            (repo.writeDelegatesCheckpoint _)
              .expects(*)
              .returns(dbio(Some(expectedDelegateHashesCount)))
          }
        )

        //when
        val action = sut.storeAccountsAndCheckpointDelegates(accountsInfo :: Nil, delegateHashes :: Nil)

        //then
        testDb.run(action).futureValue shouldBe (expectedAccountsCount, Some(expectedDelegateHashesCount))

      }

      "store delegates and copy the contracts" in {
        //given
        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val expectedDelegatesCounts = 3

        val block = generateSingleBlock(atLevel = 1, atTime = testReferenceDateTime)
        val accountHashes = generateHashes(howMany = expectedDelegatesCounts, ofLength = 10)
        val delegatesInfo = generateDelegates(accountHashes, block.data.hash, block.data.header.level)

        //set expectations and build the datastore
        val sut = prepareDatastoreMockWithExpectactions(
          delegatesRepoExpectations = repo => {
            (repo.updateDelegates _)
              .expects(*)
              .returns(dbio(expectedDelegatesCounts))

            val expectedContractIds = accountHashes.toSet.map(ContractId(_))

            (repo.copyAccountsAsDelegateContracts _)
              .expects(expectedContractIds)
              .returns(dbio(None))
              .once
          }
        )

        //when
        val action = sut.storeDelegatesAndCopyContracts(delegatesInfo :: Nil)

        //then
        testDb.run(action).futureValue shouldBe expectedDelegatesCounts
      }

    }

}
