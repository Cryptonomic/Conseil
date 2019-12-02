package tech.cryptonomic.conseil

import com.typesafe.scalalogging.LazyLogging
import scala.collection.SortedSet
import scala.concurrent.duration._

import cats.data.Kleisli
import cats.syntax.all._
import cats.effect.{ExitCode, IO, IOApp}
import tech.cryptonomic.conseil.io.MainOutputs.LorreOutput
import tech.cryptonomic.conseil.config.{LorreAppConfig, Newest, Platforms}
import tech.cryptonomic.conseil.util.IOUtils.{lift, IOLogging}
import tech.cryptonomic.conseil.application.LorreOperations
import tech.cryptonomic.conseil.application.BlocksOperations.BlocksProcessingFailed
import tech.cryptonomic.conseil.application.AccountsOperations.AccountsProcessingFailed
import tech.cryptonomic.conseil.application.DelegatesOperations.DelegatesProcessingFailed

/**
  * Entry point for synchronizing data between the Tezos blockchain and the Conseil database.
  */
object Lorre extends IOApp with LazyLogging with IOLogging with LorreAppConfig with LorreOutput with LorreOperations {

  //defines the main logic, given all necessary dependencies and configurations
  override def run(args: List[String]): IO[ExitCode] = {
    import LorreAppConfig.ConfiguredResourses
    import cats.arrow.Arrow
    import cats.instances.function._

    //builds operations on which Lorre depends, applying the configured resources
    //it uses cats.arrow idioms to pair the input with the output
    val buildDeps: ConfiguredResourses => (ConfiguredResourses, Dependencies) =
      Arrow[Function].lift(identity[ConfiguredResourses]).merge((makeOperations _).tupled)

    //an effectful program built from a function, with "adapted" input
    val program = Kleisli((runLorre _).tupled).local(buildDeps)

    //builds the actual resources and then pass the adapted program as a function to execute
    useConfiguredResources(args)(program.run(_)).as(ExitCode.Success)
  }

  /** This is the core logic of Lorre, based on additional resources and services
    * It will return an IO value of sort
    *
    * @param resources a tuple of all resources defined via configuration and needed by Lorre
    * @param ops a tuple of all speciific operations needed by Lorre
    * @return an IO value, which doesn't have any particular meaning
    */
  def runLorre(resources: LorreAppConfig.ConfiguredResourses, ops: Dependencies) = {
    val (conf, db, system, node, api) = resources
    val (blocksOperations, accountsOperations, delegatesOperations, feeOperations, contextShift) = ops
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
            _ <- blocksOperations.fetchAndStoreBakingAndEndorsingRights(blocks.map(_.data.hash))
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
        val repeatStep = (iteration: Int, accountsRefreshLevels: SortedSet[Int]) =>
          for {
            conseilLevel <- lift(api.fetchMaxLevel())(contextShift)
            nextRefreshes <- accountsOperations.processAccountRefreshes(conseilLevel, accountsRefreshLevels)
            _ <- mainCycle(conseilLevel)
            _ <- feeOperations
              .processTezosAverageFees(numberOfFeesAveraged)
              .whenA(iteration % feeUpdateInterval == 0)
            _ <- liftLog(_.info("Taking a nap."))
            _ <- IO.sleep(conf.lorre.sleepInterval)
          } yield ((iteration + 1) % Int.MaxValue, nextRefreshes)

        unprocessedLevelsForRefreshingAccounts(db, conf.lorre)(contextShift).flatMap { refreshLevels =>
          val accountRefreshesToRun = SortedSet(refreshLevels: _*)
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
