package tech.cryptonomic.conseil.application

import tech.cryptonomic.conseil.util.IOUtils.{lift, IOLogging}
import tech.cryptonomic.conseil.config.LorreAppConfig
import tech.cryptonomic.conseil.tezos.{TezosDatabaseOperations => TezosDb}
import tech.cryptonomic.conseil.tezos.TezosTypes.{discardGenesis, BlockMetadata}
import cats.effect.{IO, Timer}
import scala.{Stream => _}
import com.typesafe.scalalogging.LazyLogging
import cats.implicits._
import com.typesafe.scalalogging.Logger
import scala.concurrent.ExecutionContext
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.actor.Cancellable

class LorreBakerRightsOperations(resources: LorreAppConfig.ConfiguredResources) extends LazyLogging with IOLogging {

  //breaks up the dependency definitions
  val (conf, db, system, node, api) = resources
  val localConf = conf.lorre.blockRightsFetching

  override lazy val logger = Logger("RightsFetcher")

  implicit private val context: ExecutionContext = system.dispatcher

  /** Schedules method for fetching baking rights */
  val scheduling =
    liftLog(_.info("Starting the schedule for the future baking and endoring rights update")) >>
        IO(
          system.scheduler.schedule(localConf.initDelay, localConf.interval)(
            writeFutureRights()
          )
        )

  /** Fetches future baking and endorsing rights to insert it into the DB */
  def writeFutureRights(): Unit = {
    //Note, cycle 0 starts at the level 2 block
    def extractCyclePosition(block: BlockMetadata): Option[Int] =
      discardGenesis
        .lift(block) //this returns an Option[BlockHeaderMetadata]
        .map(_.level.cycle_position) //this is Option[Int]

    implicit val mat = ActorMaterializer()(system)
    logger.info("Fetching future baking and endorsing rights")
    val blockHead = node.getBareBlockHead()
    val brLevelFut = api.fetchMaxBakingRightsLevel()
    val erLevelFut = api.fetchMaxEndorsingRightsLevel()

    (blockHead, brLevelFut, erLevelFut).mapN { (head, brLevel, erLevel) =>
      val headLevel = head.header.level
      val rightsStartLevel = math.max(brLevel, erLevel) + 1
      logger.info(
        s"Current Tezos block head level: $headLevel DB stored baking rights level: $brLevel DB stored endorsing rights level: $erLevel"
      )

      val length = extractCyclePosition(head.metadata).map { cyclePosition =>
        // calculates amount of future rights levels to be fetched based on cycle_position, cycle_size and amount cycles to fetch
        (localConf.cycleSize - cyclePosition) + localConf.cycleSize * localConf.cyclesToFetch
      }.getOrElse(0)

      logger.info(s"Level and position to fetch ($headLevel, $length)")

      val range = List.range(Math.max(headLevel + 1, rightsStartLevel), headLevel + length)

      Source
        .fromIterator(() => range.toIterator)
        .grouped(localConf.fetchSize)
        .mapAsync(1) { partition =>
          node.getBatchBakingRightsByLevels(partition.toList).flatMap { bakingRightsResult =>
            val brResults = bakingRightsResult.values.flatten
            logger.info(s"Got ${brResults.size} baking rights")
            db.run(TezosDb.insertBakingRights(brResults.toList))
          }
          node.getBatchEndorsingRightsByLevel(partition.toList).flatMap { endorsingRightsResult =>
            val erResults = endorsingRightsResult.values.flatten
            logger.info(s"Got ${erResults.size} endorsing rights")
            db.run(TezosDb.insertEndorsingRights(erResults.toList))
          }
        }
        .runWith(Sink.ignore)
    }.flatten
    ()
  }

}
