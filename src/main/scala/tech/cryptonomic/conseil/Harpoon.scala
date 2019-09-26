package tech.cryptonomic.conseil

import cats.implicits._
import cats.effect.{ExitCode, IO, IOApp}
import tech.cryptonomic.conseil.process.AccountsTaggingProcess
import tech.cryptonomic.conseil.tezos.{Tables, TezosDatabaseOperations, TezosTypes}
import tech.cryptonomic.conseil.tezos.TezosDatabaseOperations.AccountFlags
import tech.cryptonomic.conseil.util.{DatabaseUtil, PureLogging}
import tech.cryptonomic.conseil.config.HarpoonAppConfig
import slick.jdbc.PostgresProfile.api._
import scala.concurrent.ExecutionContext
import tech.cryptonomic.conseil.util.IOUtil
import slick.jdbc.ResultSetType
import slick.jdbc.ResultSetConcurrency

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

    implicit val ctx = scala.concurrent.ExecutionContext.global
    val timer = IO.timer(scala.concurrent.ExecutionContext.global)

    //we create the processor stage, with the custom types needed
    val mainProgram = AccountsTaggingProcess[IO].flatMap { tagging =>
      /* Implement any dependency needed to run the processing.
       */
      implicit val serviceApis = injectServices(tagging)

      /* now we can call the processing stage */
      val processCycle: IO[Unit] =
        tagging.process
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
            IO.shift *>
            logger.pureLog[IO](
              _.info("Harpoon is now waiting for the next processing cycle")
            ) *>
            timer.sleep(conf.cycleSleep)

      program.foreverM
    }

    /* we return the exit status into IO. The IOApp will run the actual process */
    mainProgram.as(ExitCode.Success)

  }

  /* Allows running db-operations as cats-effect IO actions */
  private def runDbToIO[T] = IOUtil.runDbToIO[T](DatabaseUtil.db.run _) _

  /* Allows streaming db-results */
  private def runDbToStream[R, T](action: StreamingDBIO[R, T]) =
    IOUtil.publishStream(DatabaseUtil.db.stream(action))

  /* builds the service instance based on global operations available in the project */
  private def injectServices(processor: AccountsTaggingProcess[IO])(implicit ec: ExecutionContext) =
    new processor.ServiceDependencies {

      override def flagAsActive(accountId: TezosTypes.PublicKeyHash): IO[Int] =
        runDbToIO(TezosDatabaseOperations.flagAccount(accountId.value, AccountFlags.activated))

      override def flagAsRevealed(accountId: TezosTypes.PublicKeyHash): IO[Int] =
        runDbToIO(TezosDatabaseOperations.flagAccount(accountId.value, AccountFlags.revealed))

      override def readHighWatermark: IO[AccountsTaggingProcess.BlockLevel] =
        runDbToIO(TezosDatabaseOperations.findLatestFlaggedAccountLevel())

      //additional configurations are needed to make Postgres comply with the streaming protocol
      override def readOperations(fromLevel: AccountsTaggingProcess.BlockLevel): fs2.Stream[IO, Tables.OperationsRow] =
        runDbToStream(
          TezosDatabaseOperations
            .fetchRecentOperationsByKind(
              AccountsTaggingProcess.operationKinds,
              fromLevel.toIntExact
            )
            .withStatementParameters(
              rsType = ResultSetType.ForwardOnly,
              rsConcurrency = ResultSetConcurrency.ReadOnly,
              fetchSize = 50
            )
            .transactionally
        )
    }
}
