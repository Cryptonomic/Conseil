package tech.cryptonomic.conseil.indexer.tezos

import com.github.ghik.silencer.silent
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.util.JsonUtil.adaptManagerPubkeyField
import tech.cryptonomic.conseil.common.testkit.ConseilSpec

// @silent("local val derivationConf")
class TezosJsonDecodersTest extends ConseilSpec {

  import TezosJsonDecoders.Circe.Accounts._
  import TezosJsonDecoders.Circe.Numbers._
  import TezosJsonDecoders.Circe.BigMapDiff._
  import TezosJsonDecoders.Circe.Operations._
  import TezosJsonDecoders.Circe.Scripts._
  import TezosJsonDecoders.Circe.Votes._
  import TezosJsonDecoders.Circe._
  import io.circe.parser.decode
  "the json decoders" should {

      val validB58Hash =
        "signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
      val invalidB58Hash =
        "signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSl"
      val alphanumneric = "asdopkjfap2398ufa3908wimv3pw98vja3pw98v"
      val invalidAlphanumeric = "@*;akjfa80330"
      val invalidJson = """{wrongname: "name"}"""

      /** wrap in quotes to be a valid json string */
      val jsonStringOf = (content: String) => s""""$content""""

      "allow decoding json with duplicate fields" in {
        import io.circe.Decoder
        import io.circe.generic.extras.semiauto._
        implicit val derivationConf = Derivation.tezosDerivationConfig

        case class JsonTest(field: String)

        implicit val testDecoder: Decoder[JsonTest] = deriveConfiguredDecoder

        val duplicateDecoded = decode[JsonTest]("""{"field": "test", "field": "duplicate"}""")
        duplicateDecoded shouldBe 'right

        val duplicateUndecoded = decode[JsonTest]("""{"field": "test", "inner": {"key": "one", "key": "duplicate"}}""")
        duplicateUndecoded shouldBe 'right

      }

      "decode null fields as empty Options" in {
        import io.circe.Decoder
        import io.circe.generic.extras.semiauto._
        implicit val derivationConf = Derivation.tezosDerivationConfig

        case class JsonTest(field: Option[String])

        implicit val testDecoder: Decoder[JsonTest] = deriveConfiguredDecoder

        val valueDecoded = decode[JsonTest]("""{"field": "test"}""")
        valueDecoded.right.value shouldBe JsonTest(Some("test"))

        val nulllDecoded = decode[JsonTest]("""{"field": null}""")
        nulllDecoded.right.value shouldBe JsonTest(None)

      }

      "decode a timestamp from ISO-8601 string format" in {
        import java.time._
        import format.DateTimeFormatter.ISO_INSTANT

        val time = Instant.now()
        val timestamp = ISO_INSTANT.format(time)

        val decoded = decode[ZonedDateTime](s""""$timestamp"""") // wrap as a json string
        decoded shouldBe 'right

        decoded.right.value.toInstant shouldEqual Instant.parse(timestamp)
      }

      "decode valid json base58check strings into a PublicKey" in {
        val decoded = decode[PublicKey](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe PublicKey(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a PublicKey" in {
        val decoded = decode[PublicKey](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json base58check strings into a PublicKeyHash" in {
        val decoded = decode[PublicKeyHash](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe PublicKeyHash(validB58Hash)
      }

      "decode valid json base58check strings into a AccountId" in {
        val decoded = decode[AccountId](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe PublicKeyHash(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a PublicKeyHash" in {
        val decoded = decode[PublicKeyHash](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json base58check strings into a Signature" in {
        val decoded = decode[Signature](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe Signature(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a Signature" in {
        val decoded = decode[Signature](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json base58check strings into a BlockHash" in {
        val decoded = decode[TezosBlockHash](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe TezosBlockHash(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a BlockHash" in {
        val decoded = decode[TezosBlockHash](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json base58check strings into a OperationHash" in {
        val decoded = decode[OperationHash](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe OperationHash(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a OperationHash" in {
        val decoded = decode[OperationHash](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "fail to decode an invalid json base58check strings into a AccountId" in {
        val decoded = decode[AccountId](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json base58check strings into a ContractId" in {
        val decoded = decode[ContractId](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe ContractId(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a ContractId" in {
        val decoded = decode[ContractId](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json base58check strings into a ChainId" in {
        val decoded = decode[ChainId](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe ChainId(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a ChainId" in {
        val decoded = decode[ChainId](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json base58check strings into a ProtocolId" in {
        val decoded = decode[ProtocolId](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe ProtocolId(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a ProtocolId" in {
        val decoded = decode[ProtocolId](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json base58check strings into a ScriptId" in {
        val decoded = decode[ScriptId](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe ScriptId(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a ScriptId" in {
        val decoded = decode[ScriptId](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json alphanumneric strings into a Nonce" in {
        val decoded = decode[Nonce](jsonStringOf(alphanumneric))
        decoded.right.value shouldBe Nonce(alphanumneric)
      }

      "fail to decode an invalid json alphanumneric strings into a Nonce" in {
        val decoded = decode[Nonce](jsonStringOf(invalidAlphanumeric))
        decoded shouldBe 'left
      }

      "decode valid json base58check strings into a NonceHash" in {
        val decoded = decode[NonceHash](jsonStringOf(validB58Hash))
        decoded.right.value shouldBe NonceHash(validB58Hash)
      }

      "fail to decode an invalid json base58check strings into a NonceHash" in {
        val decoded = decode[NonceHash](jsonStringOf(invalidB58Hash))
        decoded shouldBe 'left
      }

      "decode valid json alphanumneric strings into a Secret" in {
        val decoded = decode[Secret](jsonStringOf(alphanumneric))
        decoded.right.value shouldBe Secret(alphanumneric)
      }

      "fail to decode an invalid json alphanumneric strings into a Secret" in {
        val decoded = decode[Secret](jsonStringOf(invalidAlphanumeric))
        decoded shouldBe 'left
      }

      "decode valid json into Micheline values" in new OperationsJsonData {
        val decoded = decode[Micheline](michelineJson)
        decoded.right.value shouldEqual expectedMicheline
      }

      "fail to decode invalid json into Micheline values" in new OperationsJsonData {
        val decoded = decode[Micheline](invalidJson)
        decoded shouldBe 'left
      }

      "decode valid json strings representing a PositiveBigNumber" in {
        val decoded = decode[PositiveBigNumber](jsonStringOf("1000000000"))
        decoded.right.value shouldEqual PositiveDecimal(1000000000)
      }

      "decode valid json strings representing zero as a PositiveBigNumber" in {
        val decoded = decode[PositiveBigNumber](jsonStringOf("0"))
        decoded.right.value shouldEqual PositiveDecimal(0)
      }

      "decode invalid json for PositiveBigNumber, representing negatives, as the original string" in {
        val decoded = decode[PositiveBigNumber](jsonStringOf("-1000000000"))
        decoded.right.value shouldBe InvalidPositiveDecimal("-1000000000")
      }

      "decode invalid json for PositiveBigNumber, not representing numbers, as the original string" in {
        val decoded = decode[PositiveBigNumber](jsonStringOf("1AA000000000"))
        decoded.right.value shouldBe InvalidPositiveDecimal("1AA000000000")
      }

      "decode valid json strings representing both positive and negative values as BigNumber" in {
        val decoded = decode[BigNumber](jsonStringOf("1000000000"))
        decoded.right.value shouldEqual Decimal(1000000000)

        val negDecoded = decode[BigNumber](jsonStringOf("-1000000000"))
        negDecoded.right.value shouldBe Decimal(-1000000000)
      }

      "decode invalid json for BigNumber, not representing numbers, as the original string" in {
        val decoded = decode[BigNumber](jsonStringOf("1AA000000000"))
        decoded.right.value shouldBe InvalidDecimal("1AA000000000")
      }

      "decode all valid voting period kinds to an enumerated value" in {
        val proposal = decode[VotingPeriod.Kind](jsonStringOf("proposal"))
        proposal shouldBe 'right
        proposal.right.value shouldBe VotingPeriod.proposal
        val promotion_vote = decode[VotingPeriod.Kind](jsonStringOf("promotion_vote"))
        promotion_vote shouldBe 'right
        promotion_vote.right.value shouldBe VotingPeriod.promotion_vote
        val testing_vote = decode[VotingPeriod.Kind](jsonStringOf("testing_vote"))
        testing_vote shouldBe 'right
        testing_vote.right.value shouldBe VotingPeriod.testing_vote
        val testing = decode[VotingPeriod.Kind](jsonStringOf("testing"))
        testing shouldBe 'right
        testing.right.value shouldBe VotingPeriod.testing
      }

      "fail to decode an invalid string as a voting period kind" in {
        val decoded = decode[VotingPeriod.Kind](jsonStringOf("undefined_period"))
        decoded shouldBe 'left
      }

      "decode all valid ballot votes to a BallotVote" in {
        val yay = decode[Voting.Vote](jsonStringOf("yay"))
        yay shouldBe 'right
        val nay = decode[Voting.Vote](jsonStringOf("nay"))
        nay shouldBe 'right
        val pass = decode[Voting.Vote](jsonStringOf("pass"))
        pass shouldBe 'right
      }

      "fail to decode an invalid string to a BallotVote" in {
        val decoded = decode[Voting.Vote](jsonStringOf("nope"))
        decoded shouldBe 'left
      }

      "decode valid protocol4 json into a BigMapDiff value" in new OperationsJsonData {
        val decoded = decode[Contract.CompatBigMapDiff](p4BigmapdiffJson)
        decoded shouldBe 'right
        decoded.right.value shouldBe 'right
        decoded.right.value.right.value shouldEqual expectedP4BigMapDiff
      }

      "decode valid json into a BigMapDiffUpdate value" in new OperationsJsonData {
        val decoded = decode[Contract.CompatBigMapDiff](bigmapdiffUpdateJson)
        decoded shouldBe 'right
        decoded.right.value shouldBe 'left
        decoded.right.value.left.value shouldEqual expectedBigmapdiffUpdate
      }

      "decode valid json into a BigMapDiffCopy value" in new OperationsJsonData {
        val decoded = decode[Contract.CompatBigMapDiff](bigmapdiffCopyJson)
        decoded shouldBe 'right
        decoded.right.value shouldBe 'left
        decoded.right.value.left.value shouldEqual expectedBigmapdiffCopy
      }

      "decode valid json into a BigMapDiffAlloc value" in new OperationsJsonData {
        val decoded = decode[Contract.CompatBigMapDiff](bigmapdiffAllocJson)
        decoded shouldBe 'right
        decoded.right.value shouldBe 'left
        decoded.right.value.left.value shouldEqual expectedBigmapdiffAlloc
      }

      "decode valid json into a BigMapDiffRemove value" in new OperationsJsonData {
        val decoded = decode[Contract.CompatBigMapDiff](bigmapdiffRemoveJson)
        decoded shouldBe 'right
        decoded.right.value shouldBe 'left
        decoded.right.value.left.value shouldEqual expectedBigmapdiffRemove
      }

      "decode valid json into a Scipted.Contracts value" in new OperationsJsonData {
        val decoded = decode[Scripted.Contracts](scriptJson)
        decoded.right.value shouldEqual expectedScript
      }

      "decode valid json into Error values" in new OperationsJsonData {
        val decoded = decode[OperationResult.Error](errorJson)
        decoded.right.value shouldEqual expectedError
      }

      "fail to decode invalid json into Error values" in new OperationsJsonData {
        val decoded = decode[OperationResult.Error](invalidJson)
        decoded shouldBe 'left
      }

      "decode an endorsement operation from json" in new OperationsJsonData {
        val decoded = decode[Operation](adaptManagerPubkeyField(endorsementJson))
        decoded shouldBe 'right

        val operation = decoded.right.value
        operation shouldBe a[Endorsement]
        operation shouldEqual expectedEndorsement

      }

      "decode a seed nonce revelation operation from json" in new OperationsJsonData {
        val decoded = decode[Operation](adaptManagerPubkeyField(nonceRevelationJson))
        decoded shouldBe 'right

        val operation = decoded.right.value
        operation shouldBe a[SeedNonceRevelation]
        operation shouldEqual expectedNonceRevelation

      }

      "decode an account activation operation from json" in new OperationsJsonData {
        val decoded = decode[Operation](adaptManagerPubkeyField(activationJson))
        decoded shouldBe 'right

        val operation = decoded.right.value
        operation shouldBe a[ActivateAccount]
        operation shouldEqual expectedActivation

      }

      "decode an reveal operation from json" in new OperationsJsonData {
        val decoded = decode[Operation](adaptManagerPubkeyField(revealJson))
        decoded shouldBe 'right

        val operation = decoded.right.value
        operation shouldBe a[Reveal]
        operation shouldEqual expectedReveal

      }

      "decode an transaction with internal transaction result operation from json" in new OperationsJsonData {
        val decoded = decode[Operation](adaptManagerPubkeyField(transactionWithInternalTransactionJson))
        decoded shouldBe 'right

        val operation = decoded.right.value
        operation shouldBe a[Transaction]
        operation shouldEqual transactionWithInternalTransactionResult

      }

      "decode an origination operation from json" in new OperationsJsonData {
        val decoded = decode[Operation](adaptManagerPubkeyField(originationJson))
        decoded shouldBe 'right

        val operation = decoded.right.value
        operation shouldBe a[Origination]
        operation shouldEqual expectedOrigination

      }

      "decode an origination operation from alphanet schema json" in new OperationsJsonData {
        val decoded = decode[Operation](adaptManagerPubkeyField(alphanetOriginationJson))
        decoded shouldBe 'right

        val operation = decoded.right.value
        operation shouldBe a[Origination]
        operation shouldEqual expectedOrigination

      }

      "decode a delegation operation from json" in new OperationsJsonData {
        val decoded = decode[Operation](adaptManagerPubkeyField(delegationJson))
        decoded shouldBe 'right

        val operation = decoded.right.value
        operation shouldBe a[Delegation]
        operation shouldEqual expectedDelegation

      }

      "decode a group of operations from json" in new OperationsJsonData {
        val decoded = decode[OperationsGroup](operationsGroupJson)
        decoded shouldBe 'right

        val operations = decoded.right.value
        operations shouldBe a[OperationsGroup]
        operations shouldEqual expectedGroup

      }

      "decode proposals elements" in {
        val decoded =
          decode[List[(ProtocolId, ProposalSupporters)]](s"""[["$validB58Hash", 1], ["$validB58Hash", 2]]""")
        decoded shouldBe 'right
        decoded.right.value should contain theSameElementsAs List(
          ProtocolId(validB58Hash) -> 1,
          ProtocolId(validB58Hash) -> 2
        )
      }

      "decode bakers rolls" in {
        val decoded = decode[Voting.BakerRolls](s"""{"pkh":"$validB58Hash", "rolls":150}""")
        decoded shouldBe 'right
        decoded.right.value shouldBe Voting.BakerRolls(pkh = PublicKeyHash(validB58Hash), rolls = 150)
      }

      "decode bakers rolls even if rolls are json encoded as 'stringly' numbers" in {
        val decoded = decode[Voting.BakerRolls](s"""{"pkh":"$validB58Hash", "rolls":"150"}""")
        decoded shouldBe 'right
        decoded.right.value shouldBe Voting.BakerRolls(pkh = PublicKeyHash(validB58Hash), rolls = 150)
      }

      "fail to decode bakers rolls for invalid fields" in {
        val failedHash = decode[Voting.BakerRolls](s"""{"pkh":"SinvalidB58Hash", "rolls":150}""")
        failedHash shouldBe 'left
        val failedRolls = decode[Voting.BakerRolls](s"""{"pkh":"$validB58Hash", "rolls":true}""")
        failedRolls shouldBe 'left
      }

      "decode ballots" in {
        val decoded = decode[Voting.Ballot](s"""{"pkh":"$validB58Hash", "ballot":"yay"}""")
        decoded shouldBe 'right
        decoded.right.value shouldBe Voting.Ballot(pkh = PublicKeyHash(validB58Hash), ballot = Voting.Vote("yay"))
      }

      "fail to decode ballots with invalid fields" in {
        val failedHash = decode[Voting.Ballot](s"""{"pkh":"SinvalidB58Hash", "ballot":"yay"}""")
        failedHash shouldBe 'left
        val failedBallot = decode[Voting.Ballot](s"""{"pkh":"$validB58Hash", "ballot":"nup"}""")
        failedBallot shouldBe 'left
      }

      "decode accounts" in new AccountsJsonData {
        val decoded = decode[Account](accountJson)
        decoded shouldBe 'right

        val account = decoded.right.value
        account shouldEqual expectedAccount
      }

      "decode legacy protocol4 accounts" in new AccountsJsonData {
        val decoded = decode[Account](legacyAccountJson)
        decoded shouldBe 'right

        val account = decoded.right.value
        account shouldEqual expectedLegacyAccount
      }

      "decode account scripts as wrapped and unparsed text, instead of a json object" in new AccountsJsonData {
        val decoded = decode[Account](accountScriptedJson)
        decoded shouldBe 'right

        val account = decoded.right.value
        account.script.value shouldEqual expectedScript
      }

      import TezosJsonDecoders.Circe.Delegates._
      "decode a delegate baker" in new DelegatesJsonData {
        val decoded = decode[Delegate](delegateJson)
        decoded shouldBe 'right

        val delegate = decoded.right.value
        delegate shouldEqual expectedDelegate
      }

      "decode delegate contracts" in new DelegatesJsonData {
        val decoded = decode[Contract](contractJson)
        decoded shouldBe 'right

        val contract = decoded.right.value
        contract shouldEqual expectedContract
      }
    }

}
