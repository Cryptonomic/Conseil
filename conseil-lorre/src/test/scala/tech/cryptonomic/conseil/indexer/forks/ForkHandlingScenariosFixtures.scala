package tech.cryptonomic.conseil.indexer.forks

import scala.collection.immutable.NumericRange
import java.time.Instant
import java.{util => ju}
import cats.implicits._
import org.scalacheck.Gen
import tech.cryptonomic.conseil.indexer.tezos.TezosDataGenerationKit
import ForkDetector.SearchBlockId

/** Provides all individual building blocks to define a specification
  * for the fork handling scenarios.
  * This includes type aliases to simplify the test definitions, instances of
  * test-doubles for sub-components of the fork handler, a simplified version of
  * the indexer itself to evaluate, and simple datatypes that simulates forks and
  * blockchain data.
  */
object ForkHandlingScenariosFixtures {

  type TestEffect[T] = Either[Throwable, T]
  /* Here we use Strings as block identifiers */
  type TestBlockId = String
  type ForkId = String
  /* A single implementation works for both locally indexed data and remote node data,
   * yet we use aliases to make the intent of methods
   * and dependent classes explicit
   */
  type MockIndexer = MockSearch
  type MockNode = MockSearch
  /* a piece of the whole chain, in its raw core form */
  type PartialChain = Seq[(Long, TestBlockId)]

  /* This implementation does the most straightforward logic
   * to handle forks by re-using the individual components
   */
  case class BasicForkHandler(
      nodeSearch: MockNode,
      indexerSearch: MockIndexer,
      amender: MockAmender
  ) extends ForkHandler[TestEffect, TestBlockId](indexerSearch, amender) {

    /* regular detector implementation */
    override protected val detector = new ForkDetector(indexerSearch, nodeSearch)

  }

  /** A basic chain search that tracks a sequence of ids for each level.
    * This same mock can be used to simulate both a local Indexer and a remote Node
    * The sequence of block-ids, can be modified from the outside to simulate any
    * progress during test scenarios.
    */
  class MockSearch(var chain: PartialChain) extends SearchBlockId[TestEffect, TestBlockId] {

    override def searchForLevel(level: Long): Either[Throwable, TestBlockId] =
      chain.collectFirst { case (`level`, block) =>
        block
      }.toRight(new NoSuchElementException(s"No element on the chain for level $level"))

  }

  /** This mock will keep track locally of invalidations/amendments.
    * We keep the values inspectable for the tests.
    * We use the same effect type as [[MockSearch]] to keep them compatible.
    */
  class MockAmender() extends ForkAmender[TestEffect, TestBlockId] {

    /** Tracks the invalidated levels */
    var invalidated: Map[ForkId, NumericRange.Inclusive[Long]] = Map.empty

    override def amendFork(
        forkLevel: Long,
        forkedBlockId: TestBlockId,
        indexedHeadLevel: Long,
        detectionTime: Instant
    ): Either[Throwable, ForkAmender.Results] = {
      //generate the amendment data
      val amendedLevels = Range.Long.inclusive(forkLevel, indexedHeadLevel, step = 1L)
      val forkId = ju.UUID.randomUUID().toString

      //update the invalidation map
      invalidated += forkId -> amendedLevels

      //expected return values
      (forkId, amendedLevels.size).asRight
    }

  }

  /** This is the bare-bone system we're going to test.
    * The internal Fork handler implementation is faithful
    * to a real one, and is essentially his behaviour that we're
    * interested in verifying.
    * The other data structures are exposed to allow the test-harness
    * to simulate the evolution of the system during the test.
    */
  class VirtualIndexer(initialChain: PartialChain) {

    /** This id allows to track each individual indexer evolution during
      * the test simulations
      */
    val uuid = ju.UUID.randomUUID()

    /** represents the interface to the local data */
    val indexer: MockIndexer = new MockSearch(initialChain)

    /** represents the interface to the remote node */
    val node: MockNode = new MockSearch(initialChain)

    /** a simple fork correction logic that will count the invalidated levels */
    val amender = new MockAmender()

    /** run the fork handling logic using the locally defined modules */
    val forkHandler = new BasicForkHandler(node, indexer, amender)

    /** the current head level of the indexer */
    def indexHead: Long = indexer.chain.map(_._1).max

    /** tracks all the invalidated levels with each detected fork */
    def forks: Map[ForkId, NumericRange.Inclusive[Long]] = amender.invalidated
  }

  /** Represents one of the possible forks, identified by a numeric index.
    * The content will need to compute block ids with their level on demand.
    *
    * @param id an index (1..n) identifying each fork
    * @param blocks the block ids and levels, lazily computed
    */
  case class ChainBranch(id: Int, blocks: Stream[(Long, TestBlockId)])

  /** will randomly create a fork chain indexed by i */
  private def branchGen(id: Int) = {
    /* we zip increasing leves with infinite hashes, where perturbation on the seed
     * should reduce the chance of conflicting sequences of hashes when we're
     * generating different forks. We're making efforts such that differently indexed
     * forks have different data, to use this as an assumption when running the tests.
     */
    val levels = Stream.from(0).map(_.toLong)
    for {
      hashes <- Gen.infiniteStream(TezosDataGenerationKit.arbitraryBase58CheckString.withPerturb(_.reseed(id)))
    } yield ChainBranch(id, levels zip hashes)
  }

  /** randomly creates a number of distinct simulated chain branches */
  def randomBranches(howMany: Int): List[ChainBranch] =
    (1 to howMany).map(branchGen).map(_.sample.get).toList

}
