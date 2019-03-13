package tech.cryptonomic.conseil.tezos

import java.time.ZonedDateTime

import org.scalatest.{Matchers, WordSpec}
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
    val block = Block(blockData, List(operationGroup.copy(contents = List(origination, transaction.copy(parameters = Some(Micheline("micheline script")))))))

    // when
    val result = parametersLens.modify(_.toUpperCase)(block)

    //then
    result.operationGroups.flatMap(_.contents).collect { case it: Transaction => it }.head.parameters.head.expression should equal("MICHELINE SCRIPT")
  }

  "should modify storage with monocle's lenses" in {
    // given
    val block = Block(blockData, List(operationGroup.copy(contents = List(origination.copy(script = Some(Contracts(Micheline("eXpR1"), Micheline("eXpR2")))), transaction))))

    // when
    val result = storageLens.modify(_.toUpperCase)(block)

    //then
    result.operationGroups.flatMap(_.contents).collect { case it: Origination => it }.head.script.head should equal(Contracts(Micheline("EXPR1"), Micheline("eXpR2")))
  }

  "should modify code with monocle's lenses" in {
    // given
    val block = Block(blockData, List(operationGroup.copy(contents = List(origination.copy(script = Some(Contracts(Micheline("eXpR1"), Micheline("eXpR2")))), transaction))))

    // when
    val result = codeLens.modify(_.toLowerCase)(block)

    //then
    result.operationGroups.flatMap(_.contents).collect { case it: Origination => it }.head.script.head should equal(Contracts(Micheline("eXpR1"), Micheline("expr2")))
  }

  private val blockAccounts = BlockAccounts(BlockHash("_"), 0, Map.empty)
  private val account = Account("_", 0, true, AccountDelegate(true, None), None, 1)
  private val blockData = BlockData("_", None, BlockHash("_"), BlockHeader(0, 0, BlockHash("_"), ZonedDateTime.now(), 0, None, Seq.empty, "_", None), BlockHeaderMetadata(None))
  private val operationGroup = OperationsGroup("_", None, OperationHash("_"), BlockHash("_"), List.empty, None)
  private val number = PositiveDecimal(1)
  private val transaction = Transaction(number, number, number, number, number, ContractId("_"), ContractId("_"), None, ResultMetadata(null, List.empty))
  private val origination = Origination(number, number, ContractId("_"), number, number, number, PublicKeyHash("_"), None, None, None, None, ResultMetadata(null, List.empty))
}