package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.util.{CryptoUtil, JsonUtil}
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.JsonUtil.fromJson
import tech.cryptonomic.conseil.config.{BatchFetchConfiguration, SodiumConfiguration}
import tech.cryptonomic.conseil.generic.chain.DataTypes.AnyMap

import scala.concurrent.{ExecutionContext, Future}
import scala.math.max
import scala.util.{Failure, Success, Try}

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
class TezosNodeOperator(val node: TezosRPCInterface, batchConf: BatchFetchConfiguration)(implicit executionContext: ExecutionContext) extends LazyLogging {
  import batchConf.{accountConcurrencyLevel, blockOperationsConcurrencyLevel, blockPageSize}
  //use this alias to make signatures easier to read and kept in-sync
  type BlockFetchingResults = List[(Block, List[AccountId])]
  type PaginatedBlocksResults = (Iterator[Future[BlockFetchingResults]], Int)

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
    * Fetches all accounts for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block.
    * @return           Accounts
    */
  def getAllAccountsForBlock(network: String, blockHash: BlockHash): Future[Map[AccountId, Account]] =
    for {
      jsonEncodedAccounts <- node.runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts")
      accountIDs = fromJson[List[String]](jsonEncodedAccounts).map(AccountId)
      accounts <- getAccountsForBlock(network, accountIDs, blockHash)
    } yield accounts

  /**
    * Fetches the accounts identified by id
    *
    * @param network    Which Tezos network to go against
    * @param accountIDs the ids
    * @param blockHash  the block storing the accounts, the head block if not specified
    * @return           the list of accounts wrapped in a [[Future]], indexed by AccountId
    */
  def getAccountsForBlock(network: String, accountIDs: List[AccountId], blockHash: BlockHash = blockHeadHash): Future[Map[AccountId, Account]] =
    node
    .runBatchedGetQuery(network, accountIDs, (id: AccountId) => s"blocks/${blockHash.value}/context/contracts/${id.id}", accountConcurrencyLevel)
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
    * Get accounts for all the identifiers passed-in with the corresponding block
    * @param network  Which Tezos network to go against
    * @param accountsBlocksIndex a map from unique id to the [latest] block reference
    * @return         Accounts with their corresponding block data
    */
  def getAccountsForBlocks(network: String, accountsBlocksIndex: Map[AccountId, BlockReference]): Future[List[BlockAccounts]] = {

    def notifyAnyLostIds(missing: Set[AccountId]) =
      if (missing.nonEmpty) logger.warn("The following account keys were not found querying the {} node: {}", network, missing.map(_.id).mkString("\n", ",", "\n"))

    //uses the index to collect together BlockAccounts matching the same block
    def groupByLatestBlock(data: Map[AccountId, Account]): List[BlockAccounts] =
      data.groupBy {
        case (id, _) => accountsBlocksIndex(id)
      }.map {
        case ((hash, level), accounts) => BlockAccounts(hash, level, accounts)
      }.toList

    //fetch accounts by requested ids and group them together with corresponding blocks
    getAccountsForBlock(network, accountsBlocksIndex.keys.toList)
      .andThen {
        case Success(accountsMap) =>
          notifyAnyLostIds(accountsBlocksIndex.keySet -- accountsMap.keySet)
        case Failure(err) =>
          val showSomeIds = accountsBlocksIndex.keys.take(30).map(_.id).mkString("", ",", if (accountsBlocksIndex.size > 30) "..." else "")
          logger.error(s"Could not get accounts' data for ids ${showSomeIds}", err)
      }.map(groupByLatestBlock)

  }

  /**
    * Fetches operations for a block, without waiting for the result
    * @param network   Which Tezos network to go against
    * @param blockHash Hash of the block
    * @return          The `Future` list of operations
    */
  def getAllOperationsForBlock(network: String, blockHash: BlockHash): Future[List[OperationsGroup]] = {
    import io.circe.parser.decode
    import JsonDecoders.Circe.Operations._
    import tech.cryptonomic.conseil.util.JsonUtil.adaptManagerPubkeyField

    //parse json, and try to convert to objects, converting failures to a failed `Future`
    //we could later improve by "accumulating" all errors in a single failed future, with `decodeAccumulating`
    def decodeOperations(json: String) =
      decode[List[List[OperationsGroup]]](adaptManagerPubkeyField(json)).map(_.flatten) match {
        case Left(failure) => Future.failed(failure)
        case Right(results) => Future.successful(results)
      }

    node.runAsyncGetQuery(network, s"blocks/${blockHash.value}/operations")
      .flatMap(decodeOperations)

  }

  /**
    * Fetches a single block from the chain, without waiting for the result
    * @param network   Which Tezos network to go against
    * @param hash      Hash of the block
    * @return          the block data wrapped in a `Future`
    */
  def getBlock(network: String, hash: BlockHash, offset: Option[Int] = None): Future[Block] = {
    val offsetString = offset.map(_.toString).getOrElse("")
    for {
      block <- node.runAsyncGetQuery(network, s"blocks/${hash.value}~$offsetString").map(fromJson[BlockMetadata])
      ops <-
        if (block.header.level > 0) getAllOperationsForBlock(network, hash)
        else Future.successful(List.empty) //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
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
    * Given a level range, creates sub-ranges of max the given size
    * @param levels a range of levels to partition into (possibly) smaller parts
    * @param pageSize how big a part is allowed to be
    * @return an iterator over the part, which are themselves `Ranges`
    */
  def partitionBlocksRanges(levels: Range.Inclusive, pageSize: Int = blockPageSize): Iterator[Range.Inclusive] =
    levels.grouped(pageSize)
      .filterNot(_.isEmpty)
      .map(subRange => subRange.head to subRange.last)

  /**
    * Gets all blocks from the head down to the oldest block not already in the database.
    * @param network    Which Tezos network to go against
    * @return           Blocks and Account hashes involved
    */
  def getBlocksNotInDatabase(network: String): Future[PaginatedBlocksResults] =
    for {
      maxLevel <- ApiOperations.fetchMaxLevel
      blockHead <- getBlockHead(network)
      headLevel = blockHead.metadata.header.level
      headHash = blockHead.metadata.hash
    } yield {
      if (maxLevel < headLevel) {
        //got something to load
        if (maxLevel == -1) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
        else logger.info("I found the new block head at level {}, the currently stored max is {}. I'll fetch the missing {} blocks.", headLevel, maxLevel, headLevel - maxLevel)
        val pagedResults = partitionBlocksRanges((maxLevel + 1) to headLevel).map(
          page => getBlocks(network, (headHash, headLevel), page)
        )
        (pagedResults, headLevel - maxLevel - 1)
      } else {
        logger.info("No new blocks to fetch from the network")
        (Iterator.empty, 0)
      }
    }

  /**
    * Gets last `depth` blocks.
    * @param network    Which Tezos network to go against
    * @param depth      Number of latest block to fetch, `None` to get all
    * @return           Blocks and Account hashes involved
    */
  def getLatestBlocks(network: String, depth: Option[Int] = None): Future[PaginatedBlocksResults] =
    getBlockHead(network).map {
      head =>
        val headLevel = head.metadata.header.level
        val headHash = head.metadata.hash
        val minLevel = depth.fold(1)(d => max(1, headLevel - d + 1))
        val pagedResults = partitionBlocksRanges(minLevel to headLevel).map(
          page => getBlocks(network, (headHash, headLevel), page)
        )
        (pagedResults, headLevel - minLevel + 1)
    }

  /**
    * Gets block from Tezos Blockchains, as well as their associated operation, from minLevel to maxLevel.
    * @param network Which Tezos network to go against
    * @param reference Hash and level of a known block
    * @param levelRange a range of levels to load
    * @return the async list of blocks with relative account ids touched in the operations
    */
  private def getBlocks(
    network : String,
    reference: (BlockHash, Int),
    levelRange: Range.Inclusive
    ): Future[BlockFetchingResults] = {
    import io.circe.parser.decode
    import JsonDecoders.Circe.{ JsonDecoded, handleDecodingErrors }
    import JsonDecoders.Circe.Operations._
    import tech.cryptonomic.conseil.util.JsonUtil.adaptManagerPubkeyField

    val (hashRef, levelRef) = reference
    require(levelRange.start >= 0 && levelRange.end <= levelRef)
    val offsets = levelRange.map(lvl => levelRef - lvl).toList
    val makeBlocksUrl = (offset: Int) => s"blocks/${hashRef.value}~${String.valueOf(offset)}"
    val makeOperationsUrl = (hash: BlockHash) => s"blocks/${hash.value}/operations"

    val jsonToBlockMetadata: ((Int, String)) => BlockMetadata = {
      case (_, json) => fromJson[BlockMetadata](json)
    }

    val jsonToOperationGroups: String => JsonDecoded[List[OperationsGroup]] =
      json => decode[List[List[OperationsGroup]]](adaptManagerPubkeyField(json)).map(_.flatten)

    //extracts any formally valid account hash from the passed-in string
    val jsonToAccountInvolved: String => List[AccountId] = {
      case JsonUtil.AccountIds(id, ids @ _*) =>
        (id :: ids.toList).distinct.map(AccountId)
      case _ => List.empty
    }

    //from the same json, converts to a list of operations and the involved account ids
    val jsonToOperationsAndAccounts: ((BlockHash, String)) => JsonDecoded[(BlockHash, List[OperationsGroup], List[AccountId])] = {
      case (hash, json) =>
        jsonToOperationGroups(json).map( groups => (hash, groups, jsonToAccountInvolved(json)))
    }

    val isGenesis = (metadata: BlockMetadata) => metadata.header.level == 0

    def decodeOperations(in: List[(BlockHash, String)]): Future[List[(BlockHash, List[OperationsGroup], List[AccountId])]] =
      handleDecodingErrors(in, jsonToOperationsAndAccounts) match {
        case Left(failure) => Future.failed(failure.errors.head)
        case Right(results) => Future.successful(results)
      }

    //Gets metadata for the requested offsets and associates the operations and account hashes available involved in said operations
    //Special care is taken for the genesis block (level = 0) that doesn't have operations defined, we use empty data for it
    for {
      fetchedBlocksMetadata <- node.runBatchedGetQuery(network, offsets, makeBlocksUrl, blockOperationsConcurrencyLevel) map (blocksMetadata => blocksMetadata.map(jsonToBlockMetadata))
      blockHashes = fetchedBlocksMetadata.filterNot(isGenesis).map(_.hash)
      fetchedOperations <- node.runBatchedGetQuery(network, blockHashes, makeOperationsUrl, blockOperationsConcurrencyLevel)
      fetchedOperationsWithAccounts <- decodeOperations(fetchedOperations)
    } yield {
      val operationalDataMap = fetchedOperationsWithAccounts.map{ case (hash, ops, accounts) => (hash, (ops, accounts))}.toMap
      fetchedBlocksMetadata.map {
        md =>
          val (ops, accs) = if (isGenesis(md)) (List.empty, List.empty) else operationalDataMap(md.hash)
          (Block(md, ops), accs)
      }
    }
  }

}

/**
  * Adds more specific API functionalities to perform on a tezos node, in particular those involving write and cryptographic operations
  */
class TezosNodeSenderOperator(override val node: TezosRPCInterface, batchConf: BatchFetchConfiguration, sodiumConf: SodiumConfiguration)(implicit executionContext: ExecutionContext)
  extends TezosNodeOperator(node, batchConf)
  with LazyLogging {
  import com.muquit.libsodiumjna.{SodiumKeyPair, SodiumLibrary, SodiumUtils}
  import TezosNodeOperator._

  //used in subsequent operations using Sodium
  SodiumLibrary.setLibraryPath(sodiumConf.libraryPath)

  /**
    * Appends a key reveal operation to an operation group if needed.
    * @param operations The operations being forged as part of this operation group
    * @param managerKey The sending account's manager information
    * @param keyStore   Key pair along with public key hash
    * @return           Operation group enriched with a key reveal if necessary
    */
  def handleKeyRevealForOperations(
    operations: List[AnyMap],
    managerKey: ManagerKey,
    keyStore: KeyStore): List[AnyMap] =
    managerKey.key match {
      case Some(_) => operations
      case None =>
        val revealMap: AnyMap = Map(
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
  def forgeOperations(
    network: String,
    blockHead: Block,
    account: Account,
    operations: List[Map[String,Any]],
    keyStore: KeyStore,
    fee: Option[Float]): Future[String] = {
    val payload: AnyMap = fee match {
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
    val payload: AnyMap = Map(
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
    val payload: AnyMap = Map(
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
    fee: Float): Future[OperationResult] = {
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
    fee: Float): Future[OperationResult] = {
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