package tech.cryptonomic.conseil.tezos

import java.sql.Timestamp
import org.scalatest.{Matchers, OptionValues, WordSpec}
import org.scalatest.Inspectors._
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.util.{Conversion, RandomSeed}
import Conversion.Syntax._
import DatabaseConversions._

class DatabaseConversionsTest
    extends WordSpec
    with Matchers
    with OptionValues
    with TezosDataGeneration
    with DBConversionsData {

  "The database conversion" should {

      implicit val seed = RandomSeed(testReferenceTimestamp.getTime)

      val groupHash = OperationHash("operationhash")

      //keep level 1, dropping the genesis block
      val (genesis :: block :: Nil) = generateBlocks(toLevel = 1, startAt = testReferenceDateTime)

      val sut = DatabaseConversions

      "correctly convert a positive bignumber valued '0' from tezos models to a BigDecimal value" in {
        sut.extractBigDecimal(PositiveDecimal(0)).value shouldBe BigDecimal(0)
      }

      "correctly convert a positive bignumber from tezos models to a BigDecimal value" in {
        sut.extractBigDecimal(PositiveDecimal(1000)).value shouldBe BigDecimal(1000)
      }

      "give no result when converting invalid positive bignumbers from tezos models to a BigDecimal value" in {
        sut.extractBigDecimal(InvalidPositiveDecimal("1000A")) shouldBe 'empty
      }

      "correctly convert a bignumber from tezos models to a BigDecimal value" in {
        sut.extractBigDecimal(Decimal(1000)).value shouldBe BigDecimal(1000)
        sut.extractBigDecimal(Decimal(0)).value shouldBe BigDecimal(0)
        sut.extractBigDecimal(Decimal(-1000)).value shouldBe BigDecimal(-1000)
      }

      "give no result when converting invalid bignumbers from tezos models to a BigDecimal value" in {
        sut.extractBigDecimal(InvalidDecimal("1000A")) shouldBe 'empty
      }

      "convert a tezos genesis block to a database row" in {
        val converted = genesis.convertTo[Tables.BlocksRow]

        val header = genesis.data.header
        val CurrentVotes(expectedQuorum, proposal) = genesis.votes

        converted should have(
          'level (header.level),
          'proto (header.proto),
          'predecessor (header.predecessor.value),
          'timestamp (java.sql.Timestamp.from(header.timestamp.toInstant)),
          'validationPass (header.validation_pass),
          'fitness (header.fitness.mkString(",")),
          'context (Some(header.context)),
          'signature (header.signature),
          'protocol (genesis.data.protocol),
          'chainId (genesis.data.chain_id),
          'hash (genesis.data.hash.value),
          'operationsHash (header.operations_hash),
          'currentExpectedQuorum (expectedQuorum),
          'activeProposal (proposal.map(_.id))
        )

        //no metadata expected
        forAll(
          converted.periodKind ::
              converted.baker ::
              converted.nonceHash ::
              converted.consumedGas ::
              converted.metaLevel ::
              converted.metaLevelPosition ::
              converted.metaCycle ::
              converted.metaCyclePosition ::
              converted.metaVotingPeriod ::
              converted.metaVotingPeriodPosition ::
              converted.expectedCommitment :: Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert a tezos block to a database row" in {
        val converted = block.convertTo[Tables.BlocksRow]

        val header = block.data.header
        val metadata = discardGenesis.lift(block.data.metadata)
        val CurrentVotes(expectedQuorum, proposal) = block.votes

        converted should have(
          'level (header.level),
          'proto (header.proto),
          'predecessor (header.predecessor.value),
          'timestamp (java.sql.Timestamp.from(header.timestamp.toInstant)),
          'validationPass (header.validation_pass),
          'fitness (header.fitness.mkString(",")),
          'context (Some(header.context)),
          'signature (header.signature),
          'protocol (block.data.protocol),
          'chainId (block.data.chain_id),
          'hash (block.data.hash.value),
          'operationsHash (header.operations_hash),
          'currentExpectedQuorum (expectedQuorum),
          'activeProposal (proposal.map(_.id)),
          'periodKind (metadata.map(_.voting_period_kind.toString)),
          'baker (metadata.map(_.baker.value)),
          'nonceHash (metadata.flatMap(_.nonce_hash.map(_.value))),
          'metaLevel (metadata.map(_.level.level)),
          'metaLevelPosition (metadata.map(_.level.level_position)),
          'metaCycle (metadata.map(_.level.cycle)),
          'metaCyclePosition (metadata.map(_.level.cycle_position)),
          'metaVotingPeriod (metadata.map(_.level.voting_period)),
          'metaVotingPeriodPosition (metadata.map(_.level.voting_period_position)),
          'expectedCommitment (metadata.map(_.level.expected_commitment)),
          'priority (block.data.header.priority)
        )

        metadata.map(_.consumed_gas) match {
          case Some(PositiveDecimal(bignumber)) => converted.consumedGas.value shouldBe bignumber
          case _ => converted.consumedGas shouldBe 'empty
        }

      }

      "convert Balance Updates in BlockData to a database row" in {
        import BlockBalances._
        import SymbolSourceLabels.Show._

        //generate data
        val updates = generateBalanceUpdates(3)
        val block = generateSingleBlock(atLevel = 1, atTime = testReferenceDateTime, balanceUpdates = updates)

        //convert
        val updateRows = block.data.convertToA[List, Tables.BalanceUpdatesRow]

        //verify
        val up1 :: up2 :: up3 :: Nil = updates

        updateRows should contain theSameElementsAs List(
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = Some(block.data.hash.value),
            source = "block",
            kind = up1.kind,
            contract = up1.contract.map(_.id),
            change = BigDecimal(up1.change),
            level = up1.level.map(BigDecimal(_)),
            delegate = up1.delegate.map(_.value),
            category = up1.category
          ),
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = Some(block.data.hash.value),
            source = "block",
            kind = up2.kind,
            contract = up2.contract.map(_.id),
            change = BigDecimal(up2.change),
            level = up2.level.map(BigDecimal(_)),
            delegate = up2.delegate.map(_.value),
            category = up2.category
          ),
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = Some(block.data.hash.value),
            source = "block",
            kind = up3.kind,
            contract = up3.contract.map(_.id),
            change = BigDecimal(up3.change),
            level = up3.level.map(BigDecimal(_)),
            delegate = up3.delegate.map(_.value),
            category = up3.category
          )
        )
      }

      "convert Balance Updates in Operations to a database row" in {
        import OperationBalances._
        import SymbolSourceLabels.Show._

        sampleReveal.convertToA[List, Tables.BalanceUpdatesRow] should contain only (
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = None,
            source = "operation",
            kind = "contract",
            contract = Some("KT1PPuBrvCGpJt54hVBgXMm2sKa6QpSwKrJq"),
            change = -10000L,
            level = None,
            delegate = None,
            category = None
          ),
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = None,
            source = "operation",
            kind = "freezer",
            contract = None,
            change = 10000L,
            level = Some(1561),
            delegate = Some("tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889"),
            category = Some("fees")
          )
        )
      }

      "convert Balance Updates in all nested levels of Operations to a database row" in {
        import OperationBalances._
        import SymbolSourceLabels.Show._

        sampleOrigination.convertToA[List, Tables.BalanceUpdatesRow] should contain only (
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = None,
            source = "operation",
            kind = "contract",
            contract = Some("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
            change = -1441L,
            level = None,
            delegate = None,
            category = None
          ),
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = None,
            source = "operation",
            kind = "freezer",
            contract = None,
            change = 1441L,
            level = Some(1583),
            delegate = Some("tz1boot1pK9h2BVGXdyvfQSv8kd1LQM6H889"),
            category = Some("fees")
          ),
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = None,
            source = "operation_result",
            kind = "contract",
            contract = Some("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
            change = -46000L,
            category = None,
            delegate = None,
            level = None
          ),
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = None,
            source = "operation_result",
            kind = "contract",
            contract = Some("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
            change = -257000L,
            category = None,
            delegate = None,
            level = None
          ),
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = None,
            source = "operation_result",
            kind = "contract",
            contract = Some("tz1hSd1ZBFVkoXC5s1zMguz3AjyCgGQ7FMbR"),
            change = -1000000L,
            category = None,
            delegate = None,
            level = None
          ),
          Tables.BalanceUpdatesRow(
            id = 0,
            sourceId = None,
            sourceHash = None,
            source = "operation_result",
            kind = "contract",
            contract = Some("KT1VuJAgTJT5x2Y2S3emAVSbUA5nST7j3QE4"),
            change = 1000000L,
            category = None,
            delegate = None,
            level = None
          )
        )
      }

      "convert an Endorsement to a database row" in {

        val converted = (block, groupHash, sampleEndorsement: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "endorsement"
        converted.level.value shouldBe sampleEndorsement.level
        converted.delegate.value shouldBe sampleEndorsement.metadata.delegate.value
        converted.slots.value shouldBe "[29,27,20,17]"
        //branch and numberOfSlots needs test

        forAll(
          converted.nonce ::
              converted.pkh ::
              converted.secret ::
              converted.source ::
              converted.fee ::
              converted.counter ::
              converted.gasLimit ::
              converted.storageLimit ::
              converted.publicKey ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.consumedGas ::
              converted.status ::
              converted.storageSize ::
              converted.paidStorageSizeDiff ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert a SeedNonceRevelation to a database row" in {

        val converted = (block, groupHash, sampleNonceRevelation: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "seed_nonce_revelation"
        converted.level.value shouldBe sampleNonceRevelation.level
        converted.nonce.value shouldBe sampleNonceRevelation.nonce.value

        forAll(
          converted.delegate ::
              converted.slots ::
              converted.pkh ::
              converted.secret ::
              converted.source ::
              converted.fee ::
              converted.counter ::
              converted.gasLimit ::
              converted.storageLimit ::
              converted.publicKey ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.consumedGas ::
              converted.status ::
              converted.storageSize ::
              converted.paidStorageSizeDiff ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert an ActivateAccount to a database row" in {

        val converted = (block, groupHash, sampleAccountActivation: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "activate_account"
        converted.pkh.value shouldBe sampleAccountActivation.pkh.value
        converted.secret.value shouldBe sampleAccountActivation.secret.value

        forAll(
          converted.level ::
              converted.delegate ::
              converted.slots ::
              converted.nonce ::
              converted.source ::
              converted.fee ::
              converted.counter ::
              converted.gasLimit ::
              converted.storageLimit ::
              converted.publicKey ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.consumedGas ::
              converted.status ::
              converted.storageSize ::
              converted.paidStorageSizeDiff ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert a Reveal to a database row" in {

        val converted = (block, groupHash, sampleReveal: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "reveal"
        converted.source.value shouldBe sampleReveal.source.id
        sampleReveal.fee match {
          case PositiveDecimal(bignumber) => converted.fee.value shouldBe bignumber
          case _ => converted.fee shouldBe 'empty
        }
        sampleReveal.counter match {
          case PositiveDecimal(bignumber) => converted.counter.value shouldBe bignumber
          case _ => converted.counter shouldBe 'empty
        }
        sampleReveal.gas_limit match {
          case PositiveDecimal(bignumber) => converted.gasLimit.value shouldBe bignumber
          case _ => converted.gasLimit shouldBe 'empty
        }
        sampleReveal.storage_limit match {
          case PositiveDecimal(bignumber) => converted.storageLimit.value shouldBe bignumber
          case _ => converted.storageLimit shouldBe 'empty
        }
        converted.publicKey.value shouldBe sampleReveal.public_key.value
        converted.status.value shouldBe sampleReveal.metadata.operation_result.status
        sampleReveal.metadata.operation_result.consumed_gas match {
          case Some(Decimal(bignumber)) => converted.consumedGas.value shouldBe bignumber
          case _ => converted.consumedGas shouldBe 'empty
        }

        forAll(
          converted.level ::
              converted.delegate ::
              converted.slots ::
              converted.nonce ::
              converted.pkh ::
              converted.secret ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.storageSize ::
              converted.paidStorageSizeDiff ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert a Transaction to a database row" in {

        val converted = (block, groupHash, sampleTransaction: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "transaction"
        converted.source.value shouldBe sampleTransaction.source.id
        sampleTransaction.fee match {
          case PositiveDecimal(bignumber) => converted.fee.value shouldBe bignumber
          case _ => converted.fee shouldBe 'empty
        }
        sampleTransaction.counter match {
          case PositiveDecimal(bignumber) => converted.counter.value shouldBe bignumber
          case _ => converted.counter shouldBe 'empty
        }
        sampleTransaction.gas_limit match {
          case PositiveDecimal(bignumber) => converted.gasLimit.value shouldBe bignumber
          case _ => converted.gasLimit shouldBe 'empty
        }
        sampleTransaction.storage_limit match {
          case PositiveDecimal(bignumber) => converted.storageLimit.value shouldBe bignumber
          case _ => converted.storageLimit shouldBe 'empty
        }
        sampleTransaction.amount match {
          case PositiveDecimal(bignumber) => converted.amount.value shouldBe bignumber
          case _ => converted.amount shouldBe 'empty
        }
        converted.destination.value shouldBe sampleTransaction.destination.id
        converted.parameters shouldBe sampleTransaction.parameters.map(_.expression)
        converted.status.value shouldBe sampleTransaction.metadata.operation_result.status
        sampleTransaction.metadata.operation_result.consumed_gas match {
          case Some(Decimal(bignumber)) => converted.consumedGas.value shouldBe bignumber
          case _ => converted.consumedGas shouldBe 'empty
        }
        sampleTransaction.metadata.operation_result.storage_size match {
          case Some(Decimal(bignumber)) => converted.storageSize.value shouldBe bignumber
          case _ => converted.storageSize shouldBe 'empty
        }
        sampleTransaction.metadata.operation_result.paid_storage_size_diff match {
          case Some(Decimal(bignumber)) => converted.paidStorageSizeDiff.value shouldBe bignumber
          case _ => converted.paidStorageSizeDiff shouldBe 'empty
        }

        forAll(
          converted.level ::
              converted.delegate ::
              converted.slots ::
              converted.nonce ::
              converted.pkh ::
              converted.secret ::
              converted.publicKey ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert an Origination to a database row" in {

        val converted = (block, groupHash, sampleOrigination: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "origination"
        converted.delegate shouldBe sampleOrigination.delegate.map(_.value)
        converted.source.value shouldBe sampleOrigination.source.id
        sampleOrigination.fee match {
          case PositiveDecimal(bignumber) => converted.fee.value shouldBe bignumber
          case _ => converted.fee shouldBe 'empty
        }
        sampleOrigination.counter match {
          case PositiveDecimal(bignumber) => converted.counter.value shouldBe bignumber
          case _ => converted.counter shouldBe 'empty
        }
        sampleOrigination.gas_limit match {
          case PositiveDecimal(bignumber) => converted.gasLimit.value shouldBe bignumber
          case _ => converted.gasLimit shouldBe 'empty
        }
        sampleOrigination.storage_limit match {
          case PositiveDecimal(bignumber) => converted.storageLimit.value shouldBe bignumber
          case _ => converted.storageLimit shouldBe 'empty
        }
        sampleOrigination.balance match {
          case PositiveDecimal(bignumber) => converted.balance.value shouldBe bignumber
          case _ => converted.balance shouldBe 'empty
        }
        converted.managerPubkey.value shouldBe sampleOrigination.manager_pubkey.value
        converted.spendable shouldBe sampleOrigination.spendable
        converted.delegatable shouldBe sampleOrigination.delegatable
        converted.script shouldBe sampleOrigination.script.map(_.code.expression)
        converted.storage shouldBe sampleOrigination.script.map(_.storage.expression)
        converted.status.value shouldBe sampleOrigination.metadata.operation_result.status
        sampleOrigination.metadata.operation_result.consumed_gas match {
          case Some(Decimal(bignumber)) => converted.consumedGas.value shouldBe bignumber
          case _ => converted.consumedGas shouldBe 'empty
        }
        sampleOrigination.metadata.operation_result.storage_size match {
          case Some(Decimal(bignumber)) => converted.storageSize.value shouldBe bignumber
          case _ => converted.storageSize shouldBe 'empty
        }
        sampleOrigination.metadata.operation_result.paid_storage_size_diff match {
          case Some(Decimal(bignumber)) => converted.paidStorageSizeDiff.value shouldBe bignumber
          case _ => converted.paidStorageSizeDiff shouldBe 'empty
        }
        converted.originatedContracts.value shouldBe "KT1VuJAgTJT5x2Y2S3emAVSbUA5nST7j3QE4,KT1Hx96yGgGk2q7Jmwm1dnYAMdRoLJNn5gnC"

        forAll(
          converted.level ::
              converted.slots ::
              converted.nonce ::
              converted.pkh ::
              converted.secret ::
              converted.publicKey ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert an Delegation to a database row" in {

        val converted = (block, groupHash, sampleDelegation: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "delegation"
        converted.delegate shouldBe sampleDelegation.delegate.map(_.value)
        converted.source.value shouldBe sampleDelegation.source.id
        sampleDelegation.fee match {
          case PositiveDecimal(bignumber) => converted.fee.value shouldBe bignumber
          case _ => converted.fee shouldBe 'empty
        }
        sampleDelegation.counter match {
          case PositiveDecimal(bignumber) => converted.counter.value shouldBe bignumber
          case _ => converted.counter shouldBe 'empty
        }
        sampleDelegation.gas_limit match {
          case PositiveDecimal(bignumber) => converted.gasLimit.value shouldBe bignumber
          case _ => converted.gasLimit shouldBe 'empty
        }
        sampleDelegation.storage_limit match {
          case PositiveDecimal(bignumber) => converted.storageLimit.value shouldBe bignumber
          case _ => converted.storageLimit shouldBe 'empty
        }
        converted.status.value shouldBe sampleDelegation.metadata.operation_result.status
        sampleDelegation.metadata.operation_result.consumed_gas match {
          case Some(Decimal(bignumber)) => converted.consumedGas.value shouldBe bignumber
          case _ => converted.consumedGas shouldBe 'empty
        }

        forAll(
          converted.level ::
              converted.slots ::
              converted.nonce ::
              converted.pkh ::
              converted.secret ::
              converted.publicKey ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.storageSize ::
              converted.paidStorageSizeDiff ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert an DoubleEndorsementEvidence to a database row" in {

        val converted = (block, groupHash, DoubleEndorsementEvidence: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "double_endorsement_evidence"

        forAll(
          converted.level ::
              converted.delegate ::
              converted.slots ::
              converted.nonce ::
              converted.pkh ::
              converted.secret ::
              converted.source ::
              converted.fee ::
              converted.counter ::
              converted.gasLimit ::
              converted.storageLimit ::
              converted.publicKey ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.status ::
              converted.consumedGas ::
              converted.storageSize ::
              converted.paidStorageSizeDiff ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert an DoubleBakingEvidence to a database row" in {

        val converted = (block, groupHash, DoubleBakingEvidence: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "double_baking_evidence"

        forAll(
          converted.level ::
              converted.delegate ::
              converted.slots ::
              converted.nonce ::
              converted.pkh ::
              converted.secret ::
              converted.source ::
              converted.fee ::
              converted.counter ::
              converted.gasLimit ::
              converted.storageLimit ::
              converted.publicKey ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.status ::
              converted.consumedGas ::
              converted.storageSize ::
              converted.paidStorageSizeDiff ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert a Proposals operation to a database row" in {

        val converted = (block, groupHash, sampleProposals: Operation).convertTo[Tables.OperationsRow]

        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.kind shouldBe "proposals"
        converted.source shouldBe Some("tz1VceyYUpq1gk5dtp6jXQRtCtY8hm5DKt72")
        //converted.period shouldBe Some(10)
        converted.proposal shouldBe Some("[\"Psd1ynUBhMZAeajwcZJAeq5NrxorM6UCU4GJqxZ7Bx2e9vUWB6z\"]")

        forAll(
          converted.level ::
              converted.delegate ::
              converted.slots ::
              converted.nonce ::
              converted.pkh ::
              converted.secret ::
              converted.source ::
              converted.fee ::
              converted.counter ::
              converted.gasLimit ::
              converted.storageLimit ::
              converted.publicKey ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.status ::
              converted.consumedGas ::
              converted.storageSize ::
              converted.paidStorageSizeDiff ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert a Ballot operation to a database row" in {

        val converted = (block, groupHash, sampleBallot: Operation).convertTo[Tables.OperationsRow]

        converted.kind shouldBe "ballot"
        converted.operationId shouldBe 0
        converted.operationGroupHash shouldBe groupHash.value
        converted.source shouldBe Some("tz1VceyYUpq1gk5dtp6jXQRtCtY8hm5DKt72")
        converted.blockHash shouldBe block.data.hash.value
        converted.blockLevel shouldBe block.data.header.level
        converted.ballot shouldBe Some("yay")
        converted.timestamp shouldBe Timestamp.from(block.data.header.timestamp.toInstant)
        converted.proposal shouldBe Some("PsBABY5HQTSkA4297zNHfsZNKtxULfL18y95qb3m53QJiXGmrbU")

        forAll(
          converted.level ::
              converted.delegate ::
              converted.slots ::
              converted.nonce ::
              converted.pkh ::
              converted.secret ::
              converted.fee ::
              converted.counter ::
              converted.gasLimit ::
              converted.storageLimit ::
              converted.publicKey ::
              converted.amount ::
              converted.destination ::
              converted.parameters ::
              converted.managerPubkey ::
              converted.balance ::
              converted.spendable ::
              converted.delegatable ::
              converted.script ::
              converted.status ::
              converted.consumedGas ::
              converted.storageSize ::
              converted.paidStorageSizeDiff ::
              converted.originatedContracts ::
              Nil
        ) {
          _ shouldBe 'empty
        }

      }

      "convert a Voting Proposal to a database row" in {
        import tech.cryptonomic.conseil.tezos.TezosTypes.Voting.Proposal

        val sampleProposal =
          Proposal(protocols = (ProtocolId("proto1"), 1) :: (ProtocolId("proto2"), 2) :: Nil, block = block)

        val expected = List(
          Tables.ProposalsRow(
            protocolHash = "proto1",
            supporters = Some(1),
            blockId = block.data.hash.value,
            blockLevel = block.data.header.level
          ),
          Tables.ProposalsRow(
            protocolHash = "proto2",
            supporters = Some(2),
            blockId = block.data.hash.value,
            blockLevel = block.data.header.level
          )
        )

        val converted = sampleProposal.convertToA[List, Tables.ProposalsRow]
        converted should have size (sampleProposal.protocols.size)

        converted should contain theSameElementsAs expected

      }

      "convert a Voting Ballot to a database row" in {
        import tech.cryptonomic.conseil.tezos.TezosTypes.Voting.{Ballot, Vote}

        val sampleBallot = Ballot(pkh = PublicKeyHash("key"), ballot = Vote("yay"))

        val converted = (block, List(sampleBallot)).convertToA[List, Tables.BallotsRow]
        converted should have size 1

        converted should contain only (
          Tables.BallotsRow(
            pkh = sampleBallot.pkh.value,
            ballot = sampleBallot.ballot.value,
            blockId = block.data.hash.value,
            blockLevel = block.data.header.level
          )
        )
      }

      "convert a Voting Baker to a database row" in {
        import tech.cryptonomic.conseil.tezos.TezosTypes.Voting.BakerRolls

        val sampleRolls = BakerRolls(pkh = PublicKeyHash("key"), rolls = 500)

        val converted = (block, List(sampleRolls)).convertToA[List, Tables.RollsRow]
        converted should have size 1

        converted should contain only (
          Tables.RollsRow(
            pkh = sampleRolls.pkh.value,
            rolls = sampleRolls.rolls,
            blockId = block.data.hash.value,
            blockLevel = block.data.header.level
          )
        )
      }

    }
}
