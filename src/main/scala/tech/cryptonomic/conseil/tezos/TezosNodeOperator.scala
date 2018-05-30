package tech.cryptonomic.conseil.tezos

import com.muquit.libsodiumjna.{SodiumKeyPair, SodiumLibrary, SodiumUtils}
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes.{AccountsWithBlockHash, Block, MichelsonExpression, OperationGroup}
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.JsonUtil.fromJson
import tech.cryptonomic.conseil.util.{CryptoUtil, JsonUtil}

import scala.util.{Failure, Success, Try}


/**
  * Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database.
  */
class TezosNodeOperator(node: TezosRPCInterface) extends LazyLogging {

  private val conf = ConfigFactory.load

  val sodiumLibraryPath: String = conf.getString("sodium.libraryPath")

  /**
    * Output of operation signing.
    * @param bytes      Signed bytes of the transaction
    * @param signature  The actual signature
    */
  case class SignedOperationGroup(bytes: Array[Byte], signature: String)

  /**
    * Result of a successfully sent operation
    * @param accounts         Handles of any accounts created, e.g. by origination
    * @param operationGroupID Operation group ID
    */
  case class OperationResult(accounts: Seq[String], operationGroupID: String)

  /**
    * Fetches a specific account for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @param accountID  Account ID
    * @return           The account
    */
  def getAccountForBlock(network: String, blockHash: String, accountID: String): Try[TezosTypes.Account] =
    node.runQuery(network, s"blocks/$blockHash/proto/context/contracts/$accountID").flatMap { jsonEncodedAccount =>
      Try(fromJson[TezosTypes.Account](jsonEncodedAccount)).flatMap(account => Try(account))
    }

  /**
    * Fetches all accounts for a given block.
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block.
    * @return           Accounts
    */
  def getAllAccountsForBlock(network: String, blockHash: String): Try[Map[String, TezosTypes.Account]] = Try {
    node.runQuery(network, s"blocks/$blockHash/proto/context/contracts") match {
      case Success(jsonEncodedAccounts) =>
        val accountIDs = fromJson[List[String]](jsonEncodedAccounts)
        val listedAccounts: List[String] = accountIDs
        val accounts = listedAccounts.map(acctID => getAccountForBlock(network, blockHash, acctID))
        accounts.count(_.isFailure) match {
          case 0 =>
            val justTheAccounts = accounts.map(_.get)
            (listedAccounts zip justTheAccounts).toMap
          case _ => throw new Exception(s"Could not decode one of the accounts for block $blockHash")
        }
      case Failure(e) =>
        logger.error(s"Could not get a list of accounts for block $blockHash")
        throw e
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
    node.runQuery(network, s"blocks/$blockHash/proto/operations").flatMap { jsonEncodedOperationsContainer =>
      Try(fromJson[List[List[OperationGroup]]](jsonEncodedOperationsContainer)).flatMap{ operationsContainer =>
        Try(operationsContainer)
      }
    }

  /**
    * Gets a given block.
    * @param network  Which Tezos network to go against
    * @param hash     Hash of the given block
    * @return         Block
    */
  def getBlock(network: String, hash: String): Try[TezosTypes.Block] =
    node.runQuery(network, s"blocks/$hash").flatMap { jsonEncodedBlock =>
      Try(fromJson[TezosTypes.BlockMetadata](jsonEncodedBlock)).flatMap { theBlock =>
        if(theBlock.level==0)
          Try(Block(theBlock, List[OperationGroup]()))    //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
        else {
          getAllOperationsForBlock(network, hash).flatMap{ itsOperations =>
            itsOperations.length match {
              case 0 => Try(Block(theBlock, List[OperationGroup]()))
              case _ => Try(Block(theBlock, itsOperations.flatten))
            }
          }
        }
      }
    }

  /**
    * Gets the block head.
    * @param network  Which Tezos network to go against
    * @return         Block head
    */
  def getBlockHead(network: String): Try[TezosTypes.Block]= {
    getBlock(network, "head")
  }

  /**
    * Gets all blocks from the head down to the oldest block not already in the database.
    * @param network    Which Tezos network to go against
    * @param followFork If the predecessor of the minLevel block appears to be on a fork, also capture the blocks on the fork.
    * @return           Blocks
    */
  def getBlocksNotInDatabase(network: String, followFork: Boolean): Try[List[Block]] =
    ApiOperations.fetchMaxLevel().flatMap{ maxLevel =>
      if(maxLevel == -1) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
      getBlockHead(network).flatMap { blockHead =>
        val headLevel = blockHead.metadata.level
        val headHash  = blockHead.metadata.hash
        if(headLevel <= maxLevel)
          Try(List[Block]())
        else
          getBlocks(network, maxLevel+1, headLevel, Some(headHash), followFork)
      }
    }

  /**
    * Gets the latest blocks from the database using an offset.
    * @param network        Which Tezos network to go against
    * @param offset         How many previous blocks to get from the start
    * @param startBlockHash The block from which to offset, using the hash provided or the head if none is provided.
    * @param followFork     If the predecessor of the minLevel block appears to be on a fork, also capture the blocks on the fork.
    * @return               Blocks
    */
  def getBlocks(network: String, offset: Int, startBlockHash: Option[String], followFork: Boolean): Try[List[Block]] =
    startBlockHash match {
      case None =>
        getBlockHead(network).flatMap(head => getBlocks(network, head.metadata.level - offset + 1, head.metadata.level, startBlockHash, followFork))
      case Some(hash) =>
        getBlock(network, hash).flatMap(block => getBlocks(network, block.metadata.level - offset + 1, block.metadata.level, startBlockHash, followFork))
    }

  /**
    * Gets the blocks using a specified rage
    * @param network        Which Tezos network to go against
    * @param minLevel       Minimum block level
    * @param maxLevel       Maximum block level
    * @param startBlockHash If specified, start from the supplied block hash.
    * @param followFork     If the predecessor of the minLevel block appears to be on a fork, also capture the blocks on the fork.
    * @return               Blocks
    */
  def getBlocks(network: String, minLevel: Int, maxLevel: Int, startBlockHash: Option[String], followFork: Boolean): Try[List[Block]] =
    startBlockHash match {
      case None =>
        getBlockHead(network).flatMap(head => Try {
          processBlocks(network, head.metadata.hash, minLevel, maxLevel, followFork = followFork)
        })
      case Some(hash) =>
        getBlock(network, hash).flatMap(block => Try {
          processBlocks(network, block.metadata.hash, minLevel, maxLevel, followFork = followFork)
        })
    }

  /**
    * Traverses Tezos blockchain as parameterized.
    * @param network    Which Tezos network to go against
    * @param hash       Current block to fetch
    * @param minLevel   Minimum level at which to stop
    * @param maxLevel   Level at which to start collecting blocks
    * @param blockSoFar Blocks collected so far
    * @param followFork If the predecessor of the minLevel block appears to be on a fork, also capture the blocks on the fork.
    * @return           All collected blocks
    */
  private def processBlocks(
                             network: String,
                             hash: String,
                             minLevel: Int,
                             maxLevel: Int,
                             blockSoFar: List[Block] = List[Block](),
                             followFork: Boolean
                           ): List[Block] =
    getBlock(network, hash) match {
      case Success(block) =>
        logger.debug(s"Current block height: ${block.metadata.level}")
        if(block.metadata.level == 0 && minLevel <= 0)
          block :: blockSoFar
        else if(block.metadata.level == minLevel && !followFork)
          block :: blockSoFar
        else if(block.metadata.level <= minLevel && followFork)
          ApiOperations.fetchBlock(block.metadata.predecessor) match {
            case Success(_)     => block :: blockSoFar
            case Failure(_)     => processBlocks(network, block.metadata.predecessor, minLevel, maxLevel, block :: blockSoFar, followFork)
          }
        else if(block.metadata.level > maxLevel)
          processBlocks(network, block.metadata.predecessor, minLevel, maxLevel, blockSoFar, followFork)
        else if(block.metadata.level > minLevel)
          processBlocks(network, block.metadata.predecessor, minLevel, maxLevel, block :: blockSoFar, followFork)
        else
          List[Block]()
      case Failure(e) => throw e
    }

  /**
    * Get all accounts for a given block
    * @param network    Which Tezos network to go against
    * @param blockHash  Hash of given block
    * @return           Accounts with their corresponding block hash
    */
  def getAccounts(network: String, blockHash: String): Try[AccountsWithBlockHash] =
    getAllAccountsForBlock(network, blockHash).flatMap{ accounts =>
      Try(AccountsWithBlockHash(blockHash, accounts))
    }

  /**
    * Get accounts for the latest block in the database.
    * @param network  Which Tezos network to go against
    * @return         Accounts with their corresponding block hash
    */
  def getLatestAccounts(network: String): Try[AccountsWithBlockHash]=
    ApiOperations.fetchLatestBlock().flatMap(dbBlockHead => getAccounts(network, dbBlockHead.hash))

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
          "branch" -> blockHead.metadata.predecessor,
          "operations" -> operations
        )
    }
    node.runQuery(network, "/blocks/head/proto/helpers/forge/operations", Some(JsonUtil.toJson(payload)))
      .flatMap { json =>
        Try(JsonUtil.fromJson[TezosTypes.SuccessfulForgedOperation](json)).flatMap{ forgedOperation =>
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
    val watermark = "03"
    val watermarkedForgedOperationBytes = SodiumUtils.hex2Binary(watermark + forgedOperation)
    val privateKeyBytes = CryptoUtil.base58CheckDecode(keyStore.privateKey, "edsk").get
    val hashedWatermarkedOpBytes = SodiumLibrary.cryptoGenerichash(watermarkedForgedOperationBytes, 32)
    val opSignature: Array[Byte] = SodiumLibrary.cryptoSignDetached(hashedWatermarkedOpBytes, privateKeyBytes.toArray)
    val hexSignature: String = CryptoUtil.base58CheckEncode(opSignature.toList, "edsig").get
    val signedOpBytes = watermarkedForgedOperationBytes ++ opSignature
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
                      signedOpGroup: SignedOperationGroup): Try[Array[String]] = {
    val payload: Map[String, Any] = Map(
      "pred_block" -> blockHead.metadata.predecessor,
      "operation_hash" -> operationGroupHash,
      "forged_operation" -> forgedOperationGroup,
      "signature" -> signedOpGroup.signature
    )
    node.runQuery(network, "/blocks/head/proto/helpers/apply_operation", Some(JsonUtil.toJson(payload)))
      .flatMap { result =>
        logger.debug(s"Result of operation application: $result")
        Try(JsonUtil.fromJson[TezosTypes.AppliedOperation](result)).flatMap{ appliedOP =>
          Try(appliedOP.contracts)
        }
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
    node.runQuery(network, "/inject_operation", Some(JsonUtil.toJson(payload))).flatMap{result =>
      Try {
        val injectedOp = JsonUtil.fromJson[TezosTypes.InjectedOperationContainer](result)
        injectedOp.ok match {
          case Some(ok) => ok.injectedOperation
          case None => throw new Exception(s"Could not inject operation because: ${injectedOp.error.get}")
        }
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
  def sendOperation(network: String, operations: List[Map[String,Any]], keyStore: KeyStore, fee: Option[Float]): Try[OperationResult] = {
    getBlockHead(network).flatMap{ blockHead =>
      getAccountForBlock(network, "head", keyStore.publicKeyHash).flatMap{ account =>
        forgeOperations(network, blockHead, account, operations, keyStore, fee).flatMap { forgedOperationGroup =>
          signOperationGroup(forgedOperationGroup, keyStore).flatMap { signedOpGroup =>
            computeOperationHash(signedOpGroup).flatMap { operationGroupHash =>
              applyOperation(network, blockHead, operationGroupHash, forgedOperationGroup, signedOpGroup).flatMap { accounts =>
                injectOperation(network, signedOpGroup).flatMap{ operation =>
                  logger.info(operation)
                  Try(OperationResult(accounts, operation))
                }
              }
            }
          }
        }
      }
    }
  }

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
    val revealMap: Map[String, Any] = Map(
      "kind"        -> "reveal",
      "public_key"  -> keyStore.publicKey
    )
    val operations = transactionMap :: Nil
    sendOperation(network, operations, keyStore, Some(fee))
  }

  /**
    * Helper for generating hex nonces.
    * Will be generalized in the future and moved to an appropriate package.
    * @return Hex nonce
    */
  private def generateHexNonce() : String = {
    val random = new scala.util.Random
    val alphabet = "0123456789abcedf"
    Stream.continually(random.nextInt(alphabet.length)).map(alphabet).take(32).mkString
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
