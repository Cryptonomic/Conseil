package tech.cryptonomic.conseil.application

import scala.concurrent.duration._
import cats.implicits._
import cats.effect.{IO, Timer}
import tech.cryptonomic.conseil.config.{ChainEvent, LorreAppConfig, Newest, Platforms}
import tech.cryptonomic.conseil.io.MainOutputs.LorreOutput
import tech.cryptonomic.conseil.util.IOUtils.{lift, IOLogging}
import tech.cryptonomic.conseil.application.BlocksOperations.BlocksProcessingFailed
import tech.cryptonomic.conseil.application.AccountsOperations.AccountsProcessingFailed
import tech.cryptonomic.conseil.application.DelegatesOperations.DelegatesProcessingFailed
import com.typesafe.scalalogging.LazyLogging

/** The main indexing program logic */
object LorreIndexing extends LorreOperations with LorreOutput with LazyLogging with IOLogging {

  /** This is the core logic of Lorre, based on additional resources and services
    * It will return an IO value of sort
    *
    * @param resources a tuple of all resources defined via configuration and needed by Lorre
    * @param ops a tuple of all speciific operations needed by Lorre
    * @return an IO value, which doesn't have any particular meaning
    */
  def run(resources: LorreAppConfig.ConfiguredResources, operationalDependencies: CompositeOps)(
      implicit timer: Timer[IO]
  ): IO[Unit] = {
    val (conf, db, system, node, api) = resources
    val (blocksOperations, accountsOperations, delegatesOperations, feesOperations, contextShift) =
      operationalDependencies

    import conf.lorre.{
      bootupConnectionCheckTimeout,
      bootupRetryInterval,
      depth => configuredDepth,
      headHash => configuredHead,
      numberOfFeesAveraged,
      feeUpdateInterval
    }
    //makes the akka execution context implicitly available in scope
    import system.dispatcher

    //IO action to show up a startup log
    val showConfig = IO(displayInfo(conf.tezos)) >>
          IO(
            displayConfiguration(
              Platforms.Tezos,
              conf.tezos,
              conf.lorre,
              (conf.failurePolicy.varName, conf.failurePolicy.ignore)
            )
          ).whenA(conf.verbose.on)

    //the main program sequence, it's just a description that is yet to be run
    def mainCycle(conseilLevel: Int) =
      for {
        head <- checkTezosConnection(node, bootupConnectionCheckTimeout, bootupRetryInterval)(
          contextShift,
          timer
        )
        _ <- liftLog(_.info("Processing Tezos Blocks..."))
        start <- timer.clock.monotonic(NANOSECONDS)
        toSync <- blocksOperations.fetchBlockPagesToSync(configuredDepth, configuredHead)(head, conseilLevel)
        (blockPages, total) = toSync
        blocksWritten <- blocksOperations.forEachBlockPage(
          blockPages,
          logProcessingProgress(total, start, contextShift, timer)
        ) { blockPage =>
          val blocks = blockPage.map { case (block, _) => block }
          val touchedAccountIds = blocksOperations.extractAccountRefs(blockPage)

          for {
            _ <- accountsOperations.storeAccountsCheckpoint(touchedAccountIds)
            storedBlocks <- blocksOperations.storeBlocks(blockPage)
            _ <- blocksOperations.fetchAndStoreVotesForBlocks(blocks)
            _ <- blocksOperations.fetchAndStoreBakingAndEndorsingRights(blocks.map(_.data))
            _ <- liftLog(_.info("Processing latest Tezos data for updated accounts..."))
            delegateKeys <- accountsOperations.processAccountsAndGetDelegateKeys(
              discardOldestDuplicates(touchedAccountIds)
            )
            _ <- liftLog(_.info("Processing latest Tezos data for account delegates..."))
            _ <- delegatesOperations.processDelegates(discardOldestDuplicates(delegateKeys))
            //retry processing leftovers in the checkpoints
            _ <- processCheckpoints(accountsOperations, delegatesOperations)
          } yield storedBlocks

        }

      } yield blocksWritten

    //the possibly repeated process
    val processing = configuredDepth match {
      case Newest =>
        //in this case we do the same thing over and over, after short breaks
        val repeatStep = (iteration: Int, accountsRefreshLevels: ChainEvent.AccountUpdatesEvents) =>
          for {
            conseilLevel <- lift(api.fetchMaxLevel())(contextShift)
            nextRefreshes <- accountsOperations.processAccountRefreshes(conseilLevel, accountsRefreshLevels)
            _ <- mainCycle(conseilLevel)
            _ <- feesOperations
              .processTezosAverageFees(numberOfFeesAveraged)
              .whenA(iteration % feeUpdateInterval == 0)
            _ <- liftLog(_.info("Taking a nap."))
            _ <- IO.sleep(conf.lorre.sleepInterval)
          } yield ((iteration + 1) % Int.MaxValue, nextRefreshes)

        unprocessedLevelsForRefreshingAccounts(db, conf.lorre)(contextShift).flatMap { accountRefreshesToRun =>
          //builds a stream over the iterated step, and then runs it ignoring the result value
          fs2.Stream
            .iterateEval((0, accountRefreshesToRun))(repeatStep.tupled)
            .compile
            .drain
        }
      case _ =>
        //this will run once and be done
        lift(api.fetchMaxLevel())(contextShift).flatMap(mainCycle) <* liftLog(
              _.info("Synchronization is done.")
            )
    }

    //combine the pieces, adding custom error handling if required
    showConfig >> processing.handleErrorWith {
      case f @ (AccountsProcessingFailed(_, _) | BlocksProcessingFailed(_, _) | DelegatesProcessingFailed(_, _))
          if conf.failurePolicy.ignore =>
        liftLog(_.error("Failed processing but will keep on going next cycle", f))
    }.void

  }
}
