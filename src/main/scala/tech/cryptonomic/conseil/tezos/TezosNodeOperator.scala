package tech.cryptonomic.conseil.tezos

import com.muquit.libsodiumjna.{SodiumKeyPair, SodiumLibrary, SodiumUtils}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.util.{CryptoUtil, DatabaseUtil, JsonUtil}
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.JsonUtil.fromJson

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

import tech.cryptonomic.conseil.util.DatabaseUtil

import cats.effect.IO
import fs2.Stream

/**
  * Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database.
  */
class TezosNodeOperator(val node: TezosRPCInterface)(implicit executionContext: ExecutionContext) extends LazyLogging {

  private val conf = ConfigFactory.load

  val sodiumLibraryPath: String = conf.getString("sodium.libraryPath")
  val accountsFetchConcurrency: Int = conf.getInt("batchedFetches.accountConcurrencyLevel")
  val blockOperationsFetchConcurrency: Int = conf.getInt("batchedFetches.blockOperationsConcurrencyLevel")

  lazy val dbHandle = DatabaseUtil.db

  /**
    * A sum type representing the two types of actions that can happen with a block that is
    * detected during a fork.
    */
  sealed trait BlockAction
  case object RevalidateBlock extends BlockAction
  case object WriteAndInvalidateBlock extends BlockAction
  case object WriteBlock extends BlockAction


  /**
    * A block detected during a fork and it's associated action required for the followFork algorithm.
    * @param block block detected during a fork
    * @param action what action to perform with said block
    */
  case class BlockWithAction (
                                   block: Block,
                                   action: BlockAction
                                   )

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
  def getAccountForBlock(network: String, blockHash: BlockHash, accountID: AccountId): Future[TezosTypes.Account] =
    node.runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts/${accountID.id}")
      .map(fromJson[TezosTypes.Account])

  /**
    * Fetches the manager of a specific account for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @param accountID  Account ID
    * @return           The account
    */
  def getAccountManagerForBlock(network: String, blockHash: BlockHash, accountID: AccountId): Future[TezosTypes.ManagerKey] =
    node.runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts/${accountID.id}/manager_key")
      .map(fromJson[TezosTypes.ManagerKey])

  /**
    * Fetches the accounts identified by id
    *
    * @param network    Which Tezos network to go against
    * @param blockHash  the block storing the accounts
    * @param accountIDs the ids
    * @return           the list of accounts wrapped in a [[Future]]
    */
  def getAccountsForBlock(network: String, blockHash: BlockHash, accountIDs: List[AccountId]): Future[List[TezosTypes.Account]] =
    node
      .runBatchedGetQuery(network, accountIDs.map(id => s"blocks/${blockHash.value}/context/contracts/${id.id}"), accountsFetchConcurrency)
      .map(_.map(fromJson[TezosTypes.Account]))

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
      accounts <- getAccountsForBlock(network, blockHash, accountIDs)
    } yield accountIDs.zip(accounts).toMap

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
    * @return           Blocks
    */
  def getBlocksNotInDatabase(network: String, followFork: Boolean): Future[List[BlockWithAction]] =
    for {
      maxLevel <- ApiOperations.fetchMaxLevel
      blockHead <- {
        if (maxLevel == -1) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
        getBlockHead(network)
      }
      headLevel = blockHead.metadata.header.level
      headHash = blockHead.metadata.hash
      blocks <-
        if (headLevel <= maxLevel) Future.successful(List.empty)
        else getBlocks(network, maxLevel + 1, headLevel, headHash, followFork)
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
                minLevel: Int,
                maxLevel: Int,
                startBlockHash: BlockHash = blockHeadHash,
                followFork: Boolean): Future[List[BlockWithAction]] = { //BlockWithAction
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
    * @param hash Hash of block at max level.
    * @param minLevel Minimum level, at which we stop.
    * @param maxLevel Level at which to stop collecting blocks.
    * @return
    */
  private def processBlocks(
                             network : String,
                             hash: BlockHash,
                             minLevel: Int,
                             maxLevel: Int
                           ): Future[List[BlockWithAction]] = {//BlockWithAction
    val maxOffset: Int = maxLevel - minLevel
    val offsets = (0 to maxOffset).toList.map(_.toString)
    val blockMetadataUrls = offsets.map{offset => s"blocks/${hash.value}~$offset"}

    val jsonToBlockMetadata: String => BlockMetadata =
      json => fromJson[BlockMetadata](json)

    val jsonToOperationGroups: String => List[OperationGroup] =
      json => fromJson[List[List[OperationGroup]]](json).flatten

    /*
    Note that this functionality is dependent on the implementation of runBatchedGetQuery, which currently
    doesn't reorder stream elements, thus ensuring the correctness of Block creation with zip.
     */
    for {
      fetchedBlocksMetadata <- node.runBatchedGetQuery(network, blockMetadataUrls, blockOperationsFetchConcurrency) map (blockMetadata => blockMetadata.map(jsonToBlockMetadata))
      blockOperationUrls = fetchedBlocksMetadata.map(metadata => s"blocks/${metadata.hash.value}/operations")
      fetchedBlocksOperations <- node.runBatchedGetQuery(network, blockOperationUrls, blockOperationsFetchConcurrency) map (operations => operations.map(jsonToOperationGroups))
    } yield fetchedBlocksMetadata.zip(fetchedBlocksOperations).map{ case (meta, ops) => BlockWithAction(Block(meta, ops), WriteBlock) }
  }

  /**
    * Given the blockchain network and the hash of the block where the fork was detected,
    * collect the list of blocks missed during the fork in reverse order.
    *
    * Note: We switch from future to IO to utilize the fs2 Stream library effectively,
    * for concision and efficiency purposes. We may want to consider using IO instead
    * of Future in general.
    *
    * @param network Current Blockchain network we're getting blocks from
    * @param hash Hash of the block where the fork was detected
    * @return Future of List of Blocks that were missed during the Fork, in reverse order
    */
  def followFork(
                  network: String,
                  hash: BlockHash
                ): Future[List[BlockWithAction]] = {

    import tech.cryptonomic.conseil.util.EffectConversionUtil._
    import cats.data._
    import cats.implicits._

    implicit val db = dbHandle

    /* just for clarity
     * Kleisli[IO, A, B] is a typed wrapper to a funtion A => IO[B]
     * but it enables a lot of powerful combinatory operations to simplify
     * coding expressions
     */

    //load a block for an offset
    val getBlockIO = Kleisli[IO, Option[Int], Block]{
      (maybeOffset) => futureToIO(getBlock(network, hash, maybeOffset))
    }

    //check if the block is on db
    val blockExists = Kleisli[IO, Block, Boolean]{
      block => runToIO(TezosDatabaseOperations.blockExists(block.metadata.hash))
    }

    //check if the block is on the invalidated list
    val blockHasBeenInvalidated = Kleisli[IO, Block, Boolean]{
      block => runToIO(TezosDatabaseOperations.blockExistsInInvalidatedBlocks(block.metadata.hash))
    }

    //given the same input, computes both outputs
    val predicate: Kleisli[IO, Block, (Boolean, Boolean)] =
      blockExists &&& blockHasBeenInvalidated

    //returns both the input block and the computed predicates in a 3-tuple
    val extractPredicatesForBlock: Kleisli[IO, Block, (Boolean, Boolean, Block)] =
      predicate.tapWith{ case (block, (exists, invalidated)) => (exists, invalidated, block)}

    /*
    Given an offset from the block where the hash was detected, figure out if we need to collect that
    block or perform a write action to our database.
     */
    def collectBlocksUntilValidExists(offset: Int): IO[Option[(BlockWithAction, Int)]] = {

      //step that evaluates if the process should continue, in which case it gives back the result with the next offset
      val evaluateConditions: ((Boolean, Boolean, Block)) => Option[(BlockWithAction, Int)] = {
        case (exists, invalidated, block) =>

          lazy val reachedValid = exists && !invalidated
          lazy val invalidYetMissing = !exists && invalidated
          lazy val needRevalidation = exists && invalidated

          if (reachedValid)
            None
          else if (invalidYetMissing) {
            //this case should be impossible, you should not have a block that doesn't exists, while being invalid
            logger.error("While following a forked chain I stepped into an invalid block that's not stored in Conseil {}", block)
            None
          }
          else if (needRevalidation)  {
            val blockWithAction = BlockWithAction(block, RevalidateBlock)
            Some(blockWithAction, offset + 1)
          }
          else {
            val blockWithAction = BlockWithAction(block, WriteAndInvalidateBlock)
            Some(blockWithAction, offset + 1)
          }
      }

      //compose all the steps and map the output
      val evaluateNextStep = (getBlockIO andThen extractPredicatesForBlock).map(evaluateConditions)
      //apply the whole computation to get the result
      evaluateNextStep(Some(offset))

    }

    /*
    Create IO Stream of the Forked Blocks, as well as the action to perform with said blocks
    Given a block, you can either update the invalidatedBlocks table, or write a new block
    to the InvalidatedBlocks table database and collect said block to be returned by the function.
    */
    val blockStream: Stream[IO, BlockWithAction] = Stream.unfoldEval(1)(collectBlocksUntilValidExists)
    // turn blockStream to Future[List[BlockWithAction]]
    blockStream.compile.toList.unsafeToFuture()
    /*blockStream.evalMap[IO, Unit]{
      case BlockWithAction(block, WriteAndInvalidateBlock) => runDBIO(TezosDatabaseOperations.writeAndInvalidateBlockIO(List(block)))
      case BlockWithAction(block, RevalidateBlock) => runDBIO(TezosDatabaseOperations.revalidateBlockIO(block)).map(_ => Unit)
    }.compile.drain*/
  }

  /**
    * Get all accounts for a given block
    * @param network     Which Tezos network to go against
    * @param blockHash   Hash of given block
    * @param headerLevel level of given block
    * @return            Accounts with their corresponding block hash
    */
  def getAccounts(network: String, blockHash: BlockHash, headerLevel: Int): Future[AccountsWithBlockHashAndLevel] = {
    val results =
      getAllAccountsForBlock(network, blockHash).map(
        accounts =>
          AccountsWithBlockHashAndLevel(blockHash, headerLevel, accounts)
      )
    results.failed.foreach(
      e => logger.error(s"Could not get a list of accounts for block $blockHash", e)
    )
    results
  }

  /**
    * Get accounts for the latest block in the database.
    * @param network  Which Tezos network to go against
    * @return         Accounts with their corresponding block hash, or [[None]] if no latest block was found
    */
  def getLatestAccounts(network: String): Future[Option[AccountsWithBlockHashAndLevel]] =
    ApiOperations.dbHandle.run(ApiOperations.latestBlockIO.zip(TezosDatabaseOperations.fetchAccountsMaxBlockLevel)).flatMap {
      case (Some(latestBlock), maxAccountsLevel) if latestBlock.level > maxAccountsLevel.toInt =>
        getAccounts(network, BlockHash(latestBlock.hash), latestBlock.level).map(Some(_))
      case (Some(latestBlock), _) =>
        Future.successful(Some(AccountsWithBlockHashAndLevel(BlockHash(latestBlock.hash), latestBlock.level)))
      case _ =>
        Future.successful(None)
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
                                    managerKey: TezosTypes.ManagerKey,
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
                        blockHead: TezosTypes.Block,
                        account: TezosTypes.Account,
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
      .map(json => fromJson[TezosTypes.ForgedOperation](json).operation)
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
                      signedOpGroup: SignedOperationGroup): Future[TezosTypes.AppliedOperation] = {
    val payload: Map[String, Any] = Map(
      "pred_block" -> blockHead.metadata.header.predecessor,
      "operation_hash" -> operationGroupHash,
      "forged_operation" -> forgedOperationGroup,
      "signature" -> signedOpGroup.signature
    )
    node.runAsyncPostQuery(network, "/blocks/head/proto/helpers/apply_operation", Some(JsonUtil.toJson(payload)))
      .map { result =>
        logger.debug(s"Result of operation application: $result")
        JsonUtil.fromJson[TezosTypes.AppliedOperation](result)
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
      .map(result => fromJson[TezosTypes.InjectedOperation](result).injectedOperation)
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