package tech.cryptonomic.conseil.application

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.util.IOUtils.{lift, IOLogging}
import cats.syntax.all._
import cats.effect.{ContextShift, IO}
import tech.cryptonomic.conseil.tezos.{TezosNodeOperator, TezosTypes, TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountId, Block, BlockHash, BlockTagged}
import scala.concurrent.{ExecutionContext, Future}
import slick.jdbc.PostgresProfile.api._
import tech.cryptonomic.conseil.tezos.TezosTypes.BlockData
import tech.cryptonomic.conseil.config.{Custom, Depth, Everything, Newest}

object BlocksOperations {

  /** Something went wrong during handling of Blocks or related sub-data */
  case class BlocksProcessingFailed(message: String, cause: Throwable) extends java.lang.RuntimeException
}

class BlocksOperations(protected val nodeOperator: TezosNodeOperator, db: Database)(
    implicit
    ec: ExecutionContext,
    cs: ContextShift[IO]
) extends LorreProcessingUtils
    with LazyLogging
    with IOLogging {

  import BlocksOperations.BlocksProcessingFailed

  /* Write the blocks to the db */
  def storeBlocks(blockResults: nodeOperator.BlockFetchingResults): IO[Int] = {
    val blocks = blockResults.map { case (block, _) => block }
    lift(db.run(TezosDb.writeBlocks(blocks)))
      .map(_ => blocks.size)
      .flatTap(LorreBlocksProcessingLog.blocksStored)
      .onError(LorreBlocksProcessingLog.failedToStoreBlocks)

  }

  /* Based on input configuration, lazily fetch the required pages of blocks from the node */
  def fetchBlockPagesToSync(
      depth: Depth,
      headHash: Option[BlockHash]
  )(currentHead: BlockData, maxLevelStoredOnConseil: Int): IO[nodeOperator.PaginatedBlocksResults] = depth match {
    case Newest => nodeOperator.getBlocksNotInDatabase(currentHead, maxLevelStoredOnConseil).pure[IO]
    case Everything => lift(nodeOperator.getLatestBlocks(head = Left(currentHead)))
    case Custom(n) => lift(nodeOperator.getLatestBlocks(head = headHash.toRight(currentHead), depth = Some(n)))
  }
  /* Fetches voting data for the blocks and stores any relevant
   * result into the appropriate database table
   */
  def fetchAndStoreVotesForBlocks(blocks: List[Block]): IO[Option[Int]] = {
    import cats.instances.list._
    import slickeffect.implicits._

    lift(nodeOperator.getVotingDetails(blocks)).flatMap {
      case (_, bakersBlock, ballotsBlock) =>
        //this is a nested list, each block with many baker rolls
        val writeBakers = TezosDb.writeVotingRolls(bakersBlock)

        val updateAccountsHistory = bakersBlock.traverse {
          case (block, bakersRolls) =>
            TezosDb.updateAccountsHistoryWithBakers(bakersRolls, block)
        }

        val updateAccounts = bakersBlock.traverse {
          case (block, bakersRolls) =>
            TezosDb.updateAccountsWithBakers(bakersRolls, block)
        }

        val combinedVoteWrites = for {
          bakersWritten <- writeBakers
          accountsHistoryUpdated <- updateAccountsHistory
          accountsUpdated <- updateAccounts
        } yield
          bakersWritten
            .map(_ + ballotsBlock.size + accountsHistoryUpdated.size + accountsUpdated.size)

        lift(db.run(combinedVoteWrites.transactionally))
    }.flatTap(LorreBlocksProcessingLog.votesStored)
      .onError(LorreBlocksProcessingLog.failedToStoreVotes)
  }

  def fetchAndStoreBakingAndEndorsingRights(blockHashes: List[BlockHash]): IO[Option[Int]] = {
    import slickeffect.implicits._
    import cats.implicits._
    (lift(nodeOperator.getBatchBakingRights(blockHashes)), lift(nodeOperator.getBatchEndorsingRights(blockHashes))).mapN {
      case (bakes, endorses) =>
        (TezosDb.writeBakingRights(bakes), TezosDb.writeEndorsingRights(endorses))
          .mapN(_ |+| _) //generic sum works over Option[Int], taking care of the optionality
    }.flatMap(dbOp => lift(db.run(dbOp.transactionally)))
      .flatTap(LorreBlocksProcessingLog.blockRightsStored)
      .onError(LorreBlocksProcessingLog.failedToStoreRights)
  }

  /* adapts the page processing to fetched blocks */
  def forEachBlockPage(
      pages: Iterator[Future[nodeOperator.BlockFetchingResults]],
      notifyProgress: Int => IO[Unit]
  )(handlePage: nodeOperator.BlockFetchingResults => IO[Int]) = {
    import cats.instances.int._

    val fetchErrorAdapter = (source: Throwable) =>
      BlocksProcessingFailed("Could not fetch blocks from the client", source)

    streamPages(pages, fetchErrorAdapter)
      .evalMap(handlePage) //processes each page
      .scanMonoid //accumulates results, e.g. blocks done, delegate keys in accounts
      .filter(_ != 0) //no use to print the first empty result
      .evalTap(notifyProgress)
      .compile
      .last
  }

  /** Re-organize the data, wrapping the account ids into a block tag wrapping */
  def extractAccountRefs(blockResults: nodeOperator.BlockFetchingResults): List[BlockTagged[List[AccountId]]] = {
    import TezosTypes.Syntax._
    blockResults.map {
      case (block, accounts) => accounts.taggedWithBlock(block.data)
    }
  }
}

object LorreBlocksProcessingLog extends LazyLogging with IOLogging {

  val blocksStored = (count: Int) =>
    liftLog(
      _.info("Wrote {} blocks to the database", count)
    )

  val failedToStoreBlocks: PartialFunction[Throwable, IO[Unit]] = {
    case t =>
      liftLog(_.error("Could not write blocks to the database", t))
  }

  val votesStored = (count: Option[Int]) =>
    liftLog(
      _.info("Wrote {} block voting data records to the database", count.getOrElse("the"))
    )

  val failedToStoreVotes: PartialFunction[Throwable, IO[Unit]] = {
    case t =>
      liftLog(_.error("Could not write voting data to the database", t))
  }

  val blockRightsStored = (count: Option[Int]) =>
    liftLog(
      _.info("Wrote {} baking and endorsing rights to the database", count.getOrElse("the"))
    )

  val failedToStoreRights: PartialFunction[Throwable, IO[Unit]] = {
    case t =>
      liftLog(_.error("Could not write baking and endorsing rights to the database", t))
  }

}
