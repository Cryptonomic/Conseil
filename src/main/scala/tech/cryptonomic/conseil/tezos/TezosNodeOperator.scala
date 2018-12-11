package tech.cryptonomic.conseil.tezos

import com.muquit.libsodiumjna.{SodiumKeyPair, SodiumLibrary, SodiumUtils}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.util.{CryptoUtil, JsonUtil}
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.JsonUtil.fromJson

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

object TezosNodeOperator {
  /**
    * Output of operation signing.
    * @param bytes      Signed bytes of the transaction
    * @param signature  The actual signature
    */
  final case class SignedOperationGroup(bytes: Array[Byte], signature: String)

  /**
    * Result of a successfully sent operation
    * @param results          Results of operation application
    * @param operationGroupID Operation group ID
    */
  final case class OperationResult(results: AppliedOperation, operationGroupID: String)
}

/**
  * Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database.
  */
class TezosNodeOperator(val node: TezosRPCInterface)(implicit executionContext: ExecutionContext) extends LazyLogging {
  import TezosNodeOperator._

  private val conf = ConfigFactory.load

  val sodiumLibraryPath: String = conf.getString("sodium.libraryPath")
  val accountsFetchConcurrency: Int = conf.getInt("batchedFetches.accountConcurrencyLevel")
  val blockOperationsFetchConcurrency: Int = conf.getInt("batchedFetches.blockOperationsConcurrencyLevel")

  SodiumLibrary.setLibraryPath(sodiumLibraryPath)

  /**
    * Fetches a specific account for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @param accountID  Account ID
    * @return           The account
    */
  def getAccountForBlock(network: String, blockHash: BlockHash, accountID: AccountId): Future[Account] =
    node.runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts/${accountID.id}")
      .map(fromJson[Account])

  /**
    * Fetches the manager of a specific account for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @param accountID  Account ID
    * @return           The account
    */
  def getAccountManagerForBlock(network: String, blockHash: BlockHash, accountID: AccountId): Future[ManagerKey] =
    node.runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts/${accountID.id}/manager_key")
      .map(fromJson[ManagerKey])

  /**
    * Fetches the accounts identified by id
    *
    * @param network    Which Tezos network to go against
    * @param blockHash  the block storing the accounts
    * @param accountIDs the ids
    * @return           the list of accounts wrapped in a [[Future]]
    */
  def getAllAccountsForBlock(network: String, blockHash: BlockHash, accountIDs: List[AccountId]): Future[Map[AccountId, Account]] =
    node
    .runBatchedGetQuery(network, accountIDs, (id: AccountId) => s"blocks/${blockHash.value}/context/contracts/${id.id}", accountsFetchConcurrency)
    .map(
      responseList =>
        responseList.collect {
          case (id, json) =>
            val accountTry = Try(fromJson[Account](json)).map((id, _))
            accountTry.failed.foreach(_ => logger.error("Failed to convert json to an Account for id {}. The content was {}.", id, json))
            accountTry.toOption
        }.flatten.toMap
    )

  /**
    * Fetches the accounts identified by id, considering them available looking at the blocks head
    *
    * @param network    Which Tezos network to go against
    * @param accountIDs the ids
    * @return           the list of accounts wrapped in a [[Future]]
    */
  def getAccounts(network: String, accountIDs: List[AccountId]): Future[Map[AccountId, Account]] =
    getAllAccountsForBlock(network, blockHeadHash, accountIDs)

    /**
    * Get accounts for all the identifiers passed-in with the corresponding block
    * @param network  Which Tezos network to go against
    * @return         all Accounts with their corresponding block hash
    */
  def getAccountsForBlocks(network: String, accountsIds: Map[Block, List[AccountId]]): Future[List[BlockAccounts]] = {
    /* making separate calls for blocks would not scale, as the same pool would be reused for thousands of batches, possibly
     * related to the same accounts involved in multiple operations
     * so we better group the ids and then recover the latest blocks involved for each
     */
    val accountBlockAssociation: Map[AccountId, Block] = {
      val distinctIds = accountsIds.values.flatten.toSet
      distinctIds.map {
        id =>
          accountsIds.toArray.collect {
            case (block, ids) if ids.contains(id) => (id, block)
          }.sortBy {
            case (_ , block) => block.metadata.header.level
          }.last
      }.toMap
    }

    //read the accounts by ids and group then again with separate blocks to get the final result
    val accountsInfos: Future[List[BlockAccounts]] =
      getAccounts(network, accountBlockAssociation.keys.toList).map {
        accountsMap =>
          val missing = (accountBlockAssociation.keySet -- accountsMap.keySet).map(_.id)
          if (missing.nonEmpty) logger.warn("The following account keys were not found querying the {} node: {}", network, missing.mkString("\n", ",", "\n"))
          accountsMap.groupBy {
            case (id, _) => (accountBlockAssociation(id).metadata.hash, accountBlockAssociation(id).metadata.header.level)
          }.map {
            case ((bh, bl), accounts) => BlockAccounts(bh, bl, accounts)
          }.toList
      }

    accountsInfos.failed.foreach {
      e =>
        val showIds = accountBlockAssociation.keys.take(30).mkString("", ",", if (accountBlockAssociation.size > 30) "..." else "")
        logger.error(s"Could not get accounts' data for ids ${showIds}", e)
    }
    accountsInfos
  }


  /**
    * Fetches operations for a block, without waiting for the result
    * @param network   Which Tezos network to go against
    * @param blockHash Hash of the block
    * @return          The [[Future]] list of operations
    */
  def getAllOperationsForBlock(network: String, blockHash: BlockHash): Future[List[OperationGroup]] =
    node.runAsyncGetQuery(network, s"blocks/${blockHash.value}/operations")
      .map(ll => fromJson[List[List[OperationGroup]]](ll).flatten)

  /**
    * Fetches a single block from the chain, without waiting for the result
    * @param network   Which Tezos network to go against
    * @param hash      Hash of the block
    * @return          the block data wrapped in a [[Future]]
    */
  def getBlock(network: String, hash: BlockHash, offset: Option[Int] = None): Future[Block] = {
    val offsetString = offset.map(_.toString).getOrElse("")
    for {
      block <- node.runAsyncGetQuery(network, s"blocks/${hash.value}~$offsetString").map(fromJson[BlockMetadata])
      ops <-
        if (block.header.level == 0)
          Future.successful(List.empty[OperationGroup]) //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
        else
          getAllOperationsForBlock(network, hash)
    } yield Block(block, ops)
  }

  /**
    * Gets the block head.
    * @param network  Which Tezos network to go against
    * @return         Block head
    */
  def getBlockHead(network: String): Future[Block]= {
    getBlock(network, blockHeadHash)
  }

  /**
    * Gets all blocks from the head down to the oldest block not already in the database.
    * @param network    Which Tezos network to go against
    * @param followFork If the predecessor of the minLevel block appears to be on a fork, also capture the blocks on the fork.
    * @return           Blocks and Account hashes involved
    */
  def getBlocksNotInDatabase(network: String, followFork: Boolean): Future[List[(Block, List[AccountId])]] =
    for {
      maxLevel <- ApiOperations.fetchMaxLevel
      blockHead <- {
        getBlockHead(network)
      }
      headLevel = blockHead.metadata.header.level
      headHash = blockHead.metadata.hash
      blocks <-
        if (headLevel <= maxLevel) {
          logger.info("No new blocks to fetch from the network")
          Future.successful(List.empty)
        } else {
          if (maxLevel == -1) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
          else logger.info("Found new block head at level {}, currently stored max is {}. Fetching missing blocks", headLevel, maxLevel)
          getBlocks(network, headHash, maxLevel + 1, headLevel, followFork)
        }
    } yield blocks

  /**
    * Get the blocks in a specified range.
    * @param network Which tezos network to go against
    * @param minLevel Minimum block level
    * @param maxLevel Maximum block level
    * @param startBlockHash If specified, start from the supplied block hash, otherwise, head of the chain.
    * @param followFork If predecessor of the minLevel block appears to be on a fork, also capture the blocks on the fork.
    * @return Blocks
    */
  def getBlocks(network: String,
                startBlockHash: BlockHash = blockHeadHash,
                minLevel: Int,
                maxLevel: Int,
                followFork: Boolean): Future[List[(Block, List[AccountId])]] = {
    val blocksInRange = processBlocks(network, startBlockHash, minLevel, maxLevel)
    val blocksFromFork =
      if (followFork) Future.successful(List.empty)
      else Future.successful(List.empty)
    for {
      forkBlocks <- blocksFromFork
      rangeBlocks <- blocksInRange
    } yield forkBlocks ++ rangeBlocks
  }

  /**
    * Gets block from Tezos Blockchains, as well as their associated operation, from minLevel to maxLevel.
    * @param network Which Tezos network to go against
    * @param hashRef Hash of block at max level.
    * @param minLevel Minimum level, at which we stop.
    * @param maxLevel Level at which to stop collecting blocks.
    * @return the async list of blocks with relative account ids touched in the operations
    */
  private def processBlocks(
                             network : String,
                             hashRef: BlockHash,
                             minLevel: Int,
                             maxLevel: Int
                           ): Future[List[(Block, List[AccountId])]] = {

    val maxOffset: Int = maxLevel - minLevel
    val offsets = (0 to maxOffset).toList
    val makeBlocksUrl = (offset: Int) => s"blocks/${hashRef.value}~${String.valueOf(offset)}"
    val makeOperationsUrl = (hash: BlockHash) => s"blocks/${hash.value}/operations"

    val jsonToBlockMetadata: ((Int, String)) => BlockMetadata = {
      case (_, json) => fromJson[BlockMetadata](json)
    }

    val jsonToOperationGroups: String => List[OperationGroup] =
      json => fromJson[List[List[OperationGroup]]](json).flatten

    //extracts any formally valid account hash from the passed-in string
    val jsonToAccountInvolved: String => List[AccountId] = {
      case JsonUtil.AccountIds(id, ids @ _*) =>
        (id :: ids.toList).distinct.map(AccountId)
      case _ => List.empty
    }

    //from the same json, converts to a list of operations and the involved account ids
    val jsonToOperationsAndAccounts: ((BlockHash, String)) => (BlockHash, List[OperationGroup], List[AccountId]) = {
      case (hash, json) =>
        (hash, jsonToOperationGroups(json), jsonToAccountInvolved(json))
    }

    for {
      fetchedBlocksMetadata <- node.runBatchedGetQuery(network, offsets, makeBlocksUrl, blockOperationsFetchConcurrency) map (blocksMetadata => blocksMetadata.map(jsonToBlockMetadata))
      blockHashes = fetchedBlocksMetadata.map(_.hash)
      fetchedOperationsWithAccounts <- node.runBatchedGetQuery(network, blockHashes, makeOperationsUrl, blockOperationsFetchConcurrency).map(operations => operations.map(jsonToOperationsAndAccounts))
    } yield {
      val operationalDataMap = fetchedOperationsWithAccounts.map{ case (hash, ops, accounts) => (hash, (ops, accounts))}.toMap
      fetchedBlocksMetadata.map {
        md =>
          val (ops, accs) = operationalDataMap(md.hash)
          (Block(md, ops), accs)
      }
    }
  }

  /**
    * Appends a key reveal operation to an operation group if needed.
    * @param operations The operations being forged as part of this operation group
    * @param managerKey The sending account's manager information
    * @param keyStore   Key pair along with public key hash
    * @return           Operation group enriched with a key reveal if necessary
    */
  def handleKeyRevealForOperations(
                                    operations: List[Map[String, Any]],
                                    managerKey: ManagerKey,
                                    keyStore: KeyStore)
  : List[Map[String, Any]] =
    managerKey.key match {
      case Some(_) => operations
      case None =>
        val revealMap: Map[String, Any] = Map(
          "kind"        -> "reveal",
          "public_key"  -> keyStore.publicKey
        )
        revealMap :: operations
    }

  /**
    * Forge an operation group using the Tezos RPC client.
    * @param network    Which Tezos network to go against
    * @param blockHead  The block head
    * @param account    The sender's account
    * @param operations The operations being forged as part of this operation group
    * @param keyStore   Key pair along with public key hash
    * @param fee        Fee to be paid
    * @return           Forged operation bytes (as a hex string)
    */
  def forgeOperations(  network: String,
                        blockHead: Block,
                        account: Account,
                        operations: List[Map[String,Any]],
                        keyStore: KeyStore,
                        fee: Option[Float]
                     ): Future[String] = {
    val payload: Map[String, Any] = fee match {
      case Some(feeAmt) =>
        Map(
          "branch" -> blockHead.metadata.hash,
          "source" -> keyStore.publicKeyHash,
          "operations" -> operations,
          "counter" -> (account.counter + 1),
          "fee" -> feeAmt,
          "kind" -> "manager",
          "gas_limit" -> "120",
          "storage_limit" -> 0
        )
      case None =>
        Map(
          "branch" -> blockHead.metadata.header.predecessor,
          "operations" -> operations
        )
    }
    node.runAsyncPostQuery(network, "/blocks/head/proto/helpers/forge/operations", Some(JsonUtil.toJson(payload)))
      .map(json => fromJson[ForgedOperation](json).operation)
  }

  /**
    * Signs a forged operation
    * @param forgedOperation  Forged operation group returned by the Tezos client (as a hex string)
    * @param keyStore         Key pair along with public key hash
    * @return                 Bytes of the signed operation along with the actual signature
    */
  def signOperationGroup(forgedOperation: String, keyStore: KeyStore): Try[SignedOperationGroup] =
    for {
      privateKeyBytes <- CryptoUtil.base58CheckDecode(keyStore.privateKey, "edsk")
      watermark = "03"  // In the future, we must support "0x02" for endorsements and "0x01" for block signing.
      watermarkedForgedOperationBytes = SodiumUtils.hex2Binary(watermark + forgedOperation)
      hashedWatermarkedOpBytes = SodiumLibrary.cryptoGenerichash(watermarkedForgedOperationBytes, 32)
      opSignature: Array[Byte] = SodiumLibrary.cryptoSignDetached(hashedWatermarkedOpBytes, privateKeyBytes.toArray)
      hexSignature <- CryptoUtil.base58CheckEncode(opSignature.toList, "edsig")
      signedOpBytes = SodiumUtils.hex2Binary(forgedOperation) ++ opSignature
  } yield SignedOperationGroup(signedOpBytes, hexSignature)

  /**
    * Computes the ID of an operation group using Base58Check.
    * @param signedOpGroup  Signed operation group
    * @return               Base58Check hash of signed operation
    */
  def computeOperationHash(signedOpGroup: SignedOperationGroup): Try[String] =
    Try(SodiumLibrary.cryptoGenerichash(signedOpGroup.bytes, 32))
      .flatMap { hash =>
        CryptoUtil.base58CheckEncode(hash.toList, "op")
      }

  /**
    * Applies an operation using the Tezos RPC client.
    * @param network              Which Tezos network to go against
    * @param blockHead            Block head
    * @param operationGroupHash   Hash of the operation group being applied (in Base58Check format)
    * @param forgedOperationGroup Forged operation group returned by the Tezos client (as a hex string)
    * @param signedOpGroup        Signed operation group
    * @return                     Array of contract handles
    */
  def applyOperation(
                      network: String,
                      blockHead: Block,
                      operationGroupHash: String,
                      forgedOperationGroup: String,
                      signedOpGroup: SignedOperationGroup): Future[AppliedOperation] = {
    val payload: Map[String, Any] = Map(
      "pred_block" -> blockHead.metadata.header.predecessor,
      "operation_hash" -> operationGroupHash,
      "forged_operation" -> forgedOperationGroup,
      "signature" -> signedOpGroup.signature
    )
    node.runAsyncPostQuery(network, "/blocks/head/proto/helpers/apply_operation", Some(JsonUtil.toJson(payload)))
      .map { result =>
        logger.debug(s"Result of operation application: $result")
        JsonUtil.fromJson[AppliedOperation](result)
      }
  }

  /**
    * Injects an opertion using the Tezos RPC client.
    * @param network        Which Tezos network to go against
    * @param signedOpGroup  Signed operation group
    * @return               ID of injected operation
    */
  def injectOperation(network: String, signedOpGroup: SignedOperationGroup): Future[String] = {
    val payload: Map[String, Any] = Map(
      "signedOperationContents" -> signedOpGroup.bytes.map("%02X" format _).mkString
    )
    node.runAsyncPostQuery(network, "/inject_operation", Some(JsonUtil.toJson(payload)))
      .map(result => fromJson[InjectedOperation](result).injectedOperation)
  }

  /**
    * Master function for creating and sending all supported types of operations.
    * @param network    Which Tezos network to go against
    * @param operations The operations to create and send
    * @param keyStore   Key pair along with public key hash
    * @param fee        The fee to use
    * @return           The ID of the created operation group
    */
  def sendOperation(network: String, operations: List[Map[String,Any]], keyStore: KeyStore, fee: Option[Float]): Future[OperationResult] = for {
    blockHead <- getBlockHead(network)
    accountId = AccountId(keyStore.publicKeyHash)
    account <- getAccountForBlock(network, blockHeadHash, accountId)
    accountManager <- getAccountManagerForBlock(network, blockHeadHash, accountId)
    operationsWithKeyReveal = handleKeyRevealForOperations(operations, accountManager, keyStore)
    forgedOperationGroup <- forgeOperations(network, blockHead, account, operationsWithKeyReveal, keyStore, fee)
    signedOpGroup <- Future.fromTry(signOperationGroup(forgedOperationGroup, keyStore))
    operationGroupHash <- Future.fromTry(computeOperationHash(signedOpGroup))
    appliedOp <- applyOperation(network, blockHead, operationGroupHash, forgedOperationGroup, signedOpGroup)
    operation <- injectOperation(network, signedOpGroup)
  } yield OperationResult(appliedOp, operation)

  /**
    * Creates and sends a transaction operation.
    * @param network    Which Tezos network to go against
    * @param keyStore   Key pair along with public key hash
    * @param to         Destination public key hash
    * @param amount     Amount to send
    * @param fee        Fee to use
    * @return           The ID of the created operation group
    */
  def sendTransactionOperation(
                                network: String,
                                keyStore: KeyStore,
                                to: String,
                                amount: Float,
                                fee: Float
                              ): Future[OperationResult] = {
    val transactionMap: Map[String,Any] = Map(
      "kind"        -> "transaction",
      "amount"      -> amount,
      "destination" -> to,
      "parameters"  -> MichelsonExpression("Unit", List[String]())
    )
    val operations = transactionMap :: Nil
    sendOperation(network, operations, keyStore, Some(fee))
  }

  /**
    * Creates and sends a delegation operation.
    * @param network  Which Tezos network to go against
    * @param keyStore Key pair along with public key hash
    * @param delegate Account ID to delegate to
    * @param fee      Operation fee
    * @return
    */
  def sendDelegationOperation(
                               network: String,
                               keyStore: KeyStore,
                               delegate: String,
                               fee: Float
                             ): Future[OperationResult] = {
    val transactionMap: Map[String,Any] = Map(
      "kind"        -> "delegation",
      "delegate"    -> delegate
    )
    val operations = transactionMap :: Nil
    sendOperation(network, operations, keyStore, Some(fee))
  }

  /**
    * Creates and sends an origination operation.
    * @param network      Which Tezos network to go against
    * @param keyStore     Key pair along with public key hash
    * @param amount       Initial funding amount of new account
    * @param delegate     Account ID to delegate to, blank if none
    * @param spendable    Is account spendable?
    * @param delegatable  Is account delegatable?
    * @param fee          Operation fee
    * @return
    */
  def sendOriginationOperation(
                                network: String,
                                keyStore: KeyStore,
                                amount: Float,
                                delegate: String,
                                spendable: Boolean,
                                delegatable: Boolean,
                                fee: Float
                              ): Future[OperationResult] = {
    val transactionMap: Map[String,Any] = Map(
      "kind"          -> "origination",
      "balance"       -> amount,
      "managerPubkey" -> keyStore.publicKeyHash,
      "spendable"     -> spendable,
      "delegatable"   -> delegatable,
      "delegate"      -> delegate
    )
    val operations = transactionMap :: Nil
    sendOperation(network, operations, keyStore, Some(fee))
  }

  /**
    * Creates a new Tezos identity.
    * @return A new key pair along with a public key hash
    */
  def createIdentity(): Try[KeyStore] = {
    //The Java bindings for libSodium don't support generating a key pair from a seed.
    //We will revisit this later in order to support mnemomics and passphrases
    //val mnemonic = bip39.generate(Entropy128, WordList.load(EnglishWordList).get, new SecureRandom())
    //val seed = bip39.toSeed(mnemonic, Some(passphrase))

    val keyPair: SodiumKeyPair = SodiumLibrary.cryptoSignKeyPair()
    val rawPublicKeyHash = SodiumLibrary.cryptoGenerichash(keyPair.getPublicKey, 20)
    for {
      privateKey <- CryptoUtil.base58CheckEncode(keyPair.getPrivateKey, "edsk")
      publicKey <- CryptoUtil.base58CheckEncode(keyPair.getPublicKey, "edpk")
      publicKeyHash <- CryptoUtil.base58CheckEncode(rawPublicKeyHash, "tz1")
    } yield KeyStore(privateKey = privateKey, publicKey = publicKey, publicKeyHash = publicKeyHash)
  }
}