package tech.cryptonomic.conseil.indexer.tezos

import java.sql.Timestamp
import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneOffset}

import cats.implicits._
import cats.{Id, Show}
import io.scalaland.chimney.dsl._
import monocle.Getter
import tech.cryptonomic.conseil.common.tezos
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Fee.AverageFees
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.TNSContract
import tech.cryptonomic.conseil.common.tezos.{Fork, Tables, TezosOptics}
import tech.cryptonomic.conseil.common.util.Conversion
import tech.cryptonomic.conseil.indexer.tezos.Tzip16MetadataJsonDecoders.Tzip16Metadata

import scala.util.Try
import tech.cryptonomic.conseil.indexer.tezos.michelson.contracts.SmartContracts

private[tezos] object TezosDatabaseConversions {

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
      Tables.FeesRow(
        low = from.low,
        medium = from.medium,
        high = from.high,
        timestamp = from.timestamp,
        kind = from.kind,
        cycle = from.cycle,
        level = Some(from.level),
        invalidatedAsof = None,
        forkId = Fork.mainForkId
      )
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
        val BlockTagged(BlockReference(hash, level, timestamp, cycle, period), accounts) = from
        accounts.map {
          case (id, Account(balance, delegate, script, counter, manager, spendable, isBaker, isActivated)) =>
            Tables.AccountsRow(
              accountId = id.value,
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
              isActivated = isActivated.getOrElse(false),
              forkId = Fork.mainForkId,
              scriptHash = script.map(it => SmartContracts.hashMichelsonScript(it.code.expression)),
              invalidatedAsof = None
            )
        }.toList
      }
    }

  implicit val blockAccountsToAccountHistoryRows =
    new Conversion[List, (BlockTagged[Map[AccountId, Account]], List[Tables.AccountsRow]), Tables.AccountsHistoryRow] {
      override def convert(
          from: (BlockTagged[Map[AccountId, Account]], List[Tables.AccountsRow])
      ): List[Tables.AccountsHistoryRow] = {
        import tech.cryptonomic.conseil.common.util.Conversion.Syntax._
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
            .withFieldConst(
              _.asof,
              Timestamp.from(blockTaggedAccounts.ref.timestamp.getOrElse(Instant.ofEpochMilli(0)))
            )
            .withFieldConst(_.cycle, blockTaggedAccounts.ref.cycle)
            .withFieldConst(_.isActiveBaker, isActiveBaker)
            .withFieldConst(_.forkId, Fork.mainForkId)
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
                  Timestamp.from(blockTaggedAccounts.ref.timestamp.getOrElse(Instant.ofEpochMilli(0)))
                )
                .withFieldConst(_.cycle, blockTaggedAccounts.ref.cycle)
                .withFieldConst(_.blockLevel, blockTaggedAccounts.ref.level)
                .withFieldConst(_.blockId, blockTaggedAccounts.ref.hash.value)
                .withFieldConst(_.isActiveBaker, Some(false))
                .transform
            }

        (untouchedAccounts ::: touchedAccounts).distinct
      }
    }

  implicit val blockToBlocksRow = new Conversion[Id, Block, Tables.BlocksRow] {
    override def convert(from: Block) = {
      import TezosOptics.Blocks._
      val header = from.data.header
      val metadata = discardGenesis(from.data.metadata)
      val CurrentVotes(expectedQuorum, proposal) = from.votes
      val (year, month, day, time) = extractDateTime(toSql(header.timestamp))
      Tables.BlocksRow(
        level = header.level,
        proto = header.proto,
        predecessor = header.predecessor.value,
        timestamp = toSql(header.timestamp),
        fitness = header.fitness.mkString(","),
        context = Some(header.context), //put in later
        signature = header.signature,
        protocol = from.data.protocol,
        chainId = from.data.chain_id,
        hash = from.data.hash.value,
        operationsHash = header.operations_hash,
        periodKind = metadata.flatMap(extractVotingPeriodKind).map(_.toString),
        currentExpectedQuorum = expectedQuorum,
        activeProposal = proposal.map(_.id),
        baker = metadata.map(_.baker.value),
        consumedGas = metadata.flatMap(md => extractBigDecimal(md.consumed_gas)),
        metaLevel = metadata.flatMap(extractLevel),
        metaLevelPosition = metadata.flatMap(extractLevelPosition),
        metaCycle = metadata.flatMap(extractCycle),
        metaCyclePosition = metadata.flatMap(extractCyclePosition),
        metaVotingPeriod = metadata.flatMap(extractVotingPeriod),
        metaVotingPeriodPosition = metadata.flatMap(extractVotingPeriodPosition),
        priority = header.priority,
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None
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
          blockLevel = from.data.header.level,
          forkId = Fork.mainForkId,
          invalidatedAsof = None
        )
      }
  }

  //Cannot directly convert a single operation to a row, because we need the block and operation-group info to build the database row
  implicit val operationToOperationsRow =
    new Conversion[Id, (Block, OperationHash, Operation), Tables.OperationsRow] {
      override def convert(from: (Block, OperationHash, Operation)) =
        (convertEndorsement orElse
            convertEndorsementWithSlot orElse
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

  private val convertEndorsementWithSlot: PartialFunction[
    (Block, OperationHash, Operation),
    Tables.OperationsRow
  ] = {
    case (block, groupHash, EndorsementWithSlot(endorsement, metadata, blockOrder)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "endorsement_with_slot",
        level = Some(endorsement.operations.level),
        delegate = Some(metadata.delegate.value),
        slots = Some(metadata.slots).map(concatenateToString),
        operationOrder = blockOrder,
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
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        pkh = None,
        fee = None,
        counter = None,
        gasLimit = None,
        storageLimit = None,
        publicKey = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        managerPubkey = None,
        balance = None,
        proposal = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        status = None,
        consumedGas = None,
        storageSize = None,
        paidStorageSizeDiff = None,
        originatedContracts = None,
        ballot = None,
        ballotPeriod = None,
        errors = None,
        secret = None,
        source = None
      )
  }

  private val convertEndorsement: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Endorsement(level, metadata, blockOrder)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "endorsement",
        level = Some(level),
        delegate = Some(metadata.delegate.value),
        slots = Some(metadata.slots).map(concatenateToString),
        operationOrder = blockOrder,
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
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        pkh = None,
        fee = None,
        counter = None,
        gasLimit = None,
        storageLimit = None,
        publicKey = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        managerPubkey = None,
        balance = None,
        proposal = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        status = None,
        consumedGas = None,
        storageSize = None,
        paidStorageSizeDiff = None,
        originatedContracts = None,
        ballot = None,
        ballotPeriod = None,
        errors = None,
        secret = None,
        source = None
      )
  }

  private val convertNonceRevelation: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, SeedNonceRevelation(level, nonce, metadata, blockOrder)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        branch = None,
        numberOfSlots = None,
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "seed_nonce_revelation",
        level = Some(level),
        nonce = Some(nonce.value),
        operationOrder = blockOrder,
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = TezosOptics.Blocks.extractCycle(block),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        pkh = None,
        fee = None,
        counter = None,
        gasLimit = None,
        storageLimit = None,
        publicKey = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        managerPubkey = None,
        balance = None,
        proposal = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        status = None,
        consumedGas = None,
        storageSize = None,
        paidStorageSizeDiff = None,
        originatedContracts = None,
        ballot = None,
        ballotPeriod = None,
        errors = None,
        secret = None,
        source = None,
        delegate = None,
        slots = None
      )
  }

  private val convertActivateAccount: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, ActivateAccount(pkh, secret, metadata, blockOrder)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "activate_account",
        operationOrder = blockOrder,
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
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        fee = None,
        counter = None,
        gasLimit = None,
        storageLimit = None,
        publicKey = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        managerPubkey = None,
        balance = None,
        proposal = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        status = None,
        consumedGas = None,
        storageSize = None,
        paidStorageSizeDiff = None,
        originatedContracts = None,
        ballot = None,
        ballotPeriod = None,
        errors = None,
        source = None,
        branch = None,
        delegate = None,
        slots = None,
        level = None,
        numberOfSlots = None
      )
  }

  private val convertReveal: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Reveal(counter, fee, gas_limit, storage_limit, pk, source, metadata, blockOrder)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "reveal",
        operationOrder = blockOrder,
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
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        pkh = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        managerPubkey = None,
        balance = None,
        proposal = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        storageSize = None,
        paidStorageSizeDiff = None,
        originatedContracts = None,
        ballot = None,
        ballotPeriod = None,
        branch = None,
        numberOfSlots = None,
        secret = None,
        delegate = None,
        slots = None,
        level = None
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
          metadata,
          blockOrder
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
        operationOrder = blockOrder,
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
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        pkh = None,
        publicKey = None,
        managerPubkey = None,
        balance = None,
        proposal = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        originatedContracts = None,
        ballot = None,
        ballotPeriod = None,
        secret = None,
        branch = None,
        numberOfSlots = None,
        delegate = None,
        slots = None,
        level = None
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
          metadata,
          blockOrder
        )
        ) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "origination",
        delegate = delegate.map(_.value),
        operationOrder = blockOrder,
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
        storageMicheline = script.flatMap(_.storage_micheline.map(_.expression)),
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
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        pkh = None,
        publicKey = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        proposal = None,
        ballot = None,
        ballotPeriod = None,
        secret = None,
        branch = None,
        numberOfSlots = None,
        slots = None,
        level = None
      )
  }

  private val convertDelegation: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (
        block,
        groupHash,
        Delegation(counter, source, fee, gas_limit, storage_limit, delegate, metadata, blockOrder)
        ) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "delegation",
        delegate = delegate.map(_.value),
        operationOrder = blockOrder,
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
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        pkh = None,
        publicKey = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        managerPubkey = None,
        balance = None,
        proposal = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        storageSize = None,
        paidStorageSizeDiff = None,
        originatedContracts = None,
        ballot = None,
        ballotPeriod = None,
        secret = None,
        branch = None,
        numberOfSlots = None,
        slots = None,
        level = None
      )
  }

  private val convertBallot: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Ballot(ballot, proposal, source, ballotPeriod, blockOrder)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "ballot",
        operationOrder = blockOrder,
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
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        pkh = None,
        fee = None,
        counter = None,
        gasLimit = None,
        storageLimit = None,
        publicKey = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        managerPubkey = None,
        balance = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        status = None,
        consumedGas = None,
        storageSize = None,
        paidStorageSizeDiff = None,
        originatedContracts = None,
        errors = None,
        secret = None,
        delegate = None,
        branch = None,
        numberOfSlots = None,
        slots = None,
        level = None
      )
  }

  private val convertProposals: PartialFunction[(Block, OperationHash, Operation), Tables.OperationsRow] = {
    case (block, groupHash, Proposals(source, ballotPeriod, proposals, blockOrder)) =>
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = "proposals",
        operationOrder = blockOrder,
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
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        pkh = None,
        fee = None,
        counter = None,
        gasLimit = None,
        storageLimit = None,
        publicKey = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        managerPubkey = None,
        balance = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        status = None,
        consumedGas = None,
        storageSize = None,
        paidStorageSizeDiff = None,
        originatedContracts = None,
        ballot = None,
        errors = None,
        secret = None,
        delegate = None,
        branch = None,
        numberOfSlots = None,
        slots = None,
        level = None
      )

  }

  private val convertUnhandledOperations: PartialFunction[
    (Block, OperationHash, Operation),
    Tables.OperationsRow
  ] = {
    case (block, groupHash, op) =>
      val kind = op match {
        case DoubleEndorsementEvidence(_) => "double_endorsement_evidence"
        case DoubleBakingEvidence(_) => "double_baking_evidence"
        case _ => ""
      }
      val (year, month, day, time) = extractDateTime(toSql(block.data.header.timestamp))
      Tables.OperationsRow(
        operationId = 0,
        operationGroupHash = groupHash.value,
        kind = kind,
        operationOrder = op.blockOrder,
        blockHash = block.data.hash.value,
        blockLevel = block.data.header.level,
        timestamp = toSql(block.data.header.timestamp),
        internal = false,
        cycle = TezosOptics.Blocks.extractCycle(block),
        period = TezosOptics.Blocks.extractPeriod(block.data.metadata),
        utcYear = year,
        utcMonth = month,
        utcDay = day,
        utcTime = time,
        forkId = Fork.mainForkId,
        invalidatedAsof = None,
        nonce = None,
        pkh = None,
        fee = None,
        counter = None,
        gasLimit = None,
        storageLimit = None,
        publicKey = None,
        amount = None,
        destination = None,
        parameters = None,
        parametersEntrypoints = None,
        parametersMicheline = None,
        managerPubkey = None,
        balance = None,
        proposal = None,
        spendable = None,
        delegatable = None,
        script = None,
        storage = None,
        storageMicheline = None,
        status = None,
        consumedGas = None,
        storageSize = None,
        paidStorageSizeDiff = None,
        originatedContracts = None,
        ballot = None,
        ballotPeriod = None,
        errors = None,
        secret = None,
        source = None,
        delegate = None,
        branch = None,
        numberOfSlots = None,
        slots = None,
        level = None
      )
  }

  /** Not all operations have some form of balance updates... we're ignoring some type, like double_x_evidence,
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
          case (BlockTagged(BlockReference(blockHash, blockLevel, _, cycle, period), tag), updates) =>
            updates.map {
              case OperationMetadata.BalanceUpdate(kind, change, category, contract, delegate, level) =>
                Tables.BalanceUpdatesRow(
                  id = 0,
                  source = tag.show,
                  sourceHash = hashing.get(from),
                  kind = kind,
                  accountId = contract.map(_.id).orElse(delegate.map(_.value)).getOrElse("N/A"),
                  change = BigDecimal(change),
                  level = level,
                  category = category,
                  blockId = blockHash.value,
                  blockLevel = blockLevel,
                  cycle = cycle,
                  period = period,
                  forkId = Fork.mainForkId
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
    import OperationBalances._
    import SymbolSourceLabels.Show._
    import tech.cryptonomic.conseil.common.util.Conversion.Syntax._

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
            val internalOperationData = internalResults.map {
              case oop =>
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
    new Conversion[
      List,
      (TezosBlockHash, BlockLevel, Option[Instant], Option[Int], Option[Int], List[AccountId]),
      Tables.AccountsCheckpointRow
    ] {
      override def convert(
          from: (TezosBlockHash, BlockLevel, Option[Instant], Option[Int], Option[Int], List[AccountId])
      ) = {
        val (blockHash, blockLevel, timestamp, cycle, _, ids) = from
        ids.map(
          accountId =>
            Tables.AccountsCheckpointRow(
              accountId = accountId.value,
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
      (TezosBlockHash, BlockLevel, Option[Instant], Option[Int], Option[Int], List[PublicKeyHash]),
      Tables.BakersCheckpointRow
    ] {
      override def convert(
          from: (TezosBlockHash, BlockLevel, Option[Instant], Option[Int], Option[Int], List[PublicKeyHash])
      ) = {
        val (blockHash, blockLevel, _, cycle, period, pkhs) = from
        pkhs.map(
          keyHash =>
            Tables.BakersCheckpointRow(
              delegatePkh = keyHash.value,
              blockId = blockHash.value,
              blockLevel = blockLevel,
              cycle = cycle,
              period = period
            )
        )
      }

    }

  implicit val blockTaggedBakerToRow =
    new Conversion[Id, BlockTagged[(PublicKeyHash, Delegate)], Tables.BakersRow] {
      override def convert(from: BlockTagged[(PublicKeyHash, Delegate)]) = {
        val BlockTagged(BlockReference(hash, level, _, cycle, period), (pkh, delegate)) = from
        Tables.BakersRow(
          pkh = pkh.value,
          blockId = hash.value,
          balance = extractBigDecimal(delegate.balance),
          frozenBalance = extractBigDecimal(delegate.frozen_balance),
          stakingBalance = extractBigDecimal(delegate.staking_balance),
          delegatedBalance = extractBigDecimal(delegate.delegated_balance),
          rolls = delegate.rolls.getOrElse(0),
          deactivated = delegate.deactivated,
          gracePeriod = delegate.grace_period,
          blockLevel = level,
          cycle = cycle,
          period = period,
          forkId = Fork.mainForkId
        )
      }
    }

  implicit val bakerHistoryToRow =
    new Conversion[Id, (Tables.BakersRow, Option[Instant]), Tables.BakersHistoryRow] {
      override def convert(from: (Tables.BakersRow, Option[Instant])) = {
        val (bakersRow, instant) = from
        bakersRow
          .into[Tables.BakersHistoryRow]
          .withFieldConst(_.asof, Timestamp.from(instant.getOrElse(Instant.ofEpochMilli(0))))
          .withFieldConst(_.forkId, Fork.mainForkId)
          .transform
      }
    }

  implicit val bakingRightsToRows =
    new Conversion[Id, (RightsFetchKey, BakingRights), Tables.BakingRightsRow] {
      override def convert(
          from: (RightsFetchKey, BakingRights)
      ): Tables.BakingRightsRow = {
        val (fetchKey, bakingRights) = from
        Tables.BakingRightsRow(
          blockHash = Some(fetchKey.blockHash.value),
          blockLevel = bakingRights.level,
          delegate = bakingRights.delegate,
          priority = bakingRights.priority,
          estimatedTime = bakingRights.estimated_time.map(toSql),
          cycle = fetchKey.cycle,
          governancePeriod = fetchKey.governancePeriod,
          forkId = Fork.mainForkId
        )
      }
    }

  implicit val endorsingRightsToRows =
    new Conversion[List, (RightsFetchKey, EndorsingRights), Tables.EndorsingRightsRow] {
      override def convert(
          from: (RightsFetchKey, EndorsingRights)
      ): List[Tables.EndorsingRightsRow] = {
        val (fetchKey, endorsingRights) = from
        endorsingRights.slots.map { slot =>
          Tables.EndorsingRightsRow(
            blockHash = Some(fetchKey.blockHash.value),
            blockLevel = endorsingRights.level,
            delegate = endorsingRights.delegate,
            slot = slot,
            estimatedTime = endorsingRights.estimated_time.map(toSql),
            governancePeriod = fetchKey.governancePeriod,
            endorsedBlock = endorsingRights.endorsedBlock,
            forkId = Fork.mainForkId,
            invalidatedAsof = None,
            cycle = None
          )
        }
      }
    }

  implicit val bakingRightsToRowsWithoutBlockHash = new Conversion[Id, BakingRights, Tables.BakingRightsRow] {
    override def convert(from: BakingRights): Tables.BakingRightsRow =
      Tables.BakingRightsRow(
        blockHash = None,
        blockLevel = from.level,
        delegate = from.delegate,
        priority = from.priority,
        estimatedTime = from.estimated_time.map(toSql),
        cycle = from.cycle,
        governancePeriod = from.governancePeriod,
        forkId = Fork.mainForkId,
        invalidatedAsof = None
      )
  }

  implicit val endorsingRightsToRowsWithoutBlockHash =
    new Conversion[List, EndorsingRights, Tables.EndorsingRightsRow] {
      override def convert(from: EndorsingRights): List[Tables.EndorsingRightsRow] =
        from.slots.map { slot =>
          Tables.EndorsingRightsRow(
            blockHash = None,
            blockLevel = from.level,
            delegate = from.delegate,
            slot = slot,
            estimatedTime = from.estimated_time.map(toSql),
            cycle = from.cycle,
            governancePeriod = from.governancePeriod,
            endorsedBlock = from.endorsedBlock,
            forkId = Fork.mainForkId,
            invalidatedAsof = None
          )
        }
    }

  implicit val governanceConv =
    new Conversion[
      Id,
      TezosGovernanceOperations.GovernanceAggregate,
      Tables.GovernanceRow
    ] {
      import TezosGovernanceOperations.{GovernanceAggregate, VoteRollsCounts}

      override def convert(
          from: GovernanceAggregate
      ): Tables.GovernanceRow = {
        import TezosOptics.Blocks._

        val metadata = from.metadata
        val VoteRollsCounts(yayRolls, nayRolls, passRolls) = from.allRolls
        val VoteRollsCounts(yayRollsPerLevel, nayRollsPerLevel, passRollsPerLevel) = from.rollsPerLevel

        Tables.GovernanceRow(
          votingPeriod = extractVotingPeriod(metadata).get,
          votingPeriodKind = extractVotingPeriodKind(metadata).get.toString,
          cycle = extractCycle(metadata),
          level = extractLevel(metadata),
          blockHash = from.hash.value,
          proposalHash = from.proposalId.map(_.id).getOrElse(""),
          yayCount = from.ballotsPerCycle.map(_.yay),
          nayCount = from.ballotsPerCycle.map(_.nay),
          passCount = from.ballotsPerCycle.map(_.pass),
          yayRolls = Some(yayRolls),
          nayRolls = Some(nayRolls),
          passRolls = Some(passRolls),
          totalRolls = Some(yayRolls + nayRolls + passRolls),
          blockYayCount = from.ballotsPerLevel.map(_.yay),
          blockNayCount = from.ballotsPerLevel.map(_.nay),
          blockPassCount = from.ballotsPerLevel.map(_.pass),
          blockYayRolls = Some(yayRollsPerLevel),
          blockNayRolls = Some(nayRollsPerLevel),
          blockPassRolls = Some(passRollsPerLevel),
          forkId = Fork.mainForkId,
          invalidatedAsof = None
        )

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

  implicit val tzip16MetadataToTokenMetadataRow =
    new Conversion[Id, ((Tables.OperationsRow, String), (String, Tzip16Metadata)), Tables.MetadataRow] {

      /** Takes a `FROM` object and retuns the `TO` object, with an effect `F`. */
      override def convert(
          from: ((Tables.OperationsRow, String), (String, Tzip16Metadata))
      ): Id[tezos.Tables.MetadataRow] = {
        val ((rt, str), (rawJson, metadata)) = from
        Tables.MetadataRow(
          address = rt.source.get,
          rawMetadata = rawJson,
          description = Some(metadata.description),
          name = metadata.name,
          lastUpdated = Some(Timestamp.from(Instant.now))
        )
      }
    }

  implicit val tzip16MetadataToNftsRow =
    new Conversion[
      Id,
      ((Tables.RegisteredTokensRow, Tables.BigMapContentsRow, String), (String, Tzip16Metadata)),
      Tables.NftsRow
    ] {

      /** Takes a `FROM` object and retuns the `TO` object, with an effect `F`. */
      override def convert(
          from: ((Tables.RegisteredTokensRow, Tables.BigMapContentsRow, String), (String, Tzip16Metadata))
      ): Id[tezos.Tables.NftsRow] = {
        val ((ar, bm, str), (rawJson, metadata)) = from
        Tables.NftsRow(
          contractAddress = ar.address,
          contractName = ar.name,
          assetType = metadata.description,
          assetLocation = metadata.source.flatMap(_.location).getOrElse(str),
          rawMetadata = rawJson,
          timestamp = Timestamp.from(Instant.now),
          opGroupHash = bm.operationGroupId.get,
          blockLevel = bm.blockLevel.get
        )
      }
    }

}
