package tech.cryptonomic.conseil.tezos

import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.util.{CryptoUtil, JsonUtil}
import tech.cryptonomic.conseil.util.CryptoUtil.KeyStore
import tech.cryptonomic.conseil.util.JsonUtil.{fromJson, JsonString => JS}
import tech.cryptonomic.conseil.config.{BatchFetchConfiguration, SodiumConfiguration}
import tech.cryptonomic.conseil.tezos.TezosTypes.Lenses._
import tech.cryptonomic.conseil.tezos.michelson.JsonToMichelson.toMichelsonScript
import tech.cryptonomic.conseil.tezos.michelson.dto.{MichelsonInstruction, MichelsonSchema}
import cats.instances.future._
import cats.syntax.applicative._
import tech.cryptonomic.conseil.generic.chain.DataFetcher.fetch
import tech.cryptonomic.conseil.tezos.TezosNodeOperator.FetchRights

import scala.concurrent.{ExecutionContext, Future}
import scala.math.max
import scala.util.{Failure, Success, Try}

object TezosNodeOperator {
  type LazyPages[T] = Iterator[Future[T]]
  type Paginated[T] = (LazyPages[T], Int)

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

  /**
    * Information for fetching baking/endorsing rights
    * @param cycle            block cycle
    * @param governancePeriod governance period
    * @param blockHash        hash of a block
    */
  final case class FetchRights(cycle: Option[Int], governancePeriod: Option[Int], blockHash: Option[BlockHash])

  /**
    * Given a contiguous valus range, creates sub-ranges of max the given size
    * @param pageSize how big a part is allowed to be
    * @param range a range of contiguous values to partition into (possibly) smaller parts
    * @return an iterator over the part, which are themselves `Ranges`
    */
  def partitionRanges(pageSize: Int)(range: Range.Inclusive): Iterator[Range.Inclusive] =
    range
      .grouped(pageSize)
      .filterNot(_.isEmpty)
      .map(subRange => subRange.head to subRange.last)

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
class TezosNodeOperator(
    val node: TezosRPCInterface,
    val network: String,
    batchConf: BatchFetchConfiguration,
    apiOperations: ApiOperations
)(
    implicit val fetchFutureContext: ExecutionContext
) extends LazyLogging
    with BlocksDataFetchers
    with AccountsDataFetchers {
  import TezosNodeOperator.{isGenesis, LazyPages, Paginated}
  import batchConf.{accountConcurrencyLevel, blockOperationsConcurrencyLevel, blockPageSize}

  override val fetchConcurrency = blockOperationsConcurrencyLevel
  override val accountsFetchConcurrency = accountConcurrencyLevel

  //use this alias to make signatures easier to read and keep in-sync
  type BlockFetchingResults = List[(Block, List[AccountId])]
  type AccountFetchingResults = List[BlockTagged[Map[AccountId, Account]]]
  type DelegateFetchingResults = List[BlockTagged[Map[PublicKeyHash, Delegate]]]
  type PaginatedBlocksResults = Paginated[BlockFetchingResults]
  type PaginatedAccountResults = Paginated[AccountFetchingResults]
  type PaginatedDelegateResults = Paginated[DelegateFetchingResults]

  //introduced to simplify signatures
  type BallotBlock = (Block, List[Voting.Ballot])
  type BakerBlock = (Block, List[Voting.BakerRolls])
  type BallotCountsBlock = (Block, Option[Voting.BallotCounts])

  /**
    * Generic fetch for paginated data relative to a specific block.
    * Will return back the entities indexed by their unique key
    * from those passed as inputs, through the use of the
    * `entityLoad` function, customized per each entity type.
    * e.g. we use it to load accounts and delegates without
    * duplicating any logic.
    */
  def getPaginatedEntitiesForBlock[Key, Entity](
      entityLoad: (List[Key], BlockHash) => Future[Map[Key, Entity]]
  )(
      keyIndex: Map[Key, BlockHash]
  ): LazyPages[(BlockHash, Map[Key, Entity])] = {
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
  def getAccountForBlock(blockHash: BlockHash, accountId: AccountId): Future[Account] =
    node
      .runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts/${accountId.id}")
      .map(fromJson[Account])

  /**
    * Fetches baking rights for given block
    * @param blockHashes  Hash of given block
    * @return             Baking rights
    */
  def getBatchBakingRights(
      blockHashesWithCycleAndGovernancePeriod: List[FetchRights]
  ): Future[Map[FetchRights, List[BakingRights]]] = {
    import cats.instances.future._
    import cats.instances.list._
    fetch[FetchRights, List[BakingRights], Future, List, Throwable]
      .run(blockHashesWithCycleAndGovernancePeriod)
      .map(_.toMap)
  }

  /**
    * Fetches baking rights for given block level
    * @param blockLevels  Block levels
    * @return             Baking rights
    */
  def getBatchBakingRightsByLevels(blockLevels: List[Int]): Future[Map[Int, List[BakingRights]]] = {
    import cats.instances.future._
    import cats.instances.list._
    fetch[Int, List[BakingRights], Future, List, Throwable].run(blockLevels).map(_.toMap)
  }

  /**
    * Fetches endorsing rights for given block level
    * @param blockLevels  Block levels
    * @return             Endorsing rights
    */
  def getBatchEndorsingRightsByLevel(blockLevels: List[Int]): Future[Map[Int, List[EndorsingRights]]] = {
    import cats.instances.future._
    import cats.instances.list._
    fetch[Int, List[EndorsingRights], Future, List, Throwable].run(blockLevels).map(_.toMap)
  }

  /**
    * Fetches endorsing rights for given block
    * @param blockHashes  Hash of given block
    * @return             Endorsing rights
    */
  def getBatchEndorsingRights(
      blockHashesWithCycleAndGovernancePeriod: List[FetchRights]
  ): Future[Map[FetchRights, List[EndorsingRights]]] = {
    import cats.instances.future._
    import cats.instances.list._
    fetch[FetchRights, List[EndorsingRights], Future, List, Throwable]
      .run(blockHashesWithCycleAndGovernancePeriod)
      .map(_.toMap)
  }

  /**
    * Fetches the manager of a specific account for a given block.
    * @param blockHash  Hash of given block
    * @param accountId  Account ID
    * @return           The account
    */
  def getAccountManagerForBlock(blockHash: BlockHash, accountId: AccountId): Future[ManagerKey] =
    node
      .runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts/${accountId.id}/manager_key")
      .map(fromJson[ManagerKey])

  /**
    * Fetches all accounts for a given block.
    * @param blockHash  Hash of given block.
    * @return           Accounts
    */
  def getAllAccountsForBlock(blockHash: BlockHash): Future[Map[AccountId, Account]] =
    for {
      jsonEncodedAccounts <- node.runAsyncGetQuery(network, s"blocks/${blockHash.value}/context/contracts")
      accountIds = fromJson[List[String]](jsonEncodedAccounts).map(AccountId)
      accounts <- getAccountsForBlock(accountIds, blockHash)
    } yield accounts

  /**
    * Fetches the accounts identified by id, lazily paginating the results
    *
    * @param delegatesIndex the accountid-to-blockhash index to get data for
    * @return               the pages of accounts wrapped in a [[Future]], indexed by AccountId
    */
  val getPaginatedAccountsForBlock: Map[AccountId, BlockHash] => LazyPages[(BlockHash, Map[AccountId, Account])] =
    getPaginatedEntitiesForBlock(getAccountsForBlock)

  /**
    * Fetches the accounts identified by id
    *
    * @param accountIds the ids
    * @param blockHash  the block storing the accounts, the head block if not specified
    * @return           the list of accounts wrapped in a [[Future]], indexed by AccountId
    */
  def getAccountsForBlock(accountIds: List[AccountId], blockHash: BlockHash): Future[Map[AccountId, Account]] = {
    import cats.instances.future._
    import cats.instances.list._
    import TezosOptics.Accounts.{scriptLens, storageLens}
    import tech.cryptonomic.conseil.generic.chain.DataFetcher.fetch

    implicit val fetcherInstance = accountFetcher(blockHash)

    val fetchedAccounts: Future[List[(AccountId, Option[Account])]] =
      fetch[AccountId, Option[Account], Future, List, Throwable].run(accountIds)

    def parseMichelsonScripts: Account => Account = {
      implicit lazy val _ = logger
      val scriptAlter = scriptLens.modify(toMichelsonScript[MichelsonSchema])
      val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction])

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
    import TezosTypes.Syntax._

    val reverseIndex =
      accountsBlocksIndex.groupBy { case (id, (blockHash, level, timestamp, cycle, period)) => blockHash }
        .mapValues(_.keySet)
        .toMap

    def notifyAnyLostIds(missing: Set[AccountId]) =
      if (missing.nonEmpty)
        logger.warn(
          "The following account keys were not found querying the {} node: {}",
          network,
          missing.map(_.id).mkString("\n", ",", "\n")
        )

    //uses the index to collect together BlockAccounts matching the same block
    def groupByLatestBlock(data: Map[AccountId, Account]): List[BlockTagged[Map[AccountId, Account]]] =
      data.groupBy {
        case (id, _) => accountsBlocksIndex(id)
      }.map {
        case ((hash, level, timestamp, cycle, period), accounts) =>
          accounts.taggedWithBlock(hash, level, timestamp, cycle, period)
      }.toList

    //fetch accounts by requested ids and group them together with corresponding blocks
    val pages = getPaginatedAccountsForBlock(accountsBlocksIndex.mapValues(_._1)) map { futureMap =>
          futureMap.andThen {
            case Success((hash, accountsMap)) =>
              val searchedFor = reverseIndex.getOrElse(hash, Set.empty)
              notifyAnyLostIds(searchedFor -- accountsMap.keySet)
            case Failure(err) =>
              val showSomeIds = accountsBlocksIndex.keys
                .take(30)
                .map(_.id)
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
    * @param blockHash Hash of the block
    * @return          The `Future` list of operations
    */
  def getAllOperationsForBlock(block: BlockData): Future[List[OperationsGroup]] = {
    import JsonDecoders.Circe.decodeLiftingTo
    import JsonDecoders.Circe.Operations._
    import tech.cryptonomic.conseil.util.JsonUtil.adaptManagerPubkeyField

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
      import JsonDecoders.Circe._
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

  /** Fetches proposals for given blocks */
  def getProposals(blocks: List[BlockData]): Future[List[(BlockHash, Option[ProtocolId])]] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.generic.chain.DataFetcher.fetch
    fetch[BlockHash, Option[ProtocolId], Future, List, Throwable].run(blocks.filterNot(b => isGenesis(b)).map(_.hash))
  }

  /** Fetches detailed data for voting associated to the passed-in blocks */
  def getVotingDetails(blocks: List[Block]): Future[List[BakerBlock]] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.generic.chain.DataFetcher.fetch

    val fetchBakers =
      fetch[Block, List[Voting.BakerRolls], Future, List, Throwable]

    fetchBakers.run(blocks.filterNot(b => isGenesis(b.data)))
  }

  /** Fetches detailed data for voting associated to the passed-in blocks */
  def getVotes(blocks: List[Block]): Future[List[BallotBlock]] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.generic.chain.DataFetcher.fetch

    val sortedBlocks = blocks.sortBy(_.data.header.level)

    val fetchBallots =
      fetch[Block, List[Voting.Ballot], Future, List, Throwable]

    fetchBallots.run(sortedBlocks.filterNot(b => isGenesis(b.data)))

  }

  /** Fetches active delegates from node */
  def fetchActiveBakers(blockHashes: List[(Int, BlockHash)]): Future[List[(BlockHash, List[String])]] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.generic.chain.DataFetcher.fetch

    val blockHashesWithoutGenesis = blockHashes.filterNot {
      case (blockLevel, _) => blockLevel == 0
    }.map(_._2)

    fetch[BlockHash, List[String], Future, List, Throwable].run(blockHashesWithoutGenesis)
  }

  /** Fetches detailed data for voting associated to the passed-in blocks */
  def getRolls(blockHashes: List[String]): Future[List[Voting.BakerRolls]] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.generic.chain.DataFetcher.fetch

    fetch[String, List[Voting.BakerRolls], Future, List, Throwable].run(blockHashes).map(_.flatMap(_._2))
  }

  //move it to the node operator
  def getBakersForBlocks(keysBlocksIndex: Map[PublicKeyHash, BlockReference]): PaginatedDelegateResults = {
    import TezosTypes.Syntax._

    val reverseIndex =
      keysBlocksIndex.groupBy { case (pkh, (blockHash, level, timestamp, cycle, period)) => blockHash }
        .mapValues(_.keySet)
        .toMap

    def notifyAnyLostKeys(missing: Set[PublicKeyHash]) =
      if (missing.nonEmpty)
        logger.warn(
          "The following delegate keys were not found querying the {} node: {}",
          network,
          missing.map(_.value).mkString("\n", ",", "\n")
        )

    //uses the index to collect together BlockAccounts matching the same block
    def groupByLatestBlock(data: Map[PublicKeyHash, Delegate]): List[BlockTagged[Map[PublicKeyHash, Delegate]]] =
      data.groupBy {
        case (pkh, _) => keysBlocksIndex(pkh)
      }.map {
        case ((hash, level, timestamp, cycle, period), delegates) =>
          delegates.taggedWithBlock(hash, level, timestamp, cycle, period)
      }.toList

    //fetch delegates by requested pkh and group them together with corresponding blocks
    val pages = getPaginatedDelegatesForBlock(keysBlocksIndex.mapValues(_._1)) map { futureMap =>
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
    * @param delegatesIndex the pkh-to-blockhash index to get data for
    * @return               the pages of delegates wrapped in a [[Future]], indexed by PublicKeyHash
    */
  val getPaginatedDelegatesForBlock: Map[PublicKeyHash, BlockHash] => LazyPages[
    (BlockHash, Map[PublicKeyHash, Delegate])
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
      blockHash: BlockHash = blockHeadHash
  ): Future[Map[PublicKeyHash, Delegate]] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.generic.chain.DataFetcher.fetch

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
  def getBlock(hash: BlockHash, offset: Option[Offset] = None): Future[Block] = {
    import JsonDecoders.Circe.decodeLiftingTo
    import JsonDecoders.Circe.Blocks._

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
  def getBareBlock(hash: BlockHash, offset: Option[Offset] = None): Future[BlockData] = {
    import JsonDecoders.Circe.decodeLiftingTo
    import JsonDecoders.Circe.Blocks._

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
    * @param pageSize how big a part is allowed to be
    * @return an iterator over the part, which are themselves `Ranges`
    */
  def partitionBlocksRanges(levels: Range.Inclusive): Iterator[Range.Inclusive] =
    TezosNodeOperator.partitionRanges(blockPageSize)(levels)

  /**
    * Given a list of generic elements, creates sub-lists of max the given size
    * @param elements a list of elements to partition into (possibly) smaller parts
    * @param pageSize how big a part is allowed to be
    * @return an iterator over the part, which are themselves elements
    */
  def partitionElements[E](elements: List[E]): Iterator[List[E]] =
    TezosNodeOperator.partitionRanges(blockPageSize)(Range.inclusive(0, elements.size - 1)) map { range =>
        elements.take(range.end + 1).drop(range.start)
      }

  /**
    * Gets all blocks from the head down to the oldest block not already in the database.
    * @return Blocks and Account hashes involved
    */
  def getBlocksNotInDatabase(): Future[PaginatedBlocksResults] =
    for {
      maxLevel <- apiOperations.fetchMaxLevel
      blockHead <- getBlockHead()
      headLevel = blockHead.data.header.level
      headHash = blockHead.data.hash
    } yield {
      val bootstrapping = maxLevel == -1
      if (maxLevel < headLevel) {
        //got something to load
        if (bootstrapping) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
        else
          logger.info(
            "I found the new block head at level {}, the currently stored max is {}. I'll fetch the missing {} blocks.",
            headLevel,
            maxLevel,
            headLevel - maxLevel
          )
        val pagedResults = partitionBlocksRanges((maxLevel + 1) to headLevel).map(
          page => getBlocks((headHash, headLevel), page)
        )
        val minLevel = if (bootstrapping) 1 else maxLevel
        (pagedResults, headLevel - minLevel)
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
  def getLatestBlocks(depth: Option[Int] = None, headHash: Option[BlockHash] = None): Future[PaginatedBlocksResults] =
    headHash
      .map(getBlock(_))
      .getOrElse(getBlockHead())
      .map { maxHead =>
        val headLevel = maxHead.data.header.level
        val headHash = maxHead.data.hash
        val minLevel = depth.fold(1)(d => max(1, headLevel - d + 1))
        val pagedResults = partitionBlocksRanges(minLevel to headLevel).map(
          page => getBlocks((headHash, headLevel), page)
        )
        (pagedResults, headLevel - minLevel + 1)
      }

  /**
    * Gets block from Tezos Blockchains, as well as their associated operation, from minLevel to maxLevel.
    * @param reference Hash and level of a known block
    * @param levelRange a range of levels to load
    * @return the async list of blocks with relative account ids touched in the operations
    */
  private def getBlocks(
      reference: (BlockHash, Int),
      levelRange: Range.Inclusive
  ): Future[BlockFetchingResults] = {
    import cats.instances.future._
    import cats.instances.list._
    import tech.cryptonomic.conseil.generic.chain.DataFetcher.{fetch, fetchMerge}

    logger.info("Fetching block data in range: " + levelRange)

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

      val codeAlter = codeLens.modify(toMichelsonScript[MichelsonSchema])
      val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction])

      val setUnparsedMicheline = transactionLens.modify(copyParametersToMicheline)
      //TODO focus the lens on the optional field and empty it if the conversion fails
      //we have a copy anyway in the parameters_micheline
      val parametersAlter = parametersLens.modify(toMichelsonScript[MichelsonInstruction])

      val setUnparsedMichelineToInternals = internalTransationsTraversal.modify(copyInternalParametersToMicheline)
      //TODO focus the lens on the optional field and empty it if the conversion fails
      //we have a copy anyway in the parameters_micheline
      val parametersAlterToInternals = internalParametersLens.modify(toMichelsonScript[MichelsonInstruction])

      //each operation will convert a block to an updated version of block, therefore compose each transformation with the next
      codeAlter andThen
        storageAlter andThen
        setUnparsedMicheline andThen
        parametersAlter andThen
        setUnparsedMichelineToInternals andThen
        parametersAlterToInternals
    }

    def extractAccountIds(blockData: BlockData): List[AccountId] =
      for {
        blockHeaderMetadata <- discardGenesis.lift(blockData.metadata).toList
        balanceUpdate <- blockHeaderMetadata.balance_updates
        id <- balanceUpdate.contract.map(_.id).toList ::: balanceUpdate.delegate.map(_.value).toList
      } yield AccountId(id)

    //Gets blocks data for the requested offsets and associates the operations and account hashes available involved in said operations
    //Special care is taken for the genesis block (level = 0) that doesn't have operations defined, we use empty data for it
    for {
      fetchedBlocksData <- fetch[Offset, BlockData, Future, List, Throwable].run(offsets)
      blockHashes = fetchedBlocksData.collect { case (offset, block) if !isGenesis(block) => block.hash }
      fetchedOperationsWithAccounts <- fetch[
        BlockHash,
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
  def getBigMapContents(hash: BlockHash, mapId: BigDecimal, mapKeyHash: ScriptId): Future[JS] =
    node
      .runAsyncGetQuery(network, s"blocks/${hash.value}/context/big_maps/$mapId/${mapKeyHash.value}")
      .flatMap(result => Future.fromTry(JS.wrapString(JS.sanitize(result))))

}

/**
  * Adds more specific API functionalities to perform on a tezos node, in particular those involving write and cryptographic operations
  */
class TezosNodeSenderOperator(
    override val node: TezosRPCInterface,
    network: String,
    batchConf: BatchFetchConfiguration,
    sodiumConf: SodiumConfiguration,
    apiOperations: ApiOperations
)(implicit executionContext: ExecutionContext)
    extends TezosNodeOperator(node, network, batchConf, apiOperations)
    with LazyLogging {
  import com.muquit.libsodiumjna.{SodiumKeyPair, SodiumLibrary, SodiumUtils}
  import TezosNodeOperator._

  /** Type representing Map[String, Any] */
  type AnyMap = Map[String, Any]

  //used in subsequent operations using Sodium
  SodiumLibrary.setLibraryPath(sodiumConf.libraryPath)

  /**
    * Appends a key reveal operation to an operation group if needed.
    * @param operations The operations being forged as part of this operation group
    * @param managerKey The sending account's manager information
    * @param keyStore   Key pair along with public key hash
    * @return           Operation group enriched with a key reveal if necessary
    */
  def handleKeyRevealForOperations(operations: List[AnyMap], managerKey: ManagerKey, keyStore: KeyStore): List[AnyMap] =
    managerKey.key match {
      case Some(_) => operations
      case None =>
        val revealMap: AnyMap = Map(
          "kind" -> "reveal",
          "public_key" -> keyStore.publicKey
        )
        revealMap :: operations
    }

  /*/**
   * Forge an operation group using the Tezos RPC client.
   * @param blockHead  The block head
   * @param account    The sender's account
   * @param operations The operations being forged as part of this operation group
   * @param keyStore   Key pair along with public key hash
   * @param fee        Fee to be paid
   * @return           Forged operation bytes (as a hex string)
   */
  def forgeOperations(
      blockHead: Block,
      account: Account,
      operations: List[AnyMap],
      keyStore: KeyStore,
      fee: Option[Float]
  ): Future[String] = {
    val payload: AnyMap = fee match {
      case Some(feeAmt) =>
        Map(
          "branch" -> blockHead.data.hash,
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
          "branch" -> blockHead.data.header.predecessor,
          "operations" -> operations
        )
    }
    node
      .runAsyncPostQuery(network, "/blocks/head/proto/helpers/forge/operations", Some(JsonUtil.toJson(payload)))
      .map(json => fromJson[ForgedOperation](json).operation)
  }*/

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
    val payload: AnyMap = Map(
      "pred_block" -> blockHead.data.header.predecessor,
      "operation_hash" -> operationGroupHash,
      "forged_operation" -> forgedOperationGroup,
      "signature" -> signedOpGroup.signature
    )
    node.runAsyncPostQuery(network, "/blocks/head/proto/helpers/apply_operation", Some(JsonUtil.toJson(payload))).map {
      result =>
        logger.debug(s"Result of operation application: $result")
        JsonUtil.fromJson[AppliedOperation](result)
    }
  }

  /**
    * Injects an opertion using the Tezos RPC client.
    * @param signedOpGroup  Signed operation group
    * @return               ID of injected operation
    */
  def injectOperation(signedOpGroup: SignedOperationGroup): Future[String] = {
    val payload: AnyMap = Map(
      "signedOperationContents" -> signedOpGroup.bytes.map("%02X" format _).mkString
    )
    node
      .runAsyncPostQuery(network, "/inject_operation", Some(JsonUtil.toJson(payload)))
      .map(result => fromJson[InjectedOperation](result).injectedOperation)
  }

  /*/**
   * Master function for creating and sending all supported types of operations.
   * @param operations The operations to create and send
   * @param keyStore   Key pair along with public key hash
   * @param fee        The fee to use
   * @return           The ID of the created operation group
   */
  def sendOperation(
      operations: List[Map[String, Any]],
      keyStore: KeyStore,
      fee: Option[Float]
  ): Future[OperationResult] =
    for {
      blockHead <- getBlockHead()
      accountId = AccountId(keyStore.publicKeyHash)
      account <- getAccountForBlock(blockHeadHash, accountId)
      accountManager <- getAccountManagerForBlock(blockHeadHash, accountId)
      operationsWithKeyReveal = handleKeyRevealForOperations(operations, accountManager, keyStore)
      forgedOperationGroup <- forgeOperations(blockHead, account, operationsWithKeyReveal, keyStore, fee)
      signedOpGroup <- Future.fromTry(signOperationGroup(forgedOperationGroup, keyStore))
      operationGroupHash <- Future.fromTry(computeOperationHash(signedOpGroup))
      appliedOp <- applyOperation(blockHead, operationGroupHash, forgedOperationGroup, signedOpGroup)
      operation <- injectOperation(signedOpGroup)
    } yield OperationResult(appliedOp, operation)*/

  /*/**
   * Creates and sends a transaction operation.
   * @param keyStore   Key pair along with public key hash
   * @param to         Destination public key hash
   * @param amount     Amount to send
   * @param fee        Fee to use
   * @return           The ID of the created operation group
   */
  def sendTransactionOperation(
      keyStore: KeyStore,
      to: String,
      amount: Float,
      fee: Float
  ): Future[OperationResult] = {
    val transactionMap: Map[String, Any] = Map(
      "kind" -> "transaction",
      "amount" -> amount,
      "destination" -> to,
      "parameters" -> MichelsonExpression("Unit", List[String]())
    )
    val operations = transactionMap :: Nil
    sendOperation(operations, keyStore, Some(fee))
  }*/

  /*/**
   * Creates and sends a delegation operation.
   * @param keyStore Key pair along with public key hash
   * @param delegate Account ID to delegate to
   * @param fee      Operation fee
   * @return
   */
  def sendDelegationOperation(keyStore: KeyStore, delegate: String, fee: Float): Future[OperationResult] = {
    val transactionMap: Map[String, Any] = Map(
      "kind" -> "delegation",
      "delegate" -> delegate
    )
    val operations = transactionMap :: Nil
    sendOperation(operations, keyStore, Some(fee))
  }*/

  /*/**
   * Creates and sends an origination operation.
   * @param keyStore     Key pair along with public key hash
   * @param amount       Initial funding amount of new account
   * @param delegate     Account ID to delegate to, blank if none
   * @param spendable    Is account spendable?
   * @param delegatable  Is account delegatable?
   * @param fee          Operation fee
   * @return
   */
  def sendOriginationOperation(
      keyStore: KeyStore,
      amount: Float,
      delegate: String,
      spendable: Boolean,
      delegatable: Boolean,
      fee: Float
  ): Future[OperationResult] = {
    val transactionMap: Map[String, Any] = Map(
      "kind" -> "origination",
      "balance" -> amount,
      "managerPubkey" -> keyStore.publicKeyHash,
      "spendable" -> spendable,
      "delegatable" -> delegatable,
      "delegate" -> delegate
    )
    val operations = transactionMap :: Nil
    sendOperation(operations, keyStore, Some(fee))
  }*/

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
