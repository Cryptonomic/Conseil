package tech.cryptonomic.conseil

import cats.~>
import cats.implicits._
import cats.data.StateT
import cats.effect.{ExitCode, IO, IOApp, Resource}
import cats.mtl.implicits._
import tech.cryptonomic.conseil.process.AccountsTaggingProcess
import tech.cryptonomic.conseil.process.AccountsTaggingProcess.BlockLevel
import tech.cryptonomic.conseil.tezos.{Tables, TezosDatabaseOperations, TezosTypes}
import tech.cryptonomic.conseil.tezos.TezosDatabaseOperations.AccountFlags
import tech.cryptonomic.conseil.util.{DatabaseUtil, PureLogging}
import tech.cryptonomic.conseil.config.HarpoonAppConfig
import scala.concurrent.ExecutionContext
import tech.cryptonomic.conseil.util.IOUtil
import slick.jdbc.{ResultSetConcurrency, ResultSetType}
import slick.jdbc.PostgresProfile.api._

/** Runs an independent process that scans existing operations
  * recorded from the chainm to look for reveal or activation
  * and thus mark the corresponding Account record
  */
object Harpoon extends IOApp with PureLogging {

  /** A type alias to provide state management on top of IO.
    * The state threaded through corresponds to the actual
    * process input value (AccountsTaggingProcess.ProcessorInput i.e. Option[BlockLevel]),
    * which allows to start processing from that point onward
    */
  type IOState[T] = StateT[IO, Option[BlockLevel], T]

  /* Reference to the database to use, we need write access to update accounts.
   * A reference is guaranteed to be released after use, avoiding leaks
   */
  private val dbAccess: Resource[IO, Database] =
    Resource.fromAutoCloseable(IO(DatabaseUtil.lorreDb))

  // private lazy val dbHandle: Database = DatabaseUtil.lorreDb

  /* This is the main entrypoint, the return type is wrapped in IO and provides an ExitCode for the process
   * Described in cats-effect apis: https://typelevel.org/cats-effect/api/cats/effect/IOApp.html
   */
  override def run(args: List[String]): IO[ExitCode] = {

    //we read custom conf, usually provided from the environment
    val conf = HarpoonAppConfig

    //we create the processor stage, with the custom types needed
    val tagging = AccountsTaggingProcess[IOState]

    //we use the database resource, which will be acquired before and released after
    dbAccess.use { implicit db =>
      implicit val ctx = scala.concurrent.ExecutionContext.global

      /* Implement any dependency needed to run the processing. */
      implicit val serviceApis = injectServices(tagging)

      /* now we can call the processing stage */
      val processCycle = (processedTopLevel: tagging.ProcessorInput) =>
        tagging.process
          .run(processedTopLevel)
          .flatTap {
            case (level, flagged) =>
              logger.pureLog[IO](
                _.info(
                  "This Harpoon processing cycle marked {} accounts, up to level {}.",
                  flagged.fold("an unknown number of")(String.valueOf),
                  level.fold("unspecified")(String.valueOf)
                )
              )
          }
          .handleErrorWith(
            err =>
              logger.pureLog[IO](_.error("Harpoon processing failed to complete", err)) *>
                  IO.pure((processedTopLevel, 0)) //we reset to the previous state as to retry next round
          )

      //a wait period before running the process once more
      val waitRound =
        IO.shift *>
            logger.pureLog[IO](
              _.info("Harpoon is now waiting for the next processing cycle")
            ) *>
            timer.sleep(conf.cycleSleep)

      //combine the two parts, recursively calling itself, with an updated state
      def program(level: tagging.ProcessorInput): IO[Unit] =
        for {
          out <- processCycle(level)
          (levelReached, flagCounts) = out
          _ <- waitRound
          _ <- program(levelReached)
        } yield ()

      /* we return the exit status into IO. The IOApp will run the actual process */
      program(Option.empty).as(ExitCode.Success)
    }

  }

  /* Allows running db-operations as cats-effect IO actions */
  private def runDbToIO[T](implicit dbHandle: Database): DBIO[T] => IO[T] =
    IOUtil.runDbToIO[T](dbHandle.run) _

  /* Allows streaming db-results */
  private def runDbToStream[R, T](action: StreamingDBIO[R, T])(implicit dbHandle: Database) =
    IOUtil.publishStream(dbHandle.stream(action))

  /* builds the service instance based on global operations available in the project */
  private def injectServices(
      processor: AccountsTaggingProcess[IOState]
  )(implicit ec: ExecutionContext, dbHandle: Database) =
    new processor.ServiceDependencies {

      //Converts from one wrapper to another, for any content type, adding State handling on top of IO
      val liftIOState: IO ~> IOState = StateT.liftK[IO, processor.ProcessorInput]

      override def flagAsActive(accountId: TezosTypes.PublicKeyHash): IOState[Int] =
        liftIOState(
          runDbToIO.apply(TezosDatabaseOperations.flagAccount(accountId.value, AccountFlags.activated))
        )

      override def flagAsRevealed(accountId: TezosTypes.PublicKeyHash): IOState[Int] =
        liftIOState(
          runDbToIO.apply(TezosDatabaseOperations.flagAccount(accountId.value, AccountFlags.revealed))
        )

      override def readHighWatermark: IOState[BlockLevel] =
        liftIOState(
          runDbToIO.apply(TezosDatabaseOperations.findLatestFlaggedAccountLevel())
        )

      /* Additional configurations are needed to make Postgres comply with the streaming protocol.
       * We also need to "lift/translate" from the IO-based stream to a more general IOState effect, thus
       * a conversion function is needed, which is liftIOState
       */
      override def readOperations(fromLevel: BlockLevel): fs2.Stream[IOState, Tables.OperationsRow] =
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
        ).translate(liftIOState)

    }
}
