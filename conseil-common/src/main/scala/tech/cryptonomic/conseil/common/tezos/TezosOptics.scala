package tech.cryptonomic.conseil.common.tezos

import java.time.ZonedDateTime

import TezosTypes._
import TezosTypes.OperationMetadata.BalanceUpdate
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Scripted.Contracts
import monocle.std.all._
import monocle.function.Each._
import cats.implicits._
import tech.cryptonomic.conseil.common.tezos.TezosTypes.InternalOperationResults.InternalOperationResult
import tech.cryptonomic.conseil.common.tezos.TezosOptics.Blocks.KeyedOperation

/** Provides [[https://www.optics.dev/Monocle/ monocle]] lenses and additional "optics"
  * for most common access and mutation patterns for Tezos type hierarchies and ADTs
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
   *   val aToCLens = aToBLens composeLens bToCLens
   * which allows you to
   *
   *   val nestedC: C = aToCLens.get(a)
   *   val updatedWithC: A = aToCLens.set(a)(c)
   *
   * Now it seems somebody lost control using this stuff and thus you now have a whole family of
   * things like lenses but for complex fields and other kind of "nesting", e.g.
   *
   * - Optional: a getter/setter for fields whose value might not be there
   * - Prism: a selector within a sealed trait hierarchy (a.k.a. a Sum type)
   * - Traversal: a getter/setter that operates on many value in the data structure (e.g. for collection-like things)
   * - Iso: a bidirectional lossless conversion between two types
   *
   * For each optic type, we agree on a standard naming convention that makes each one easier to identify and
   * meaningfully compose with each other. Assuming a field/subtype named XXX:
   * - Lens: onXXX
   * - Optional: whenXXX
   * - Prism : selectXXX
   * - Traversal: acrossXXX
   * - Iso: ontoXXX
   *
   * We also make use of standard instances of optics for common types, e.g.
   * - `stdLeft`/`stdRight`, a Prism that digs into a Left/Right value if it matches
   * - ...
   *
   * And standard functions to obtain optics based on certain conditions (based on type classes), e.g.
   * - `each` will provide a Traversal for any type that is Traversable (e.g. `List`)
   * - `some` will provide an Optional for an instance of Option, in the way you would expect
   * - ...
   *
   * There are plenty existing optics available which often could substitute common functions too, e.g.
   * - Iso between similar representations: List/Vector, NonEmptyList/NonEmptyChain/OneAnd, ...
   * - Prisms for numeric down-casting BigDecimal to Int/Long, Byte to Boolean, Double to Float, ...
   * - `curry`/`uncurry`, to convert functions between curried/uncurried form
   * - `flipped` to switch two arguments of a function
   * - `head` to access first element of a non-empty cons-list, or option variant for cons-list
   * - `first/second/third/...` to access the nth element
   * - ...
   */
  import monocle.{Lens, Optional, Traversal}
  import monocle.macros.{GenLens, GenPrism}

  /** Many useful optics for blocks and their inner structure */
  object Blocks {

    /* let's keep some operation optics at hand to shorten the code */
    import Operations._

    case class KeyedOperation(key: OperationHash, operation: Either[Operation, InternalOperationResult])

    //basic building blocks to reach into the block's structure
    private[TezosOptics] val onData = GenLens[Block](_.data)
    private[TezosOptics] val onHeader = GenLens[BlockData](_.header)
    private[TezosOptics] val onMetadata = GenLens[BlockData](_.metadata)
    private[TezosOptics] val onHeaderTimestamp = GenLens[BlockHeader](_.timestamp)
    private[TezosOptics] val selectHeaderMetadata = GenPrism[BlockMetadata, BlockHeaderMetadata]
    private[TezosOptics] val onHeaderBalances = GenLens[BlockHeaderMetadata](_.balance_updates)
    private[TezosOptics] val acrossOperationGroups = GenLens[Block](_.operationGroups) composeTraversal each
    private[TezosOptics] val acrossOperations = GenLens[OperationsGroup](_.contents) composeTraversal each

    /** Read/write access to the block header level */
    val onLevel = onData composeLens onHeader composeLens GenLens[BlockHeader](_.level)

    /** An optional optic allowing to reach into balances for blocks' metadata */
    val forMetadataBalanceUpdates: Optional[Block, List[BalanceUpdate]] =
      onData composeLens onMetadata composePrism selectHeaderMetadata composeLens onHeaderBalances

    /** a function to set the header timestamp for a block, returning the modified block */
    def setTimestamp(ts: ZonedDateTime): (Block => Block) =
      onData composeLens onHeader composeLens onHeaderTimestamp set (ts)

    /** a function to set metadata balance updates in a block, returning the modified block */
    def setBalances(updates: List[BalanceUpdate]): Block => Block = forMetadataBalanceUpdates.set(updates)

    /** a function to traverse all originated scripts for a block */
    private[TezosOptics] val acrossOriginatedScripts: Traversal[Block, Scripted.Contracts] =
      acrossOperationGroups composeTraversal
          acrossOperations composePrism
          selectOrigination composeLens
          onOriginationScript composePrism some

    /** a function to traverse all of a block's script storage expressions */
    val acrossScriptsStorage = acrossOriginatedScripts composeLens Contracts.onStorage composeLens Contracts.onExpression

    /** a function to traverse all of a block's script code expressions */
    val acrossScriptsCode = acrossOriginatedScripts composeLens Contracts.onCode composeLens Contracts.onExpression

    /** functions to operate on all big maps copy diffs within a block */
    val acrossBigMapDiffCopy = acrossOperationGroups composeTraversal acrossOperations composeTraversal Operations.acrossOperationBigMapDiffCopy

    /** functions to operate on all big maps remove diffs within a block */
    val acrossBigMapDiffRemove = acrossOperationGroups composeTraversal acrossOperations composeTraversal Operations.acrossOperationBigMapDiffRemove

    /** a function to traverse all of a block's transaction operations */
    val acrossTransactions: Traversal[Block, Transaction] =
      acrossOperationGroups composeTraversal
          acrossOperations composePrism
          selectTransaction

    /** a function to traverse all of a block's internal transaction operations */
    val acrossInternalTransactions: Traversal[Block, InternalOperationResults.Transaction] =
      acrossTransactions composeLens
          onTransactionMetadata composeOptional
          whenInternalResults composeTraversal
          each composePrism
          selectInternalTransaction

    /** a function to traverse all of a block's transaction parameters' expressions */
    val acrossTransactionParameters: Traversal[Block, String] =
      acrossTransactions composeLens
          onTransactionParameters composePrism
          some composeLens
          Contracts.onParametersValue composeLens
          Contracts.onExpression

    /** a function to traverse all of a block's internal transaction parameters' expressions */
    val acrossInternalParameters: Traversal[Block, String] =
      acrossInternalTransactions composeLens
          onInternalParameters composePrism
          some composeLens
          Contracts.onParametersValue composeLens
          Contracts.onExpression

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

    /** Extracts all operations, primary and internal, and pre-order traverse
      * the two-level structure to try and preserve the original intended sequence,
      * foregoing the grouping structure
      */
    def extractOperationsSequence(block: Block): List[KeyedOperation] =
      for {
        group <- block.operationGroups
        op <- group.contents
        all <- op match {
          case r: Reveal =>
            Left(op) :: r.metadata.internal_operation_results.toList.flatten.map(Right(_))
          case t: Transaction =>
            Left(op) :: t.metadata.internal_operation_results.toList.flatten.map(Right(_))
          case o: Origination =>
            Left(op) :: o.metadata.internal_operation_results.toList.flatten.map(Right(_))
          case d: Delegation =>
            Left(op) :: d.metadata.internal_operation_results.toList.flatten.map(Right(_))
          case _ => List(Left(op))
        }
      } yield KeyedOperation(group.hash, all)

    //Note, cycle 0 starts at the level 2 block
    def extractCycle(block: Block): Option[Int] =
      discardGenesis(block.data.metadata) //this returns an Option[BlockHeaderMetadata]
        .map(_.level.cycle) //this is Option[Int]

    //Note, cycle 0 starts at the level 2 block
    def extractCycle(block: BlockData): Option[Int] =
      discardGenesis(block.metadata) //this returns an Option[BlockHeaderMetadata]
        .map(_.level.cycle) //this is Option[Int]

    //Note, cycle 0 starts at the level 2 block
    def extractCyclePosition(block: BlockMetadata): Option[Int] =
      discardGenesis(block) //this returns an Option[BlockHeaderMetadata]
        .map(_.level.cycle_position) //this is Option[Int]

    //Note, cycle 0 starts at the level 2 block
    def extractPeriod(block: BlockMetadata): Option[Int] =
      discardGenesis(block)
        .map(_.level.voting_period)
  }

  /** Provides optics to navigate across operations data structures */
  object Operations {
    import tech.cryptonomic.conseil.common.tezos.TezosTypes.InternalOperationResults.{
      Transaction => InternalTransaction,
      Origination => InternalOrigination
    }
    import tech.cryptonomic.conseil.common.tezos.TezosTypes.OperationResult.Status

    val selectOrigination = GenPrism[Operation, Origination]
    val onOriginationResult = GenLens[Origination](_.metadata.operation_result)
    val onOriginationScript = GenLens[Origination](_.script)

    val selectTransaction = GenPrism[Operation, Transaction]
    val onTransactionResult = GenLens[Transaction](_.metadata.operation_result)
    val onTransactionMetadata = GenLens[Transaction](_.metadata)
    val onTransactionParameters = GenLens[Transaction](_.parameters)
    val onInternalParameters = GenLens[InternalTransaction](_.parameters)

    private[TezosOptics] def whenInternalResults[OP]: Optional[ResultMetadata[OP], List[
      InternalOperationResults.InternalOperationResult
    ]] =
      Optional(
        _getOption = (_: ResultMetadata[OP]).internal_operation_results
      )(
        _set = internalResults =>
          metadata =>
            metadata.copy(
              internal_operation_results = metadata.internal_operation_results.map(_ => internalResults)
            )
      )

    val selectInternalTransaction =
      GenPrism[InternalOperationResults.InternalOperationResult, InternalTransaction]

    val whenOriginationBigMapDiffs =
      Optional[OperationResult.Origination, List[Contract.CompatBigMapDiff]](_.big_map_diff)(
        diffs => result => result.copy(big_map_diff = diffs.some)
      )

    val whenTransactionBigMapDiffs =
      Optional[OperationResult.Transaction, List[Contract.CompatBigMapDiff]](_.big_map_diff)(
        diffs => result => result.copy(big_map_diff = diffs.some)
      )

    private[TezosOptics] val selectBigMapAlloc = stdLeft[Contract.BigMapDiff, Contract.Protocol4BigMapDiff] composePrism GenPrism[
            Contract.BigMapDiff,
            Contract.BigMapAlloc
          ]

    private[TezosOptics] val selectBigMapUpdate = stdLeft[Contract.BigMapDiff, Contract.Protocol4BigMapDiff] composePrism GenPrism[
            Contract.BigMapDiff,
            Contract.BigMapUpdate
          ]

    private[TezosOptics] val selectBigMapCopy = stdLeft[Contract.BigMapDiff, Contract.Protocol4BigMapDiff] composePrism GenPrism[
            Contract.BigMapDiff,
            Contract.BigMapCopy
          ]

    private[TezosOptics] val selectBigMapRemove = stdLeft[Contract.BigMapDiff, Contract.Protocol4BigMapDiff] composePrism GenPrism[
            Contract.BigMapDiff,
            Contract.BigMapRemove
          ]

    val acrossOperationBigMapDiffAlloc =
      selectOrigination composeLens
          onOriginationResult composeOptional
          whenOriginationBigMapDiffs composeTraversal
          (Traversal.fromTraverse[List, Contract.CompatBigMapDiff] composePrism selectBigMapAlloc)

    val acrossOperationBigMapDiffUpdate =
      selectTransaction composeLens
          onTransactionResult composeOptional
          whenTransactionBigMapDiffs composeTraversal
          (Traversal.fromTraverse[List, Contract.CompatBigMapDiff] composePrism selectBigMapUpdate)

    val acrossOperationBigMapDiffCopy =
      selectTransaction composeLens
          onTransactionResult composeOptional
          whenTransactionBigMapDiffs composeTraversal
          (Traversal.fromTraverse[List, Contract.CompatBigMapDiff] composePrism selectBigMapCopy)

    val acrossOperationBigMapDiffRemove =
      selectTransaction composeLens
          onTransactionResult composeOptional
          whenTransactionBigMapDiffs composeTraversal
          (Traversal.fromTraverse[List, Contract.CompatBigMapDiff] composePrism selectBigMapRemove)

    private def isApplied(status: String) = Status.parse(status).contains(Status.applied)

    def extractAppliedOriginationsResults(block: Block): List[(OperationHash, OperationResult.Origination)] = {
      val operationSequence = TezosOptics.Blocks.extractOperationsSequence(block).collect {
        case KeyedOperation(groupHash, Left(op: Origination)) => groupHash -> op.metadata.operation_result
        case KeyedOperation(groupHash, Right(intOp: InternalOrigination)) => groupHash -> intOp.result
      }

      operationSequence.filter(result => isApplied(result._2.status))
    }

    def extractAppliedTransactionsResults(block: Block): List[(OperationHash, OperationResult.Transaction)] = {
      val operationSequence = TezosOptics.Blocks.extractOperationsSequence(block).collect {
        case KeyedOperation(groupHash, Left(op: Transaction)) => groupHash -> op.metadata.operation_result
        case KeyedOperation(groupHash, Right(intOp: InternalTransaction)) => groupHash -> intOp.result
      }

      operationSequence.filter(result => isApplied(result._2.status))
    }

    def extractAppliedTransactions(block: Block): List[Either[Transaction, InternalTransaction]] =
      TezosOptics.Blocks.extractOperationsSequence(block).map(_.operation).collect {
        case Left(op: Transaction) if isApplied(op.metadata.operation_result.status) => Left(op)
        case Right(intOp: InternalTransaction) if isApplied(intOp.result.status) => Right(intOp)
      }

  }

  /** Provides optics to navigate across accounts data structures */
  object Accounts {

    //basic building blocks to reach into the account's structure
    private[TezosOptics] val onScript = GenLens[Account](_.script)

    /** an optional lens allowing to reach into the script code field of an account*/
    val whenAccountCode = onScript composePrism some composeLens Contracts.onCode composeLens Contracts.onExpression

    /** an optional lens allowing to reach into the script storage field of an account*/
    val whenAccountStorage = onScript composePrism some composeLens Contracts.onStorage composeLens Contracts.onExpression
  }

  /** Provides optics to navigate across contracts data structures */
  object Contracts {
    import tech.cryptonomic.conseil.common.util.JsonUtil

    private[TezosOptics] val onCode = GenLens[Contracts](_.code)

    private[TezosOptics] val onStorage = GenLens[Contracts](_.storage)

    private[TezosOptics] val onExpression = GenLens[Micheline](_.expression)

    val onParametersValue = Lens[ParametersCompatibility, Micheline] {
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
          (id :: ids.toList).distinct.map(makeAccountId)
        case _ =>
          List.empty[AccountId]
      }
    }
  }
}
