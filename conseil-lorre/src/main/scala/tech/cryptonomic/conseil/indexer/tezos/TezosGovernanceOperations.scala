package tech.cryptonomic.conseil.indexer.tezos

import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import com.typesafe.scalalogging.LazyLogging
import slick.jdbc.PostgresProfile.api.Database
import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._

/** Process blocks and voting data to compute details for
  * the governance-related cycles
  */
object TezosGovernanceOperations extends LazyLogging {

  /** Collects all relevant data for the governance
    * voting process
    *
    * @param hash identifies a specific block
    * @param metadata the block metadata
    * @param proposalId a specific proposal under evaluation
    * @param allRolls ballot rolls up to this block for the proposal
    * @param singleBlockRolls ballot rolls included in this specific block
    * @param ballotsPerLevel how many ballots for the single block level
    * @param ballotsPerCycle how many baloots for a whole cycle
    */
  case class GovernanceAggregate(
      hash: BlockHash,
      metadata: BlockHeaderMetadata,
      proposalId: Option[ProtocolId],
      allRolls: VoteRollsCounts,
      rollsPerLevel: VoteRollsCounts,
      ballotsPerCycle: Option[Voting.BallotCounts],
      ballotsPerLevel: Option[Voting.BallotCounts]
  )

  //wrappings to make the distinction between two different counts
  private case class BallotsPerCycle(counts: Voting.BallotCounts) extends AnyVal
  private case class BallotsPerLevel(counts: Voting.BallotCounts) extends AnyVal

  /** Collects rolls of votes counted during ballots
    *
    * @param yay how many yays
    * @param nay how many nays
    * @param pass how many passes
    */
  case class VoteRollsCounts(yay: Int, nay: Int, pass: Int)

  /** We take basic governance data and the blocks storing that
    * with the goal of deriving more detailed aggregate data on
    * the voting process.
    * The computation needs to access the database to fetch essential
    * ballot data
    *
    * SAFETY NOTE: make sure that the blocks passed to this method have
    *              have a proper metadata value, different from the genesis one
    *
    * @param db needed to fetch previously stored voting data
    * @param proposalsBlocks blocks we want to have governance data for, associated to the protocol of the proposal
    * @param proposalsBallots votes cast on the blocks of interest,
    *                         the keys should be a superset of the `proposalsBlocks` keys,
    *                         so that the value is available for each of those blocks.
    * @param levelsRolls listings of baker rolls involved, indexed per level, we expect all levels for the
    *                         `proposalsBlocks` to be available, plus the level immediately before this batch.
    *                          ideally this would be a `Level => List[Voting.BakerRolls]` (i.e. a total function)
    * @param ec needed to compose concurrent operations
    * @return aggregated data
    */
  def aggregateData(db: Database)(
      proposalsBlocks: Map[Block, ProtocolId],
      proposalsBallots: Map[Block, List[Voting.Ballot]],
      levelsRolls: Map[Int, List[Voting.BakerRolls]]
  )(
      implicit ec: ExecutionContext
  ): Future[List[GovernanceAggregate]] = {

    //local functions, should simplify reading the calling code
    def countBallotsPerLevel(block: Block) =
      TezosDatabaseOperations.getBallotOperationsForLevel(block.data.header.level)

    // as stated, this is a runtime failure if the block contains the wrong metadata
    def countBallotsPerCycle(block: Block) = block.data.metadata match {
      case BlockHeaderMetadata(_, _, _, _, _, level) =>
        TezosDatabaseOperations.getBallotOperationsForCycle(level.cycle)
    }

    // as stated, this is a runtime failure if the block contains the wrong metadata
    def proposalOperationsHashes(block: Block) = block.data.metadata match {
      case BlockHeaderMetadata(_, _, _, _, _, level) =>
        TezosDatabaseOperations.getProposalOperationHashesByCycle(level.cycle)
    }

    logger.info("Searching for governance data in voting period...")

    //main algorithm
    val ballotCountsResult =
      Future
        .traverse(proposalsBlocks.keys) { block =>
          val cycleQuery = db.run(countBallotsPerCycle(block))
          val levelQuery = db.run(countBallotsPerLevel(block))
          (cycleQuery, levelQuery).mapN(
            (cycleCounts, levelCounts) => block -> ((BallotsPerCycle(cycleCounts), BallotsPerLevel(levelCounts)))
          )
        }
        .map(_.toMap)

    val proposalCountsResult = Future.traverse(proposalsBlocks.keys) { block =>
      db.run(proposalOperationsHashes(block).map(block -> _))
    }

    for {
      countsMap <- ballotCountsResult
      proposalCounts <- proposalCountsResult
    } yield
      fillAggregates(
        proposalsBlocks,
        levelsRolls,
        proposalsBallots,
        countsMap,
        proposalCounts.toMap
      )
  }

  /* Having all data ready, we can process per block,
   * extract the numbers returning a collector object.
   */
  private def fillAggregates(
      proposalsBlocks: Map[Block, ProtocolId],
      listingsPerLevel: Map[Int, List[Voting.BakerRolls]],
      ballots: Map[Block, List[Voting.Ballot]],
      ballotCounts: Map[Block, (BallotsPerCycle, BallotsPerLevel)],
      proposalProtocolCounts: Map[Block, Map[ProtocolId, Int]] //comes from individual operations on the block
  ): List[GovernanceAggregate] =
    proposalsBlocks.toList.flatMap {
      case (block, proposal) =>
        val listing = listingsPerLevel.get(block.data.header.level).getOrElse(List.empty)
        val prevListings = listingsPerLevel.get(block.data.header.level - 1).getOrElse(List.empty)
        val listingByBlock = listing.diff(prevListings)
        val ballot = ballots.getOrElse(block, List.empty)
        val ballotCountPerCycle = ballotCounts.get(block).map(_._1.counts)
        val ballotCountPerLevel = ballotCounts.get(block).map(_._2.counts)
        val proposalCounts = proposalProtocolCounts.getOrElse(block, Map.empty).toList

        val allRolls = countRolls(listing, ballot)
        val levelRolls = countRolls(listingByBlock, ballot)

        /* Here we collect a row for the block being considered
         * to get voting data for the periods with a specific proposal
         * under scrutinee: testing vote, testing, promotion.
         * In addition, we have many rows appended that comes
         * from operations during the proposal period, contained
         * in the block, which is now assumed to have no current proposal.
         * The previous considerations would make it impossible to have
         * the same proposal protocol and block hash for the two kind
         * of entries just described. We only have one or the other, by
         * construction, as the chain would reject any proposal with the
         * same protocol as the one under voting during the ballots phases.
         */
        block.data.metadata match {
          case metadata: BlockHeaderMetadata =>
            GovernanceAggregate(
              block.data.hash,
              metadata,
              Some(proposal),
              allRolls,
              levelRolls,
              ballotCountPerCycle,
              ballotCountPerLevel
            ) ::
                proposalCounts.map { //these come from all individual proposal operations during the proposal period
                  case (proposalProtocol, count) =>
                    GovernanceAggregate(
                      block.data.hash,
                      metadata.copy(voting_period_kind = VotingPeriod.proposal), //we know these are from operations
                      Some(proposalProtocol),
                      allRolls,
                      levelRolls,
                      Some(Voting.BallotCounts(count, 0, 0)),
                      ballotCountPerLevel
                    )
                }
          case GenesisMetadata =>
            //case handled to satisfy the compiler, should never run by design
            List.empty[GovernanceAggregate]
        }

    }

  /* Will scan the ballots to count all rolls associated with each vote outcome */
  private def countRolls(listings: List[Voting.BakerRolls], ballots: List[Voting.Ballot]): VoteRollsCounts = {
    val (yays, nays, passes) = ballots.foldLeft((0, 0, 0)) {
      case ((yays, nays, passes), votingBallot) =>
        val rolls = listings.find(_.pkh == votingBallot.pkh).fold(0)(_.rolls)
        votingBallot.ballot match {
          case Voting.Vote("yay") => (yays + rolls, nays, passes)
          case Voting.Vote("nay") => (yays, nays + rolls, passes)
          case Voting.Vote("pass") => (yays, nays, passes + rolls)
          case Voting.Vote(notSupported) =>
            logger.error("Not supported vote type {}", notSupported)
            (yays, nays, passes)
        }
    }
    VoteRollsCounts(yays, nays, passes)
  }
}
