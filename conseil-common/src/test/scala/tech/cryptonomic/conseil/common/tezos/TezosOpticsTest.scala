package tech.cryptonomic.conseil.common.tezos

import java.time.ZonedDateTime
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Scripted.Contracts
import TezosOptics.Blocks.{acrossScriptsCode, acrossScriptsStorage, acrossTransactionParameters, acrossTransactions}
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

class TezosOpticsTest extends ConseilSpec {

  "The optics for tezos types" should {

    val blockData =
      BlockData(
        protocol = "_",
        chain_id = None,
        hash = TezosBlockHash("_"),
        header = BlockHeader(
          level = 0,
          proto = 0,
          predecessor = TezosBlockHash("_"),
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
          voting_period_kind = Some(defaultVotingPeriod),
          voting_period_info = None,
          nonce_hash = None,
          consumed_gas = Some(PositiveDecimal(0)),
          consumed_milligas = Some(PositiveDecimal(0)),
          level = Some(BlockHeaderMetadataLevel(0, 0, 0, 0, 0, 0, expected_commitment = false)),
          level_info = None,
          implicit_operations_results = None
        )
      )
    val blockVotes = CurrentVotes.empty
    val operationGroup = OperationsGroup("_", None, OperationHash("_"), TezosBlockHash("_"), List.empty, None)
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
      ResultMetadata(null, None)
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
      ResultMetadata(null, None)
    )

    "modify parameters with monocle's lenses" in {
      // given
      val modifiedTransaction =
        transaction.copy(parameters = Some(Left(Parameters(Micheline("micheline script"), Some("default")))))
      val modifiedOperations = List(operationGroup.copy(contents = origination :: modifiedTransaction :: Nil))

      val block = Block(blockData, modifiedOperations, blockVotes)

      // when
      val result = acrossTransactionParameters.modify(_.toUpperCase)(block)

      // then
      import org.scalatest.Inspectors._

      forAll(result.operationGroups.flatMap(_.contents)) {
        case op: Transaction =>
          op.parameters.value.left.value.value.expression shouldEqual "MICHELINE SCRIPT"
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
      val result = acrossScriptsStorage.modify(_.toUpperCase)(block)

      //then
      import org.scalatest.Inspectors._

      forAll(result.operationGroups.flatMap(_.contents)) {
        case op: Origination =>
          op.script.value shouldEqual Contracts(Micheline("EXPR1"), Micheline("eXpR2"))
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
      val result = acrossScriptsCode.modify(_.toLowerCase)(block)

      //then
      import org.scalatest.Inspectors._

      forAll(result.operationGroups.flatMap(_.contents)) {
        case op: Origination =>
          op.script.value shouldEqual Contracts(Micheline("eXpR1"), Micheline("expr2"))
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
      val modified = acrossTransactionParameters.modify(_ + " [modified]")(block)

      //then
      val associativeMap = modified.operationGroups.map { g =>
        val params = g.contents.collect { case t: Transaction =>
          t.parameters.value.left.value
        }
        val contents = params.map { case Parameters(Micheline(micheline), _) =>
          micheline
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
      val modified = acrossTransactions.modify(copyParametersToMicheline)(block)

      //then
      val associativeMap = modified.operationGroups.map { g =>
        val params = g.contents.collect { case t: Transaction =>
          (t.parameters, t.parameters_micheline)
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
      val groups = transactions.zipWithIndex.map { case (t, i) =>
        operationGroup.copy(hash = OperationHash(s"op-$i"), contents = t :: Nil)
      }
      val block = Block(blockData, groups, blockVotes)

      //when
      val modified = acrossTransactionParameters.modify(_ + " [modified]")(block)

      //then
      val associativeMap = modified.operationGroups.map { g =>
        val params = g.contents.collect { case t: Transaction =>
          t.parameters.get.left.value
        }
        val content = params.headOption.map { case Parameters(Micheline(micheline), _) =>
          micheline
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
      val groups = transactions.zipWithIndex.map { case (t, i) =>
        operationGroup.copy(hash = OperationHash(s"op-$i"), contents = t :: Nil)
      }
      val block = Block(blockData, groups, blockVotes)

      val copyParametersToMicheline = (t: Transaction) => t.copy(parameters_micheline = t.parameters)
      //when
      val modified = acrossTransactions.modify(copyParametersToMicheline)(block)

      //then
      val associativeMap = modified.operationGroups.map { g =>
        val params = g.contents.collectFirst { case t: Transaction =>
          (t.parameters, t.parameters_micheline)
        }.value

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
      val groups = transactions.zipWithIndex.map { case (t, i) =>
        operationGroup.copy(hash = OperationHash(s"op-$i"), contents = t :: Nil)
      }
      val block = Block(blockData, groups, blockVotes)

      val copyParametersToMicheline = (t: Transaction) => t.copy(parameters_micheline = t.parameters)
      //when
      val updateParams = acrossTransactionParameters.modify(_ + " [modified]")
      val copyToMicheline = acrossTransactions.modify(copyParametersToMicheline)

      val modified = (copyToMicheline andThen updateParams)(block)

      //then
      val associativeMap = modified.operationGroups.map { g =>
        val params = g.contents.collectFirst { case t: Transaction =>
          (t.parameters, t.parameters_micheline)
        }.value

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

      sut.whenAccountCode.getOption(account).value shouldBe "Some code here"
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

      sut.whenAccountCode.getOption(account) shouldBe 'empty
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

      val updated = sut.whenAccountCode.modify(old => old + "; new code")(account)
      updated.script.value shouldBe Contracts(
        storage = Micheline("storage code"),
        code = Micheline("Some code here; new code")
      )
    }

  }

}
