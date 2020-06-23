package tech.cryptonomic.conseil.indexer.tezos.processing

import tech.cryptonomic.conseil.common.tezos.TezosOptics
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{
  BlockHeaderMetadata,
  Endorsement,
  EndorsingRights,
  FetchRights,
  GenesisMetadata
}
import tech.cryptonomic.conseil.indexer.config
import tech.cryptonomic.conseil.indexer.tezos.{
  TezosIndexedDataOperations,
  TezosNodeOperator,
  TezosDatabaseOperations => TezosDb
}

import org.slf4j.LoggerFactory
import scala.concurrent.ExecutionContext
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.stream.ActorMaterializer
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.Future

class BakingAndEndorsingRightsProcessing(
    db: Database,
    configuration: config.BakingAndEndorsingRights,
    nodeOperator: TezosNodeOperator,
    indexedData: TezosIndexedDataOperations
)(implicit mat: ActorMaterializer) {

  private[tezos] def processBakingAndEndorsingRights(
      fetchingResults: nodeOperator.BlockFetchingResults
  )(
      implicit ec: ExecutionContext
  ): Future[Unit] = {
    import cats.implicits._

    val blockHashesWithCycleAndGovernancePeriod = fetchingResults.map { results =>
      {
        val data = results._1.data
        val hash = data.hash
        data.metadata match {
          case GenesisMetadata => FetchRights(None, None, Some(hash))
          case BlockHeaderMetadata(_, _, _, _, _, level) =>
            FetchRights(Some(level.cycle), Some(level.voting_period), Some(hash))

        }
      }
    }

    (
      nodeOperator.getBatchBakingRights(blockHashesWithCycleAndGovernancePeriod),
      nodeOperator.getBatchEndorsingRights(blockHashesWithCycleAndGovernancePeriod)
    ).mapN { (br, er) =>
      val updatedEndorsingRights = updateEndorsingRights(er, fetchingResults)
      (db.run(TezosDb.upsertBakingRights(br)), db.run(TezosDb.upsertEndorsingRights(updatedEndorsingRights)))
    }.void
  }

  /** Updates endorsing rights with endorsed block */
  private def updateEndorsingRights(
      endorsingRights: Map[FetchRights, List[EndorsingRights]],
      fetchingResults: nodeOperator.BlockFetchingResults
  ): Map[FetchRights, List[EndorsingRights]] =
    endorsingRights.map {
      case (fetchRights, endorsingRightsList) =>
        fetchRights -> endorsingRightsList.map { rights =>
              val endorsedBlock = fetchingResults.find {
                case (block, _) =>
                  fetchRights.blockHash.contains(block.data.hash)
              }.flatMap {
                case (block, _) =>
                  block.operationGroups.flatMap {
                    _.contents.collect {
                      case e: Endorsement if e.metadata.delegate.value == rights.delegate => e
                    }.map(_.level)
                  }.headOption
              }
              rights.copy(endorsedBlock = endorsedBlock)
            }
    }

  /** Fetches future baking and endorsing rights to insert it into the DB */
  private[tezos] def writeFutureRights()(implicit ec: ExecutionContext): Unit = {
    val berLogger = LoggerFactory.getLogger("RightsFetcher")

    import cats.implicits._

    berLogger.info("Fetching future baking and endorsing rights")
    val blockHead = nodeOperator.getBareBlockHead()
    val brLevelFut = indexedData.fetchMaxBakingRightsLevel()
    val erLevelFut = indexedData.fetchMaxEndorsingRightsLevel()

    (blockHead, brLevelFut, erLevelFut).mapN { (head, brLevel, erLevel) =>
      val headLevel = head.header.level
      val rightsStartLevel = math.max(brLevel, erLevel) + 1
      berLogger.info(
        s"Current Tezos block head level: $headLevel DB stored baking rights level: $brLevel DB stored endorsing rights level: $erLevel"
      )

      val length = TezosOptics.Blocks
        .extractCyclePosition(head.metadata)
        .map { cyclePosition =>
          // calculates amount of future rights levels to be fetched based on cycle_position, cycle_size and amount cycles to fetch
          (configuration.cycleSize - cyclePosition) + configuration.cycleSize * configuration.cyclesToFetch
        }
        .getOrElse(0)

      berLogger.info(s"Level and position to fetch ($headLevel, $length)")
      val range = List.range(Math.max(headLevel + 1, rightsStartLevel), headLevel + length)
      Source
        .fromIterator(() => range.toIterator)
        .grouped(configuration.fetchSize)
        .mapAsync(1) { partition =>
          nodeOperator.getBatchBakingRightsByLevels(partition.toList).flatMap { bakingRightsResult =>
            val brResults = bakingRightsResult.values.flatten
            berLogger.info(s"Got ${brResults.size} baking rights")
            db.run(TezosDb.insertBakingRights(brResults.toList))
          }
          nodeOperator.getBatchEndorsingRightsByLevel(partition.toList).flatMap { endorsingRightsResult =>
            val erResults = endorsingRightsResult.values.flatten
            berLogger.info(s"Got ${erResults.size} endorsing rights")
            db.run(TezosDb.insertEndorsingRights(erResults.toList))
          }
        }
        .runWith(Sink.ignore)
    }.flatten
    ()
  }

  /** Updates timestamps in the baking/endorsing rights tables */
  private[tezos] def updateRightsTimestamps()(implicit ec: ExecutionContext): Future[Unit] = {
    val logger = LoggerFactory.getLogger("RightsUpdater")
    import cats.implicits._
    val blockHead = nodeOperator.getBareBlockHead()

    blockHead.flatMap { blockData =>
      val headLevel = blockData.header.level
      val blockLevelsToUpdate = List.range(headLevel + 1, headLevel + configuration.updateSize)
      val br = nodeOperator.getBatchBakingRightsByLevels(blockLevelsToUpdate).flatMap { bakingRightsResult =>
        val brResults = bakingRightsResult.values.flatten
        logger.info(s"Got ${brResults.size} baking rights")
        db.run(TezosDb.updateBakingRightsTimestamp(brResults.toList))
      }
      val er = nodeOperator.getBatchEndorsingRightsByLevel(blockLevelsToUpdate).flatMap { endorsingRightsResult =>
        val erResults = endorsingRightsResult.values.flatten
        logger.info(s"Got ${erResults.size} endorsing rights")
        db.run(TezosDb.updateEndorsingRightsTimestamp(erResults.toList))
      }
      (br, er).mapN {
        case (bb, ee) =>
          logger.info("Updated {} baking rights and {} endorsing rights rows", bb.sum, ee.sum)
      }
    }
  }
}
