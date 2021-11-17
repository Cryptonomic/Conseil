package tech.cryptonomic.conseil.indexer.tezos.processing

import tech.cryptonomic.conseil.common.io.Logging.ConseilLogger
import tech.cryptonomic.conseil.common.tezos.{TezosOptics, TezosTypes}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.{
  Block,
  BlockHeaderMetadata,
  Endorsement,
  EndorsementWithSlot,
  EndorsingRights,
  RightsFetchKey
}
import tech.cryptonomic.conseil.indexer.config
import tech.cryptonomic.conseil.indexer.tezos.{
  TezosIndexedDataOperations,
  TezosNodeOperator,
  TezosDatabaseOperations => TezosDb
}

import scala.concurrent.ExecutionContext
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Sink
import akka.stream.{ActorMaterializer, Materializer}
import slick.jdbc.PostgresProfile.api._

import scala.concurrent.Future

/** Takes care of fetching and processing rights to bake/endorse blocks,
  * both past and future.
  *
  * @param nodeOperator access to the remote node to read data
  * @param indexedData access to locally indexed data
  * @param db raw access to the slick database
  * @param configuration defines values for how to fetch and process rights
  * @param mat needed to execute akka streams
  */
class BakingAndEndorsingRightsProcessor(
    nodeOperator: TezosNodeOperator,
    indexedData: TezosIndexedDataOperations,
    db: Database,
    configuration: config.BakingAndEndorsingRights
)(implicit mat: ActorMaterializer) {

  private[tezos] def processBakingAndEndorsingRights(
      fetchingResults: nodeOperator.BlockFetchingResults
  )(
      implicit ec: ExecutionContext
  ): Future[Unit] = {
    import cats.implicits._

    val blockHashesWithCycleAndGovernancePeriod = fetchingResults.map {
      case (Block(data, _, _), _) => {
        data.metadata match {
          case TezosTypes.GenesisMetadata =>
            RightsFetchKey(data.hash, None, None)
          case BlockHeaderMetadata(_, _, _, _, _, voting_period_info, level, level_info) =>
            RightsFetchKey(
              data.hash,
              level.map(_.cycle).orElse(level_info.map(_.cycle)),
              level.map(_.voting_period).orElse(voting_period_info.map(_.voting_period.index))
            )

        }
      }
    }

    (
      nodeOperator.getBatchBakingRights(blockHashesWithCycleAndGovernancePeriod),
      nodeOperator.getBatchEndorsingRights(blockHashesWithCycleAndGovernancePeriod)
    ).mapN { (br, er) =>
      val updatedEndorsingRights = addEndorsedBlockToRights(er, fetchingResults)
      (db.run(TezosDb.upsertBakingRights(br)), db.run(TezosDb.upsertEndorsingRights(updatedEndorsingRights)))
    }.void
  }

  /** Updates endorsing rights with endorsed block */
  private def addEndorsedBlockToRights(
      endorsingRights: Map[RightsFetchKey, List[EndorsingRights]],
      fetchingResults: nodeOperator.BlockFetchingResults
  ): Map[RightsFetchKey, List[EndorsingRights]] = {

    val endorsementsForBlock = fetchingResults.map {
      case (Block(data, operations, _), _) =>
        data.hash -> operations.flatMap(_.contents.collect {
              case e: EndorsementWithSlot => e
              case e: Endorsement => e
            })
    }.toMap.withDefaultValue(List.empty)

    endorsingRights.map {
      case (fetch @ RightsFetchKey(fetchHash, _, _), rightsList) if endorsementsForBlock.contains(fetchHash) =>
        val updatedRights = rightsList.map { rights =>
          val endorsedLevel = endorsementsForBlock(fetchHash).collectFirst {
            case endorsement: EndorsementWithSlot if endorsement.metadata.delegate.value == rights.delegate =>
              endorsement.endorsement.operations.level
            case endorsement: Endorsement if endorsement.metadata.delegate.value == rights.delegate => endorsement.level
          }
          rights.copy(endorsedBlock = endorsedLevel)
        }
        fetch -> updatedRights
      case noEndorseInfo => noEndorseInfo
    }
  }

  /** Fetches future baking and endorsing rights to insert it into the DB */
  private[tezos] def writeFutureRights()(implicit ec: ExecutionContext): Unit = {
    val berLogger = ConseilLogger("RightsFetcher")

    import cats.implicits._

    berLogger.info("Fetching future baking and endorsing rights")
    val blockHead = nodeOperator.getBareBlockHead()
    val brLevelFut = indexedData.fetchMaxBakingRightsLevel()
    val erLevelFut = indexedData.fetchMaxEndorsingRightsLevel()

    (blockHead, brLevelFut, erLevelFut).mapN { (head, brLevel, erLevel) =>
      val headLevel = head.header.level
      val rightsStartLevel = (brLevel max erLevel) + 1
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
      val range = List.range((headLevel + 1) max rightsStartLevel, headLevel + length)
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
  private[tezos] def updateRights()(implicit ec: ExecutionContext): Future[Unit] = {
    val logger = ConseilLogger("RightsUpdater")
    import cats.implicits._
    val blockHead = nodeOperator.getBareBlockHead()

    blockHead.flatMap { blockData =>
      val headLevel = blockData.header.level
      val blockLevelsToUpdate = List.range(headLevel + 1, headLevel + configuration.updateSize)
      val br = nodeOperator.getBatchBakingRightsByLevels(blockLevelsToUpdate).flatMap { bakingRightsResult =>
        val brResults = bakingRightsResult.values.flatten
        logger.info(s"Got ${brResults.size} baking rights")
        db.run(TezosDb.updateBakingRights(brResults.toList))
      }
      val er = nodeOperator.getBatchEndorsingRightsByLevel(blockLevelsToUpdate).flatMap { endorsingRightsResult =>
        val erResults = endorsingRightsResult.values.flatten
        logger.info(s"Got ${erResults.size} endorsing rights")
        db.run(TezosDb.updateEndorsingRights(erResults.toList))
      }
      (br, er).mapN {
        case (bb, ee) =>
          logger.info(s"Updated $bb baking rights and $ee endorsing rights rows")
      }
    }
  }
}
