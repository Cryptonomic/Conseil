package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import com.github.tminglei.slickpg.ExPostgresProfile
import scala.concurrent.ExecutionContext
import cats.implicits._
import tech.cryptonomic.conseil.util.Conversion.Syntax._
import tech.cryptonomic.conseil.tezos.michelson.contracts.TokenContracts
import tech.cryptonomic.conseil.tezos.TezosTypes.ContractId

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
      val diffMap = blocks.map(b => b.data.hash.value -> TezosOptics.Blocks.readBigMapDiffCopy.getAll(b))
      diffMap.foreach {
        case (hash, diffs) if diffs.nonEmpty =>
          logger.debug(
            "For block hash {}, I'm about to copy the following big maps data: \n\t{}",
            hash.value,
            diffs.mkString(", ")
          )
        case _ =>
      }
      diffMap.map(_._2).flatten
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
      val diffMap = blocks.map(b => b.data.hash.value -> TezosOptics.Blocks.readBigMapDiffRemove.getAll(b))
      diffMap.foreach {
        case (hash, diffs) if diffs.nonEmpty =>
          logger.debug(
            "For block hash {}, I'm about to delete the big maps for ids: \n\t{}",
            hash.value,
            diffs.mkString(", ")
          )
        case _ =>
      }
      diffMap.map(_._2).flatten
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
    val maps = if (logger.underlying.isDebugEnabled()) {
      val rowsMap = blocks.map(b => b.data.hash.value -> b.convertToA[List, BigMapsRow])
      rowsMap.foreach {
        case (hash, rows) if rows.nonEmpty =>
          logger.debug(
            "For block hash {}, I'm about to add the following big maps: \n\t{}",
            hash.value,
            rows.mkString(", ")
          )
        case _ =>
      }
      rowsMap.map(_._2).flatten
    } else blocks.flatMap(_.convertToA[List, BigMapsRow])

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
    //only keep latest values from multiple blocks
    val rowsMap = blocks.map(b => b.data.hash -> b.convertToA[List, BigMapContentsRow]).toMap
    if (logger.underlying.isDebugEnabled()) {
      rowsMap.foreach {
        case (hash, rows) if rows.nonEmpty =>
          logger.debug(
            "For block hash {}, I'm about to update big map contents with the following data: \n\t{}",
            hash.value,
            rows.mkString(", ")
          )
        case _ =>
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
            .flatMap(b => rowsMap.getOrElse(b.data.hash, List.empty))
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
    val refs = if (logger.underlying.isDebugEnabled()) {
      val rowsMap = blocks.map(b => b.data.hash.value -> b.convertToA[List, OriginatedAccountMapsRow])
      rowsMap.foreach {
        case (hash, rows) if rows.nonEmpty =>
          logger.debug(
            "For block hash {}, I'm about to add the following links from big maps to originated accounts: \n\t{}",
            hash.value,
            rows.mkString(", ")
          )
        case _ =>
      }
      rowsMap.map(_._2).flatten
    } else blocks.flatMap(_.convertToA[List, OriginatedAccountMapsRow])

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
  def updateTokenBalances(blocks: List[Block])(implicit ec: ExecutionContext, tokenContracts: TokenContracts): DBIO[Option[Int]] = {
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

}
