package tech.cryptonomic.conseil.util

import org.scalatest.{WordSpec, Matchers, EitherValues}
import tech.cryptonomic.conseil.tezos.TezosTypes._

class JsonDecodersTest extends WordSpec with Matchers with EitherValues {

  import JsonDecoders.Circe._
  import JsonDecoders.Circe.Operations._
  import io.circe.parser.decode

  "the json decoders" should {

    val validB58Hash = "signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSf"
    val invalidB58Hash = "signiRfcqmbGc6UtW1WzuJNGzRRsWDLpafxZZPwwTMntFwup8rTxXEgcLD5UBWkYmMqZECVEr33Xw5sh9NVi45c4FVAXvQSl"
    val alphanumneric = "asdopkjfap2398ufa3908wimv3pw98vja3pw98v"
    val invalidAlphanumeric = "@*;akjfa80330"
    val invalidJson = """{wrongname: "name"}"""

    /** wrap in quotes to be a valid json string */
    val jsonStringOf = (content: String) => s""""$content""""

    "fail to decode json with duplicate fields" in {
      import io.circe.Decoder
      import io.circe.generic.extras._
      import io.circe.generic.extras.semiauto._

      case class JsonTest(field: String)

      implicit val testDecoder: Decoder[JsonTest] = deriveDecoder

      val duplicateDecoded = decode[JsonTest]("""{"field": "test", "field": "duplicate"}""")
      duplicateDecoded shouldBe 'right

      val duplicateUndecoded = decode[JsonTest]("""{"field": "test", "inner": {"key": "one", "key": "duplicate"}}""")
      duplicateUndecoded shouldBe 'right

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
      val decoded = decode[BlockHash](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe BlockHash(validB58Hash)
    }

    "fail to decode an invalid json base58check strings into a BlockHash" in {
      val decoded = decode[BlockHash](jsonStringOf(invalidB58Hash))
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

    "decode valid json base58check strings into a AccountId" in {
      val decoded = decode[AccountId](jsonStringOf(validB58Hash))
      decoded.right.value shouldBe AccountId(validB58Hash)
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

    "decode valid json alphanumneric strings into a Secret" in {
      val decoded = decode[Secret](jsonStringOf(alphanumneric))
      decoded.right.value shouldBe Secret(alphanumneric)
    }

    "fail to decode an invalid json alphanumneric strings into a Secret" in {
      val decoded = decode[Secret](jsonStringOf(invalidAlphanumeric))
      decoded shouldBe 'left
    }

    "decode valid json into MichelsonV1 values" in new OperationsJsonData {
      val decoded = decode[MichelsonV1](michelsonJson)
      decoded.right.value shouldEqual expectedMichelson
    }

    "fail to decode invalid json into MichelsonV1 values" in new OperationsJsonData {
      val decoded = decode[MichelsonV1](invalidJson)
      decoded shouldBe 'left
    }

    "decode valid json strings representing a PositiveBigNumber" in {
      val decoded = decode[TezosOperations.PositiveBigNumber](jsonStringOf("1000000000"))
      decoded.right.value shouldEqual TezosOperations.PositiveDecimal(1000000000)
    }

    "decode valid json strings representing zero as a PositiveBigNumber" in {
      val decoded = decode[TezosOperations.PositiveBigNumber](jsonStringOf("0"))
      decoded.right.value shouldEqual TezosOperations.PositiveDecimal(0)
    }

    "decode invalid json for PositiveBigNumber, representing negatives, as the original string" in {
      val decoded = decode[TezosOperations.PositiveBigNumber](jsonStringOf("-1000000000"))
      decoded.right.value shouldBe TezosOperations.InvalidPositiveDecimal("-1000000000")
    }

    "decode invalid json for PositiveBigNumber, not representing numbers, as the original string" in {
      val decoded = decode[TezosOperations.PositiveBigNumber](jsonStringOf("1AA000000000"))
      decoded.right.value shouldBe TezosOperations.InvalidPositiveDecimal("1AA000000000")
    }

    "decode valid json strings representing both positive and negative values as BigNumber" in {
      val decoded = decode[TezosOperations.BigNumber](jsonStringOf("1000000000"))
      decoded.right.value shouldEqual TezosOperations.Decimal(1000000000)

      val negDecoded = decode[TezosOperations.BigNumber](jsonStringOf("-1000000000"))
      negDecoded.right.value shouldBe TezosOperations.Decimal(-1000000000)
    }

    "decode invalid json for BigNumber, not representing numbers, as the original string" in {
      val decoded = decode[TezosOperations.BigNumber](jsonStringOf("1AA000000000"))
      decoded.right.value shouldBe TezosOperations.InvalidDecimal("1AA000000000")
    }

    "decode valid json into a BigMapDiff value" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Contract.BigMapDiff](bigmapdiffJson)
      decoded.right.value shouldEqual expectedBigMapDiff
    }

    "decode valid json into a Scipted.Cntracts value" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Scripted.Contracts](scriptJson)
      decoded.right.value shouldEqual expectedScript
    }

    "decode valid json into Error values" in new OperationsJsonData {
      val decoded = decode[TezosOperations.OperationResult.Error](errorJson)
      decoded.right.value shouldEqual expectedError
    }

    "fail to decode invalid json into Error values" in new OperationsJsonData {
      val decoded = decode[TezosOperations.OperationResult.Error](invalidJson)
      decoded shouldBe 'left
    }

    "decode an endorsement operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](endorsementJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.Endorsement]
      operation shouldEqual expectedEndorsement

    }

    "decode a seed nonce revelation operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](nonceRevelationJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.SeedNonceRevelation]
      operation shouldEqual expectedNonceRevelation

    }

    "decode an account activation operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](activationJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.ActivateAccount]
      operation shouldEqual expectedActivation

    }

    "decode an reveal operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](revealJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.Reveal]
      operation shouldEqual expectedReveal

    }

    "decode an transaction operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](transactionJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.Transaction]
      operation shouldEqual expectedTransaction

    }

    "decode an origination operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](originationJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.Origination]
      operation shouldEqual expectedOrigination

    }

    "decode a delegation operation from json" in new OperationsJsonData {
      val decoded = decode[TezosOperations.Operation](delegationJson)
      decoded shouldBe 'right

      val operation = decoded.right.value
      operation shouldBe a [TezosOperations.Delegation]
      operation shouldEqual expectedDelegation

    }

    "decode a group of operations from json" in new OperationsJsonData {

      val decoded = decode[TezosOperations.Group](operationsGroupJson)
      decoded shouldBe 'right

      val operations = decoded.right.value
      operations shouldBe a [TezosOperations.Group]
      operations shouldEqual expectedGroup

    }

  }

}