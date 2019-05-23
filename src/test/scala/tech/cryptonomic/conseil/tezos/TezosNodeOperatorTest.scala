package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import org.scalamock.scalatest.MockFactory
import org.scalatest.{FlatSpec, Matchers, Inspectors}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import cats.{Id, ~>}
import cats.data.Reader
import cats.effect.IO
import cats.arrow.FunctionK
import tech.cryptonomic.conseil.config.BatchFetchConfiguration

class TezosNodeOperatorTest
  extends FlatSpec
  with MockFactory
  with Matchers
  with LazyLogging
  with TezosNodeOperatorTestImplicits {

  // create a test instance
  val sut: NodeOperator = new NodeOperator(
    network = "network",
    batchConf = BatchFetchConfiguration(
      blockFetchConcurrencyLevel = 100,
      accountFetchConcurrencyLevel = 100,
      blockPageSize = 100
    )
  )

  //needed to instantiate fetchers from the generic test builder, providing
  //a transformation that actually does nothing real
  implicit val idK: Id ~> Id = FunctionK.id[Id]
  //needed to instantiate fetchers for IO from the generic test builder which
  // has no effect (cats.Id[_])
  implicit val ioK: Id ~> IO = FunctionK.lift[Id, IO](IO.pure)


  "getBlock" should "correctly fetch the genesis block" in withDummyFetchers[Id] {
    implicit extraBlockFetcher => implicit quorumFetcher => implicit proposalFetcher =>
      //given

      //test dataFetcher for blocks, returns a single response
      implicit val blockFetchProvider = Reader(
        (_: BlockHash) => testBlockFetcher[Id](_ => TezosResponseBuilder.genesisBlockResponse)
      )

      //when
      val block: TezosTypes.Block = sut.getBlock[Id](BlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe"))

      //then
      block.data.hash shouldBe BlockHash("BLockGenesisGenesisGenesisGenesisGenesisb83baZgbyZe")
      block.data.header.level shouldBe 0
    }

  "getLatestBlocks" should "correctly fetch all the blocks if no depth is passed-in" in withDummyFetchers[IO] {
    implicit extraBlockFetcher => implicit quorumFetcher => implicit proposalFetcher =>
      //given

      //needed to run the method under test which needs this to run in IO concurrently
      implicit val shift = IO.contextShift(scala.concurrent.ExecutionContext.global)
      //test dataFetcher for blocks, returning pre-made responses
      implicit val blockFetchProvider = Reader {
        (_: BlockHash) => testBlockFetcher[IO](
          //an array can be used as a function where the index is the input
          TezosResponseBuilder.precannedGetBlockQueryResponse(_)
        )
      }

      //when
      val (blocksResults, fetched): (sut.BlockFetchingResults[IO], Int) =
        sut.getLatestBlocks[IO]().unsafeRunSync

      //then
      fetched shouldBe 4

      val resultsList = blocksResults.compile.toList.unsafeRunSync
      resultsList should have size 4

      import Inspectors._

      forAll(resultsList) {
        case (Block(data, ops, votes), accounts) =>
        ops shouldBe empty
        if (TezosTypes.isGenesis(data)) votes shouldEqual CurrentVotes.empty
        else votes shouldEqual CurrentVotes.empty.copy(quorum = Some(TezosResponseBuilder.votesQuorum))
        accounts shouldBe empty
      }
      val blocks = resultsList.map(_._1.data)

      blocks.map(_.header.level) should contain theSameElementsInOrderAs (0 to 3).toList

  }

  "getLatestBlocks" should "correctly fetch latest blocks with a given depth" in withDummyFetchers[IO] {
    implicit extraBlockFetcher => implicit quorumFetcher => implicit proposalFetcher =>
      //given

      //needed to run the method under test which needs this to run in IO concurrently
      implicit val shift = IO.contextShift(scala.concurrent.ExecutionContext.global)
      //test dataFetcher for blocks, returning pre-made responses
      implicit val blockFetchProvider = Reader {
        (_: BlockHash) => testBlockFetcher[IO](
          //an array can be used as a function where the index is the input
          TezosResponseBuilder.precannedGetBlockQueryResponse(_)
        )
      }

      //when
      val (blocksResults, fetched): (sut.BlockFetchingResults[IO], Int) =
        sut.getLatestBlocks[IO](depth = Some(1)).unsafeRunSync

      //then
      fetched shouldBe 1

      val resultsList = blocksResults.compile.toList.unsafeRunSync
      resultsList should have size 1

      val (Block(data, ops, votes), accounts) :: Nil = resultsList

      data.header.level shouldBe 3

      ops shouldBe empty
      votes shouldEqual CurrentVotes.empty.copy(quorum = Some(TezosResponseBuilder.votesQuorum))
      accounts shouldBe empty

  }

  "getLatestBlocks" should "correctly fetch blocks starting from a given head" in withDummyFetchers[IO] {
    implicit extraBlockFetcher => implicit quorumFetcher => implicit proposalFetcher =>
      //given

      //coprresponds to the second element in the test precanned responses
      val referenceHash = BlockHash("BLwKksYwrxt39exDei7yi47h7aMcVY2kZMZhTwEEoSUwToQUiDV")

      //needed to run the method under test which needs this to run in IO concurrently
      implicit val shift = IO.contextShift(scala.concurrent.ExecutionContext.global)
      //test dataFetcher for blocks, returning pre-made responses
      implicit val blockFetchProvider = Reader {
        (hash: BlockHash) =>
          if (hash == referenceHash)
            testBlockFetcher[IO](
              //an array can be used as a function where the index is the input
              offset => TezosResponseBuilder.precannedGetBlockQueryResponse(offset + 1)
            )
          else //the expected reference hash is wrong, return something bad
            fail(s"the test fetcher was requested relative to head $hash, while expecting $referenceHash")
      }

      //when
      val (blocksResults, fetched): (sut.BlockFetchingResults[IO], Int) =
        sut.getLatestBlocks[IO](depth = Some(1), headHash = Some(referenceHash)).unsafeRunSync

      //then
      fetched shouldBe 1

      val resultsList = blocksResults.compile.toList.unsafeRunSync
      resultsList should have size 1

      val (Block(data, ops, votes), accounts) :: Nil = resultsList

      data.header.level shouldBe 2

      ops shouldBe empty
      votes shouldEqual CurrentVotes.empty.copy(quorum = Some(TezosResponseBuilder.votesQuorum))
      accounts shouldBe empty

  }

}
