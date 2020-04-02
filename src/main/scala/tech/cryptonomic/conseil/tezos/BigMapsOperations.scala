package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import com.github.tminglei.slickpg.ExPostgresProfile
import scala.concurrent.ExecutionContext
import cats.implicits._
import tech.cryptonomic.conseil.util.Conversion.Syntax._
import tech.cryptonomic.conseil.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.tezos.TezosTypes.{ContractId, InternalOperationResults, Origination, Transaction}
import tech.cryptonomic.conseil.tezos.TezosTypes.OperationResult.Status

/** Defines big-map-diffs specific handling, from block data extraction to database storage
  *
  * @param profile is the actual profile needed to define database operations
  */
case class BigMapsOperations[Profile <: ExPostgresProfile](profile: Profile) extends LazyLogging {
  import TezosTypes.{Block, Contract, Decimal}
  import Tables.{BigMapContentsRow, BigMapsRow, OriginatedAccountMapsRow}
  import profile.api._
  import DatabaseConversions._

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
          .map(it => (destinationId, it.key, it.keyHash, it.value))
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

    val diffsPerBlock = blocks.flatMap(
      b =>
        extractAppliedOriginationsResults(b)
          .flatMap(_.big_map_diff.toList.flatMap(keepLatestDiffsFormat))
          .map(diff => DatabaseConversions.BlockBigMapDiff(b.data.hash, diff))
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

    val diffsPerBlock = blocks.flatMap(
      b =>
        extractAppliedTransactionsResults(b)
          .flatMap(_.big_map_diff.toList.flatMap(keepLatestDiffsFormat))
          .map(diff => DatabaseConversions.BlockBigMapDiff(b.data.hash, diff))
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
  def saveContractOrigin(blocks: List[Block])(implicit tokenContracts: TokenContracts): DBIO[Option[Int]] = {
    val diffsPerBlock = blocks.flatMap(
      b =>
        extractAppliedOriginationsResults(b).flatMap { results =>
          for {
            contractIds <- results.originated_contracts.toList
            diff <- results.big_map_diff.toList.flatMap(keepLatestDiffsFormat)
          } yield DatabaseConversions.BlockContractIdsBigMapDiff((b.data.hash, contractIds, diff))
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
    //we might need to update the information registered about tokens
    updateTokens(refs, tokenContracts)
    Tables.OriginatedAccountMaps ++= refs
  }

  /* Updates information on the stored map, for accounts associated to a token contract */
  private def updateTokens(contractsReferences: List[OriginatedAccountMapsRow], tokenContracts: TokenContracts) =
    contractsReferences.foreach {
      case OriginatedAccountMapsRow(mapId, accountId) if tokenContracts.isKnownToken(ContractId(accountId)) =>
        tokenContracts.setMapId(ContractId(accountId), mapId)
      case _ =>
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
    val toSql = (zdt: java.time.ZonedDateTime) => java.sql.Timestamp.from(zdt.toInstant)

    //we first extract all data available from the blocks themselves, as necessary to make a proper balance entry
    val balanceData = if (logger.underlying.isDebugEnabled()) {
      val balanceMap = blocks.map(b => b.data.hash.value -> b.convertToA[List, DatabaseConversions.BlockTokenBalances])
      balanceMap.foreach {
        case (hash, balances) if balances.nonEmpty =>
          logger.debug(
            "For block hash {}, I'm about to extract the following token balance updates from big maps: \n\t{}",
            hash.value,
            balances.mkString(", ")
          )
        case _ =>
      }
      balanceMap.map(_._2).flatten
    } else blocks.flatMap(_.convertToA[List, DatabaseConversions.BlockTokenBalances])

    //now we need to check on the token registry for matching contracts, to get a valid token-id as defined on the db
    val rowOptions = balanceData.map { data =>
      val blockData = data.block.data

      Tables.RegisteredTokens
        .filter(_.accountId === data.tokenContractId.id)
        .map(_.id)
        .result
        .map { results =>
          results.headOption.map(
            tokenId =>
              Tables.TokenBalancesRow(
                tokenId,
                address = data.accountId.id,
                balance = BigDecimal(data.balance),
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

  private def isApplied(status: String) = Status.parse(status).contains(Status.applied)

  private def extractAppliedOriginationsResults(block: TezosTypes.Block) = {
    val (ops, intOps) = TezosOptics.Blocks.extractOperationsAlongWithInternalResults(block).values.unzip

    val results =
      (ops.toList.flatten.collect { case op: Origination => op.metadata.operation_result }) ++
          (intOps.toList.flatten.collect { case intOp: InternalOperationResults.Origination => intOp.result })

    results.filter(result => isApplied(result.status))
  }

  private def extractAppliedTransactionsResults(block: TezosTypes.Block) = {
    val (ops, intOps) = TezosOptics.Blocks.extractOperationsAlongWithInternalResults(block).values.unzip

    val results =
      (ops.toList.flatten.collect { case op: Transaction => op.metadata.operation_result }) ++
          (intOps.toList.flatten.collect { case intOp: InternalOperationResults.Transaction => intOp.result })

    results.filter(result => isApplied(result.status))
  }

  /* filter out pre-babylon big map diffs */
  private def keepLatestDiffsFormat: List[Contract.CompatBigMapDiff] => List[Contract.BigMapDiff] =
    compatibilityWrapper =>
      compatibilityWrapper.collect {
        case Left(diff) => diff
      }
}
