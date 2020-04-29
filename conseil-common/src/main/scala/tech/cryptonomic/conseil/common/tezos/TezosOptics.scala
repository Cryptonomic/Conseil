package tech.cryptonomic.conseil.common.tezos

import java.time.ZonedDateTime

import TezosTypes._
import TezosTypes.OperationMetadata.BalanceUpdate
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Scripted.Contracts
import monocle.std.option._
import cats.implicits._

/** Provides [[http://julien-truffaut.github.io/Monocle/ monocle]] lenses and additional "optics"
  * for most common access and modifcation patterns for Tezos type hierarchies and ADTs
  */
object TezosOptics {
  /* A brief summary for anyone unfamiliar with optics/lenses concept
   *
   * The simplest optic is a Lens. The raison-d'etre of this is better handling of deeply nested
   * fields in case classes, whenever you need to read and update a value to get a copy of the
   * original object. This usually translates to a galore of a.copy(b = a.b.copy(c = a.b.c.copy(...)))
   * Well you can imagine the fun!
   * A lens is a function container that wraps both a getter and a setter for an object field
   * Such lenses can be then composed with each other, using guess what?... a "composeLens" function
   * Hence you now can have
   *
   *   val a: A = ...
   *   val c: C = ...
   *   val acLens = abLens composeLens bcLens
   * which allows you to
   *
   *   val nestedC: C = acLens.get(a)
   *   val updatedWithC: A = acLens.set(a)(c)
   *
   * Now it seems somebody lost control using this stuff and thus you now have a whole family of
   * things like lenses but for complex fields and other kind of "nesting", e.g.
   *
   * - Optional: a getter/setter for fields whose value might not be there
   * - Prism: a selector within a sealed trait hierarchy (a.k.a. a Sum type)
   * - Traversal: a getter/setter that operates on many value in the data structure (e.g. for collection-like things)
   * - Iso: a bidirectional lossless conversion between two types
   *
   */

  import monocle.{Lens, Optional, Traversal}
  import monocle.macros.{GenLens, GenPrism}

  //optional lenses into the Either branches
  def left[A, B] = GenPrism[Either[A, B], Left[A, B]] composeLens GenLens[Left[A, B]](_.value)
  def right[A, B] = GenPrism[Either[A, B], Right[A, B]] composeLens GenLens[Right[A, B]](_.value)

  /** Many useful optics for blocks and their inner structure */
  object Blocks {

    //basic building blocks to reach into the block's structure
    val blockData = GenLens[Block](_.data)
    val dataHeader = GenLens[BlockData](_.header)
    val metadata = GenLens[BlockData](_.metadata)
    val headerTimestamp = GenLens[BlockHeader](_.timestamp)
    val metadataType = GenPrism[BlockMetadata, BlockHeaderMetadata]
    val metadataBalances = GenLens[BlockHeaderMetadata](_.balance_updates)
    val blockOperationsGroup =
      GenLens[Block](_.operationGroups) composeTraversal Traversal.fromTraverse[List, OperationsGroup]
    val groupOperations =
      GenLens[OperationsGroup](_.contents) composeTraversal Traversal.fromTraverse[List, Operation]

    /** An optional lens allowing to reach into balances for blocks' metadata */
    val blockBalances: Optional[Block, List[BalanceUpdate]] =
      blockData composeLens metadata composePrism metadataType composeLens metadataBalances

    /** a function to set the header timestamp for a block, returning the modified block */
    val setTimestamp: ZonedDateTime => Block => Block = blockData composeLens dataHeader composeLens headerTimestamp set _

    /** a function to set metadata balance updates in a block, returning the modified block */
    val setBalances: List[BalanceUpdate] => Block => Block = blockBalances set _

    /** functions to operate on all big maps copy diffs within a block */
    val readBigMapDiffCopy = blockOperationsGroup composeTraversal groupOperations composeTraversal Operations.operationBigMapDiffCopy

    /** functions to operate on all big maps remove diffs within a block */
    val readBigMapDiffRemove = blockOperationsGroup composeTraversal groupOperations composeTraversal Operations.operationBigMapDiffRemove

    /**  Utility extractor that collects, for a block, both operations and internal operations results, grouped
      * in a form more amenable to processing
      * @param block the block to inspect
      * @return a Map holding for each group both external and internal operations' results
      */
    def extractOperationsAlongWithInternalResults(
        block: Block
    ): Map[OperationsGroup, (List[Operation], List[InternalOperationResults.InternalOperationResult])] =
      block.operationGroups.map { group =>
        val internal = group.contents.flatMap { op =>
          op match {
            case r: Reveal => r.metadata.internal_operation_results.toList.flatten
            case t: Transaction => t.metadata.internal_operation_results.toList.flatten
            case o: Origination => o.metadata.internal_operation_results.toList.flatten
            case d: Delegation => d.metadata.internal_operation_results.toList.flatten
            case _ => List.empty
          }
        }
        group -> (group.contents, internal)
      }.toMap

    //Note, cycle 0 starts at the level 2 block
    def extractCycle(block: Block): Option[Int] =
      discardGenesis
        .lift(block.data.metadata) //this returns an Option[BlockHeaderMetadata]
        .map(_.level.cycle) //this is Option[Int]

    //Note, cycle 0 starts at the level 2 block
    def extractCyclePosition(block: BlockMetadata): Option[Int] =
      discardGenesis
        .lift(block) //this returns an Option[BlockHeaderMetadata]
        .map(_.level.cycle_position) //this is Option[Int]

    //Note, cycle 0 starts at the level 2 block
    def extractPeriod(block: BlockMetadata): Option[Int] =
      discardGenesis
        .lift(block)
        .map(_.level.voting_period)
  }

  object Operations {
    import tech.cryptonomic.conseil.common.tezos.TezosTypes.InternalOperationResults.{
      Transaction => InternalTransaction
    }
    import tech.cryptonomic.conseil.common.tezos.TezosTypes.OperationResult.Status

    val selectOrigination = GenPrism[Operation, Origination]
    val originationResult = GenLens[Origination](_.metadata.operation_result)
    val originationBigMapDiffs =
      Optional[OperationResult.Origination, List[Contract.CompatBigMapDiff]](_.big_map_diff)(
        diffs => result => result.copy(big_map_diff = diffs.some)
      )

    val selectTransaction = GenPrism[Operation, Transaction]
    val transactionResult = GenLens[Transaction](_.metadata.operation_result)
    val transactionBigMapDiffs =
      Optional[OperationResult.Transaction, List[Contract.CompatBigMapDiff]](_.big_map_diff)(
        diffs => result => result.copy(big_map_diff = diffs.some)
      )

    val selectBigMapAlloc = left[Contract.BigMapDiff, Contract.Protocol4BigMapDiff] composePrism GenPrism[
            Contract.BigMapDiff,
            Contract.BigMapAlloc
          ]

    val selectBigMapUpdate = left[Contract.BigMapDiff, Contract.Protocol4BigMapDiff] composePrism GenPrism[
            Contract.BigMapDiff,
            Contract.BigMapUpdate
          ]

    val selectBigMapCopy = left[Contract.BigMapDiff, Contract.Protocol4BigMapDiff] composePrism GenPrism[
            Contract.BigMapDiff,
            Contract.BigMapCopy
          ]

    val selectBigMapRemove = left[Contract.BigMapDiff, Contract.Protocol4BigMapDiff] composePrism GenPrism[
            Contract.BigMapDiff,
            Contract.BigMapRemove
          ]

    val operationBigMapDiffAlloc =
      selectOrigination composeLens
          originationResult composeOptional
          originationBigMapDiffs composeTraversal
          (Traversal.fromTraverse[List, Contract.CompatBigMapDiff] composeOptional selectBigMapAlloc)

    val operationBigMapDiffUpdate =
      selectTransaction composeLens
          transactionResult composeOptional
          transactionBigMapDiffs composeTraversal
          (Traversal.fromTraverse[List, Contract.CompatBigMapDiff] composeOptional selectBigMapUpdate)

    val operationBigMapDiffCopy =
      selectTransaction composeLens
          transactionResult composeOptional
          transactionBigMapDiffs composeTraversal
          (Traversal.fromTraverse[List, Contract.CompatBigMapDiff] composeOptional selectBigMapCopy)

    val operationBigMapDiffRemove =
      selectTransaction composeLens
          transactionResult composeOptional
          transactionBigMapDiffs composeTraversal
          (Traversal.fromTraverse[List, Contract.CompatBigMapDiff] composeOptional selectBigMapRemove)

    private def isApplied(status: String) = Status.parse(status).contains(Status.applied)

    def extractAppliedOriginationsResults(block: Block) = {
      val (ops, intOps) = TezosOptics.Blocks.extractOperationsAlongWithInternalResults(block).values.unzip

      val results =
        (ops.toList.flatten.collect { case op: Origination => op.metadata.operation_result }) ++
            (intOps.toList.flatten.collect { case intOp: InternalOperationResults.Origination => intOp.result })

      results.filter(result => isApplied(result.status))
    }

    def extractAppliedTransactionsResults(block: Block) = {
      val (ops, intOps) = TezosOptics.Blocks.extractOperationsAlongWithInternalResults(block).values.unzip

      val results =
        (ops.toList.flatten.collect { case op: Transaction => op.metadata.operation_result }) ++
            (intOps.toList.flatten.collect { case intOp: InternalTransaction => intOp.result })

      results.filter(result => isApplied(result.status))
    }

    def extractAppliedTransactions(block: Block): List[Either[Transaction, InternalTransaction]] = {
      val (ops, intOps) = TezosOptics.Blocks.extractOperationsAlongWithInternalResults(block).values.unzip

      (ops.toList.flatten.collect {
        case op: Transaction if isApplied(op.metadata.operation_result.status) => Left(op)
      }) ++
        (intOps.toList.flatten.collect {
          case intOp: InternalTransaction if isApplied(intOp.result.status) => Right(intOp)
        })
    }

  }

  object Accounts {

    //basic building blocks to reach into the account's structure
    private val accountScript = GenLens[Account](_.script)

    private val contractsCode = GenLens[Contracts](_.code)

    private val storageCode = GenLens[Contracts](_.storage)

    private val expression = GenLens[Micheline](_.expression)

    /** an optional lens allowing to reach into the script code field of an account*/
    val scriptLens = accountScript composePrism some composeLens contractsCode composeLens expression

    /** an optional lens allowing to reach into the script storage field of an account*/
    val storageLens = accountScript composePrism some composeLens storageCode composeLens expression
  }

  object Contracts {
    import tech.cryptonomic.conseil.common.util.JsonUtil

    val parametersExpression = Lens[ParametersCompatibility, Micheline] {
      case Left(value) => value.value
      case Right(value) => value
    } { micheline =>
      {
        case Left(value) => Left(value.copy(value = micheline))
        case Right(_) => Right(micheline)
      }
    }

    //we'll use this to get out any address-looking string from the transaction params
    val extractAddressesFromExpression = (micheline: Micheline) => {
      micheline.expression match {
        case JsonUtil.AccountIds(id, ids @ _*) =>
          (id :: ids.toList).distinct.map(AccountId)
        case _ =>
          List.empty[AccountId]
      }
    }
  }
}
