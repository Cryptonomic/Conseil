package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import tech.cryptonomic.conseil.tezos.TezosRemoteInstances.Akka.TezosNodeContext
import tech.cryptonomic.conseil.util.{CryptoUtil, JsonUtil}
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.JsonUtil.{fromJson, JsonString => JS}
import tech.cryptonomic.conseil.config.{BatchFetchConfiguration, SodiumConfiguration}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.TezosTypes.Lenses._
import tech.cryptonomic.conseil.tezos.michelson.JsonToMichelson.convert
import tech.cryptonomic.conseil.tezos.michelson.dto.{MichelsonElement, MichelsonInstruction, MichelsonSchema}
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser.Parser
import cats.instances.future._
import cats.syntax.applicative._

import scala.concurrent.{ExecutionContext, Future}
import scala.math.max
import scala.reflect.ClassTag
import scala.util.{Failure, Success, Try}

/** TODO refactor to new type classes
  * Adds more specific API functionalities to perform on a tezos node, in particular those involving write and cryptographic operations
  */
class TezosNodeSenderOperator(network: String, batchConf: BatchFetchConfiguration, sodiumConf: SodiumConfiguration)(implicit val tezosContext: TezosNodeContext, system: ActorSystem, executionContext: ExecutionContext)
  // extends TezosNodeOperator(network, batchConf)
  // with LazyLogging {
  // import com.muquit.libsodiumjna.{SodiumKeyPair, SodiumLibrary, SodiumUtils}
  // import TezosNodeOperator._

  // /** Type representing Map[String, Any] */
  // type AnyMap = Map[String, Any]

  // //used in subsequent operations using Sodium
  // SodiumLibrary.setLibraryPath(sodiumConf.libraryPath)

  // /**
  //   * Appends a key reveal operation to an operation group if needed.
  //   * @param operations The operations being forged as part of this operation group
  //   * @param managerKey The sending account's manager information
  //   * @param keyStore   Key pair along with public key hash
  //   * @return           Operation group enriched with a key reveal if necessary
  //   */
  // def handleKeyRevealForOperations(
  //   operations: List[AnyMap],
  //   managerKey: ManagerKey,
  //   keyStore: KeyStore): List[AnyMap] =
  //   managerKey.key match {
  //     case Some(_) => operations
  //     case None =>
  //       val revealMap: AnyMap = Map(
  //         "kind"        -> "reveal",
  //         "public_key"  -> keyStore.publicKey
  //       )
  //       revealMap :: operations
  //   }

  // /**
  //   * Forge an operation group using the Tezos RPC client.
  //   * @param blockHead  The block head
  //   * @param account    The sender's account
  //   * @param operations The operations being forged as part of this operation group
  //   * @param keyStore   Key pair along with public key hash
  //   * @param fee        Fee to be paid
  //   * @return           Forged operation bytes (as a hex string)
  //   */
  // def forgeOperations(
  //   blockHead: Block,
  //   account: Account,
  //   operations: List[AnyMap],
  //   keyStore: KeyStore,
  //   fee: Option[Float]): Future[String] = {
  //   val payload: AnyMap = fee match {
  //     case Some(feeAmt) =>
  //       Map(
  //         "branch" -> blockHead.data.hash,
  //         "source" -> keyStore.publicKeyHash,
  //         "operations" -> operations,
  //         "counter" -> (account.counter + 1),
  //         "fee" -> feeAmt,
  //         "kind" -> "manager",
  //         "gas_limit" -> "120",
  //         "storage_limit" -> 0
  //       )
  //     case None =>
  //       Map(
  //         "branch" -> blockHead.data.header.predecessor,
  //         "operations" -> operations
  //       )
  //   }

  //   RemoteRpc.runPost("/blocks/head/proto/helpers/forge/operations", Some(JsonUtil.toJson(payload)))
  //     .map(json => fromJson[ForgedOperation](json).operation)
  // }

  // /**
  //   * Signs a forged operation
  //   * @param forgedOperation  Forged operation group returned by the Tezos client (as a hex string)
  //   * @param keyStore         Key pair along with public key hash
  //   * @return                 Bytes of the signed operation along with the actual signature
  //   */
  // def signOperationGroup(forgedOperation: String, keyStore: KeyStore): Try[SignedOperationGroup] =
  //   for {
  //     privateKeyBytes <- CryptoUtil.base58CheckDecode(keyStore.privateKey, "edsk")
  //     watermark = "03"  // In the future, we must support "0x02" for endorsements and "0x01" for block signing.
  //     watermarkedForgedOperationBytes = SodiumUtils.hex2Binary(watermark + forgedOperation)
  //     hashedWatermarkedOpBytes = SodiumLibrary.cryptoGenerichash(watermarkedForgedOperationBytes, 32)
  //     opSignature: Array[Byte] = SodiumLibrary.cryptoSignDetached(hashedWatermarkedOpBytes, privateKeyBytes.toArray)
  //     hexSignature <- CryptoUtil.base58CheckEncode(opSignature.toList, "edsig")
  //     signedOpBytes = SodiumUtils.hex2Binary(forgedOperation) ++ opSignature
  // } yield SignedOperationGroup(signedOpBytes, hexSignature)

  // /**
  //   * Computes the ID of an operation group using Base58Check.
  //   * @param signedOpGroup  Signed operation group
  //   * @return               Base58Check hash of signed operation
  //   */
  // def computeOperationHash(signedOpGroup: SignedOperationGroup): Try[String] =
  //   Try(SodiumLibrary.cryptoGenerichash(signedOpGroup.bytes, 32))
  //     .flatMap { hash =>
  //       CryptoUtil.base58CheckEncode(hash.toList, "op")
  //     }

  // /**
  //   * Applies an operation using the Tezos RPC client.
  //   * @param blockHead            Block head
  //   * @param operationGroupHash   Hash of the operation group being applied (in Base58Check format)
  //   * @param forgedOperationGroup Forged operation group returned by the Tezos client (as a hex string)
  //   * @param signedOpGroup        Signed operation group
  //   * @return                     Array of contract handles
  //   */
  // def applyOperation(
  //   blockHead: Block,
  //   operationGroupHash: String,
  //   forgedOperationGroup: String,
  //   signedOpGroup: SignedOperationGroup): Future[AppliedOperation] = {
  //   val payload: AnyMap = Map(
  //     "pred_block" -> blockHead.data.header.predecessor,
  //     "operation_hash" -> operationGroupHash,
  //     "forged_operation" -> forgedOperationGroup,
  //     "signature" -> signedOpGroup.signature
  //   )

  //   RemoteRpc.runPost("/blocks/head/proto/helpers/apply_operation", Some(JsonUtil.toJson(payload)))
  //     .map { result =>
  //       logger.debug(s"Result of operation application: $result")
  //       JsonUtil.fromJson[AppliedOperation](result)
  //     }
  // }

  // /**
  //   * Injects an opertion using the Tezos RPC client.
  //   * @param signedOpGroup  Signed operation group
  //   * @return               ID of injected operation
  //   */
  // def injectOperation(signedOpGroup: SignedOperationGroup): Future[String] = {
  //   val payload: AnyMap = Map(
  //     "signedOperationContents" -> signedOpGroup.bytes.map("%02X" format _).mkString
  //   )

  //   RemoteRpc.runPost("/inject_operation", Some(JsonUtil.toJson(payload)))
  //     .map(result => fromJson[InjectedOperation](result).injectedOperation)
  // }

  // /**
  //   * Master function for creating and sending all supported types of operations.
  //   * @param operations The operations to create and send
  //   * @param keyStore   Key pair along with public key hash
  //   * @param fee        The fee to use
  //   * @return           The ID of the created operation group
  //   */
  // def sendOperation(operations: List[Map[String,Any]], keyStore: KeyStore, fee: Option[Float]): Future[OperationResult] = for {
  //   blockHead <- getBlockHead()
  //   accountId = AccountId(keyStore.publicKeyHash)
  //   account <- getAccountForBlock(blockHeadHash, accountId)
  //   accountManager <- getAccountManagerForBlock(blockHeadHash, accountId)
  //   operationsWithKeyReveal = handleKeyRevealForOperations(operations, accountManager, keyStore)
  //   forgedOperationGroup <- forgeOperations(blockHead, account, operationsWithKeyReveal, keyStore, fee)
  //   signedOpGroup <- Future.fromTry(signOperationGroup(forgedOperationGroup, keyStore))
  //   operationGroupHash <- Future.fromTry(computeOperationHash(signedOpGroup))
  //   appliedOp <- applyOperation(blockHead, operationGroupHash, forgedOperationGroup, signedOpGroup)
  //   operation <- injectOperation(signedOpGroup)
  // } yield OperationResult(appliedOp, operation)

  // /**
  //   * Creates and sends a transaction operation.
  //   * @param keyStore   Key pair along with public key hash
  //   * @param to         Destination public key hash
  //   * @param amount     Amount to send
  //   * @param fee        Fee to use
  //   * @return           The ID of the created operation group
  //   */
  // def sendTransactionOperation(
  //   keyStore: KeyStore,
  //   to: String,
  //   amount: Float,
  //   fee: Float
  // ): Future[OperationResult] = {
  //   val transactionMap: Map[String,Any] = Map(
  //     "kind"        -> "transaction",
  //     "amount"      -> amount,
  //     "destination" -> to,
  //     "parameters"  -> MichelsonExpression("Unit", List[String]())
  //   )
  //   val operations = transactionMap :: Nil
  //   sendOperation(operations, keyStore, Some(fee))
  // }

  // /**
  //   * Creates and sends a delegation operation.
  //   * @param keyStore Key pair along with public key hash
  //   * @param delegate Account ID to delegate to
  //   * @param fee      Operation fee
  //   * @return
  //   */
  // def sendDelegationOperation(
  //   keyStore: KeyStore,
  //   delegate: String,
  //   fee: Float): Future[OperationResult] = {
  //   val transactionMap: Map[String,Any] = Map(
  //     "kind"        -> "delegation",
  //     "delegate"    -> delegate
  //   )
  //   val operations = transactionMap :: Nil
  //   sendOperation(operations, keyStore, Some(fee))
  // }

  // /**
  //   * Creates and sends an origination operation.
  //   * @param keyStore     Key pair along with public key hash
  //   * @param amount       Initial funding amount of new account
  //   * @param delegate     Account ID to delegate to, blank if none
  //   * @param spendable    Is account spendable?
  //   * @param delegatable  Is account delegatable?
  //   * @param fee          Operation fee
  //   * @return
  //   */
  // def sendOriginationOperation(
  //   keyStore: KeyStore,
  //   amount: Float,
  //   delegate: String,
  //   spendable: Boolean,
  //   delegatable: Boolean,
  //   fee: Float): Future[OperationResult] = {
  //   val transactionMap: Map[String,Any] = Map(
  //     "kind"          -> "origination",
  //     "balance"       -> amount,
  //     "managerPubkey" -> keyStore.publicKeyHash,
  //     "spendable"     -> spendable,
  //     "delegatable"   -> delegatable,
  //     "delegate"      -> delegate
  //   )
  //   val operations = transactionMap :: Nil
  //   sendOperation(operations, keyStore, Some(fee))
  // }

  // /**
  //   * Creates a new Tezos identity.
  //   * @return A new key pair along with a public key hash
  //   */
  // def createIdentity(): Try[KeyStore] = {
  //   //The Java bindings for libSodium don't support generating a key pair from a seed.
  //   //We will revisit this later in order to support mnemomics and passphrases
  //   //val mnemonic = bip39.generate(Entropy128, WordList.load(EnglishWordList).get, new SecureRandom())
  //   //val seed = bip39.toSeed(mnemonic, Some(passphrase))

  //   val keyPair: SodiumKeyPair = SodiumLibrary.cryptoSignKeyPair()
  //   val rawPublicKeyHash = SodiumLibrary.cryptoGenerichash(keyPair.getPublicKey, 20)
  //   for {
  //     privateKey <- CryptoUtil.base58CheckEncode(keyPair.getPrivateKey, "edsk")
  //     publicKey <- CryptoUtil.base58CheckEncode(keyPair.getPublicKey, "edpk")
  //     publicKeyHash <- CryptoUtil.base58CheckEncode(rawPublicKeyHash, "tz1")
  //   } yield KeyStore(privateKey = privateKey, publicKey = publicKey, publicKeyHash = publicKeyHash)
  // }
//
// }