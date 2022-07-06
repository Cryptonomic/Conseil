package tech.cryptonomic.conseil.indexer.tezos.forks

import tech.cryptonomic.conseil.common.tezos.TezosTypes.{BlockData, BlockHeader, TezosBlockHash}
import tech.cryptonomic.conseil.indexer.forks.ForkDetector
import tech.cryptonomic.conseil.indexer.tezos.TezosDataGenerationKit
import org.scalatest.propspec.AnyPropSpec
import org.scalatest.matchers.should.Matchers
import org.scalatestplus.scalacheck.ScalaCheckDrivenPropertyChecks
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.ScalacheckShapeless._
import monocle.macros.GenLens
import ConsistentForkDetector.ConsistentDetectionRepeatingFailure
import tech.cryptonomic.conseil.common.testkit.LoggingTestSupport

/** Here we verify the properties of the [[ConsistentForkDetector]] implementation.
  * This spec works essentially in the same way as
  * the [[tech.cryptonomic.conseil.indexer.forks.ForkDetectorSpec]]
  */
class ConsistentForkDetectorSpec
    extends AnyPropSpec
    with ScalaCheckDrivenPropertyChecks
    with Matchers
    with LoggingTestSupport {

  import Fixtures._
  import TezosDataGenerationKit.DomainModelGeneration._

  /* Each property call will generate a hundred test values and verify the assertion on each of them.
   *
   * We can establish how many discarded values (by a `whenever(predicate)` block) are
   * acceptable, as a multiplier of the overall successes.
   * E.g. forAll(maxDiscardedFactor(3.0)) means that for 100 successful runs, no more than 300
   * test values should've been discarded by using `whenever`.
   */

  property("The fork-detection algorithm should identify the forking point in arbitrary ranges") {
    forAll { (range: ForkDetectionRange) =>
      val sut = detector(forkingPoint = range.fork, withDataSearch = consistentBlockSearch)

      sut.searchForkLevel(range.low, range.high) shouldBe range.fork
    }
  }

  property("The fork-detection algorithm should fail to identify the forking point when the consistency check fails") {
    forAll { (range: ForkDetectionRange) =>
      val sut = detector(forkingPoint = range.fork, withDataSearch = _ => randomBlockSearch)

      val failure = the[ConsistentDetectionRepeatingFailure] thrownBy {
        sut.searchForkLevel(range.low, range.high)
      }

      failure.attempts shouldBe 2
    }
  }

  property("The fork detection level check should return consistent results below the forking point") {
    forAll { (range: ForkDetectionRange, level: PreFork) =>
      whenever(level.toLong < range.fork) {
        val sut = detector(forkingPoint = range.fork, withDataSearch = consistentBlockSearch)
        sut.checkOnLevel(level.toLong) shouldBe ForkDetector.SameId
      }
    }
  }

  property("The fork detection level check should return consistent results above the forking point") {
    forAll { (range: ForkDetectionRange, level: PostFork) =>
      whenever(level.toLong >= range.fork) {
        val sut = detector(forkingPoint = range.fork, withDataSearch = consistentBlockSearch)
        sut.checkOnLevel(level.toLong) shouldBe ForkDetector.ForkedId
      }
    }
  }

  /** Defines all necessary data, generators, dummy objects to run the test */
  private object Fixtures {
    /* we only use two possible block id values, since we only need to
     * test if they match or not for a single level, but care not what are
     * the actual values.
     * The dummy services that return block-ids per level, will be defined
     * as to provide the same block id value for inputs below a specific level
     * and different ids for inputs above such level, where such level is when
     * the fork is supposed to have happened.
     */
    val blockA: TezosBlockHash = TezosBlockHash("A")
    val blockB: TezosBlockHash = TezosBlockHash("B")

    private val fixBlockPredecessor: BlockData => BlockData =
      GenLens[BlockData](_.header) //focus on the data header
        .composeLens(GenLens[BlockHeader](_.predecessor)) //focus on the header predecessor
        .asSetter //use as a setter
        .set(blockA) //which sets the specific value

    /* Our test detector will always return the same id for the indexer.
     * In the case of the node search, while up-to the given [[forkingPoint]]
     * we'll receive the same indexer id as the node one, and the other
     * one above the fork point.
     *
     * The consistency check behaviour is defined by the second argument.
     * It allows to build a different mock checker based on the fork level.
     * Available choices are provided by the methods
     * `consistentBlockSearch` and `randomBlockSearch`
     *
     * The effect wrapping the results used for the test is [[cats.Id]],
     * which is nothing but an alias for an empty wrapper, which corresponds
     * directly to the underlying content type,
     *  e.g. a cats.Id[Int] = Int
     */
    def detector(forkingPoint: Long, withDataSearch: Long => SearchBlockData[cats.Id]) =
      new ConsistentForkDetector[cats.Id](
        indexerSearch = _ => blockA,
        nodeSearch = lvl => if (lvl >= forkingPoint) blockB else blockA,
        nodeData = withDataSearch(forkingPoint),
        maxAttempts = 2
      )

    /* When check for the passed fork point it will return the test data
     * with a predecessor fixed to the `blockA`, the same returned by any id search
     * for levels before the fork.
     * Therefore the consistency check with this instance should always succeed.
     */
    def consistentBlockSearch(forkingPoint: Long): SearchBlockData[cats.Id] = {
      case `forkingPoint` => fixBlockPredecessor(arbitrary[BlockData].sample.get)
      case _ => arbitrary[BlockData].suchThat(_.header.predecessor != blockA).sample.get
    }

    /* This will return a random block independently of the argument, but taking care
     * that the predecessor won't ever match the block ids before the fork.
     * We use this to test the cases where the consistency-check always fails.
     */
    val randomBlockSearch: SearchBlockData[cats.Id] = { case _ =>
      arbitrary[BlockData].suchThat(_.header.predecessor != blockA).sample.get
    }

    /** we want to test by generating random triplets that
      * define the lower/upper bounds of a range of levels and
      * the internal forking level in-between.
      * This class captures our intent
      */
    case class ForkDetectionRange(
        low: Long,
        fork: Long,
        high: Long,
        preFork: Long = 0,
        postFork: Long = 1000000
    )

    /** this arbitrary instance will generate ranges of positive numbers
      * up to a cap (1e6 for this test) with a value in between, representing
      * the fork point
      * The property-check library will use this instance to generate multiple
      * values of ForkDetectionRange, with the rules hereto outlined, and
      * run the test assertions for each of those generated ranges.
      */
    implicit val rangeGenerator: Arbitrary[ForkDetectionRange] = Arbitrary(
      for {
        low <- Gen.choose(0L, 1000) //arbitrary positive number with a cap
        high <- Gen.choose(low + 2, low + 1000000) //arbitrary number in a range
        fork <- Gen.choose(low + 1, high - 1) //arbitrary number in-between
      } yield ForkDetectionRange(low, fork, high)
    )

    /* A typed wrapper to identify a level which is lower than a fork */
    case class PreFork(toLong: Long)

    /* A typed wrapper to identify a level which is higher than a fork */
    case class PostFork(toLong: Long)

    /** will generate a detection range and a random value that is
      * guaranteed to be below the fork level generated
      */
    implicit val rangeWithPreForkLevels: Arbitrary[(ForkDetectionRange, PreFork)] = Arbitrary(
      for {
        range <- arbitrary[ForkDetectionRange]
        level <- Gen.choose(range.low, range.fork - 1)
      } yield (range, PreFork(level))
    )

    /** will generate a detection range and a random value that is
      * guaranteed to be above the fork level generated
      */
    implicit val rangeWithPostForkLevels: Arbitrary[(ForkDetectionRange, PostFork)] = Arbitrary(
      for {
        range <- arbitrary[ForkDetectionRange]
        level <- Gen.choose(range.fork, range.high)
      } yield (range, PostFork(level))
    )

  }
}
