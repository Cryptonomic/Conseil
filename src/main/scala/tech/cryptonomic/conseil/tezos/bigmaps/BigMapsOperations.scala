package tech.cryptonomic.conseil.tezos.bigmaps

import com.typesafe.scalalogging.LazyLogging
import com.github.tminglei.slickpg.ExPostgresProfile

import scala.concurrent.ExecutionContext
import cats.implicits._
import tech.cryptonomic.conseil.util.Conversion.Syntax._
import tech.cryptonomic.conseil.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.tezos.TezosTypes.{
  Block,
  Contract,
  ContractId,
  Decimal,
  OperationHash,
  ParametersCompatibility
}
import tech.cryptonomic.conseil.tezos.TezosTypes.Contract.BigMapUpdate
import tech.cryptonomic.conseil.tezos.Tables
import tech.cryptonomic.conseil.tezos.Tables.{BigMapContentsRow, BigMapsRow, OriginatedAccountMapsRow}
import tech.cryptonomic.conseil.tezos.TezosOptics
import tech.cryptonomic.conseil.tezos.michelson.contracts.TNSContract

/** Defines big-map-diffs specific handling, from block data extraction to database storage
  *
  * @param profile is the actual profile needed to define database operations
  */
case class BigMapsOperations[Profile <: ExPostgresProfile](profile: Profile) extends LazyLogging {
  import profile.api._

  /** Create an action to find and copy big maps based on the diff contained in the blocks
    *
    * @param blocks the blocks containing the diffs
    * @param ec needed to compose db operations
    * @return the count of records added
    */
  def copyContent(blocks: List[Block])(implicit ec: ExecutionContext): DBIO[Int] = {
    val copyDiffs = if (logger.underlying.isDebugEnabled()) {
      val diffsPerBlock = blocks.map(b => b.data.hash.value -> TezosOptics.Blocks.readBigMapDiffCopy.getAll(b))
      diffsPerBlock.foreach {
        case (hash, diffs) if diffs.nonEmpty =>
          logger.debug(
            "For block hash {}, I'm about to copy the following big maps data: \n\t{}",
            hash.value,
            diffs.mkString(", ")
          )
        case _ =>
      }
      diffsPerBlock.map(_._2).flatten
    } else blocks.flatMap(TezosOptics.Blocks.readBigMapDiffCopy.getAll)

    //need to load the sources and copy them with a new destination id
    val contentCopies = copyDiffs.collect {
      case Contract.BigMapCopy(_, Decimal(sourceId), Decimal(destinationId)) =>
        Tables.BigMapContents
          .filter(_.bigMapId === sourceId)
          .map(it => (destinationId, it.key, it.keyHash, it.operationGroupId, it.value))
          .result
          .map(rows => rows.map(BigMapContentsRow.tupled).toList)
    }

    DBIO
      .sequence(contentCopies)
      .flatMap { updateRows =>
        val copies = updateRows.flatten
        logger.info(
          "{} big maps will be copied in the db.",
          if (copies.nonEmpty) s"A total of ${copies.size}"
          else "No"
        )
        Tables.BigMapContents.insertOrUpdateAll(copies)
      }
      .map(_.sum)

  }

  /** Create an action to delete big maps and all related content based on the diff contained in the blocks
    *
    * @param blocks the blocks containing the diffs
    * @return a database operation with no results
    */
  def removeMaps(blocks: List[Block]): DBIO[Unit] = {

    val removalDiffs = if (logger.underlying.isDebugEnabled()) {
      val diffsPerBlock = blocks.map(b => b.data.hash.value -> TezosOptics.Blocks.readBigMapDiffRemove.getAll(b))
      diffsPerBlock.foreach {
        case (hash, diffs) if diffs.nonEmpty =>
          logger.debug(
            "For block hash {}, I'm about to delete the big maps for ids: \n\t{}",
            hash.value,
            diffs.mkString(", ")
          )
        case _ =>
      }
      diffsPerBlock.map(_._2).flatten
    } else blocks.flatMap(TezosOptics.Blocks.readBigMapDiffRemove.getAll)

    val idsToRemove = removalDiffs.collect {
      case Contract.BigMapRemove(_, Decimal(bigMapId)) =>
        bigMapId
    }.toSet

    logger.info(
      "{} big maps will be removed from the db.",
      if (idsToRemove.nonEmpty) s"A total of ${idsToRemove.size}" else "No"
    )

    DBIO.seq(
      Tables.BigMapContents.filter(_.bigMapId inSet idsToRemove).delete,
      Tables.BigMaps.filter(_.bigMapId inSet idsToRemove).delete,
      Tables.OriginatedAccountMaps.filter(_.bigMapId inSet idsToRemove).delete
    )
  }

  /** Creates an action to add new maps based on the diff contained in the blocks
    *
    * @param blocks the blocks containing the diffs
    * @return the count of records added, if available from the underlying db-engine
    */
  def saveMaps(blocks: List[Block]): DBIO[Option[Int]] = {
    import TezosOptics.Operations.extractAppliedOriginationsResults

    val diffsPerBlock = blocks.flatMap(
      b =>
        extractAppliedOriginationsResults(b)
          .flatMap(_.big_map_diff.toList.flatMap(keepLatestDiffsFormat))
          .map(
            diff =>
              BigMapsConversions.BlockBigMapDiff(b.data.hash, b.data.header.operations_hash.map(OperationHash), diff)
          )
    )

    val maps = if (logger.underlying.isDebugEnabled()) {
      val rowsPerBlock = diffsPerBlock
        .map(it => it.get._1 -> it.convertToA[Option, BigMapsRow])
        .filterNot(_._2.isEmpty)
        .groupBy { case (hash, _) => hash }
        .mapValues(entries => List.concat(entries.map(_._2.toList): _*))
        .toMap

      rowsPerBlock.foreach {
        case (hash, rows) =>
          logger.debug(
            "For block hash {}, I'm about to add the following big maps: \n\t{}",
            hash.value,
            rows.mkString(", ")
          )
      }

      rowsPerBlock.map(_._2).flatten
    } else diffsPerBlock.flatMap(_.convertToA[Option, BigMapsRow].toList)

    logger.info("{} big maps will be added to the db.", if (maps.nonEmpty) s"A total of ${maps.size}" else "No")
    Tables.BigMaps ++= maps
  }

  /** Creates an action to add or replace map contents based on the diff contained in the blocks
    * The contents might override existing data with the latest, based on block level
    *
    * @param blocks the blocks containing the diffs
    * @return the count of records added, if available from the underlying db-engine
    */
  def upsertContent(blocks: List[Block]): DBIO[Option[Int]] = {
    import TezosOptics.Operations.extractAppliedTransactionsResults

    val diffsPerBlock = blocks.flatMap(
      b =>
        extractAppliedTransactionsResults(b)
          .flatMap(_.big_map_diff.toList.flatMap(keepLatestDiffsFormat))
          .map(
            diff =>
              BigMapsConversions.BlockBigMapDiff(b.data.hash, b.data.header.operations_hash.map(OperationHash), diff)
          )
    )

    val rowsPerBlock = diffsPerBlock
      .map(it => it.get._1 -> it.convertToA[Option, BigMapContentsRow])
      .filterNot(_._2.isEmpty)
      .groupBy { case (hash, _) => hash }
      .mapValues(entries => List.concat(entries.map(_._2.toList): _*))
      .toMap

    if (logger.underlying.isDebugEnabled()) {
      rowsPerBlock.foreach {
        case (hash, rows) =>
          logger.debug(
            "For block hash {}, I'm about to update big map contents with the following data: \n\t{}",
            hash.value,
            rows.mkString(", ")
          )
      }
    }

    //we want to use block levels to figure out correct processing order
    //and then collect only the latest contents for each map-id and key
    val newContent = blocks
      .sortBy(_.data.header.level)(Ordering[Int].reverse)
      .foldLeft(Map.empty[(BigDecimal, String), BigMapContentsRow]) {
        case (collected, block) =>
          val seen = collected.keySet
          val rows = blocks
            .flatMap(b => rowsPerBlock.getOrElse(b.data.hash, List.empty))
            .filterNot(row => seen((row.bigMapId, row.key)))
          collected ++ rows.map(row => (row.bigMapId, row.key) -> row)
      }
      .values

    logger.info(
      "{} big map content entries will be added.",
      if (newContent.nonEmpty) s"A total of ${newContent.size}" else "No"
    )
    Tables.BigMapContents.insertOrUpdateAll(newContent)
  }

  /** Takes from blocks referring a big map allocation the reference to the
    * corresponding account, storing that reference in the database
    *
    * @param blocks the blocks containing the diffs
    * @return the count of records added, if available from the underlying db-engine
    */
  def saveContractOrigin(blocks: List[Block]): DBIO[List[OriginatedAccountMapsRow]] = {
    import TezosOptics.Operations.extractAppliedOriginationsResults

    val diffsPerBlock = blocks.flatMap(
      b =>
        extractAppliedOriginationsResults(b).flatMap { results =>
          for {
            contractIds <- results.originated_contracts.toList
            diff <- results.big_map_diff.toList.flatMap(keepLatestDiffsFormat)
          } yield BigMapsConversions.BlockContractIdsBigMapDiff((b.data.hash, contractIds, diff))
        }
    )

    val refs = if (logger.underlying.isDebugEnabled()) {
      val rowsPerBlock = diffsPerBlock
        .map(it => it.get._1 -> it.convertToA[List, OriginatedAccountMapsRow])
        .filterNot(_._2.isEmpty)
        .groupBy { case (hash, _) => hash }
        .mapValues(entries => List.concat(entries.map(_._2): _*))
        .toMap

      rowsPerBlock.foreach {
        case (hash, rows) =>
          logger.debug(
            "For block hash {}, I'm about to add the following links from big maps to originated accounts: \n\t{}",
            hash.value,
            rows.mkString(", ")
          )
      }

      rowsPerBlock.map(_._2).flatten.toList
    } else diffsPerBlock.flatMap(_.convertToA[List, OriginatedAccountMapsRow])

    logger.info("{} big map accounts references will be made.", if (refs.nonEmpty) s"A total of ${refs.size}" else "No")
    //the returned DBIOAction will provide the rows just added
    (Tables.OriginatedAccountMaps ++= refs) andThen DBIO.successful(refs)
  }

  /** Updates the reference to the stored big map, for accounts associated to a token contract */
  def initTokenMaps(
      contractsReferences: List[OriginatedAccountMapsRow]
  )(implicit tokenContracts: TokenContracts): Unit =
    contractsReferences.foreach {
      case OriginatedAccountMapsRow(mapId, accountId) if tokenContracts.isKnownToken(ContractId(accountId)) =>
        tokenContracts.setMapId(ContractId(accountId), mapId)
      case _ =>
    }

  /** Updates the reference to the stored big maps, for accounts associated to a name-service contract */
  def initTNSMaps(
      contractsReferences: List[OriginatedAccountMapsRow]
  )(implicit ec: ExecutionContext, tnsContracts: TNSContract): DBIO[Unit] = {
    //fetch the right maps
    val mapsQueries: List[DBIO[Option[(String, BigMapsRow)]]] =
      contractsReferences.collect {
        case OriginatedAccountMapsRow(mapId, accountId) if tnsContracts.isKnownRegistrar(ContractId(accountId)) =>
          Tables.BigMaps
            .findBy(_.bigMapId)
            .applied(mapId)
            .map(accountId -> _) //track each map row with the originated account
            .result
            .headOption
      }
    //put together the values and record on the TNS
    DBIO.sequence(mapsQueries).map(collectMapsByContract _ andThen setOnTNS)
  }

  /* Reorganize the input list to discard empty values and group them by first element, i.e. the contract id */
  private def collectMapsByContract(ids: List[Option[(String, BigMapsRow)]]): Map[ContractId, List[BigMapsRow]] =
    ids.flattenOption.groupBy {
      case (contractId, row) => ContractId(contractId)
    }.mapValues(values => values.map(_._2))

  /* For each entry, tries to pass the values to the TNS object to initialize the map ids properly */
  private def setOnTNS(maps: Map[ContractId, List[BigMapsRow]])(implicit tnsContracts: TNSContract) =
    maps.foreach {
      case (contractId, rows) => tnsContracts.setMaps(contractId, rows)
    }

  /** Matches blocks' transactions to extract updated balance for any contract corresponding to a known
    * token definition
    *
    * We only consider transactions whose source starts with a valid contract address hash
    *
    * @param blocks containing the possible token exchange operations
    * @param ec needed to sequence multiple database operations
    * @return optional count of rows stored on db
    */
  def updateTokenBalances(
      blocks: List[Block]
  )(implicit ec: ExecutionContext, tokenContracts: TokenContracts): DBIO[Option[Int]] = {
    import slickeffect.implicits._
    import TezosOptics.Operations.extractAppliedTransactions

    val toSql = (zdt: java.time.ZonedDateTime) => java.sql.Timestamp.from(zdt.toInstant)

    //we first extract all data available from the blocks themselves, as necessary to make a proper balance entry
    val tokenUpdates = blocks.flatMap { b =>
      val transactions = extractAppliedTransactions(b).filter {
        case Left(op) => tokenContracts.isKnownToken(op.destination)
        case Right(op) => tokenContracts.isKnownToken(op.destination)
      }

      //now extract relevant diffs for each destination, along with call parameters
      val updatesMap: Map[ContractId, (Option[ParametersCompatibility], List[BigMapUpdate])] = transactions.map {
        case Left(op) =>
          op.destination -> (op.parameters_micheline.orElse(op.parameters), op.metadata.operation_result.big_map_diff)
        case Right(op) =>
          op.destination -> (op.parameters_micheline.orElse(op.parameters), op.result.big_map_diff)
      }.toMap.mapValues {
        case (optionalParams, optionalDiffs) =>
          optionalParams -> keepLatestUpdateFormat(optionalDiffs.toList.flatten)

      }

      if (logger.underlying.isDebugEnabled()) {
        logger.debug(
          "For block hash {}, I'm about to extract the token balance updates from the following transactions' data : \n{}",
          b.data.hash.value,
          updatesMap.map {
            case (tokenId, (params, updates)) =>
              val paramsValue = params.fold("missing params") {
                case Left(params) => params.toString
                case Right(micheline) => micheline.toString
              }
              val updateString = updates.mkString("\t", "\n\t", "\n")
              s"Token ${tokenId.id}:\n $paramsValue -> $updateString"
          }.mkString("\n")
        )
      }

      //convert packed data
      BigMapsConversions
        .TokenUpdatesInput(b, updatesMap)
        .convertToA[List, BigMapsConversions.TokenUpdate]
    }

    //now we need to check on the token registry for matching contracts, to get a valid token-id as defined on the db
    val rowOptions = tokenUpdates.map { tokenUpdate =>
      val blockData = tokenUpdate.block.data

      Tables.RegisteredTokens
        .filter(_.accountId === tokenUpdate.tokenContractId.id)
        .map(_.id)
        .result
        .map { results =>
          results.headOption.map(
            tokenId =>
              Tables.TokenBalancesRow(
                tokenId,
                address = tokenUpdate.accountId.id,
                balance = BigDecimal(tokenUpdate.balance),
                blockId = blockData.hash.value,
                blockLevel = BigDecimal(blockData.header.level),
                asof = toSql(blockData.header.timestamp)
              )
          )
        }
    }.sequence[DBIO, Option[Tables.TokenBalancesRow]]

    rowOptions.flatMap { ops =>
      val validBalances = ops.flatten
      logger.info(
        "{} token balance updates will be stored.",
        if (validBalances.nonEmpty) s"A total of ${validBalances.size}" else "No"
      )

      Tables.TokenBalances ++= validBalances
    }
  }

  /* filter out pre-babylon big map updates */
  private def keepLatestUpdateFormat: List[Contract.CompatBigMapDiff] => List[Contract.BigMapUpdate] =
    compatibilityWrapped =>
      compatibilityWrapped.collect {
        case Left(update: Contract.BigMapUpdate) => update
      }

  /* filter out pre-babylon big map diffs */
  private def keepLatestDiffsFormat: List[Contract.CompatBigMapDiff] => List[Contract.BigMapDiff] =
    compatibilityWrapped =>
      compatibilityWrapped.collect {
        case Left(diff) => diff
      }
}
