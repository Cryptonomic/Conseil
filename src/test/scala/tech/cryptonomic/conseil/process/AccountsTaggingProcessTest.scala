package tech.cryptonomic.conseil.process

import org.scalatest.{Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.tezos.TezosTypes.PublicKeyHash
import tech.cryptonomic.conseil.tezos.Tables.OperationsRow
import cats.effect.SyncIO
import cats.effect.concurrent.Ref
import java.sql.Timestamp

class AccountsTaggingProcessTest extends WordSpec with Matchers with OptionValues {

  val sut = AccountsTaggingProcess[SyncIO].unsafeRunSync()

  "the accounts tagging process" should {

      "return no flags if no operations exists" in {
        makeApi().map { implicit api =>
          val flagged = sut.process.unsafeRunSync()

          flagged.value shouldBe 0

          api.activated.get.unsafeRunSync() shouldBe 'empty
          api.revealed.get.unsafeRunSync() shouldBe 'empty
        }

      }

      "return the count of flags and mark the correct account ids if operations are found" in {

        makeApi(exampleOperations).map { implicit api =>
          val flagged = sut.process.unsafeRunSync()

          flagged.value shouldBe 4

          api.activated.get.unsafeRunSync() should contain theSameElementsAs
            List(
              PublicKeyHash("pkh-activate_account-1"),
              PublicKeyHash("pkh-activate_account-2")
            )
          api.revealed.get.unsafeRunSync() should contain theSameElementsAs
            List(
              PublicKeyHash("pkh-reveal-1"),
              PublicKeyHash("pkh-reveal-2")
            )
        }
      }
    }

  /* Creates a RecordingApiStub implementation, with empty records and returning the operations passed
   * as arguments
   */
  def makeApi(testOperations: List[OperationsRow] = List.empty): SyncIO[RecordingApiStub] = {
    val records = Ref[SyncIO].of(List.empty[PublicKeyHash])

    for {
      emptyActivated <- records
      emptyRevealed <- records
    } yield
      new RecordingApiStub {
        override val activated = emptyActivated
        override val revealed = emptyRevealed
        override def readOperations(fromLevel: BigDecimal) = fs2.Stream(testOperations: _*)
      }
  }

  /* records calls by storing the argument in the activated and revealed lists */
  trait RecordingApiStub extends sut.ServiceDependencies {

    val activated: Ref[SyncIO, List[PublicKeyHash]]
    val revealed: Ref[SyncIO, List[PublicKeyHash]]

    override def readHighWatermark: SyncIO[BigDecimal] = SyncIO(0)

    def recordPkh(id: PublicKeyHash) = (old: List[PublicKeyHash]) => {
      val list = id :: old
      (list, list.size)
    }

    override def flagAsActive(accountId: PublicKeyHash): SyncIO[Int] =
      activated.modify(recordPkh(accountId))

    override def flagAsRevealed(accountId: PublicKeyHash): SyncIO[Int] =
      revealed.modify(recordPkh(accountId))

    override def readOperations(fromLevel: BigDecimal): fs2.Stream[SyncIO, OperationsRow] = fs2.Stream.empty
  }

  /* a test set of operations to return as data */
  val exampleOperations = List(
    OperationsRow(
      kind = "ignored",
      operationGroupHash = "",
      operationId = 1,
      blockHash = "hash-1",
      blockLevel = 1,
      timestamp = new Timestamp(1),
      level = None,
      internal = false
    ),
    OperationsRow(
      kind = "reveal",
      operationGroupHash = "",
      operationId = 2,
      blockHash = "hash-2",
      blockLevel = 2,
      timestamp = new Timestamp(2),
      level = None,
      internal = false,
      source = Some("pkh-reveal-1")
    ),
    OperationsRow(
      kind = "reveal",
      operationGroupHash = "",
      operationId = 3,
      blockHash = "hash-3",
      blockLevel = 3,
      timestamp = new Timestamp(3),
      level = None,
      internal = false,
      source = Some("pkh-reveal-2")
    ),
    OperationsRow(
      kind = "activate_account",
      operationGroupHash = "",
      operationId = 4,
      blockHash = "hash-4",
      blockLevel = 4,
      timestamp = new Timestamp(4),
      level = None,
      internal = false,
      pkh = Some("pkh-activate_account-1")
    ),
    OperationsRow(
      kind = "activate_account",
      operationGroupHash = "",
      operationId = 5,
      blockHash = "hash-5",
      blockLevel = 5,
      timestamp = new Timestamp(5),
      level = None,
      internal = false,
      pkh = Some("pkh-activate_account-2")
    )
  )

}
