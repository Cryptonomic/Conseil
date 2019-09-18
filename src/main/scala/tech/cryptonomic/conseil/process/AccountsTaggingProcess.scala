package tech.cryptonomic.conseil.process
import cats.effect.IO
import cats.syntax.apply._
import cats.syntax.applicative._
import cats.syntax.option._
import cats.instances.int._
import scala.{Stream => _} //hide from default scope
import fs2._
import tech.cryptonomic.conseil.tezos.Tables.OperationsRow
import tech.cryptonomic.conseil.tezos.TezosTypes.PublicKeyHash
import cats.effect.concurrent.Ref

object AccountsTaggingProcess {

  type Config
  type BlockLevel = Int

}

/** Scans the existing blocks stored by conseil for specific account-related operations
  * i.e. Revelation, Activation.
  * It then marks such accounts with the appropriate flag (e.g. revealed, activated).
  */
class AccountsTaggingProcess(config: AccountsTaggingProcess.Config) {
  import AccountsTaggingProcess._

  /* tracks the highest checked block level when scanning the blocks
   * it's empty for a processor which was just created
   */
  private val highWatermarkRef = Ref[IO].of(Option.empty[BlockLevel])

  /** the process signals how many flags have been added */
  type ProcessorOutput = Option[Int]

  /* an optional input (might be implicit?) that contain references to
   * other conseil internal services/apis needed to perform the processing
   * e.g. database operations, conseil api calls
   */
  trait ServiceDependencies {

    /** Computes the highest level that previously "tagged" an account.
      * @return the highest marked account level, or `0`, if none fits the role
      */
    def readHighWatermark: IO[BlockLevel]

    /** mark as active an account on the db */
    def flagAsActive(accountId: PublicKeyHash): IO[Int]

    /** mark as revealed an account on the db */
    def flagAsRevealed(accountId: PublicKeyHash): IO[Int]

    /** Loads all stored reveal or activation operations from a block level onward
      * @param fromLevel the [excluded] lowest level to load
      * @return an effectful stream of rows, sorted with ascending block level references
      */
    def readOperations(fromLevel: BlockLevel): Stream[IO, OperationsRow]

  }

  private def scan(fromLevel: BlockLevel)(implicit api: ServiceDependencies): Stream[IO, Int] = {

    /* Actually flag an account based on the operation passed, which can be a reveal or activation
     * @return the IO action that will store the flags and return the number of changed rows, or none if
     *         the operation was not valid
     */
    def flag(accountRef: Option[String], flagOperation: PublicKeyHash => IO[Int]): IO[Option[Int]] =
      accountRef match {
        case None =>
          //no actual account reference for the reveal operation, log the error and proceed on next
          Option.empty[Int].pure[IO]
        case Some(hash) =>
          flagOperation(PublicKeyHash(hash)).map(_.some)
      }

    /* Uppdates the passed-in reference with the new level, if higher */
    def updateIfHigher(newLevel: BlockLevel)(ref: Ref[IO, Option[BlockLevel]]): IO[Unit] =
      ref update {
          //puts the new level if it's higher than the stored one, or if there was nothing there
          stored =>
            stored.map(math.max(_, newLevel)).orElse(newLevel.some)
        }

    /* find sorted operations and flag the related accounts as they come by, all the while keeping track of the
     * new block level, as read from the operations
     */
    api
      .readOperations(fromLevel)
      .evalMap { operation =>
        (operation.kind match {
          case "reveal" =>
            flag(operation.source, api.flagAsRevealed)
          case "activate_account" =>
            flag(operation.pkh, api.flagAsActive)
          case kind =>
            //log unexpected kind
            Option.empty[Int].pure[IO]
        }).map { flagResult =>
          (flagResult, operation.blockLevel)
        }
      }
      .evalTap {
        //evenutually store the reference level
        case (Some(flaggedCount), blockLevel) => highWatermarkRef.flatMap(updateIfHigher(blockLevel))
        case (None, _) => IO.unit
      }
      .foldMap {
        //extracts and sums all integer values (counting the actual updates)
        case (anyFlag, _) => anyFlag.getOrElse(0)
      }

  }

  /* will have the method that accepts data and actualy processes it*/
  def process(implicit api: ServiceDependencies): IO[ProcessorOutput] =
    for {
      markRef <- highWatermarkRef
      initialMark <- markRef.get
      defaultedMark <- initialMark.fold(api.readHighWatermark)(_.pure[IO])
      _ <- markRef.set(defaultedMark.some)
      newFlags <- scan(defaultedMark).last.compile.toList
    } yield newFlags.headOption.flatten

}
