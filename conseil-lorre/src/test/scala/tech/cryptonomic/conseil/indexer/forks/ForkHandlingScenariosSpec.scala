package tech.cryptonomic.conseil.indexer.forks

import cats.implicits._
import org.scalacheck._
import org.scalacheck.commands.Commands
import org.scalatest.matchers.should.Matchers
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatestplus.scalacheck.Checkers
import tech.cryptonomic.conseil.common.testkit.LoggingTestSupport

/** Runs random scenarios that simulate a fork on the chain.
  * We make use of stateful property testing as provided by the
  * scalacheck library to verify a stateful machine that represents
  * the indexer and the fork-handling logic.
  * Such test will simply execute a run of a single specification written
  * using the library tools.
  * For a more detailed explanation,
  * see: https://github.com/typelevel/scalacheck/blob/master/doc/UserGuide.md#stateful-testing
  */
class ForkHandlingScenariosSpec extends AnyFlatSpec with Checkers with Matchers with LoggingTestSupport {

  "The forks scenarios" should "simulate multiple fork changes" in {
    check(ForkHandlingScenariosSpec.property())
  }

}

/** Defines all the elements to randomly test that the system under test (SUT) behaves correctly
  * following a sequence of changes.
  * The spec defines how to create the SUT and how to apply commands that changes its state.
  * Since the SUT cannot be directly inspected, the spec will define a simplified version of
  * the expected state, which is also updated with each command, following some defined rules.
  * Matching the simplified state evolution with the result of applying each command to the
  * real SUT proves the correctness of the logic for the latter.
  *
  * In our case we define the following:
  * The SUT is a bare-bone implementation of the Lorre indexer along with the forking logic handler.
  * We chose to simplify the indexer to be able to test its evolution much more predictably and without
  * the need to keep track of integration details with external systems.
  * For the Tezos Indexer for example, it's currently unfeasible to define a simplified instance that
  * wouldn't require connecting to a real tezos node and a local db. This might change in the future with
  * partial rewrites and refactors to modularize the system more.
  *
  * The SimulationState keeps track of the state of the indexed chain, and its evolution tracks the expected one
  * for the real SUT.
  *
  * The Commands simulates how the chain on the remote node bakes new blocks along different forks each time,
  * with a random command being generated at each step.
  * Each command will then activate the fork-handler logic after each progress, and eventually update the SUT
  * state to simulate the indexer syncing along the new block sequence.
  *
  * In all these pieces we use a very simple interface to the local indexed state (i.e. the DB) and the remote node.
  * We only care to retrieve block ids (block hashes) for any given level  requested, and ignore any other detail related
  * to the blocks themselves, which are not relevant to the fork handling process.
  */
object ForkHandlingScenariosSpec extends Commands {

  import ForkHandlingScenariosFixtures._

  /** Tracks the ongoing system state as the test evolves.
    *
    * @param currentlyOnBranch the index identifying which forked branch the system is indexing
    * @param indexedChain the blocks (i.e. ids) indexed up till now
    */
  case class SimulationState(
      currentlyOnBranch: Int,
      indexedChain: PartialChain
  ) {

    /** the head level indexed so far */
    def currentHead = indexedChain.map(_._1).max
  }

  /* this is the real system we're testing, which has internal
   * state not visible to the outside, and changes at each step
   */
  type Sut = VirtualIndexer

  /* this is a simplified version of the system state that keeps
   * track of how it should evolve, and will be used to compare the
   * results of the Sut after each evolution step.
   * It act as an explicit and observable version of the hidden system
   * state machine.
   */
  type State = SimulationState

  /* Every new scenario will begin with the indexer and node having this number of blocks
   * Take care that this is NOT the initial head level: N blocks => head = N-1
   */
  val initialBlocks = 5

  /* We want to test scenarios where the system jumps between distinct forks.
   * The forks' blocks are completely random, but we want to start from a common baseline
   * to pinpoint a deterministic fork point, therefore we fix the first n levels to match
   * between all branches.
   * We choose the initial matching length such that initially the state will include at
   * least one level which differs between branches.
   */
  val availableBranches = makeCommonBaseLine(
    ofLength = initialBlocks - 1,
    fromBranches = randomBranches(howMany = 3)
  )

  /* We base the fork level on the initial number of blocks shared bewteen branches.
   * We know that branches are equal for the first initialBlocks - 1, therefore the fork level
   * is exactly the initial tip of the chain, corresponding to block numbers minus 1
   * (accounting for the fact that levels starts from 0)
   */
  val forkLevel = initialBlocks.toLong - 1

  /* a generator that randomly chooses between the existing generated branches,
   * favouring more the first one
   */
  def branchSelector = Gen.frequency(
    3 -> availableBranches(0),
    1 -> availableBranches(1),
    1 -> availableBranches(2)
  )

  /* This check allows avoid starting a new scenario in parallel with others, based on
   * the current running Suts and initial states used to generate them.
   * We should have no issue running multiple parallel scenarios concurrently.
   */
  override def canCreateNewSut(
      newState: State,
      initSuts: Traversable[State],
      runningSuts: Traversable[Sut]
  ): Boolean = true

  /* Builds a real system based on a simplified initial state,
   * We take the current sequence of blocks on the state and assume
   * that both the local indexer and the remote node start aligned on that
   */
  override def newSut(state: State): Sut = {
    val sut = new VirtualIndexer(state.indexedChain)
    println(
      s"Fork-scenario-${sut.uuid.toString().take(6)} >> start on branch number ${state.currentlyOnBranch}, current level is ${sut.indexHead}."
    )
    sut
  }

  /* Nothing to clean here, we depend on no external resources */
  override def destroySut(sut: Sut): Unit = ()

  /* The precondition for the initial state, when no commands yet have
   *  run. This is used by ScalaCheck when command sequences are shrinked
   *  and the first state might differ from what is returned from
   *  [[genInitialState]].
   * Otherwise we might have an inconsistent initial state for the
   * shrinked (i.e. simplified steps) scenario.
   */
  override def initialPreCondition(state: State): Boolean =
    state.indexedChain.size == initialBlocks

  /* Picks a starting branch to generate the state, which begins
   * with a fixed number of blocks
   */
  override def genInitialState: Gen[State] =
    branchSelector.map(branch =>
      SimulationState(
        currentlyOnBranch = branch.id,
        indexedChain = branch.blocks.take(initialBlocks)
      )
    )

  /* At each step a new command will be run on the system and will update the State.
   * The command knows how to check pre-conditions and* post-conditions to verify
   * the system acts as expected.
   * Each step increments the chain by a random number of levels in range [1..10],
   * with higher chances for bounds and customly chosen values (i.e. 3, 5)
   */
  override def genCommand(state: State): Gen[Command] =
    for {
      selectedBranch <- branchSelector
      levelIncrement <- Gen.chooseNum[Int](1, 10, 3, 5)
    } yield AdvanceOnBranch(selectedBranch, levelIncrement)

  /* Hereby we define the commands that will advance the test simulation
   * The test toolkit will run for a random number of rounds by applying a
   * random command.
   * Commands define how to execute an operation on the real system, update the
   * simplified test state, do pre and post conditions checks to test
   * the correctness of the system.
   * Our command simulates the block-chain evolution for a given number of levels,
   * based on a specific, randomly selected, branch.
   * This captures many different scenarios: same branch advance, jumping to a new branch and then
   * back to the original, jumping between three branches multiple times
   */
  case class AdvanceOnBranch(selectedBranch: ChainBranch, plusLevels: Int) extends SuccessCommand {

    /* The type corresponding to the result of the system operation being tested */
    type Result = TestEffect[Option[ForkAmender.Results]]

    /* Each step will need to
     * - pick the current level on the sut node
     * - update the node such that the resulting chain will be on the designated
     *   branch, and have moved on for the given number of levels
     * - run the fork handler and record the results
     * - rewrite the sut indexed data to simulate the indexer syncing to the new
     *   branch
     * - return the fork handling outcome
     */
    override def run(sut: Sut): Result = {
      val newHeadLevel = sut.indexHead + plusLevels
      println(
        s"Fork-scenario-${sut.uuid.toString().take(6)} >> proceeding on branch number ${selectedBranch.id}, advancing $plusLevels levels to $newHeadLevel."
      )
      val newNodeChain = selectedBranch.blocks.take(sut.node.chain.size + plusLevels)
      sut.node.chain = newNodeChain

      val outcome = sut.forkHandler.handleFork(sut.indexHead)
      sut.indexer.chain = newNodeChain
      outcome
    }

    /* We generate the next expected state */
    override def nextState(state: State): State = {
      /* Here we update the state internal indexer based on the new branch data up to
       * the level reached
       */
      val indexed = selectedBranch.blocks.take(state.indexedChain.size + plusLevels)

      state.copy(
        currentlyOnBranch = selectedBranch.id,
        indexedChain = indexed
      )

    }

    /* Here we would check if the state is valid for the command: we have no conditions to meet */
    override def preCondition(state: State): Boolean = true

    /* Here we check the results are consistent with the given initial state */
    override def postCondition(state: State, result: TestEffect[Option[(String, Int)]]): Prop =
      result match {
        case Left(error: Throwable) =>
          Prop.falsified :| s"Unexpected response from the fork handler: ${error.getMessage}"
        case Right(None) =>
          Prop(state.currentlyOnBranch == selectedBranch.id) :| "No amendment only if on the same branch"
        case Right(Some((forkId, amendedLevels))) if amendedLevels > 0 =>
          //compute expected level invalidations
          val invalidated = state.currentHead - forkLevel + 1
          Prop(state.currentHead >= forkLevel) :| "Fork level reached" &&
          Prop(state.currentlyOnBranch != selectedBranch.id) :| "Current branch should change" &&
          Prop(amendedLevels == invalidated.toInt) :| "Expected number of corrections"
        case _ =>
          Prop.falsified :| "Amendment result with no actual amendment is unexpected"
      }

  }

  /** Will make the first "length" blocks the same for all the branches */
  private def makeCommonBaseLine(ofLength: Int, fromBranches: List[ChainBranch]): List[ChainBranch] = {
    assume(fromBranches.nonEmpty, "The spec cannot align different branches' baseline if no branch is given")
    val baseline = fromBranches.head.blocks.take(ofLength)
    fromBranches.map(branch =>
      branch.copy(
        blocks = baseline #::: branch.blocks.drop(ofLength)
      )
    )
  }

}
