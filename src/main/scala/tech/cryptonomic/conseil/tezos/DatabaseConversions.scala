package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.util.Conversion
import cats.{Id, Show}
import cats.implicits._
import java.sql.Timestamp
import java.time.Instant

import monocle.Getter
import io.scalaland.chimney.dsl._
import tech.cryptonomic.conseil.tezos
import tech.cryptonomic.conseil.tezos.TezosNodeOperator.FetchRights
import tech.cryptonomic.conseil.tezos.TezosTypes.{BakingRights, Contract, EndorsingRights}
import com.typesafe.scalalogging.Logger
import tech.cryptonomic.conseil.tezos.TezosTypes.Voting.Vote

object DatabaseConversions extends LazyLogging {

  // Simplify understanding in parts of the code
  case class BlockBigMapDiff(get: (BlockHash, Contract.BigMapDiff)) extends AnyVal
  case class BlockContractIdsBigMapDiff(get: (BlockHash, List[ContractId], Contract.BigMapDiff)) extends AnyVal

  //adapts from the java timestamp to sql
  private def toSql(datetime: java.time.ZonedDateTime): Timestamp = Timestamp.from(datetime.toInstant)

  //single field conversions
  def concatenateToString[A, T[_] <: scala.collection.GenTraversableOnce[_]](traversable: T[A]): String =
    traversable.mkString("[", ",", "]")

  def toCommaSeparated[A, T[_] <: scala.collection.GenTraversableOnce[_]](traversable: T[A]): String =
    traversable.mkString(",")

  def extractBigDecimal(number: PositiveBigNumber): Option[BigDecimal] = number match {
    case PositiveDecimal(value) => Some(value)
    case _ => None
  }

  def extractBigDecimal(number: BigNumber): Option[BigDecimal] = number match {
    case Decimal(value) => Some(value)
    case _ => None
  }

  private def extractResultErrorIds(errors: Option[List[OperationResult.Error]]) = {
    def extractId(error: OperationResult.Error): Option[String] = {
      import io.circe.JsonObject

      //we expect this to work as long as the error was previously built from json parsing
      //refer to the JsonDecoders
      for {
        obj <- io.circe.parser
          .decode[JsonObject](error.json)
          .toOption
        id <- obj("id")
        idString <- id.asString
      } yield idString
    }

    for {
      es <- errors
      ids <- es.traverse(extractId)
    } yield concatenateToString(ids)
  }

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

  //implicit conversions to database row types

  implicit val averageFeesToFeeRow = new Conversion[Id, AverageFees, Tables.FeesRow] {
    override def convert(from: AverageFees) =
      from.into[Tables.FeesRow].transform
  }

  implicit val blockAccountsToAccountRows =
    new Conversion[List, BlockTagged[Map[AccountId, Account]], Tables.AccountsRow] {
      val toDelegateSetable: Option[AccountDelegate] => Option[Boolean] = delegate =>
        PartialFunction.condOpt(delegate) {
          case Some(Left(Protocol4Delegate(setable, _))) => setable
        }

      val toDelegateValue: Option[AccountDelegate] => Option[String] = delegate =>
        PartialFunction.condOpt(delegate) {
          case Some(Left(Protocol4Delegate(_, Some(pkh)))) => pkh.value
          case Some(Right(pkh)) => pkh.value
        }

      override def convert(from: BlockTagged[Map[AccountId, Account]]) = {
        val BlockTagged(hash, level, timestamp, cycle, accounts) = from
        accounts.map {
          case (id, Account(balance, delegate, script, counter, manager, spendable, isBaker, isActivated)) =>
            Tables.AccountsRow(
              accountId = id.id,
              blockId = hash.value,
              counter = counter,
              script = script.map(_.code.expression),
              storage = script.map(_.storage.expression),
              balance = balance,
              blockLevel = level,
              manager = manager.map(_.value),
              spendable = spendable,
              delegateSetable = toDelegateSetable(delegate),
              delegateValue = toDelegateValue(delegate),
              isBaker = isBaker.getOrElse(false),
              isActivated = isActivated.getOrElse(false)
            )
        }.toList
      }
    }

  implicit val blockAccountsToAccountHistoryRows =
    new Conversion[List, (BlockTagged[Map[AccountId, Account]], List[Tables.AccountsRow]), Tables.AccountsHistoryRow] {
      override def convert(
          from: (BlockTagged[Map[AccountId, Account]], List[Tables.AccountsRow])
      ): List[Tables.AccountsHistoryRow] = {
        import tech.cryptonomic.conseil.util.Conversion.Syntax._
        val (blockTaggedAccounts, inactiveBakers) = from
        val touchedAccounts = blockTaggedAccounts.convertToA[List, Tables.AccountsRow].map { accountsRow =>
          val isActiveBaker =
            if (inactiveBakers.map(_.accountId).contains(accountsRow.accountId))
              Some(false)
            else if (accountsRow.isBaker)
              Some(true)
            else
              None

          accountsRow
            .into[Tables.AccountsHistoryRow]
            .withFieldConst(_.asof, Timestamp.from(blockTaggedAccounts.timestamp.getOrElse(Instant.ofEpochMilli(0))))
            .withFieldConst(_.cycle, blockTaggedAccounts.cycle)
            .withFieldConst(_.isActiveBaker, isActiveBaker)
            .transform
        }

        val touched = touchedAccounts.map(_.accountId).toSet

        val untouchedAccounts =
          inactiveBakers
            .filterNot(row => touched.contains(row.accountId))
            .map {
              _.into[Tables.AccountsHistoryRow]
                .withFieldConst(
                  _.asof,
                  Timestamp.from(blockTaggedAccounts.timestamp.getOrElse(Instant.ofEpochMilli(0)))
                )
                .withFieldConst(_.cycle, blockTaggedAccounts.cycle)
                .withFieldConst(_.blockLevel, BigDecimal(blockTaggedAccounts.blockLevel))
                .withFieldConst(_.blockId, blockTaggedAccounts.blockHash.value)
                .withFieldConst(_.isActiveBaker, Some(false))
                .transform
            }

        (untouchedAccounts ::: touchedAccounts).distinct
      }
    }

  implicit val blockToBlocksRow = new Conversion[Id, Block, Tables.BlocksRow] {
    override def convert(from: Block) = {
      val header = from.data.header
      val metadata = discardGenesis.lift(from.data.metadata)
      val CurrentVotes(expectedQuorum, proposal) = from.votes
      Tables.BlocksRow(
        level = header.level,
        proto = header.proto,
        predecessor = header.predecessor.value,
        timestamp = toSql(header.timestamp),
        validationPass = header.validation_pass,
        fitness = header.fitness.mkString(","),
        context = Some(header.context), //put in later
        signature = header.signature,
        protocol = from.data.protocol,
        chainId = from.data.chain_id,
        hash = from.data.hash.value,
        operationsHash = header.operations_hash,
        periodKind = metadata.map(_.voting_period_kind.toString),
        currentExpectedQuorum = expectedQuorum,
        activeProposal = proposal.map(_.id),
        baker = metadata.map(_.baker.value),
        nonceHash = metadata.flatMap(_.nonce_hash.map(_.value)),
        consumedGas = metadata.flatMap(md => extractBigDecimal(md.consumed_gas)),
        metaLevel = metadata.map(_.level.level),
        metaLevelPosition = metadata.map(_.level.level_position),
        metaCycle = metadata.map(_.level.cycle),
        metaCyclePosition = metadata.map(_.level.cycle_position),
        metaVotingPeriod = metadata.map(_.level.voting_period),
        metaVotingPeriodPosition = metadata.map(_.level.voting_period_position),
        expectedCommitment = metadata.map(_.level.expected_commitment),
        priority = header.priority
      )
    }
  }

  implicit val blockToOperationGroupsRow = new Conversion[List, Block, Tables.OperationGroupsRow] {
    override def convert(from: Block) =
      from.operationGroups.map { og =>
        Tables.OperationGroupsRow(
          protocol = og.protocol,
          chainId = og.chain_id.map(_.id),
          hash = og.hash.value,
          branch = og.branch.value,
          signature = og.signature.map(_.value),
          blockId = from.data.hash.value,
          blockLevel = from.data.header.level
        )
      }
  }

  //Cannot directly convert a single operation to a row, because we need the block and operation-group info to build the database row
  implicit val operationToOperationsRow = new Conversion[Id, (Block, OperationHash, Operation), Tables.OperationsRow] {
    override def convert(from: (Block, OperationHash, Operation)) =
      (convertEndorsement orElse
          convertNonceRevelation orElse
          convertActivateAccount orElse
          convertReveal orElse
          convertTransaction orElse
          convertOrigination orElse
          convertDelegation orElse
          convertBallot orElse
          convertProposals orElse
          convertUnhandledOperations)(from)
  }

  private val convertEndorsement: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Endorsement(level, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "endorsement",
        level = Some(level),
        delegate = Some(metadata.delegate.value),
        slots = Some(metadata.slots).map(concatenateToString),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block),
        branch = block.operationGroups.find(h => h.hash == groupHash).map(_.branch.value),
        numberOfSlots = Some(metadata.slots.length),
        period = extractPeriod(block.data.metadata)
      )
  }

  private val convertNonceRevelation: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, SeedNonceRevelation(level, nonce, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "seed_nonce_revelation",
        level = Some(level),
        nonce = Some(nonce.value),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block),
        period = extractPeriod(block.data.metadata)
      )
  }

  private val convertActivateAccount: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, ActivateAccount(pkh, secret, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "activate_account",
        pkh = Some(pkh.value),
        secret = Some(secret.value),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block),
        period = extractPeriod(block.data.metadata)
      )
  }

  private val convertReveal: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Reveal(counter, fee, gas_limit, storage_limit, pk, source, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "reveal",
        source = Some(source.value),
        fee = extractBigDecimal(fee),
        counter = extractBigDecimal(counter),
        gasLimit = extractBigDecimal(gas_limit),
        storageLimit = extractBigDecimal(storage_limit),
        publicKey = Some(pk.value),
        status = Some(metadata.operation_result.status),
        consumedGas = metadata.operation_result.consumed_gas.flatMap(extractBigDecimal),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block),
        period = extractPeriod(block.data.metadata),
        errors = extractResultErrorIds(metadata.operation_result.errors)
      )
  }

  private val convertTransaction: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (
        block,
        groupHash,
        Transaction(counter, amount, fee, gas_limit, storage_limit, source, destination, parameters, metadata)
        ) =>
      val extractedParameters = parameters.map(_.map(Parameters(_)).merge)
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "transaction",
        source = Some(source.value),
        fee = extractBigDecimal(fee),
        counter = extractBigDecimal(counter),
        gasLimit = extractBigDecimal(gas_limit),
        storageLimit = extractBigDecimal(storage_limit),
        amount = extractBigDecimal(amount),
        destination = Some(destination.id),
        parameters = extractedParameters.map(_.value.expression),
        parametersEntrypoints = extractedParameters.flatMap(_.entrypoint),
        status = Some(metadata.operation_result.status),
        consumedGas = metadata.operation_result.consumed_gas.flatMap(extractBigDecimal),
        storageSize = metadata.operation_result.storage_size.flatMap(extractBigDecimal),
        paidStorageSizeDiff = metadata.operation_result.paid_storage_size_diff.flatMap(extractBigDecimal),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block),
        period = extractPeriod(block.data.metadata),
        errors = extractResultErrorIds(metadata.operation_result.errors)
      )
  }

  private val convertOrigination: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (
        block,
        groupHash,
        Origination(
          counter,
          fee,
          source,
          balance,
          gas_limit,
          storage_limit,
          mpk,
          delegatable,
          delegate,
          spendable,
          script,
          metadata
        )
        ) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "origination",
        delegate = delegate.map(_.value),
        source = Some(source.value),
        fee = extractBigDecimal(fee),
        counter = extractBigDecimal(counter),
        gasLimit = extractBigDecimal(gas_limit),
        storageLimit = extractBigDecimal(storage_limit),
        managerPubkey = mpk.map(_.value),
        balance = extractBigDecimal(balance),
        spendable = spendable,
        delegatable = delegatable,
        script = script.map(_.code.expression),
        storage = script.map(_.storage.expression),
        status = Some(metadata.operation_result.status),
        consumedGas = metadata.operation_result.consumed_gas.flatMap(extractBigDecimal),
        storageSize = metadata.operation_result.storage_size.flatMap(extractBigDecimal),
        paidStorageSizeDiff = metadata.operation_result.paid_storage_size_diff.flatMap(extractBigDecimal),
        originatedContracts = metadata.operation_result.originated_contracts.map(_.map(_.id)).map(toCommaSeparated),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block),
        period = extractPeriod(block.data.metadata),
        errors = extractResultErrorIds(metadata.operation_result.errors)
      )
  }

  private val convertDelegation: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Delegation(counter, source, fee, gas_limit, storage_limit, delegate, metadata)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "delegation",
        delegate = delegate.map(_.value),
        source = Some(source.value),
        fee = extractBigDecimal(fee),
        counter = extractBigDecimal(counter),
        gasLimit = extractBigDecimal(gas_limit),
        storageLimit = extractBigDecimal(storage_limit),
        status = Some(metadata.operation_result.status),
        consumedGas = metadata.operation_result.consumed_gas.flatMap(extractBigDecimal),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block),
        period = extractPeriod(block.data.metadata),
        errors = extractResultErrorIds(metadata.operation_result.errors)
      )
  }

  private val convertBallot: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Ballot(ballot, proposal, source, ballotPeriod)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "ballot",
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        ballot = Some(ballot.value),
        internal = false,
        proposal = proposal,
        source = source.map(_.id),
        cycle = extractCycle(block),
        ballotPeriod = ballotPeriod,
        period = extractPeriod(block.data.metadata)
      )
  }

  private val convertProposals: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Proposals(source, ballotPeriod, proposals)) =>
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "proposals",
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        proposal = proposals.map(x => concatenateToString(x)),
        source = source.map(_.id),
        cycle = extractCycle(block),
        ballotPeriod = ballotPeriod,
        period = extractPeriod(block.data.metadata)
      )

  }

  private val convertUnhandledOperations: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, op) =>
      val kind = op match {
        case DoubleEndorsementEvidence => "double_endorsement_evidence"
        case DoubleBakingEvidence => "double_baking_evidence"
        case _ => ""
      }
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = kind,
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = extractCycle(block),
        period = extractPeriod(block.data.metadata)
      )
  }

  /** Not all operations have some form of balance updates... we're ignoring some type, like ballots,
    * thus we can't extract from those anyway.
    * We get back an empty List, where not applicable
    * We define a typeclass/trait that encodes the availability of balance updates, to reuse it for operations as well as blocks
    * @tparam T the source type that contains the balance updates values
    * @tparam Label the source Label type, which has a `Show` contraint, to be representable as a string
    * @param balances an optic instance that lets extract the Map of tagged updates from the source
    * @param hashing an optic instance to extract an optional reference hash value
    */
  implicit def anyToBalanceUpdates[T, Label](
      implicit
      balances: Getter[T, Map[Label, List[OperationMetadata.BalanceUpdate]]],
      hashing: Getter[T, Option[String]],
      showing: Show[Label]
  ) = new Conversion[List, T, Tables.BalanceUpdatesRow] {
    import cats.syntax.show._

    override def convert(from: T) =
      balances
        .get(from)
        .flatMap {
          case (tag, updates) =>
            updates.map {
              case OperationMetadata.BalanceUpdate(
                  kind,
                  change,
                  category,
                  contract,
                  delegate,
                  level
                  ) =>
                Tables.BalanceUpdatesRow(
                  id = 0,
                  source = tag.show,
                  sourceHash = hashing.get(from),
                  kind = kind,
                  contract = contract.map(_.id),
                  change = BigDecimal(change),
                  level = level.map(BigDecimal(_)),
                  delegate = delegate.map(_.value),
                  category = category
                )
            }
        }
        .toList
  }

  /** Utility alias when we need to keep related data paired together */
  type OperationTablesData = (Tables.OperationsRow, List[Tables.BalanceUpdatesRow])

  /** */
  implicit val internalOperationResultToOperation =
    new Conversion[Id, InternalOperationResults.InternalOperationResult, Operation] {
      override def convert(from: InternalOperationResults.InternalOperationResult): Id[Operation] =
        // counter/fee/gas_limit/storage_limit are not in internal operations results,
        // so values below are going to be discarded during transformation Operation -> OperationRow
        from match {
          case reveal: InternalOperationResults.Reveal =>
            reveal
              .into[Reveal]
              .withFieldConst(_.counter, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.fee, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.gas_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.storage_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.metadata, ResultMetadata[OperationResult.Reveal](reveal.result, List.empty, None))
              .transform
          case transaction: InternalOperationResults.Transaction =>
            transaction
              .into[Transaction]
              .withFieldConst(_.counter, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.fee, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.gas_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.storage_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(
                _.metadata,
                ResultMetadata[OperationResult.Transaction](
                  transaction.result,
                  transaction.result.balance_updates.getOrElse(List.empty),
                  None
                )
              )
              .transform

          case origination: InternalOperationResults.Origination =>
            origination
              .into[Origination]
              .withFieldConst(_.counter, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.fee, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.gas_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.storage_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(
                _.metadata,
                ResultMetadata[OperationResult.Origination](
                  origination.result,
                  origination.result.balance_updates.getOrElse(List.empty),
                  None
                )
              )
              .transform

          case delegation: InternalOperationResults.Delegation =>
            delegation
              .into[Delegation]
              .withFieldConst(_.counter, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.fee, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.gas_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(_.storage_limit, InvalidPositiveDecimal("Discarded"))
              .withFieldConst(
                _.metadata,
                ResultMetadata[OperationResult.Delegation](delegation.result, List.empty, None)
              )
              .transform
        }
    }

  /** Will convert to paired list of operations with related balance updates
    * with one HUGE CAVEAT: both have only temporary, meaningless, `sourceId`s
    *
    * To correctly create the relation on the db, we must first store the operations, get
    * each generated id, and pass it to the associated balance-updates
    */
  implicit val blockToOperationTablesData = new Conversion[List, Block, OperationTablesData] {
    import tech.cryptonomic.conseil.util.Conversion.Syntax._
    import tech.cryptonomic.conseil.tezos.SymbolSourceLabels.Show._
    import tech.cryptonomic.conseil.tezos.OperationBalances._

    override def convert(from: Block) =
      extractOperationsWithInternalResults(from).flatMap {
        case (group, (operations, internalResults)) =>
          val mainOperationData = operations.map(
            op =>
              (from, group.hash, op).convertTo[Tables.OperationsRow] ->
                  op.convertToA[List, Tables.BalanceUpdatesRow]
          )
          val internalOperationData = internalResults.map { oop =>
            val op = oop.convertTo[Operation]
            (from, group.hash, op)
              .convertTo[Tables.OperationsRow]
              .copy(internal = true, nonce = Some(oop.nonce.toString)) -> op.convertToA[List, Tables.BalanceUpdatesRow]
          }
          mainOperationData ++ internalOperationData
      }.toList

  }

  /* Utility extractor that collects, for a block, both operations and internal operations results, grouped
   * in a from more amenable to processing
   */
  private def extractOperationsWithInternalResults(
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

  implicit private val bigMapDiffToBigMapRow =
    new Conversion[Option, BlockBigMapDiff, Tables.BigMapsRow] {
      import tech.cryptonomic.conseil.tezos.TezosTypes.Contract.BigMapAlloc
      import michelson.dto.MichelsonExpression
      import michelson.JsonToMichelson.toMichelsonScript
      import michelson.parser.JsonParser._
      //needed to call the michelson conversion
      implicit lazy val _: Logger = logger

      def convert(from: BlockBigMapDiff) = from.get match {
        case (_, BigMapAlloc(_, Decimal(id), key_type, value_type)) =>
          Some(
            Tables.BigMapsRow(
              bigMapId = id,
              keyType = Some(toMichelsonScript[MichelsonExpression](key_type.expression)),
              valueType = Some(toMichelsonScript[MichelsonExpression](value_type.expression))
            )
          )
        case (hash, BigMapAlloc(_, InvalidDecimal(json), _, _)) =>
          logger.warn(
            "A big_map_diff allocation hasn't been converted to a BigMap on db, because the map id '{}' is not a valid number. The block containing the Origination operation is {}",
            json,
            hash.value
          )
          None
        case diff =>
          logger.warn(
            "A big_map_diff result will be ignored by the allocation conversion to BigMap on db, because the diff action is not supported: {}",
            from.get._2
          )
          None
      }
    }

  /* This will only convert big map updates actually, as the other types of
   * operations are handled differently
   */
  implicit private val bigMapDiffToBigMapContentsRow =
    new Conversion[Option, BlockBigMapDiff, Tables.BigMapContentsRow] {
      import tech.cryptonomic.conseil.tezos.TezosTypes.Contract.BigMapUpdate
      import michelson.dto.MichelsonInstruction
      import michelson.JsonToMichelson.toMichelsonScript
      import michelson.parser.JsonParser._
      //needed to call the michelson conversion
      implicit lazy val _: Logger = logger

      def convert(from: BlockBigMapDiff) = from.get match {
        case (_, BigMapUpdate(_, key, keyHash, Decimal(id), value)) =>
          Some(
            Tables.BigMapContentsRow(
              bigMapId = id,
              key = toMichelsonScript[MichelsonInstruction](key.expression), //we're using instructions to represent data values
              keyHash = Some(keyHash.value),
              value = value.map(it => toMichelsonScript[MichelsonInstruction](it.expression)) //we're using instructions to represent data values
            )
          )
        case (hash, BigMapUpdate(_, _, _, InvalidDecimal(json), _)) =>
          logger.warn(
            "A big_map_diff update hasn't been converted to a BigMapContent on db, because the map id '{}' is not a valid number. The block containing the Transation operation is {}",
            json,
            hash.value
          )
          None
        case diff =>
          logger.warn(
            "A big_map_diff result will be ignored by the update conversion to BigMapContent on db, because the diff action is not supported: {}",
            from.get._2
          )
          None
      }
    }

  implicit private val bigMapDiffToBigMapOriginatedContracts =
    new Conversion[List, BlockContractIdsBigMapDiff, Tables.OriginatedAccountMapsRow] {
      import tech.cryptonomic.conseil.tezos.TezosTypes.Contract.BigMapAlloc
      implicit lazy val _ = logger

      def convert(from: BlockContractIdsBigMapDiff) = from.get match {
        case (_, ids, BigMapAlloc(_, Decimal(id), _, _)) =>
          ids.map(
            contractId =>
              Tables.OriginatedAccountMapsRow(
                bigMapId = id,
                accountId = contractId.id
              )
          )
        case (hash, ids, BigMapAlloc(_, InvalidDecimal(json), _, _)) =>
          logger.warn(
            "A big_map_diff allocation hasn't been converted to a relation for OriginatedAccounts to BigMap on db, because the map id '{}' is not a valid number. The block containing the Transation operation is {}, involving accounts {}",
            json,
            ids.mkString(", "),
            hash.value
          )
          List.empty
        case diff =>
          logger.warn(
            "A big_map_diff result will be ignored and not be converted to a relation for OriginatedAccounts to BigMap on db, because the diff action is not supported: {}",
            from.get._2
          )
          List.empty
      }
    }

  /* We make a few assumptions here:
   * Big Maps information is extracted from Originations only,
   * whenever Big map diffs contain some "alloc" actions.
   * Each valid allocation will be converted to a row in the Big Maps table.
   */
  implicit val blockToBigMapsRow = new Conversion[List, Block, Tables.BigMapsRow] {
    import tech.cryptonomic.conseil.util.Conversion.Syntax._

    override def convert(from: TezosTypes.Block): List[Tables.BigMapsRow] = {

      val (ops, intOps) = extractOperationsWithInternalResults(from).values.unzip

      val extractDiffsToRows = (originationResult: OperationResult.Origination) =>
        originationResult.big_map_diff.toList.flatten
          .collect[Contract.BigMapDiff, List[Contract.BigMapDiff]] {
            case Left(diff) => diff
          }
          .flatMap(diff => BlockBigMapDiff((from.data.hash, diff)).convertToA[Option, Tables.BigMapsRow])

      ops.toList.flatten.flatMap {
        case op: Origination => extractDiffsToRows(op.metadata.operation_result)
        case _ => List.empty
      } ++
        intOps.toList.flatten.flatMap {
          case intOp: InternalOperationResults.Origination => extractDiffsToRows(intOp.result)
          case _ => List.empty
        }
    }
  }

  /* We make a few assumptions here:
   * Big Map content information is extracted from Transactions only,
   * whenever Big map diffs contain some "update" actions.
   * Each valid upate will be converted to a row in the Big Map Contents table.
   */
  implicit val blockToBigMapContentsRow = new Conversion[List, Block, Tables.BigMapContentsRow] {
    import tech.cryptonomic.conseil.util.Conversion.Syntax._

    override def convert(from: TezosTypes.Block): List[Tables.BigMapContentsRow] = {
      val (ops, intOps) = extractOperationsWithInternalResults(from).values.unzip

      val extractDiffsToRows = (transactionResult: OperationResult.Transaction) =>
        transactionResult.big_map_diff.toList.flatten
          .collect[Contract.BigMapDiff, List[Contract.BigMapDiff]] {
            case Left(diff) => diff
          }
          .flatMap(diff => BlockBigMapDiff((from.data.hash, diff)).convertToA[Option, Tables.BigMapContentsRow])

      ops.toList.flatten.flatMap {
        case op: Transaction => extractDiffsToRows(op.metadata.operation_result)
        case _ => List.empty
      } ++
        intOps.toList.flatten.flatMap {
          case intOp: InternalOperationResults.Transaction => extractDiffsToRows(intOp.result)
          case _ => List.empty
        }

    }
  }

  /** Creates association rows based on the relationship between a Big Map allocation and the
    * contracts associated therein via a map-id reference.
    * This consist of a relation table between the "Map" and "Accounts" tables.
    */
  implicit val blockToBigMapOriginatedContracts = new Conversion[List, Block, Tables.OriginatedAccountMapsRow] {
    import tech.cryptonomic.conseil.util.Conversion.Syntax._

    override def convert(from: TezosTypes.Block): List[Tables.OriginatedAccountMapsRow] = {
      val (ops, intOps) = extractOperationsWithInternalResults(from).values.unzip

      val extractDiffsToRows = (originationResult: OperationResult.Origination) =>
        for {
          contractIds <- originationResult.originated_contracts.toList
          diff <- originationResult.big_map_diff.toList.flatten
            .collect[Contract.BigMapDiff, List[Contract.BigMapDiff]] {
              case Left(diff) => diff
            }
          rows <- BlockContractIdsBigMapDiff((from.data.hash, contractIds, diff))
            .convertToA[List, Tables.OriginatedAccountMapsRow]
        } yield rows

      ops.toList.flatten.flatMap {
        case op: Origination => extractDiffsToRows(op.metadata.operation_result)
        case _ => List.empty
      } ++
        intOps.toList.flatten.flatMap {
          case intOp: InternalOperationResults.Origination => extractDiffsToRows(intOp.result)
          case _ => List.empty
        }
    }
  }

  implicit val blockAccountsAssociationToCheckpointRow =
    new Conversion[List, (BlockHash, Int, Option[Instant], Option[Int], List[AccountId]), Tables.AccountsCheckpointRow] {
      override def convert(from: (BlockHash, Int, Option[Instant], Option[Int], List[AccountId])) = {
        val (blockHash, blockLevel, timestamp, cycle, ids) = from
        ids.map(
          accountId =>
            Tables.AccountsCheckpointRow(
              accountId = accountId.id,
              blockId = blockHash.value,
              blockLevel = blockLevel,
              asof = Timestamp.from(timestamp.getOrElse(Instant.ofEpochMilli(0))),
              cycle = cycle
            )
        )
      }

    }

  implicit val blockDelegatesAssociationToCheckpointRow =
    new Conversion[
      List,
      (BlockHash, Int, Option[Instant], Option[Int], List[PublicKeyHash]),
      Tables.DelegatesCheckpointRow
    ] {
      override def convert(from: (BlockHash, Int, Option[Instant], Option[Int], List[PublicKeyHash])) = {
        val (blockHash, blockLevel, _, _, pkhs) = from
        pkhs.map(
          keyHash =>
            Tables.DelegatesCheckpointRow(
              delegatePkh = keyHash.value,
              blockId = blockHash.value,
              blockLevel = blockLevel
            )
        )
      }

    }

  implicit val delegateToRow = new Conversion[Id, (BlockHash, Int, PublicKeyHash, Delegate), Tables.DelegatesRow] {
    override def convert(from: (BlockHash, Int, PublicKeyHash, Delegate)) = {
      val (blockHash, blockLevel, keyHash, delegate) = from
      Tables.DelegatesRow(
        pkh = keyHash.value,
        blockId = blockHash.value,
        balance = extractBigDecimal(delegate.balance),
        frozenBalance = extractBigDecimal(delegate.frozen_balance),
        stakingBalance = extractBigDecimal(delegate.staking_balance),
        delegatedBalance = extractBigDecimal(delegate.delegated_balance),
        rolls = delegate.rolls.getOrElse(0),
        deactivated = delegate.deactivated,
        gracePeriod = delegate.grace_period,
        blockLevel = blockLevel
      )
    }
  }

  implicit val bakingRightsToRows =
    new Conversion[Id, (FetchRights, BakingRights), Tables.BakingRightsRow] {
      override def convert(
          from: (FetchRights, BakingRights)
      ): tezos.Tables.BakingRightsRow = {
        val (fetchRights, bakingRights) = from
        bakingRights
          .into[Tables.BakingRightsRow]
          .withFieldConst(_.blockHash, fetchRights.blockHash.map(_.value))
          .withFieldConst(_.estimatedTime, bakingRights.estimated_time.map(toSql))
          .withFieldConst(_.cycle, fetchRights.cycle)
          .withFieldConst(_.governancePeriod, fetchRights.governancePeriod)
          .transform
      }
    }

  implicit val endorsingRightsToRows =
    new Conversion[List, (FetchRights, EndorsingRights), Tables.EndorsingRightsRow] {
      override def convert(
          from: (FetchRights, EndorsingRights)
      ): List[Tables.EndorsingRightsRow] = {
        val (fetchRights, endorsingRights) = from
        endorsingRights.slots.map { slot =>
          endorsingRights
            .into[Tables.EndorsingRightsRow]
            .withFieldConst(_.estimatedTime, endorsingRights.estimated_time.map(toSql))
            .withFieldConst(_.slot, slot)
            .withFieldConst(_.blockHash, fetchRights.blockHash.map(_.value))
            .withFieldConst(_.cycle, fetchRights.cycle)
            .withFieldConst(_.governancePeriod, fetchRights.governancePeriod)
            .transform
        }
      }
    }

  implicit val bakingRightsToRowsWithoutBlockHash = new Conversion[Id, BakingRights, Tables.BakingRightsRow] {
    override def convert(from: BakingRights): tezos.Tables.BakingRightsRow = {
      val bakingRights = from
      bakingRights
        .into[Tables.BakingRightsRow]
        .withFieldConst(_.blockHash, None)
        .withFieldConst(_.estimatedTime, bakingRights.estimated_time.map(toSql))
        .transform
    }
  }

  implicit val endorsingRightsToRowsWithoutBlockHash =
    new Conversion[List, EndorsingRights, Tables.EndorsingRightsRow] {
      override def convert(from: EndorsingRights): List[Tables.EndorsingRightsRow] = {
        val endorsingRights = from
        endorsingRights.slots.map { slot =>
          endorsingRights
            .into[Tables.EndorsingRightsRow]
            .withFieldConst(_.estimatedTime, endorsingRights.estimated_time.map(toSql))
            .withFieldConst(_.slot, slot)
            .withFieldConst(_.blockHash, None)
            .transform
        }
      }
    }

  implicit val governanceConv =
    new Conversion[
      Id,
      (BlockData, Option[ProtocolId], List[Voting.BakerRolls], List[Voting.Ballot], Option[Voting.BallotCounts]),
      Tables.GovernanceRow
    ] {

      override def convert(
          from: (
              BlockData,
              Option[ProtocolId],
              List[Voting.BakerRolls],
              List[Voting.Ballot],
              Option[Voting.BallotCounts]
          )
      ): Id[Tables.GovernanceRow] = {
        val (block, proposal, listings, ballots, count) = from
        val blockHeaderMetadata: BlockHeaderMetadata = TezosTypes.discardGenesis(block.metadata)
        val (yayRolls, nayRolls, passRolls) = ballots.foldLeft((0, 0, 0)) {
          case ((yays, nays, passes), votingBallot) =>
            val rolls = listings.find(_.pkh == votingBallot.pkh).map(_.rolls).getOrElse(0)
            votingBallot.ballot match {
              case Vote("yay") => (yays + rolls, nays, passes)
              case Vote("nay") => (yays, nays + rolls, passes)
              case Vote("pass") => (yays, nays, passes + rolls)
              case Vote(notSupported) =>
                logger.error("Not supported vote type {}", notSupported)
                (yays, nays, passes)
            }
        }
        Tables.GovernanceRow(
          votingPeriod = blockHeaderMetadata.level.voting_period,
          votingPeriodKind = blockHeaderMetadata.voting_period_kind.toString,
          cycle = Some(blockHeaderMetadata.level.cycle),
          level = Some(blockHeaderMetadata.level.level),
          blockHash = block.hash.value,
          proposalHash = proposal.map(_.id).getOrElse(""),
          yayCount = count.map(_.yay),
          nayCount = count.map(_.nay),
          passCount = count.map(_.pass),
          yayRolls = Some(yayRolls),
          nayRolls = Some(nayRolls),
          passRolls = Some(passRolls),
          totalRolls = Some(yayRolls + nayRolls + passRolls)
        )
      }
    }

}
