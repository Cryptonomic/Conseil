package tech.cryptonomic.conseil.tezos

import scala.util.Try
import com.typesafe.scalalogging.LazyLogging
import cats.{Id, Show}
import cats.implicits._
import java.sql.Timestamp
import java.time.{Instant, ZoneOffset}
import java.time.format.DateTimeFormatter

import monocle.Getter
import io.scalaland.chimney.dsl._
import tech.cryptonomic.conseil.util.Conversion
import tech.cryptonomic.conseil.tezos.TezosNodeOperator.FetchRights
import tech.cryptonomic.conseil.tezos.FeeOperations._
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.TezosTypes.Voting.Vote
import tech.cryptonomic.conseil.tezos.michelson.contracts.TNSContract

object DatabaseConversions extends LazyLogging {

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

  /** Extracts date parts and time in UTC */
  def extractDateTime(timestamp: java.sql.Timestamp): (Int, Int, Int, String) = {
    val offsetDateTime = timestamp.toLocalDateTime.atOffset(ZoneOffset.UTC)
    val format = DateTimeFormatter.ofPattern("HH:mm:ss")
    (
      offsetDateTime.getYear,
      offsetDateTime.getMonth.getValue,
      offsetDateTime.getDayOfMonth,
      offsetDateTime.format(format)
    )
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
        val BlockTagged(hash, level, timestamp, cycle, period, accounts) = from
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
      val (year, month, day, time) = extractDateTime(toSql(header.timestamp))
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
        priority = header.priority,
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
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
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
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
        cycle = TezosOptics.Blocks.extractCycle(block),
        branch = block.operationGroups.find(h => h.hash == groupHash).map(_.branch.value),
        numberOfSlots = Some(metadata.slots.length),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
      )
  }

  private val convertNonceRevelation: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, SeedNonceRevelation(level, nonce, metadata)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
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
        cycle = TezosOptics.Blocks.extractCycle(block),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
      )
  }

  private val convertActivateAccount: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, ActivateAccount(pkh, secret, metadata)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
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
        cycle = TezosOptics.Blocks.extractCycle(block),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
      )
  }

  private val convertReveal: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Reveal(counter, fee, gas_limit, storage_limit, pk, source, metadata)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
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
        cycle = TezosOptics.Blocks.extractCycle(block),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        errors = extractResultErrorIds(metadata.operation_result.errors),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
      )
  }

  private val convertTransaction: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (
        block,
        groupHash,
        Transaction(
          counter,
          amount,
          fee,
          gas_limit,
          storage_limit,
          source,
          destination,
          parameters,
          parameters_micheline,
          metadata
        )
        ) =>
      /* If the parameters parsed correctly
       * - parameters will hold the michelson format
       * - parameters_micheline will hold the micheline format
       * if parsing failed
       * - parameters will hold the original micheline format
       * - parameters_micheline will be empty
       *
       * In the record we want to swap the meaning of those fields, such that micheline will always hold the
       * original data, and parameters will be empty if parsing failed
       */
      //will align to a common representation that will include entry-points, possibly empty
      val upgradeToLatest: ParametersCompatibility => Parameters = compat => compat.map(Parameters(_)).merge

      //could contain michelson or micheline
      val hopefullyMichelson = parameters.map(upgradeToLatest).map(_.value.expression)
      //could be empty
      val maybeMicheline = parameters_micheline.map(upgradeToLatest).map(_.value.expression)

      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
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
        parameters = maybeMicheline.flatMap(_ => hopefullyMichelson),
        parametersMicheline = maybeMicheline.orElse(hopefullyMichelson),
        parametersEntrypoints = parameters.map(upgradeToLatest).flatMap(_.entrypoint),
        status = Some(metadata.operation_result.status),
        consumedGas = metadata.operation_result.consumed_gas.flatMap(extractBigDecimal),
        storageSize = metadata.operation_result.storage_size.flatMap(extractBigDecimal),
        paidStorageSizeDiff = metadata.operation_result.paid_storage_size_diff.flatMap(extractBigDecimal),
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = TezosOptics.Blocks.extractCycle(block),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        errors = extractResultErrorIds(metadata.operation_result.errors),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
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
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
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
        cycle = TezosOptics.Blocks.extractCycle(block),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        errors = extractResultErrorIds(metadata.operation_result.errors),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
      )
  }

  private val convertDelegation: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Delegation(counter, source, fee, gas_limit, storage_limit, delegate, metadata)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
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
        cycle = TezosOptics.Blocks.extractCycle(block),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        errors = extractResultErrorIds(metadata.operation_result.errors),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
      )
  }

  private val convertBallot: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Ballot(ballot, proposal, source, ballotPeriod)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
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
        cycle = TezosOptics.Blocks.extractCycle(block),
        ballotPeriod = ballotPeriod,
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
      )
  }

  private val convertProposals: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Proposals(source, ballotPeriod, proposals)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
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
        cycle = TezosOptics.Blocks.extractCycle(block),
        ballotPeriod = ballotPeriod,
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
      )

  }

  private val convertUnhandledOperations: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, op) =>
      val kind = op match {
        case DoubleEndorsementEvidence => "double_endorsement_evidence"
        case DoubleBakingEvidence => "double_baking_evidence"
        case _ => ""
      }
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = kind,
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = TezosOptics.Blocks.extractCycle(block),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time
      )
  }

  /** Not all operations have some form of balance updates... we're ignoring some type, like ballots,
    * thus we can't extract from those anyway.
    * We get back an empty List, where not applicable
    * We define a typeclass/trait that encodes the availability of balance updates, to reuse it for operations as well as blocks
    * @tparam T the source type that contains the balance updates values
    * @tparam Label the source Label type, which has a `Show` constraint, to be representable as a string
    * @param balances an optic instance that lets extract the Map of tagged updates from the source
    * @param hashing an optic instance to extract an optional reference hash value
    */
  implicit def anyToBalanceUpdates[T, Label](
      implicit
      balances: Getter[T, Map[BlockTagged[Label], List[OperationMetadata.BalanceUpdate]]],
      hashing: Getter[T, Option[String]],
      showing: Show[Label]
  ) = new Conversion[List, T, Tables.BalanceUpdatesRow] {
    import cats.syntax.show._

    override def convert(from: T) =
      balances
        .get(from)
        .flatMap {
          case (BlockTagged(blockHash, blockLevel, _, cycle, period, tag), updates) =>
            updates.map {
              case OperationMetadata.BalanceUpdate(kind, change, category, contract, delegate, level) =>
                Tables.BalanceUpdatesRow(
                  id = 0,
                  source = tag.show,
                  sourceHash = hashing.get(from),
                  kind = kind,
                  accountId = contract.map(_.id).orElse(delegate.map(_.value)).getOrElse("N/A"),
                  change = BigDecimal(change),
                  level = level.map(BigDecimal(_)),
                  category = category,
                  blockId = blockHash.value,
                  blockLevel = blockLevel,
                  cycle = cycle,
                  period = period
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
      TezosOptics.Blocks
        .extractOperationsAlongWithInternalResults(from)
        .flatMap {
          case (group, (operations, internalResults)) =>
            val mainOperationData = operations.map(
              op =>
                (from, group.hash, op).convertTo[Tables.OperationsRow] ->
                    BlockTagged
                      .fromBlockData(from.data, op)
                      .convertToA[List, Tables.BalanceUpdatesRow]
            )
            val internalOperationData = internalResults.map { oop =>
              val op = oop.convertTo[Operation]
              (from, group.hash, op)
                .convertTo[Tables.OperationsRow]
                .copy(internal = true, nonce = Some(oop.nonce.toString)) -> BlockTagged
                .fromBlockData(from.data, op)
                .convertToA[List, Tables.BalanceUpdatesRow]
            }
            mainOperationData ++ internalOperationData
        }
        .toList

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
      Tables.BakersCheckpointRow
    ] {
      override def convert(from: (BlockHash, Int, Option[Instant], Option[Int], List[PublicKeyHash])) = {
        val (blockHash, blockLevel, _, _, pkhs) = from
        pkhs.map(
          keyHash =>
            Tables.BakersCheckpointRow(
              delegatePkh = keyHash.value,
              blockId = blockHash.value,
              blockLevel = blockLevel
            )
        )
      }

    }

  implicit val delegateToRow =
    new Conversion[Id, (BlockHash, Int, PublicKeyHash, Delegate, Option[Int], Option[Int]), Tables.BakersRow] {
      override def convert(from: (BlockHash, Int, PublicKeyHash, Delegate, Option[Int], Option[Int])) = {
        val (blockHash, blockLevel, keyHash, delegate, cycle, period) = from
        Tables.BakersRow(
          pkh = keyHash.value,
          blockId = blockHash.value,
          balance = extractBigDecimal(delegate.balance),
          frozenBalance = extractBigDecimal(delegate.frozen_balance),
          stakingBalance = extractBigDecimal(delegate.staking_balance),
          delegatedBalance = extractBigDecimal(delegate.delegated_balance),
          rolls = delegate.rolls.getOrElse(0),
          deactivated = delegate.deactivated,
          gracePeriod = delegate.grace_period,
          blockLevel = blockLevel,
          cycle = cycle,
          period = period
        )
      }
    }

  implicit val bakingRightsToRows =
    new Conversion[Id, (FetchRights, BakingRights), Tables.BakingRightsRow] {
      override def convert(
          from: (FetchRights, BakingRights)
      ): Tables.BakingRightsRow = {
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
    override def convert(from: BakingRights): Tables.BakingRightsRow = {
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
      (
          BlockData,
          Option[ProtocolId],
          List[Voting.BakerRolls],
          List[Voting.BakerRolls],
          List[Voting.Ballot],
          Option[Voting.BallotCounts],
          Option[Voting.BallotCounts]
      ),
      Tables.GovernanceRow
    ] {

      override def convert(
          from: (
              BlockData,
              Option[ProtocolId],
              List[Voting.BakerRolls],
              List[Voting.BakerRolls],
              List[Voting.Ballot],
              Option[Voting.BallotCounts],
              Option[Voting.BallotCounts]
          )
      ): Id[Tables.GovernanceRow] = {
        val (block, proposal, listings, listingsPerLevel, ballots, ballotCountsPerCycle, ballotCountsPerLevel) = from
        val blockHeaderMetadata: BlockHeaderMetadata = TezosTypes.discardGenesis(block.metadata)
        val (yayRolls, nayRolls, passRolls) = countRolls(listings, ballots)
        val (yayRollsPerLevel, nayRollsPerLevel, passRollsPerLevel) = countRolls(listingsPerLevel, ballots)
        Tables.GovernanceRow(
          votingPeriod = blockHeaderMetadata.level.voting_period,
          votingPeriodKind = blockHeaderMetadata.voting_period_kind.toString,
          cycle = Some(blockHeaderMetadata.level.cycle),
          level = Some(blockHeaderMetadata.level.level),
          blockHash = block.hash.value,
          proposalHash = proposal.map(_.id).getOrElse(""),
          yayCount = ballotCountsPerCycle.map(_.yay),
          nayCount = ballotCountsPerCycle.map(_.nay),
          passCount = ballotCountsPerCycle.map(_.pass),
          yayRolls = Some(yayRolls),
          nayRolls = Some(nayRolls),
          passRolls = Some(passRolls),
          totalRolls = Some(yayRolls + nayRolls + passRolls),
          blockYayCount = ballotCountsPerLevel.map(_.yay),
          blockNayCount = ballotCountsPerLevel.map(_.nay),
          blockPassCount = ballotCountsPerLevel.map(_.pass),
          blockYayRolls = Some(yayRollsPerLevel),
          blockNayRolls = Some(nayRollsPerLevel),
          blockPassRolls = Some(passRollsPerLevel)
        )
      }

      def countRolls(listings: List[Voting.BakerRolls], ballots: List[Voting.Ballot]): (Int, Int, Int) =
        ballots.foldLeft((0, 0, 0)) {
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
    }

  implicit val tnsNameRecordToRow =
    new Conversion[Id, TNSContract.NameRecord, Tables.TezosNamesRow] {
      def convert(from: TNSContract.NameRecord): cats.Id[Tables.TezosNamesRow] = {
        val registrationTimestamp = Try(java.time.ZonedDateTime.parse(from.registeredAt)).toOption.map(toSql)
        Tables.TezosNamesRow(
          name = from.name,
          owner = Some(from.owner.trim).filter(_.nonEmpty),
          resolver = Some(from.resolver),
          registeredAt = registrationTimestamp,
          registrationPeriod = Try(from.registrationPeriod.toInt).toOption,
          modified = Try(from.updated.toLowerCase.trim.toBoolean).toOption
        )
      }
    }

}
