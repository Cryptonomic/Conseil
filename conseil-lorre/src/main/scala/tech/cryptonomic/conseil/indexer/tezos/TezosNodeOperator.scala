package tech.cryptonomic.conseil.indexer.tezos

import cats.instances.future._
import cats.syntax.applicative._
import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.fetch
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.io.Logging.ConseilLogSupport
import tech.cryptonomic.conseil.common.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.common.util.JsonUtil.{fromJson, JsonString => JS}
import tech.cryptonomic.conseil.common.util.{CryptoUtil, JsonUtil}
import tech.cryptonomic.conseil.indexer.config.{BatchFetchConfiguration, SodiumConfiguration}
import tech.cryptonomic.conseil.indexer.tezos.michelson.JsonToMichelson.toMichelsonScript
import tech.cryptonomic.conseil.indexer.tezos.michelson.dto.{MichelsonInstruction, MichelsonSchema}

import scala.concurrent.{ExecutionContext, Future}
import scala.collection.immutable.NumericRange
import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

private[tezos] object TezosNodeOperator {
  type LazyPages[T] = Iterator[Future[T]]
  type Paginated[T, N] = (LazyPages[T], N)

  /** Process every single page of results by converting the content with a custom function.
    * The returned value will be the content of the paginated results.
    */
  def mapByPage[T](pages: LazyPages[T])(pageMapper: T => T)(implicit ec: ExecutionContext): LazyPages[T] =
    pages.map(_.map(pageMapper))

  /** Process every single page of results by converting the content with a custom asynchronous function.
    * The returned value will be the content of the paginated results.
    */
  def mapAsyncByPage[T](pages: LazyPages[T])(pageMapper: T => Future[T])(implicit ec: ExecutionContext): LazyPages[T] =
    pages.map(_.flatMap(pageMapper))

  /** Process every single page of results by converting the content with a custom asynchronous function.
    *  In this case the mapping is applied independently for every item of the page.
    * The returned value will be the content of the paginated results.
    */
  def mapAsyncByPageItem[T](
      pages: LazyPages[List[T]]
  )(pageItemMapper: T => Future[T])(implicit ec: ExecutionContext): LazyPages[List[T]] =
    mapAsyncByPage(pages) { accountsResults =>
      Future.traverse(accountsResults)(pageItemMapper)
    }

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

  val isGenesis = (data: BlockData) => data.header.level == 0

}

/**
  * Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database.
  *
  * @param node               Tezos node connection object
  * @param network            Which Tezos network to go against
  * @param batchConf          configuration for batched download of node data
  * @param fetchFutureContext thread context for async operations
  */
private[tezos] class TezosNodeOperator(
    val node: TezosRPCInterface,
    val network: String,
    batchConf: BatchFetchConfiguration
)(
    implicit val fetchFutureContext: ExecutionContext
) extends TezosBlocksDataFetchers
    with AccountsDataFetchers
    with ConseilLogSupport {
  import TezosNodeOperator.{isGenesis, LazyPages, Paginated}
  import batchConf.{accountConcurrencyLevel, blockOperationsConcurrencyLevel, blockPageSize}

  override val fetchConcurrency = blockOperationsConcurrencyLevel
  override val accountsFetchConcurrency = accountConcurrencyLevel

  //use this alias to make signatures easier to read and keep in-sync
  type BlockFetchingResults = List[(Block, List[AccountId])]
  type AccountFetchingResults = List[BlockTagged[Map[AccountId, Account]]]
  type DelegateFetchingResults = List[BlockTagged[Map[PublicKeyHash, Delegate]]]
  type PaginatedBlocksResults = Paginated[BlockFetchingResults, Long]
  type PaginatedAccountResults = Paginated[AccountFetchingResults, Long]
  type PaginatedDelegateResults = Paginated[DelegateFetchingResults, Long]

  //introduced to simplify signatures
  type BallotsByBlock = (Block, List[Voting.Ballot])
  type BakerRollsByBlock = (Block, List[Voting.BakerRolls])
  type BallotCountsByBlock = (Block, Option[Voting.BallotCounts])

  /**
    * Generic fetch for paginated data relative to a specific block.
    * Will return back the entities indexed by their unique key
    * from those passed as inputs, through the use of the
    * `entityLoad` function, customized per each entity type.
    * e.g. we use it to load accounts and delegates without
    * duplicating any logic.
    */
  def getPaginatedEntitiesForBlock[Key, Entity](
      entityLoad: (List[Key], TezosBlockHash) => Future[Map[Key, Entity]]
  )(
      keyIndex: Map[Key, TezosBlockHash]
  ): LazyPages[(TezosBlockHash, Map[Key, Entity])] = {
    //collect by hash and paginate based on that
    val reversedIndex =
      keyIndex.groupBy { case (key, blockHash) => blockHash }
        .mapValues(_.keySet.toList)

    reversedIndex.keysIterator.map { blockHash =>
      val keys = reversedIndex.getOrElse(blockHash, List.empty)
      entityLoad(keys, blockHash).map(blockHash -> _)
    }
  }

  /**
    * Fetches a specific account for a given block.
    * @param blockHash  Hash of given block
    * @param accountId  Account ID
    * @return           The account
    */
  def getAccountForBlock(blockHash: TezosBlockHash, accountId: AccountId): Future[Account] = {
    import TezosJsonDecoders.Circe.Accounts._
    node
      .runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts/${accountId.value}")
      .flatMap(result => Future.fromTry(fromJson[Account](result)))

  }

  /**
    * Fetches baking rights for given block
    * @return             Baking rights
    */
  def getBatchBakingRights(
      blockHashesWithCycleAndGovernancePeriod: List[RightsFetchKey]
  ): Future[Map[RightsFetchKey, List[BakingRights]]] = {
    /* implicitly uses: TezosBlocksDataFetchers.bakingRightsFetcher  */
    import cats.instances.future._
    import cats.instances.list._
    fetch[RightsFetchKey, List[BakingRights], Future, List, Throwable]
      .run(blockHashesWithCycleAndGovernancePeriod)
      .map(_.toMap)
  }

  /**
    * Fetches baking rights for given block level
    * @param blockLevels  Block levels
    * @return             Baking rights
    */
  def getBatchBakingRightsByLevels(blockLevels: List[BlockLevel]): Future[Map[BlockLevel, List[BakingRights]]] = {
    /* implicitly uses: TezosBlocksDataFetchers.futureBakingRightsFetcher */
    import cats.instances.future._
    import cats.instances.list._
    fetch[BlockLevel, List[BakingRights], Future, List, Throwable].run(blockLevels).map(_.toMap)
  }

  /**
    * Fetches endorsing rights for given block level
    * @param blockLevels  Block levels
    * @return             Endorsing rights
    */
  def getBatchEndorsingRightsByLevel(blockLevels: List[BlockLevel]): Future[Map[BlockLevel, List[EndorsingRights]]] = {
    /* implicitly uses: TezosBlocksDataFetchers.futureEndorsingRightsFetcher */
    import cats.instances.future._
    import cats.instances.list._
    fetch[BlockLevel, List[EndorsingRights], Future, List, Throwable].run(blockLevels).map(_.toMap)
  }

  /**
    * Fetches endorsing rights for given block
    * @return             Endorsing rights
    */
  def getBatchEndorsingRights(
      blockHashesWithCycleAndGovernancePeriod: List[RightsFetchKey]
  ): Future[Map[RightsFetchKey, List[EndorsingRights]]] = {
    /* implicitly uses: TezosBlocksDataFetchers.endorsingRightsFetcher */
    import cats.instances.future._
    import cats.instances.list._
    fetch[RightsFetchKey, List[EndorsingRights], Future, List, Throwable]
      .run(blockHashesWithCycleAndGovernancePeriod)
      .map(_.toMap)
  }

  /**
    * Fetches the manager of a specific account for a given block.
    * @param blockHash  Hash of given block
    * @param accountId  Account ID
    * @return           The account
    */
  def getAccountManagerForBlock(blockHash: TezosBlockHash, accountId: AccountId): Future[ManagerKey] = {
    import TezosJsonDecoders.Circe.Accounts._
    node
      .runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts/${accountId.value}/manager_key")
      .flatMap(result => Future.fromTry(fromJson[ManagerKey](result)))

  }

  /**
    * Fetches all accounts for a given block.
    * @param blockHash  Hash of given block.
    * @return           Accounts
    */
  def getAllAccountsForBlock(blockHash: TezosBlockHash): Future[Map[AccountId, Account]] =
    for {
      jsonEncodedAccounts <- node.runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts")
      accountIds <- Future.fromTry(fromJson[List[String]](jsonEncodedAccounts).map(_.map(makeAccountId)))
      accounts <- getAccountsForBlock(accountIds, blockHash)
    } yield accounts

  /**
    * Fetches the accounts identified by id, lazily paginating the results
    *
    * @return               the pages of accounts wrapped in a [[Future]], indexed by AccountId
    */
  val getPaginatedAccountsForBlock: Map[AccountId, TezosBlockHash] => LazyPages[
    (TezosBlockHash, Map[AccountId, Account])
  ] =
    getPaginatedEntitiesForBlock(getAccountsForBlock)

  /**
    * Fetches the accounts identified by id
    *
    * @param accountIds the ids
    * @param blockHash  the block storing the accounts, the head block if not specified
    * @return           the list of accounts wrapped in a [[Future]], indexed by AccountId
    */
  def getAccountsForBlock(accountIds: List[AccountId], blockHash: TezosBlockHash): Future[Map[AccountId, Account]] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.fetch
    import tech.cryptonomic.conseil.common.tezos.TezosOptics.Accounts.{whenAccountCode, whenAccountStorage}

    implicit val fetcherInstance = accountFetcher(blockHash)

    /* implicitly uses: AccountsDataFetchers.accountFetcher*/
    val fetchedAccounts: Future[List[(AccountId, Option[Account])]] =
      fetch[AccountId, Option[Account], Future, List, Throwable].run(accountIds)

    def parseMichelsonScripts: Account => Account = {
      implicit lazy val _ = logger
      val scriptAlter = whenAccountCode.modify(toMichelsonScript[MichelsonSchema])
      val storageAlter = whenAccountStorage.modify(toMichelsonScript[MichelsonInstruction])

      scriptAlter compose storageAlter
    }

    fetchedAccounts.map(
      indexedAccounts =>
        indexedAccounts.collect {
          case (accountId, Some(account)) => accountId -> parseMichelsonScripts(account)
        }.toMap
    )
  }

  /**
    * Get accounts for all the identifiers passed-in with the corresponding block
    * @param accountsBlocksIndex a map from unique id to the [latest] block reference
    * @return         Accounts with their corresponding block data
    */
  def getAccountsForBlocks(accountsBlocksIndex: Map[AccountId, BlockReference]): PaginatedAccountResults = {
    import tech.cryptonomic.conseil.common.tezos.TezosTypes.Syntax._

    val reverseIndex =
      accountsBlocksIndex.groupBy { case (id, ref) => ref.hash }
        .mapValues(_.keySet)
        .toMap

    def notifyAnyLostIds(missing: Set[AccountId]) =
      if (missing.nonEmpty)
        logger.warn(
          s"The following account keys were not found querying the $network node: ${missing.map(_.value).mkString("\n", ",", "\n")}"
        )

    //uses the index to collect together BlockAccounts matching the same block
    def groupByLatestBlock(data: Map[AccountId, Account]): List[BlockTagged[Map[AccountId, Account]]] =
      data.groupBy {
        case (id, _) => accountsBlocksIndex(id)
      }.map {
        case (blockReference, accounts) =>
          accounts.taggedWithBlock(blockReference)
      }.toList

    //fetch accounts by requested ids and group them together with corresponding blocks
    val pages = getPaginatedAccountsForBlock(accountsBlocksIndex.mapValues(_.hash)) map { futureMap =>
          futureMap.andThen {
            case Success((hash, accountsMap)) =>
              val searchedFor = reverseIndex.getOrElse(hash, Set.empty)
              notifyAnyLostIds(searchedFor -- accountsMap.keySet)
            case Failure(err) =>
              val showSomeIds = accountsBlocksIndex.keys
                .take(30)
                .map(_.value)
                .mkString("", ",", if (accountsBlocksIndex.size > 30) "..." else "")
              logger.error(s"Could not get accounts' data for ids ${showSomeIds}", err)
          }.map {
            case (_, map) => groupByLatestBlock(map)
          }
        }

    (pages, accountsBlocksIndex.size)
  }

  /**
    * Fetches operations for a block, without waiting for the result
    * @return          The `Future` list of operations
    */
  def getAllOperationsForBlock(block: BlockData): Future[List[OperationsGroup]] = {
    import TezosJsonDecoders.Circe.Operations._
    import TezosJsonDecoders.Circe.decodeLiftingTo
    import tech.cryptonomic.conseil.common.util.JsonUtil.adaptManagerPubkeyField

    //parse json, and try to convert to objects, converting failures to a failed `Future`
    //we could later improve by "accumulating" all errors in a single failed future, with `decodeAccumulating`
    def decodeOperations(json: String) =
      decodeLiftingTo[Future, List[List[OperationsGroup]]](adaptManagerPubkeyField(JS.sanitize(json)))
        .map(_.flatten)

    if (isGenesis(block))
      Future.successful(List.empty) //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
    else
      node
        .runAsyncGetQuery(network, s"blocks/${block.hash.value}/operations")
        .flatMap(decodeOperations)

  }

  /** Fetches votes information for the block */
  def getCurrentVotesForBlock(block: BlockData, offset: Option[Offset] = None): Future[CurrentVotes] =
    if (isGenesis(block))
      CurrentVotes.empty.pure
    else {
      import TezosJsonDecoders.Circe._
      import cats.syntax.apply._

      val offsetString = offset.map(_.toString).getOrElse("")
      val hashString = block.hash.value

      val fetchCurrentQuorum =
        node.runAsyncGetQuery(network, s"blocks/$hashString~$offsetString/votes/current_quorum") flatMap { json =>
            val nonEmptyJson = if (json.isEmpty) "0" else json
            decodeLiftingTo[Future, Option[Int]](nonEmptyJson)
          }

      val fetchCurrentProposal =
        node.runAsyncGetQuery(network, s"blocks/$hashString~$offsetString/votes/current_proposal") flatMap { json =>
            val nonEmptyJson = if (json.isEmpty) """ "3M" """.trim else json
            decodeLiftingTo[Future, Option[ProtocolId]](nonEmptyJson)
          }

      (fetchCurrentQuorum, fetchCurrentProposal).mapN(CurrentVotes.apply)
    }

  /** Fetches any active proposal id for the given blocks.
    * The returned value, when available for active voting periods, is the ID of
    * a proposal, though for the sake of naming consistency with tezos schema
    * we refer to that as a protocol ID.
    */
  def getActiveProposals(blocks: List[BlockData]): Future[List[(TezosBlockHash, Option[ProtocolId])]] = {
    /* implicitly uses: TezosBlocksDataFetchers.currentProposalFetcher */
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.fetch

    val nonGenesisHashes = blocks.filterNot(isGenesis).map(_.hash)
    fetch[TezosBlockHash, Option[ProtocolId], Future, List, Throwable].run(nonGenesisHashes)
  }

  /** Fetches rolls for all bakers of individual blocks
    *
    * @param blockHashes defines the blocks we want the rolls for
    * @return the lists of rolls, for each individual requested hash
    */
  def getBakerRollsForBlockHashes(
      blockHashes: List[TezosBlockHash]
  ): Future[List[(TezosBlockHash, List[Voting.BakerRolls])]] = {
    /* implicitly uses: TezosBlocksDataFetchers.bakersRollsFetcher */
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.fetch

    fetch[TezosBlockHash, List[Voting.BakerRolls], Future, List, Throwable].run(blockHashes)
  }

  /** Fetches rolls for all bakers of individual blocks
    *
    * @param blocks the blocks we want the rolls for
    * @return the lists of rolls, for each individual requested block
    */
  def getBakerRollsForBlocks(blocks: List[Block]): Future[List[BakerRollsByBlock]] = {

    /* given a hash-keyed pair, restores the full block information, searching in the input */
    def adaptResults[A](hashKeyedValue: (TezosBlockHash, A)) = {
      val (hash, a) = hashKeyedValue
      blocks
        .find(_.data.hash == hash)
        .map(block => block -> a)
        .toList
    }

    val nonGenesisHashes =
      blocks
        .map(_.data)
        .filterNot(isGenesis)
        .map(_.hash)

    //we re-use the hash-based query, and then restore the block information
    getBakerRollsForBlockHashes(nonGenesisHashes).map(
      _.flatMap(adaptResults)
    )

  }

  /** Fetches detailed data for voting associated to the passed-in blocks */
  def getVotes(blocks: List[Block]): Future[List[BallotsByBlock]] = {
    /* implicitly uses: TezosBlocksDataFetchers.ballotsFetcher */
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.fetch

    val sortedBlocks = blocks.sortBy(_.data.header.level)

    val fetchBallots =
      fetch[Block, List[Voting.Ballot], Future, List, Throwable]

    fetchBallots.run(sortedBlocks.filterNot(b => isGenesis(b.data)))

  }

  /** Fetches active delegates from node
    * We get the situation for a block identified by the input hash.
    * Any failure in fetching will result in a empty result.
    */
  def fetchActiveBakers(blockHash: TezosBlockHash): Future[Option[(TezosBlockHash, List[AccountId])]] = {
    /* implicitly uses: AccountsDataFetchers.activeDelegateFetcher */
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.fetchOne

    //we consider it might fail for, i.e., the genesis block, so we simply fallback to an empty result
    fetchOne[TezosBlockHash, List[AccountId], Future, List, Throwable]
      .run(blockHash)
      .recover {
        case NonFatal(error) => None
      }
  }

  //move it to the node operator
  def getBakersForBlocks(keysBlocksIndex: Map[PublicKeyHash, BlockReference]): PaginatedDelegateResults = {
    import tech.cryptonomic.conseil.common.tezos.TezosTypes.Syntax._

    val reverseIndex =
      keysBlocksIndex.groupBy { case (pkh, ref) => ref.hash }
        .mapValues(_.keySet)
        .toMap

    def notifyAnyLostKeys(missing: Set[PublicKeyHash]) =
      if (missing.nonEmpty)
        logger.warn(
          s"The following delegate keys were not found querying the $network node: ${missing.map(_.value).mkString("\n", ",", "\n")}"
        )

    //uses the index to collect together BlockAccounts matching the same block
    def groupByLatestBlock(data: Map[PublicKeyHash, Delegate]): List[BlockTagged[Map[PublicKeyHash, Delegate]]] =
      data.groupBy {
        case (pkh, _) => keysBlocksIndex(pkh)
      }.map {
        case (blockReference, delegates) =>
          delegates.taggedWithBlock(blockReference)
      }.toList

    //fetch delegates by requested pkh and group them together with corresponding blocks
    val pages = getPaginatedDelegatesForBlock(keysBlocksIndex.mapValues(_.hash)) map { futureMap =>
          futureMap.andThen {
            case Success((hash, delegatesMap)) =>
              val searchedFor = reverseIndex.getOrElse(hash, Set.empty)
              notifyAnyLostKeys(searchedFor -- delegatesMap.keySet)
            case Failure(err) =>
              val showSomeIds = keysBlocksIndex.keys
                .take(30)
                .map(_.value)
                .mkString("", ",", if (keysBlocksIndex.size > 30) "..." else "")
              logger.error(s"Could not get delegates' data for key hashes ${showSomeIds}", err)
          }.map {
            case (_, map) => groupByLatestBlock(map)
          }
        }

    (pages, keysBlocksIndex.size)
  }

  /**
    * Fetches the delegates bakers identified by key hash, lazily paginating the results
    *
    * @return               the pages of delegates wrapped in a [[Future]], indexed by PublicKeyHash
    */
  val getPaginatedDelegatesForBlock: Map[PublicKeyHash, TezosBlockHash] => LazyPages[
    (TezosBlockHash, Map[PublicKeyHash, Delegate])
  ] =
    getPaginatedEntitiesForBlock(getDelegatesForBlock)

  /**
    * Fetches the delegate and delegated contracts identified by key hash
    *
    * @param pkhs the delegate key hashes
    * @param blockHash  the block storing the delegation reference, the head block if not specified
    * @return           the list of delegates and associated contracts, wrapped in a [[Future]], indexed by key hash
    */
  def getDelegatesForBlock(
      pkhs: List[PublicKeyHash],
      blockHash: TezosBlockHash = blockHeadHash
  ): Future[Map[PublicKeyHash, Delegate]] = {
    /* implicitly uses: AccountsDataFetchers.delegateFetcher */
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.fetch

    implicit val fetcherInstance = delegateFetcher(blockHash)

    val fetchedDelegates: Future[List[(PublicKeyHash, Option[Delegate])]] =
      fetch[PublicKeyHash, Option[Delegate], Future, List, Throwable].run(pkhs)

    fetchedDelegates.map(
      indexedDelegates =>
        indexedDelegates.collect {
          case (pkh, Some(delegate)) => pkh -> delegate
        }.toMap
    )
  }

  /**
    * Fetches a single block along with associated data from the chain, without waiting for the result
    * @param hash      Hash of the block
    * @return          the block data wrapped in a `Future`
    */
  def getBlock(hash: TezosBlockHash, offset: Option[Offset] = None): Future[Block] = {
    import TezosJsonDecoders.Circe.Blocks._
    import TezosJsonDecoders.Circe.decodeLiftingTo

    val offsetString = offset.map(_.toString).getOrElse("")

    //starts immediately
    val fetchBlock =
      node.runAsyncGetQuery(network, s"blocks/${hash.value}~$offsetString") flatMap { json =>
          decodeLiftingTo[Future, BlockData](JS.sanitize(json))
        }

    for {
      block <- fetchBlock
      ops <- getAllOperationsForBlock(block)
      votes <- getCurrentVotesForBlock(block)
    } yield Block(block, ops, votes)
  }

  /**
    * Fetches a single block from the chain without associated data, without waiting for the result
    * @param hash      Hash of the block
    * @return          the block data wrapped in a `Future`
    */
  def getBareBlock(hash: TezosBlockHash, offset: Option[Offset] = None): Future[BlockData] = {
    import TezosJsonDecoders.Circe.Blocks._
    import TezosJsonDecoders.Circe.decodeLiftingTo

    val offsetString = offset.map(_.toString).getOrElse("")

    node.runAsyncGetQuery(network, s"blocks/${hash.value}~$offsetString") flatMap { json =>
      decodeLiftingTo[Future, BlockData](JS.sanitize(json))
    }
  }

  /**
    * Gets the block head along with associated data.
    * @return Block head
    */
  def getBlockHead(): Future[Block] =
    getBlock(blockHeadHash)

  /**
    * Gets just the block head without associated data.
    * @return Block head
    */
  def getBareBlockHead(): Future[BlockData] =
    getBareBlock(blockHeadHash)

  /**
    * Given a level range, creates sub-ranges of max the given size
    * @param levels a range of levels to partition into (possibly) smaller parts
    * @return an iterator over the part, which are themselves `Ranges`
    */
  def partitionLevelsRange(levels: NumericRange.Inclusive[BlockLevel]): Iterator[NumericRange.Inclusive[BlockLevel]] =
    levels
      .grouped(blockPageSize)
      .filterNot(_.isEmpty)
      .map(subRange => subRange.head to subRange.last)

  /**
    * Gets all blocks from the head down to the oldest block not already in the database.
    *
    * @param maxIndexedLevel the highest block level already indexed
    * @return Blocks and Account hashes involved
    */
  def getBlocksNotInDatabase(maxIndexedLevel: BlockLevel): Future[PaginatedBlocksResults] =
    for {
      blockHead <- getBlockHead()
      headLevel = blockHead.data.header.level
      headHash = blockHead.data.hash
    } yield {
      val bootstrapping = maxIndexedLevel == -1
      if (maxIndexedLevel < headLevel) {
        //got something to load
        if (bootstrapping)
          logger.warn("There were apparently no blocks in the database. Downloading the whole chain...")
        else
          logger.info(
            s"I found the new block head at level $headLevel, the currently stored max is $maxIndexedLevel. I'll fetch the missing ${headLevel - maxIndexedLevel} blocks."
          )
        val paginatedResults = partitionLevelsRange((maxIndexedLevel + 1) to headLevel).map(
          page => getBlocks((headHash, headLevel), page)
        )
        val minLevel = if (bootstrapping) 1L else maxIndexedLevel
        (paginatedResults, headLevel - minLevel)
      } else {
        logger.info("No new blocks to fetch from the network")
        (Iterator.empty, 0)
      }
    }

  /**
    * Gets last `depth` blocks.
    * @param depth      Number of latest block to fetch, `None` to get all
    * @param headHash   Hash of a block from which to start, None to start from a real head
    * @return           Blocks and Account hashes involved
    */
  def getLatestBlocks(
      depth: Option[Int] = None,
      headHash: Option[TezosBlockHash] = None,
      maxIndexedLevel: Option[BlockLevel] = None
  ): Future[PaginatedBlocksResults] =
    headHash
      .map(getBlock(_))
      .getOrElse(getBlockHead())
      .map { maxHead =>
        val headLevel = maxHead.data.header.level
        val headHash = maxHead.data.hash
        val minLevel =
          depth.map(d => math.max(headLevel - d, maxIndexedLevel.getOrElse(1L)) + 1).filter(_ >= 1).getOrElse(1L)
        val paginatedResults = partitionLevelsRange(minLevel to headLevel).map(
          page => getBlocks((headHash, headLevel), page)
        )
        (paginatedResults, headLevel - minLevel + 1)
      }

  /**
    * Gets block from Tezos Blockchains, as well as their associated operation, from minLevel to maxLevel.
    * @param reference Hash and level of a known block
    * @param levelRange a range of levels to load
    * @return the async list of blocks with relative account ids touched in the operations
    */
  private def getBlocks(
      reference: (TezosBlockHash, BlockLevel),
      levelRange: NumericRange.Inclusive[BlockLevel]
  ): Future[BlockFetchingResults] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.common.generic.chain.DataFetcher.{fetch, fetchMerge}
    import tech.cryptonomic.conseil.common.tezos.TezosOptics.Blocks._

    logger.info(s"Fetching block data in range: $levelRange")

    val (hashRef, levelRef) = reference
    require(levelRange.start >= 0 && levelRange.end <= levelRef)
    val offsets = levelRange.map(lvl => levelRef - lvl).toList

    implicit val blockFetcher = blocksFetcher(hashRef)

    //read the separate parts of voting and merge the results
    val proposalsStateFetch =
      fetchMerge(currentQuorumFetcher, currentProposalFetcher)(CurrentVotes.apply)

    def parseMichelsonScripts: Block => Block = {
      implicit lazy val _ = logger
      val copyParametersToMicheline = (t: Transaction) => t.copy(parameters_micheline = t.parameters)
      val copyInternalParametersToMicheline = (t: InternalOperationResults.Transaction) =>
        t.copy(parameters_micheline = t.parameters)

      val copyOriginationToMicheline = (o: Origination) =>
        o.copy(script = o.script.map(_.copy(storage_micheline = o.script.map(_.storage))))

      val setOriginationMicheline = acrossOriginations.modify(copyOriginationToMicheline)
      val codeAlter = acrossScriptsCode.modify(toMichelsonScript[MichelsonInstruction])
      val storageAlter = acrossScriptsStorage.modify(toMichelsonScript[MichelsonInstruction])

      val setUnparsedMicheline = acrossTransactions.modify(copyParametersToMicheline)
      //TODO focus the lens on the optional field and empty it if the conversion fails
      //we have a copy anyway in the parameters_micheline
      val parametersAlter = acrossTransactionParameters.modify(toMichelsonScript[MichelsonInstruction])

      val setUnparsedMichelineToInternals = acrossInternalTransactions.modify(copyInternalParametersToMicheline)
      //TODO focus the lens on the optional field and empty it if the conversion fails
      //we have a copy anyway in the parameters_micheline
      val parametersAlterToInternals = acrossInternalParameters.modify(toMichelsonScript[MichelsonInstruction])

      //each operation will convert a block to an updated version of block, therefore compose each transformation with the next

      setOriginationMicheline andThen codeAlter andThen
        storageAlter andThen
        setUnparsedMicheline andThen
        parametersAlter andThen
        setUnparsedMichelineToInternals andThen
        parametersAlterToInternals
    }

    def extractAccountIds(blockData: BlockData): List[AccountId] =
      for {
        blockHeaderMetadata <- discardGenesis(blockData.metadata).toList
        balanceUpdate <- blockHeaderMetadata.balance_updates
        id <- balanceUpdate.contract.map(_.id).toList ::: balanceUpdate.delegate.map(_.value).toList
      } yield makeAccountId(id)

    //Gets blocks data for the requested offsets and associates the operations and account hashes available involved in said operations
    //Special care is taken for the genesis block (level = 0) that doesn't have operations defined, we use empty data for it
    /* implicitly uses:
     * - blocksFetcher
     * - operationsWithAccountsFetcher
     */
    for {
      fetchedBlocksData <- fetch[Offset, BlockData, Future, List, Throwable].run(offsets)
      blockHashes = fetchedBlocksData.collect { case (offset, block) if !isGenesis(block) => block.hash }
      fetchedOperationsWithAccounts <- fetch[
        TezosBlockHash,
        (List[OperationsGroup], List[AccountId]),
        Future,
        List,
        Throwable
      ].run(blockHashes)
      proposalsState <- proposalsStateFetch.run(blockHashes)
    } yield {
      val operationalDataMap = fetchedOperationsWithAccounts.toMap
      val proposalsMap = proposalsState.toMap
      fetchedBlocksData.map {
        case (offset, md) =>
          val (ops, accs) = if (isGenesis(md)) (List.empty, List.empty) else operationalDataMap(md.hash)
          val votes = proposalsMap.getOrElse(md.hash, CurrentVotes.empty)
          (parseMichelsonScripts(Block(md, ops, votes)), (accs ::: extractAccountIds(md)).distinct)
      }
    }
  }

  /** Gets map contents from the chain, using a generic Json representation, since we don't know
    * exactly what is stored there, if anything
    *
    * @param hash the block reference to look inside the map
    * @param mapId which map
    * @param mapKeyHash we need to use a hashed b58check representation of the key to find the value
    * @return async json value of the content if all went correct
    */
  def getBigMapContents(hash: TezosBlockHash, mapId: BigDecimal, mapKeyHash: ScriptId): Future[JS] =
    node
      .runAsyncGetQuery(network, s"blocks/${hash.value}/context/big_maps/$mapId/${mapKeyHash.value}")
      .flatMap(result => Future.fromTry(JS.fromString(JS.sanitize(result))))

}

/**
  * Adds more specific API functionalities to perform on a tezos node, in particular those involving write and cryptographic operations
  */
class TezosNodeSenderOperator(
    override val node: TezosRPCInterface,
    network: String,
    batchConf: BatchFetchConfiguration,
    sodiumConf: SodiumConfiguration
)(implicit executionContext: ExecutionContext)
    extends TezosNodeOperator(node, network, batchConf)
    with ConseilLogSupport {
  import TezosNodeOperator._
  import TezosJsonDecoders.Circe.Operations._
  import com.muquit.libsodiumjna.{SodiumKeyPair, SodiumLibrary, SodiumUtils}
  /* Check out the wiki page on using shapeless for details on the following types
   * Short survey
   * HList is short-hand for Heterogeneus List, a fancy name for a dynamically sized tuple, but
   * defined as a recursive structure, just like a linked list.
   * Many type classes and code generation can be based on HList at compile-time, making use
   * of its recursive definition, and the fact that each element has a known type.
   * Record is a specific type of HList where each element of the tuple is "labelled" with an extra
   * type which mirrors a field name. The Record syntax builds a complex HList of Key-Values, where
   * the key is actually encoded statically in the type.
   * The ReprObjectEncoder can recursively build a json encoder for any Record type
   */
  import shapeless.HList
  import shapeless.record.Record
  import io.circe.generic.encoding.ReprAsObjectEncoder._

  //used in subsequent operations using Sodium
  SodiumLibrary.setLibraryPath(sodiumConf.libraryPath)

  /**
    * Appends a key reveal operation to an operation group if needed.
    * The input operations can be built using the [[Record]] syntax, which
    * generates a specifically structured [[HList]].
    * @param operations The operations being forged as part of this operation group
    * @param managerKey The sending account's manager information
    * @param keyStore   Key pair along with public key hash
    * @return           Operation group enriched with a key reveal if necessary
    */
  def handleKeyRevealForOperations(
      operations: List[HList],
      managerKey: ManagerKey,
      keyStore: KeyStore
  ): List[HList] =
    managerKey.key match {
      case Some(_) => operations
      case None =>
        val revealMap = Record(
          kind = "reveal",
          public_key = keyStore.publicKey
        )
        revealMap :: operations
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
      watermark = "03" // In the future, we must support "0x02" for endorsements and "0x01" for block signing.
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
    Try(SodiumLibrary.cryptoGenerichash(signedOpGroup.bytes, 32)).flatMap { hash =>
      CryptoUtil.base58CheckEncode(hash.toList, "op")
    }

  /**
    * Applies an operation using the Tezos RPC client.
    * @param blockHead            Block head
    * @param operationGroupHash   Hash of the operation group being applied (in Base58Check format)
    * @param forgedOperationGroup Forged operation group returned by the Tezos client (as a hex string)
    * @param signedOpGroup        Signed operation group
    * @return                     Array of contract handles
    */
  def applyOperation(
      blockHead: Block,
      operationGroupHash: String,
      forgedOperationGroup: String,
      signedOpGroup: SignedOperationGroup
  ): Future[AppliedOperation] = {
    val payload = Record(
      pred_block = blockHead.data.header.predecessor.value,
      operation_hash = operationGroupHash,
      forged_operation = forgedOperationGroup,
      signature = signedOpGroup.signature
    )
    node
      .runAsyncPostQuery(network, "/blocks/head/proto/helpers/apply_operation", Some(JsonUtil.toJson(payload)))
      .flatMap { result =>
        logger.debug(s"Result of operation application: $result")
        Future.fromTry(JsonUtil.fromJson[AppliedOperation](result))
      }
  }

  /**
    * Injects an opertion using the Tezos RPC client.
    * @param signedOpGroup  Signed operation group
    * @return               ID of injected operation
    */
  def injectOperation(signedOpGroup: SignedOperationGroup): Future[String] = {
    val payload = Record(signedOperationContents = signedOpGroup.bytes.map("%02X" format _).mkString)
    node
      .runAsyncPostQuery(network, "/inject_operation", Some(JsonUtil.toJson(payload)))
      .flatMap(result => Future.fromTry(fromJson[InjectedOperation](result)))
      .map(_.injectedOperation)
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
