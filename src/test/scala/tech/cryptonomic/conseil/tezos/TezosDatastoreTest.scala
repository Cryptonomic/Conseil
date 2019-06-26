package tech.cryptonomic.conseil.tezos

import org.scalatest.{Matchers, WordSpec}
import org.scalatest.concurrent.ScalaFutures
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
import tech.cryptonomic.conseil.tezos.TezosTypes.AccountId

class TezosDatastoreTest extends WordSpec with MockFactory with TezosDataGeneration with ScalaFutures with Matchers {

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

      "store blocks only" in {
        //given
        val blocksRepo = mock[BlocksRepository[DBIO]]
        val accountsRepo = mock[AccountsRepository[DBIO]]
        val opsRepo = mock[OperationsRepository[DBIO, OperationGroupsRow, OperationsRow, Int, BalanceUpdatesRow]]
        val votesRepo = mock[VotingRepository[DBIO]]
        val delRepo = mock[DelegatesRepository[DBIO]]

        implicit val randomSeed = RandomSeed(testReferenceTimestamp.getTime)

        val maxLevel = 5

        val generatedBlocks = generateBlocks(maxLevel, testReferenceDateTime)

        val blockAccountsMap = generatedBlocks.map { block =>
          block -> List.empty[AccountId]
        }.toMap

        //set expectations
        (blocksRepo.writeBlocks _)
          .expects(blockAccountsMap.keys.toList)
          .returns(dbio(Some(generatedBlocks.size)))
          .once

        (opsRepo.writeOperationsGroups _)
          .expects(
            where(
              (groups: List[OperationGroupsRow]) => groups.isEmpty
            )
          )
          .returns(dbio(None))
          .once

        //once only for blocks-related updates
        (opsRepo.writeUpdates _)
          .expects(List.empty[BalanceUpdatesRow], None)
          .returns(dbio(Option.empty[Int]))
          .once

        (accountsRepo.writeAccountsCheckpoint _)
          .expects(
            where(
              (accountUpdates: List[(TezosTypes.BlockHash, Int, List[AccountId])]) =>
                accountUpdates.size == generatedBlocks.size &&
                  accountUpdates.forall { case (_, _, accounts) => accounts.isEmpty }
            )
          )
          .returns(dbio(Some(0)))

        //build the datastore
        val sut = new TezosDatastore()(blocksRepo, accountsRepo, opsRepo, votesRepo, delRepo)

        //when
        val action = sut.storeBlocksAndCheckpointAccounts(blockAccountsMap)

        //then
        testDb.run(action).futureValue shouldBe (Some(generatedBlocks.size), Some(0))
      }

      "store blocks with operations data" in {
        //given
        val blocksRepo = mock[BlocksRepository[DBIO]]
        val accountsRepo = mock[AccountsRepository[DBIO]]
        val opsRepo = mock[OperationsRepository[DBIO, OperationGroupsRow, OperationsRow, Int, BalanceUpdatesRow]]
        val votesRepo = mock[VotingRepository[DBIO]]
        val delRepo = mock[DelegatesRepository[DBIO]]

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

        //set expectations
        var fakeOperationId = 0

        (blocksRepo.writeBlocks _)
          .expects(*)
          .returns(dbio(Some(generatedBlocks.size)))
          .once

        (opsRepo.writeOperationsGroups _)
          .expects(*)
          .returns(dbio(None))
          .once

        (opsRepo.writeOperationWithNewId _)
          .expects(*)
          .onCall((_: OperationsRow) => {
            fakeOperationId += 1
            dbio(fakeOperationId)
          })
          .repeated(totalOperationsCount)
          .times

        //once for blocks-related updates and the rest for operations-related updates
        (opsRepo.writeUpdates _)
          .expects(*, *)
          .returns(dbio(Option.empty[Int]))
          .repeated(totalOperationsCount + 1)
          .times

        (accountsRepo.writeAccountsCheckpoint _)
          .expects(
            where(
              (accountUpdates: List[(TezosTypes.BlockHash, Int, List[AccountId])]) =>
                accountUpdates.size == generatedBlocks.size &&
                  accountUpdates.forall { case (_, _, accounts) => accounts.isEmpty }
            )
          )
          .returns(dbio(Some(0)))

        //build the datastore
        val sut = new TezosDatastore()(blocksRepo, accountsRepo, opsRepo, votesRepo, delRepo)

        //when
        val action = sut.storeBlocksAndCheckpointAccounts(blockAccountsMap)

        //then
        testDb.run(action).futureValue shouldBe (Some(generatedBlocks.size), Some(0))
      }

      "store blocks with account ids to checkpoint" in {
        //given
        val blocksRepo = mock[BlocksRepository[DBIO]]
        val accountsRepo = mock[AccountsRepository[DBIO]]
        val opsRepo = mock[OperationsRepository[DBIO, OperationGroupsRow, OperationsRow, Int, BalanceUpdatesRow]]
        val votesRepo = mock[VotingRepository[DBIO]]
        val delRepo = mock[DelegatesRepository[DBIO]]

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

        (blocksRepo.writeBlocks _)
          .expects(*)
          .returns(dbio(Some(generatedBlocks.size)))
          .once

        (opsRepo.writeOperationsGroups _)
          .expects(
            where(
              (groups: List[OperationGroupsRow]) => groups.isEmpty
            )
          )
          .returns(dbio(None))
          .once

        //once only for blocks-related updates
        (opsRepo.writeUpdates _)
          .expects(List.empty[BalanceUpdatesRow], None)
          .returns(dbio(Option.empty[Int]))
          .once

        (accountsRepo.writeAccountsCheckpoint _)
          .expects(
            where(
              (accountUpdates: List[(TezosTypes.BlockHash, Int, List[AccountId])]) =>
                accountUpdates.size == generatedBlocks.size &&
                  accountUpdates.forall { case (_, _, ids) => ids.size == accountsPerBlock }
            )
          )
          .returns(dbio(Some(checkpointSize)))

        //build the datastore
        val sut = new TezosDatastore()(blocksRepo, accountsRepo, opsRepo, votesRepo, delRepo)

        //when
        val action = sut.storeBlocksAndCheckpointAccounts(blockAccountsMap)

        //then
        testDb.run(action).futureValue shouldBe (Some(generatedBlocks.size), Some(checkpointSize))
      }
    }

}
