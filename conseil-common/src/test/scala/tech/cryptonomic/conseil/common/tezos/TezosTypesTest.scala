package tech.cryptonomic.conseil.common.tezos

import java.time.{Instant, ZonedDateTime}

import org.scalatest.{EitherValues, Matchers, OptionValues, WordSpec}
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Lenses._
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Scripted.Contracts
import tech.cryptonomic.conseil.common.tezos.TezosTypes._

class TezosTypesTest extends WordSpec with Matchers with OptionValues with EitherValues {

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

        content.taggedWithBlock(hash, level, someTime, None, None) shouldEqual BlockTagged(
          hash,
          level,
          someTime,
          None,
          None,
          content
        )
      }
    }

  "The BlockTagged wrapper" should {
      "convert to a tuple" in {
        val someTime = Some(Instant.ofEpochMilli(0))
        val content = "A content string"
        val (hash, level) = (BlockHash("hash"), 1)

        BlockTagged(hash, level, someTime, None, None, content).asTuple shouldEqual (hash, level, someTime, None, None, content)
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
        val modifiedTransaction =
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script"), Some("default")))))
        val modifiedOperations = List(operationGroup.copy(contents = origination :: modifiedTransaction :: Nil))

        val block = Block(blockData, modifiedOperations, blockVotes)

        // when
        val result = parametersLens.modify(_.toUpperCase)(block)

        // then
        import org.scalatest.Inspectors._

        forAll(result.operationGroups.flatMap(_.contents)) {
          case op: Transaction =>
            op.parameters.head.left.value.value.expression shouldEqual "MICHELINE SCRIPT"
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

      "not mixup transactions in the same op-group when traversing for update" in {
        //given
        val transactions = List(
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 0"))))),
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 1")))))
        )
        val group = operationGroup.copy(hash = OperationHash(s"opgroup"), contents = transactions)
        val block = Block(blockData, group :: Nil, blockVotes)

        //when
        val modified = parametersLens.modify(_ + " [modified]")(block)

        //then
        val associativeMap = modified.operationGroups.map { g =>
          val params = g.contents.collect {
            case t: Transaction => t.parameters.get.left.get
          }
          val contents = params.map {
            case Parameters(Micheline(micheline), _) => micheline
          }
          g.hash.value -> contents
        }

        associativeMap should contain only (
          "opgroup" -> List("micheline script 0 [modified]", "micheline script 1 [modified]")
        )

      }

      "not mixup transactions in the same op-group when copying between micheline/michelson fields" in {
        //given
        val transactions = List(
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 0"))))),
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 1")))))
        )
        val group = operationGroup.copy(hash = OperationHash(s"opgroup"), contents = transactions)
        val block = Block(blockData, group :: Nil, blockVotes)

        val copyParametersToMicheline = (t: Transaction) => t.copy(parameters_micheline = t.parameters)
        //when
        val modified = transactionLens.modify(copyParametersToMicheline)(block)

        //then
        val associativeMap = modified.operationGroups.map { g =>
          val params = g.contents.collect {
            case t: Transaction => (t.parameters, t.parameters_micheline)
          }

          val contents = params map {
                case (Some(Left(Parameters(Micheline(original), _))), Some(Left(Parameters(Micheline(copied), _)))) =>
                  original -> copied
              }
          g.hash.value -> contents
        }

        associativeMap should contain only (
          "opgroup" -> List(
            "micheline script 0" -> "micheline script 0",
            "micheline script 1" -> "micheline script 1"
          )
        )

      }

      "not mixup transactions in separate op-groups when traversing for update" in {
        //given
        val transactions = List(
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 0"))))),
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 1")))))
        )
        val groups = transactions.zipWithIndex.map {
          case (t, i) => operationGroup.copy(hash = OperationHash(s"op-$i"), contents = t :: Nil)
        }
        val block = Block(blockData, groups, blockVotes)

        //when
        val modified = parametersLens.modify(_ + " [modified]")(block)

        //then
        val associativeMap = modified.operationGroups.map { g =>
          val params = g.contents.collect {
            case t: Transaction => t.parameters.get.left.get
          }
          val content = params.headOption.map {
            case Parameters(Micheline(micheline), _) => micheline
          }
          g.hash.value -> content.value
        }

        associativeMap should contain theSameElementsInOrderAs List(
          "op-0" -> "micheline script 0 [modified]",
          "op-1" -> "micheline script 1 [modified]"
        )

      }

      "not mixup transactions in separate op-groups when copying between micheline/michelson fields" in {
        //given
        val transactions = List(
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 0"))))),
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 1")))))
        )
        val groups = transactions.zipWithIndex.map {
          case (t, i) => operationGroup.copy(hash = OperationHash(s"op-$i"), contents = t :: Nil)
        }
        val block = Block(blockData, groups, blockVotes)

        val copyParametersToMicheline = (t: Transaction) => t.copy(parameters_micheline = t.parameters)
        //when
        val modified = transactionLens.modify(copyParametersToMicheline)(block)

        //then
        val associativeMap = modified.operationGroups.map { g =>
          val params = g.contents.collect {
            case t: Transaction => (t.parameters, t.parameters_micheline)
          }.headOption.value

          val contents = params match {
            case (Some(Left(Parameters(Micheline(original), _))), Some(Left(Parameters(Micheline(copied), _)))) =>
              original -> copied
          }
          g.hash.value -> contents
        }

        associativeMap should contain theSameElementsInOrderAs List(
          "op-0" -> ("micheline script 0", "micheline script 0"),
          "op-1" -> ("micheline script 1", "micheline script 1")
        )

      }

      "not mixup transactions in separate op-groups when traversing to both copy micheline/michelson and update" in {
        //given
        val transactions = List(
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 0"))))),
          transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script 1")))))
        )
        val groups = transactions.zipWithIndex.map {
          case (t, i) => operationGroup.copy(hash = OperationHash(s"op-$i"), contents = t :: Nil)
        }
        val block = Block(blockData, groups, blockVotes)

        val copyParametersToMicheline = (t: Transaction) => t.copy(parameters_micheline = t.parameters)
        //when
        val updateParams = parametersLens.modify(_ + " [modified]")
        val copyToMicheline = transactionLens.modify(copyParametersToMicheline)

        val modified = (copyToMicheline andThen updateParams)(block)

        //then
        val associativeMap = modified.operationGroups.map { g =>
          val params = g.contents.collect {
            case t: Transaction => (t.parameters, t.parameters_micheline)
          }.headOption.value

          val contents = params match {
            case (Some(Left(Parameters(Micheline(original), _))), Some(Left(Parameters(Micheline(copied), _)))) =>
              original -> copied
          }
          g.hash.value -> contents
        }

        associativeMap should contain theSameElementsInOrderAs List(
          "op-0" -> ("micheline script 0 [modified]", "micheline script 0"),
          "op-1" -> ("micheline script 1 [modified]", "micheline script 1")
        )

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
          spendable = None,
          isBaker = None,
          isActivated = None
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
          spendable = None,
          isBaker = None,
          isActivated = None
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
          spendable = None,
          isBaker = None,
          isActivated = None
        )

        val updated = sut.scriptLens.modify(old => old + "; new code")(account)
        updated.script.value shouldBe Contracts(
          storage = Micheline("storage code"),
          code = Micheline("Some code here; new code")
        )
      }

    }

}
