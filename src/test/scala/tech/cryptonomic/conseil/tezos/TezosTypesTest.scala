package tech.cryptonomic.conseil.tezos

import java.time.{Instant, ZonedDateTime}

import org.scalatest.{Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.tezos.TezosTypes.Lenses._
import tech.cryptonomic.conseil.tezos.TezosTypes.Scripted.Contracts
import tech.cryptonomic.conseil.tezos.TezosTypes._

class TezosTypesTest extends WordSpec with Matchers with OptionValues {

  val sut = TezosTypes

  "The Base58Check verifier" should {
      "accept an empty string" in {
        sut.isBase58Check("") shouldBe true
      }

      "accept a correctly encoded string" in {
        sut.isBase58Check(
          "signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe true
      }

      "reject a string with forbidden chars" in {
        sut.isBase58Check(
          "signiRfcqmbGc6UtW1WzulJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        sut.isBase58Check(
          "$signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        sut.isBase58Check(
          "signiRfcqmbGc6UtW1WzulJNGzRRsWDpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf*"
        ) shouldBe false
      }

      "reject a string with spaces" in {
        sut.isBase58Check(
          "signiRfcqmbGc6UtW1WzuJNGzRRs DLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        sut.isBase58Check(
          " signiRfcqmbGc6UtW1WzuJNGzRRsDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
        ) shouldBe false
        sut.isBase58Check(
          "signiRfcqmbGc6UtW1WzuJNGzRRsDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf "
        ) shouldBe false
      }

    }

  "The Syntax import" should {
      "allow building Block-tagged generic data" in {
        import TezosTypes.Syntax._
        val someTime = Some(Instant.ofEpochMilli(0))
        val content = "A content string"
        val (hash, level) = (BlockHash("hash"), 1)

        content.taggedWithBlock(hash, level, someTime) shouldEqual BlockTagged(hash, level, someTime, content)
      }
    }

  "The BlockTagged wrapper" should {
      "convert to a tuple" in {
        val someTime = Some(Instant.ofEpochMilli(0))
        val content = "A content string"
        val (hash, level) = (BlockHash("hash"), 1)

        BlockTagged(hash, level, someTime, content).asTuple shouldEqual (hash, level, someTime, content)
      }
    }

  "The lenses for tezos types" should {

      val blockData =
        BlockData(
          protocol = "_",
          chain_id = None,
          hash = BlockHash("_"),
          header = BlockHeader(
            level = 0,
            proto = 0,
            predecessor = BlockHash("_"),
            timestamp = ZonedDateTime.now(),
            validation_pass = 0,
            operations_hash = None,
            fitness = Seq.empty,
            priority = None,
            context = "_",
            signature = None
          ),
          metadata = BlockHeaderMetadata(
            balance_updates = List.empty,
            baker = PublicKeyHash("_"),
            voting_period_kind = defaultVotingPeriod,
            nonce_hash = None,
            consumed_gas = PositiveDecimal(0),
            level = BlockHeaderMetadataLevel(0, 0, 0, 0, 0, 0, false)
          )
        )
      val blockVotes = CurrentVotes.empty
      val operationGroup = OperationsGroup("_", None, OperationHash("_"), BlockHash("_"), List.empty, None)
      val number = PositiveDecimal(1)
      val transaction = Transaction(
        number,
        number,
        number,
        number,
        number,
        PublicKeyHash("_"),
        ContractId("_"),
        None,
        ResultMetadata(null, List.empty)
      )
      val origination = Origination(
        number,
        number,
        PublicKeyHash("_"),
        number,
        number,
        number,
        None,
        None,
        None,
        None,
        None,
        ResultMetadata(null, List.empty)
      )

      "modify parameters with monocle's lenses" in {
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

      "modify storage with monocle's lenses" in {
        // given
        val modifiedOrigination =
          origination.copy(script = Some(Contracts(storage = Micheline("eXpR1"), code = Micheline("eXpR2"))))
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

      "modify code with monocle's lenses" in {
        // given
        val modifiedOrigination =
          origination.copy(script = Some(Contracts(storage = Micheline("eXpR1"), code = Micheline("eXpR2"))))
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

    }

  "The TezosOptics" should {

      "allow to read existing code within an account" in {
        val sut = TezosOptics.Accounts
        val account = Account(
          balance = 0L,
          counter = Some(0),
          delegate = None,
          script = Some(Contracts(storage = Micheline("storage code"), code = Micheline("Some code here"))),
          manager = None,
          spendable = None
        )

        sut.scriptLens.getOption(account).value shouldBe "Some code here"
      }

      "read None if there's no script in an account" in {
        val sut = TezosOptics.Accounts
        val account = Account(
          balance = 0L,
          counter = Some(0),
          delegate = None,
          script = None,
          manager = None,
          spendable = None
        )

        sut.scriptLens.getOption(account) shouldBe 'empty
      }

      "allow to update an existing script within an account" in {
        val sut = TezosOptics.Accounts
        val account = Account(
          balance = 0L,
          counter = Some(0),
          delegate = None,
          script = Some(Contracts(storage = Micheline("storage code"), code = Micheline("Some code here"))),
          manager = None,
          spendable = None
        )

        val updated = sut.scriptLens.modify(old => old + "; new code")(account)
        updated.script.value shouldBe Contracts(
          storage = Micheline("storage code"),
          code = Micheline("Some code here; new code")
        )
      }

    }

}
