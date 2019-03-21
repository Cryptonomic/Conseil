package tech.cryptonomic.conseil.tezos

import java.time.ZonedDateTime

import org.scalatest.{Matchers, WordSpec}
import cats.syntax.either._
import tech.cryptonomic.conseil.tezos.TezosTypes.Lenses._
import tech.cryptonomic.conseil.tezos.TezosTypes.Scripted.Contracts
import tech.cryptonomic.conseil.tezos.TezosTypes._

class TezosTypesTest extends WordSpec with Matchers {

  val sut = TezosTypes

  "The Base58Check verifier" should {
    "accept an empty string" in {
      sut.isBase58Check("") shouldBe true
    }

    "accept a correctly encoded string" in {
      sut.isBase58Check("signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe true
    }

    "reject a string with forbidden chars" in {
      sut.isBase58Check("signiRfcqmbGc6UtW1WzulJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe false
      sut.isBase58Check("$signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe false
      sut.isBase58Check("signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf*") shouldBe false
    }

    "reject a string with spaces" in {
      sut.isBase58Check("signiRfcqmbGc6UtW1WzuJNGzRRs DLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe false
      sut.isBase58Check(" signiRfcqmbGc6UtW1WzuJNGzRRsDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf") shouldBe false
      sut.isBase58Check("signiRfcqmbGc6UtW1WzuJNGzRRsDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf ") shouldBe false
    }

  }

  "should modify parameters with monocle's lenses" in {
    // given
    val modifiedTransaction = transaction.copy(parameters = Some(Micheline("micheline script")))
    val modifiedOperations = List(operationGroup.copy(contents = origination :: modifiedTransaction :: Nil))

    val block = Block(blockData, modifiedOperations, blockVotes)

    // when
    val result = parametersLens.modify(_.toUpperCase)(block)

    // then
    import org.scalatest.Inspectors._

    forAll(result.operationGroups.flatMap(_.contents)) {
      case op: Transaction =>
        op.parameters.head.expression shouldEqual "MICHELINE SCRIPT"
      case _ =>
    }
  }

  "should modify storage with monocle's lenses" in {
    // given
    val modifiedOrigination = origination.copy(script = Some(Contracts(storage = Micheline("eXpR1"), code = Micheline("eXpR2"))))
    val modifiedOperations = List(operationGroup.copy(contents = modifiedOrigination :: transaction :: Nil))

    val block = Block(blockData, modifiedOperations, blockVotes)

    // when
    val result = storageLens.modify(_.toUpperCase)(block)

    //then
    import org.scalatest.Inspectors._

    forAll(result.operationGroups.flatMap(_.contents)) {
      case op: Origination =>
        op.script.head shouldEqual Contracts(Micheline("EXPR1"), Micheline("eXpR2"))
      case _ =>
    }
  }

  "should modify code with monocle's lenses" in {
    // given
    val modifiedOrigination = origination.copy(script = Some(Contracts(storage = Micheline("eXpR1"), code = Micheline("eXpR2"))))
    val modifiedOperations = List(operationGroup.copy(contents = modifiedOrigination :: transaction :: Nil))

    val block = Block(blockData, modifiedOperations, blockVotes)

    // when
    val result = codeLens.modify(_.toLowerCase)(block)

    //then
    import org.scalatest.Inspectors._

    forAll(result.operationGroups.flatMap(_.contents)) {
      case op: Origination =>
        op.script.head shouldEqual Contracts(Micheline("eXpR1"), Micheline("expr2"))
      case _ =>
    }
  }


  private val blockMetadata = BlockHeaderMetadata(balance_updates = List.empty, baker = PublicKeyHash("_"), votingPeriodKind = defaultVotingPeriod, nonceHash = None)
  private val blockData = BlockData("_", None, BlockHash("_"), BlockHeader(0, 0, BlockHash("_"), ZonedDateTime.now(), 0, None, Seq.empty, "_", None), blockMetadata.asLeft)
  private val blockVotes = CurrentVotes.empty
  private val operationGroup = OperationsGroup("_", None, OperationHash("_"), BlockHash("_"), List.empty, None)
  private val number = PositiveDecimal(1)
  private val transaction = Transaction(number, number, number, number, number, ContractId("_"), ContractId("_"), None, ResultMetadata(null, List.empty))
  private val origination = Origination(number, number, ContractId("_"), number, number, number, PublicKeyHash("_"), None, None, None, None, ResultMetadata(null, List.empty))
}