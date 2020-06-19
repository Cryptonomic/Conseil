package tech.cryptonomic.conseil.common.tezos

import tech.cryptonomic.conseil.common.tezos.TezosTypes._

/** Provides utilities for voting-related operations */
object VotingOperations {

  /** The periods where a specific proposal is considered */
  val activePeriods = VotingPeriod.values.filterNot(_ == VotingPeriod.proposal)

  /** The periods where a ballot is under way */
  val ballotPeriods = VotingPeriod.ValueSet(VotingPeriod.testing_vote, VotingPeriod.promotion_vote)

  /* Used to classify blocks by voting periods */
  def votingPeriodIn(accept: VotingPeriod.ValueSet)(block: Block): Boolean =
    votingPeriodIn(accept, block.data.metadata)

  /* Used to select blocks with a specific voting period */
  def votingPeriodIs(accept: VotingPeriod.Kind)(block: Block): Boolean =
    votingPeriodIn(VotingPeriod.ValueSet(accept), block.data.metadata)

  /* Used to classify blocks by voting periods via metadata */
  private def votingPeriodIn(accept: VotingPeriod.ValueSet, metadata: BlockMetadata): Boolean = metadata match {
    case BlockHeaderMetadata(_, _, _, _, voting_period_kind, _) =>
      accept(voting_period_kind)
    case GenesisMetadata =>
      false
  }

}
