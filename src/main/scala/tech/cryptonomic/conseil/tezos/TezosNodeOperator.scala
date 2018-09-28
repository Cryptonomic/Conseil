package tech.cryptonomic.conseil.tezos

import com.muquit.libsodiumjna.{SodiumKeyPair, SodiumLibrary, SodiumUtils}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.util.{CryptoUtil, JsonUtil}
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.JsonUtil.fromJson
//import tech.cryptonomic.conseil.util.FPUtil.sequence
import cats._
import cats.data._
import cats.implicits._


import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Try}


/**
  * Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database.
  */
class TezosNodeOperator(node: TezosRPCInterface)(implicit ec: ExecutionContext) extends LazyLogging {

  private val conf = ConfigFactory.load

  val sodiumLibraryPath: String = conf.getString("sodium.libraryPath")
  val accountsFetchConcurrency: Int = conf.getInt("batchedFetches.accountConcurrencyLevel")
  val blockOperationsFetchConcurrency: Int = conf.getInt("batchedFetches.blockOperationsConcurrencyLevel")

  /**
    * Output of operation signing.
    * @param bytes      Signed bytes of the transaction
    * @param signature  The actual signature
    */
  case class SignedOperationGroup(bytes: Array[Byte], signature: String)

  /**
    * Result of a successfully sent operation
    * @param results          Results of operation application
    * @param operationGroupID Operation group ID
    */
  case class OperationResult(results: TezosTypes.AppliedOperation, operationGroupID: String)

  /**
    * Fetches a specific account for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @param accountID  Account ID
    * @return           The account
    */
  def getAccountForBlock(network: String, blockHash: String, accountID: String): Try[TezosTypes.Account] =
  node.runGetQuery(network, s"blocks/$blockHash/context/contracts/$accountID").flatMap { jsonEncodedAccount =>
      Try(fromJson[TezosTypes.Account](jsonEncodedAccount)).flatMap(account => Try(account))
    }

  /**
    * Fetches the manager of a specific account for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @param accountID  Account ID
    * @return           The account
    */
  def getAccountManagerForBlock(network: String, blockHash: String, accountID: String): Try[TezosTypes.ManagerKey] =
  node.runGetQuery(network, s"blocks/$blockHash/context/contracts/$accountID/manager_key").flatMap { json =>
      Try(fromJson[TezosTypes.ManagerKey](json)).flatMap(managerKey => Try(managerKey))
    }


  /**
    * Fetches the accounts identified by id
    *
    * @param network    Which Tezos network to go against
    * @param blockHash  the block storing the accounts
    * @param accountIDs the ids
    * @param ec         an implicit context to chain async operations
    * @return           the list of accounts wrapped in a [[Future]]
    */
  def getAccountsForBlock(network: String, blockHash: String, accountIDs: List[String])(implicit  ec: ExecutionContext): Future[List[TezosTypes.Account]] =
    node
      .runBatchedGetQuery(network, accountIDs.map(id => s"blocks/$blockHash/context/contracts/$id"), accountsFetchConcurrency)
      .map(_.map(fromJson[TezosTypes.Account]))

  /**
    * Fetches all accounts for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block.
    * @return           Accounts
    */
  def getAllAccountsForBlock(network: String, blockHash: String): Future[Map[String, TezosTypes.Account]] =
    node.runAsyncGetQuery(network, s"blocks/$blockHash/context/contracts") flatMap {
      jsonEncodedAccounts =>
        val accountIDs = fromJson[List[String]](jsonEncodedAccounts)
        getAccountsForBlock(network, blockHash, accountIDs) map {
          accounts =>
            accountIDs.zip(accounts).toMap
        } andThen {
          case Failure(e) =>
            logger.error(s"Failed to load accounts for ${accountIDs.size} ids $accountIDs", e)
        }
    }

  /**
    * Returns all operation groups for a given block.
    * The list of lists return type is intentional as it corresponds to the return value of the Tezos client.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of the given block
    * @return           Operation groups
    */
  def getAllOperationsForBlock(network: String, blockHash: String): Try[List[List[OperationGroup]]] =
    node.runGetQuery(network, s"blocks/$blockHash/operations").flatMap { jsonEncodedOperationsContainer =>
      Try(fromJson[List[List[OperationGroup]]](jsonEncodedOperationsContainer)).flatMap{ operationsContainer =>
        Try(operationsContainer)
      }
    }

  /**
    * Fetches operations for a block, without waiting for the result
    * @param network   Which Tezos network to go against
    * @param blockHash Hash of the block
    * @return          The [[Future]] list of operations
    */
  def asyncGetAllOperationsForBlock(network: String, blockHash: String): Future[List[OperationGroup]] =
    node.runAsyncGetQuery(network, s"blocks/$blockHash/operations")
      .map(ll => fromJson[List[List[OperationGroup]]](ll).flatten)

  /**
    * Fetches a single block from the chain, without waiting for the result
    * @param network   Which Tezos network to go against
    * @param hash      Hash of the block
    * @return          the block data wrapped in a [[Future]]
    */
  def asyncGetBlock(network: String, hash: String, offset: Option[Int]): Future[TezosTypes.Block] = {
    val offsetString = offset.fold("")(off => s"~$off")
    for {
      block <- node.runAsyncGetQuery(network, s"blocks/$hash$offsetString") map fromJson[BlockMetadata]
      ops <- if (block.header.level == 0)
        Future.successful(List.empty[OperationGroup]) //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
      else
        asyncGetAllOperationsForBlock(network, hash)
    } yield Block(block, ops)
  }

  /**
    * Gets the block head.
    * @param network  Which Tezos network to go against
    * @return         Block head
    */
  def getBlockHead(network: String): Try[TezosTypes.Block]=
    Try(Await.result(asyncGetBlock(network, "head", None), TezosNodeInterface.entityGetTimeout))

  /**
    * Gets all blocks from the head down to the oldest block not already in the database.
    * @param network    Which Tezos network to go against
    * @param followFork If the predecessor of the minLevel block appears to be on a fork, also capture the blocks on the fork.
    * @return           Blocks
    */
  def getBlocksNotInDatabase(network: String, followFork: Boolean): Future[List[Block]] =
    Future.fromTry(ApiOperations.fetchMaxLevel()).flatMap{ maxLevel =>
      if(maxLevel == -1) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
      asyncGetBlock(network, "head", None).flatMap { blockHead =>
        val headLevel =  blockHead.metadata.header.level
        if(headLevel <= maxLevel)
          Future.successful(List.empty)
        else
          fetchBoundedBlockChain(network, blockHead, maxLevel+1, headLevel, followFork)
      }
    }

  /**
    * Fetches metadata for a block only, without waiting for the answer
    * @param network   Which Tezos network to go against
    * @param hash      Hash of the block
    * @return          the metadata wrapped in a [[Future]]
    */
  private def getMetadata(network: String, hash: String): Future[BlockMetadata] =
    node.runAsyncGetQuery(network, s"blocks/$hash") map fromJson[BlockMetadata]

  /**
    * Asynchronously fetches a whole chain of metadata for blocks, using the parameters to
    * decide when to stop the recursive descent on predecessors
    *
    * @param network    which Tezos network to go against
    * @param current    The current metadata object to start descending
    * @param minLevel   The lowest level for which to include results, unless a fork should be followed
    * @param maxLevel   The highest level for which to include results
    * @param followFork If the predecessor of the minLevel block appears to be on a fork, also capture the blocks on the fork.
    * @param collected  The currently accumulated metadata so far
    * @return           The metadata list, wrapped in a [[Future]]
    */
  def fetchChainedMetadata(
    network: String,
    current: BlockMetadata,
    minLevel: Int,
    maxLevel: Int,
    followFork: Boolean,
    collected: List[BlockMetadata] = List.empty
  ) : Future[List[BlockMetadata]] = {
    val currentLevel = current.header.level
    logger.info("Current metadata level is {}", currentLevel)
    val reachedBottom = currentLevel == 0 && minLevel <= 0 //can we get further on? Why checking min?
    val reachedMinNoForkin = currentLevel == minLevel && !followFork
    val belowMinWithForking = currentLevel <= minLevel && followFork
    val aboveMax = currentLevel > maxLevel
    val aboveMin = currentLevel > minLevel

    if(reachedBottom || reachedMinNoForkin)
      Future.successful(current :: collected)
    else if (belowMinWithForking)
      TezosDatabaseOperations.blockExists(current.header.predecessor) flatMap { predecessorStored =>
        if (predecessorStored)
        Future.successful(current :: collected) //pred on db, we're done here
      else
        getMetadata(network, current.header.predecessor) flatMap { pred =>
          fetchChainedMetadata(network, pred, minLevel, maxLevel, followFork, current :: collected)
        }
      }
    else if (aboveMax)
      //skip this
      getMetadata(network, current.header.predecessor) flatMap { pred =>
        fetchChainedMetadata(network, pred, minLevel, maxLevel, followFork, collected)
      }
    else if (aboveMin)
      getMetadata(network, current.header.predecessor) flatMap { pred =>
        fetchChainedMetadata(network, pred, minLevel, maxLevel, followFork, current :: collected)
      }
    else
      Future.successful(List.empty)

  }

  private def processBlocks(
                           network : String,
                           hash: String,
                           minLevel: Int,
                           maxLevel: Int
                           ): Future[List[Block]] = {
    val maxOffset: Int = maxLevel - minLevel
    val offsets = (0 to maxOffset).toList
    offsets.map{ offset => asyncGetBlock(network, hash, Some(offset))}.sequence
  }

  /**
    * Traverses Tezos blockchain as parameterized, without waiting for the answer
    *
    * @param network       Which Tezos network to go against
    * @param startingBlock Current block to start from
    * @param minLevel      Minimum level at which to stop
    * @param maxLevel      Level at which to start collecting blocks
    * @param followFork    If the predecessor of the minLevel block appears to be on a fork, also capture the blocks on the fork.
    * @return              All collected blocks, wrapped in a [[Future]]
    */
  private def fetchBoundedBlockChain(
    network: String,
    startingBlock: Block,
    minLevel: Int,
    maxLevel: Int,
    followFork: Boolean
  ): Future[List[Block]] = {
    val jsonToOperationGroups: String => List[OperationGroup] =
      json => fromJson[List[List[OperationGroup]]](json).flatten

    for {
      metadataChain <- fetchChainedMetadata(network, startingBlock.metadata, minLevel, maxLevel, followFork)
      operationsUrls = metadataChain.map(d => s"blocks/${d.hash}/operations")
      operations <- node.runBatchedGetQuery(network, operationsUrls, blockOperationsFetchConcurrency) map (all => all.map(jsonToOperationGroups))
    } yield metadataChain.zip(operations).map(Block.tupled)
  }


  def getBlocks(network: String, minLevel: Int, maxLevel: Int,
                startBlockHash: Option[String], followFork: Boolean): Future[List[Block]] = {
    val hash = startBlockHash.getOrElse("head")
    val blocksInRange = asyncGetBlock(network, hash, None)
        .flatMap(block => processBlocks(network, block.metadata.hash, minLevel, maxLevel))
    val blocksFromFork = followFork match {
      case false => Future.successful(List.empty : List[Block])
      case true => Future.successful(List.empty : List[Block])
    }
    blocksInRange
    /*
    for {
      range <- blocksFromRange
      fork <- blocksFromFork
    }
    */

  }

  /**
    * Get all accounts for a given block
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @return           Accounts with their corresponding block hash
    */
  def getAccounts(network: String, blockHash: String, headerLevel: Int)(implicit  ec: ExecutionContext): Future[AccountsWithBlockHashAndLevel] =
      getAllAccountsForBlock(network, blockHash).map { accounts =>
        AccountsWithBlockHashAndLevel(blockHash, headerLevel, accounts)
      }

  /**
    * Get accounts for the latest block in the database.
    * @param network  Which Tezos network to go against
    * @return         Accounts with their corresponding block hash
    */
  def getLatestAccounts(network: String)(implicit  ec: ExecutionContext): Future[AccountsWithBlockHashAndLevel]=
    for {
      dbBlockHead <- Future fromTry ApiOperations.fetchLatestBlock()
      maxLevelForAccounts <- Future fromTry ApiOperations.fetchMaxBlockLevelForAccounts()
      blockAccounts <- if(maxLevelForAccounts.toInt < dbBlockHead.level)
        getAccounts(network, dbBlockHead.hash, dbBlockHead.level)
      else
        Future.successful(AccountsWithBlockHashAndLevel(dbBlockHead.hash, dbBlockHead.level, Map.empty))
    } yield blockAccounts

  /**
    * Appends a key reveal operation to an operation group if needed.
    * @param operations The operations being forged as part of this operation group
    * @param managerKey The sending account's manager information
    * @param keyStore   Key pair along with public key hash
    * @return           Operation group enriched with a key reveal if necessary
    */
  def handleKeyRevealForOperations(
                                    operations: List[Map[String, Any]],
                                    managerKey: TezosTypes.ManagerKey,
                                    keyStore: KeyStore)
                                    : Try[List[Map[String, Any]]] =
  Try{
    managerKey.key match {
      case Some(_) => operations
      case None =>
        val revealMap: Map[String, Any] = Map(
          "kind"        -> "reveal",
          "public_key"  -> keyStore.publicKey
        )
        revealMap :: operations
    }
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
                        blockHead: TezosTypes.Block,
                        account: TezosTypes.Account,
                        operations: List[Map[String,Any]],
                        keyStore: KeyStore,
                        fee: Option[Float]
                     ): Try[String] = {
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
    node.runPostQuery(network, "/blocks/head/proto/helpers/forge/operations", Some(JsonUtil.toJson(payload)))
      .flatMap { json =>
        Try(JsonUtil.fromJson[TezosTypes.ForgedOperation](json)).flatMap{ forgedOperation =>
          Try(forgedOperation.operation)
          }
        }
      }

  /**
    * Signs a forged operation
    * @param forgedOperation  Forged operation group returned by the Tezos client (as a hex string)
    * @param keyStore         Key pair along with public key hash
    * @return                 Bytes of the signed operation along with the actual signature
    */
  def signOperationGroup(forgedOperation: String, keyStore: KeyStore): Try[SignedOperationGroup] = Try{
    SodiumLibrary.setLibraryPath(sodiumLibraryPath)
    val privateKeyBytes = CryptoUtil.base58CheckDecode(keyStore.privateKey, "edsk").get
    val watermark = "03"  // In the future, we must support "0x02" for endorsements and "0x01" for block signing.
    val watermarkedForgedOperationBytes = SodiumUtils.hex2Binary(watermark + forgedOperation)
    val hashedWatermarkedOpBytes = SodiumLibrary.cryptoGenerichash(watermarkedForgedOperationBytes, 32)
    val opSignature: Array[Byte] = SodiumLibrary.cryptoSignDetached(hashedWatermarkedOpBytes, privateKeyBytes.toArray)
    val hexSignature: String = CryptoUtil.base58CheckEncode(opSignature.toList, "edsig").get
    val signedOpBytes = SodiumUtils.hex2Binary(forgedOperation) ++ opSignature
    SignedOperationGroup(signedOpBytes, hexSignature)
  }

  /**
    * Computes the ID of an operation group using Base58Check.
    * @param signedOpGroup  Signed operation group
    * @return               Base58Check hash of signed operation
    */
  def computeOperationHash(signedOpGroup: SignedOperationGroup): Try[String] =
    Try{
      SodiumLibrary.setLibraryPath(sodiumLibraryPath)
      SodiumLibrary.cryptoGenerichash(signedOpGroup.bytes, 32)
    }.flatMap { hash =>
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
                      blockHead: TezosTypes.Block,
                      operationGroupHash: String,
                      forgedOperationGroup: String,
                      signedOpGroup: SignedOperationGroup): Try[TezosTypes.AppliedOperation] = {
    val payload: Map[String, Any] = Map(
      "pred_block" -> blockHead.metadata.header.predecessor,
      "operation_hash" -> operationGroupHash,
      "forged_operation" -> forgedOperationGroup,
      "signature" -> signedOpGroup.signature
    )
    node.runPostQuery(network, "/blocks/head/proto/helpers/apply_operation", Some(JsonUtil.toJson(payload)))
      .flatMap { result =>
        logger.debug(s"Result of operation application: $result")
        Try(JsonUtil.fromJson[TezosTypes.AppliedOperation](result))
      }
  }

  /**
    * Injects an opertion using the Tezos RPC client.
    * @param network        Which Tezos network to go against
    * @param signedOpGroup  Signed operation group
    * @return               ID of injected operation
    */
  def injectOperation(network: String, signedOpGroup: SignedOperationGroup): Try[String] = {
    val payload: Map[String, Any] = Map(
      "signedOperationContents" -> signedOpGroup.bytes.map("%02X" format _).mkString
    )
    node.runPostQuery(network, "/inject_operation", Some(JsonUtil.toJson(payload))).flatMap{ result =>
      Try {
        val injectedOp = JsonUtil.fromJson[TezosTypes.InjectedOperation](result)
        injectedOp.injectedOperation
        }
      }
    }

  /**
    * Master function for creating and sending all supported types of operations.
    * @param network    Which Tezos network to go against
    * @param operations The operations to create and send
    * @param keyStore   Key pair along with public key hash
    * @param fee        The fee to use
    * @return           The ID of the created operation group
    */
  def sendOperation(network: String, operations: List[Map[String,Any]], keyStore: KeyStore, fee: Option[Float]): Try[OperationResult] = for {
    blockHead <- getBlockHead(network)
    account <- getAccountForBlock(network, "head", keyStore.publicKeyHash)
    accountManager <- getAccountManagerForBlock(network, "head", keyStore.publicKeyHash)
    operationsWithKeyReveal <- handleKeyRevealForOperations(operations, accountManager, keyStore)
    forgedOperationGroup <- forgeOperations(network, blockHead, account, operationsWithKeyReveal, keyStore, fee)
    signedOpGroup <- signOperationGroup(forgedOperationGroup, keyStore)
    operationGroupHash <- computeOperationHash(signedOpGroup)
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
                     ): Try[OperationResult] = {
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
                             ): Try[OperationResult] = {
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
                             ): Try[OperationResult] = {
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
    SodiumLibrary.setLibraryPath(sodiumLibraryPath)

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
