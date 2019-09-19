package tech.cryptonomic.conseil
import cats.implicits._
import cats.effect.IOApp
import cats.effect.IO
import cats.effect.ExitCode
import tech.cryptonomic.conseil.process.AccountsTaggingProcess
import tech.cryptonomic.conseil.tezos.TezosTypes
import tech.cryptonomic.conseil.tezos.Tables
import tech.cryptonomic.conseil.util.PureLogging
import tech.cryptonomic.conseil.config.HarpoonAppConfig

/** Runs an independet process that scans existing operations
  * recorded from the chainm to look for reveal or activation
  * and thus mark the corresponding Account record
  */
object Harpoon extends IOApp with PureLogging {

  //there might be additional setup/shutdown methods to prepare any needed resource, we can detail that later on

  /* This is the main entrypoint, the return type is wrapped in IO and provides an ExitCode for the process
   * Described in cats-effect apis: https://typelevel.org/cats-effect/api/cats/effect/IOApp.html
   */
  override def run(args: List[String]): IO[ExitCode] = {

    //we read custom conf, usually provided from the environment
    val conf = HarpoonAppConfig
    val ctxShifter = IO.contextShift(scala.concurrent.ExecutionContext.global)
    val timer = IO.timer(scala.concurrent.ExecutionContext.global)

    //we create the processor stage, with the custom types needed
    val mainProcess = new AccountsTaggingProcess()

    /* Implement any dependency needed to run the processing.
     */
    implicit val serviceApis = injectServices(mainProcess)

    /* now we can call the processing stage */
    val processCycle: IO[Unit] =
      mainProcess.process
        .redeemWith(
          err =>
            logger.pureLog[IO](
              _.error("Harpoon processing failed to complete", err)
            ),
          flags =>
            logger.pureLog[IO](
              _.info(
                "This Harpoon processing cycle marked {} accounts",
                flags.fold("an unknown number of")(String.valueOf)
              )
            )
        )

    val program =
      processCycle *>
          ctxShifter.shift *>
          logger.pureLog[IO](
            _.info("Harpoon is now waiting for the next processing cycle")
          ) *>
          timer.sleep(conf.cycleSleep)

    /* we return the exit status into IO. The IOApp will run the actual process */
    program.as(ExitCode.Success)

  }

  private def injectServices(processor: AccountsTaggingProcess) =
    new processor.ServiceDependencies {
      override def flagAsActive(accountId: TezosTypes.PublicKeyHash): IO[Int] = ???
      override def flagAsRevealed(accountId: TezosTypes.PublicKeyHash): IO[Int] = ???
      override def readHighWatermark: IO[AccountsTaggingProcess.BlockLevel] = ???
      override def readOperations(fromLevel: AccountsTaggingProcess.BlockLevel): fs2.Stream[IO, Tables.OperationsRow] =
        ???
    }
}
