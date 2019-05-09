package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.generic.chain.{DataFetcher, RpcHandler}
import tech.cryptonomic.conseil.generic.chain.DataFetcher.fetcher
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.michelson.JsonToMichelson.convert
import tech.cryptonomic.conseil.tezos.michelson.dto.{MichelsonElement, MichelsonInstruction, MichelsonSchema}
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser.Parser
import tech.cryptonomic.conseil.util.JsonUtil.fromJson
import com.typesafe.scalalogging.Logger
import cats.{ApplicativeError, FlatMap, Functor, MonadError, Monoid}
import cats.data.Reader
import cats.syntax.applicative._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import scala.{Stream => _}
import fs2.Stream
import scala.math.max
import scala.reflect.ClassTag
import scala.util.Try

/** Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database. */
trait NodeOperator {

  /** which tezos network to go against */
  def network: String
  /** assumes some capability to log */
  def logger: Logger

  //use this aliases to make signatures easier to read and kept in-sync

  /** describes capability to run a single remote call returning strings*/
  type GetHandler[F[_]] = RpcHandler.Aux[F, String, _, String]
  /** alias for a fetcher with String json encoding and Throwable failures */
  type NodeFetcherThrow[F[_], In, Out] = DataFetcher.Aux[F, Throwable, In, Out, String]
  /** a monad that can raise and handle Throwables */
  type MonadThrow[F[_]] = MonadError[F, Throwable]
  /** an applicative that can raise and handle Throwables */
  type ApplicativeThrow[F[_]] = ApplicativeError[F, Throwable]
  /** the whole results of reading latest info from the chain */
  type BlockFetchingResults[F[_]] = Stream[F, (Block, List[AccountId])]

  /**
    * Fetches a specific account for a given block.
    * @param blockHash  Hash of given block
    * @param accountId  Account ID
    * @return           The account
    */
  def getAccountForBlock[F[_] : GetHandler : Functor](blockHash: BlockHash, accountId: AccountId): F[Account] =
    RpcHandler.runGet(s"blocks/${blockHash.value}/context/contracts/${accountId.id}")
      .map(fromJson[Account])

  /**
    * Fetches the manager of a specific account for a given block.
    * @param blockHash  Hash of given block
    * @param accountId  Account ID
    * @return           The account's manager key
    */
  def getAccountManagerForBlock[F[_] : GetHandler : Functor](blockHash: BlockHash, accountId: AccountId): F[ManagerKey] =
    RpcHandler.runGet(s"blocks/${blockHash.value}/context/contracts/${accountId.id}/manager_key")
      .map(fromJson[ManagerKey])

  /**
    * Fetches all accounts for a given block.
    * @param blockHash  Hash of given block.
    * @return           Accounts
    */
  def getAllAccountsForBlock[F[_] : GetHandler : MonadThrow](blockHash: BlockHash)
    (implicit fetchProvider: Reader[BlockHash, NodeFetcherThrow[F, AccountId, Option[Account]]]): F[Map[AccountId, Account]] =
    for {
      jsonEncodedAccounts <- RpcHandler.runGet(s"blocks/${blockHash.value}/context/contracts")
      accountIds = fromJson[List[String]](jsonEncodedAccounts).map(AccountId)
      accounts <- getAccountsForBlock(accountIds, blockHash)
    } yield accounts

  /**
    * Fetches the accounts identified by id
    *
    * @param accountIds the ids
    * @param blockHash  the block storing the accounts, the head block if not specified
    * @return           the list of accounts, indexed by AccountId
    */
  def getAccountsForBlock[F[_] : MonadThrow](accountIds: List[AccountId], blockHash: BlockHash = blockHeadHash)
    (implicit fetchProvider: Reader[BlockHash, NodeFetcherThrow[F, AccountId, Option[Account]]]): F[Map[AccountId, Account]] = {
      import TezosOptics.Accounts.{scriptLens, storageLens}
      import cats.instances.list._

      implicit val accountFetcher: DataFetcher.Aux[F, Throwable, AccountId, Option[Account], String] = fetchProvider(blockHash)

      val parseMichelsonScripts: Account => Account = {
        val scriptAlter = scriptLens.modify(toMichelsonScript[MichelsonSchema])
        val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction])

        scriptAlter compose storageAlter
      }

      val fetchedAccounts: F[List[(AccountId, Option[Account])]] =
        fetcher.tapWith((_, _))
          .traverse(accountIds)

      fetchedAccounts.map{
        indexedAccounts =>
          indexedAccounts.collect {
            case (accountId, Some(account)) => accountId -> parseMichelsonScripts(account)
          }.toMap
      }

  }

  // /**
  //   * Fetches all accounts for a given block.
  //   * @param blockHash  Hash of given block.
  //   * @return           Accounts
  //   */
  // def getAllAccountsForBlock[F[_] : MonadThrow : RpcGet](blockHash: BlockHash)
  //   (implicit fetchProvider: Reader[BlockHash, ListFetcher[F, AccountId, Option[Account]]]): F[Map[AccountId, Account]] = {

  //   for {
  //     jsonEncodedAccounts <- RemoteRpc.runGet(s"blocks/${blockHash.value}/context/contracts")
  //     accountIds = fromJson[List[String]](jsonEncodedAccounts).map(AccountId)
  //     accounts <- getAccountsForBlock(accountIds, blockHash)
  //   } yield accounts
  // }

  // /**
  //   * Fetches the accounts identified by id
  //   *
  //   * @param accountIds the ids
  //   * @param blockHash  the block storing the accounts, the head block if not specified
  //   * @return           the list of accounts, indexed by AccountId
  //   */
  // def getAccountsForBlock[F[_] : MonadThrow](accountIds: List[AccountId], blockHash: BlockHash = blockHeadHash)
  //   (implicit fetchProvider: Reader[BlockHash, ListFetcher[F, AccountId, Option[Account]]]): F[Map[AccountId, Account]] = {
  //     import TezosOptics.Accounts.{scriptLens, storageLens}
  //     import cats.instances.list._

  //     implicit val fetcher = fetchProvider(blockHash)

  //     def parseMichelsonScripts(account: Account): Account = {
  //       val scriptAlter = scriptLens.modify(toMichelsonScript[MichelsonSchema])
  //       val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction])

  //       (scriptAlter compose storageAlter)(account)
  //     }

  //     val fetchedAccounts: F[List[(AccountId, Option[Account])]] =
  //       fetch[AccountId, Option[Account], F, List, Throwable].run(accountIds)

  //     fetchedAccounts.map(
  //       indexedAccounts =>
  //         indexedAccounts.collect {
  //           case (accountId, Some(account)) => accountId -> parseMichelsonScripts(account)
  //         }.toMap
  //     )
  //   }

  // /**
  //   * Get accounts for all the identifiers passed-in with the corresponding block
  //   * @param accountsBlocksIndex a map from unique id to the [latest] block reference
  //   * @return         Accounts with their corresponding block data
  //   *
  //   */
  // @deprecated(message = "This might not be needed anymore", since = "April 2019")
  // def getAccountsForBlocks[F[_] : MonadThrow](accountsBlocksIndex: Map[AccountId, BlockReference])
  //   (implicit fetchProvider: Reader[BlockHash, ListFetcher[F, AccountId, Option[Account]]]): F[List[BlockAccounts]] = {

  //   def notifyAnyLostIds(missing: Set[AccountId]) =
  //     if (missing.nonEmpty) {
  //       logger.warn(
  //         "The following account keys were not found querying the {} node: {}",
  //         network,
  //         missing.map(_.id).mkString("\n", ",", "\n")
  //       )
  //     }

  //   //uses the index to collect together BlockAccounts matching the same block
  //   def groupByLatestBlock(data: Map[AccountId, Account]): List[BlockAccounts] =
  //     data.groupBy {
  //       case (id, _) => accountsBlocksIndex(id)
  //     }.map {
  //       case ((hash, level), accounts) => BlockAccounts(hash, level, accounts)
  //     }.toList

  //   //fetch accounts by requested ids and group them together with corresponding blocks
  //   getAccountsForBlock(accountsBlocksIndex.keys.toList)
  //     .onError{
  //       case err =>
  //         val showSomeIds = accountsBlocksIndex.keys.take(30).map(_.id).mkString("", ",", if (accountsBlocksIndex.size > 30) "..." else "")
  //         logger.error(s"Could not get accounts' data for ids ${showSomeIds}", err).pure[F]
  //     }
  //     .map {
  //       accountsMap =>
  //         notifyAnyLostIds(accountsBlocksIndex.keySet -- accountsMap.keySet)
  //         groupByLatestBlock(accountsMap)
  //     }

  // }

  /**
    * Fetches operations for a block, without waiting for the result
    * @param blockHash Hash of the block
    * @return          The list of operations
    */
  def getAllOperationsForBlock[F[_] : ApplicativeThrow : FlatMap](block: BlockData)(
    implicit operationsFetcher: NodeFetcherThrow[F, BlockHash, List[OperationsGroup]]
  ): F[List[OperationsGroup]] =
    if (isGenesis(block)) List.empty.pure //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
    else fetcher.run(block.hash)

    /**
    * Fetches operations for a block, without waiting for the result
    * @param blockHash Hash of the block
    * @return          The list of operations
    */
  def getAllOperationsAndAccountsForBlock[F[_] : ApplicativeThrow : FlatMap](block: BlockData)(
    implicit additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])]
  ): F[(List[OperationsGroup], List[AccountId])] =
    if (isGenesis(block)) (List.empty[OperationsGroup], List.empty[AccountId]).pure //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
    else fetcher.run(block.hash)

  /** Fetches current votes information at the specific block */
  def getCurrentVotesForBlock[F[_] : ApplicativeThrow : FlatMap](
    block: BlockData,
    offset: Option[Offset] = None
  )(implicit
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]]
  ): F[CurrentVotes] =
    if (isGenesis(block)) CurrentVotes.empty.pure
    else {
      val fetchCurrentQuorum = fetcher[F, (BlockHash, Option[Offset]), Option[Int], Throwable]
      val fetchCurrentProposal = fetcher[F, (BlockHash, Option[Offset]), Option[ProtocolId], Throwable]
      (fetchCurrentQuorum, fetchCurrentProposal).mapN(CurrentVotes(_, _)).run(block.hash -> offset)
    }

  /** Fetches detailed data for voting associated to the passed-in blocks */
  def getVotingDetails[F[_] : MonadThrow](block: Block)
    (implicit
      proposalFetcher: NodeFetcherThrow[F, Block, List[ProtocolId]],
      bakersFetch: NodeFetcherThrow[F, Block, List[Voting.BakerRolls]],
      ballotsFetcher: NodeFetcherThrow[F, Block, List[Voting.Ballot]]
    ): F[(Voting.Proposal, List[Voting.BakerRolls], List[Voting.Ballot])] = {

    //adapt the proposal protocols result to include the block
    val fetchProposals =
      fetcher[F, Block, List[ProtocolId], Throwable]
        .tapWith {
            case (block, protocols) => Voting.Proposal(protocols, block)
          }

    val fetchBakers =
      fetcher[F, Block, List[Voting.BakerRolls], Throwable]

    val fetchBallots =
      fetcher[F, Block, List[Voting.Ballot], Throwable]


    /* combine the three kleisli operations to return a tuple of the results
     * and then run the composition on the input block
     */
    (fetchProposals, fetchBakers, fetchBallots).tupled.run(block)
  }

  /** Fetches a single block from the chain, without waiting for the result
    * @param hash Hash of the block
    * @param offset an offset level to use from the passed hash, optionally
    * @return the block data
    */
  def getBlockWithAccounts[F[_] : MonadThrow](
    hash: BlockHash,
    offset: Option[Offset] = None
  )(implicit
    blockDataFetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]]
  ): F[(Block, List[AccountId])] = {
    import TezosTypes.Lenses._

    /*DataFetcher.Aux[F, Throwable, Offset, BlockData, String] */
    implicit val blockDataFetcher = blockDataFetchProvider(hash)
    val fetchBlockData = fetcher[F, Offset, BlockData, Throwable].run(offset.getOrElse(0))


    val parseMichelsonScripts: Block => Block = {
      val codeAlter = codeLens.modify(toMichelsonScript[MichelsonSchema])
      val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction])
      val parametersAlter = parametersLens.modify(toMichelsonScript[MichelsonInstruction])

      codeAlter compose storageAlter compose parametersAlter
    }

    fetchBlockData.flatMap( data =>
      (getAllOperationsAndAccountsForBlock(data), getCurrentVotesForBlock(data)).mapN {
        case ((operations, accountIds), votes) =>
          parseMichelsonScripts(Block(data, operations, votes)) -> accountIds
      }
    )

  }

  /** Fetches a single block from the chain, without waiting for the result
    * @param hash Hash of the block
    * @param offset an offset level to use from the passed hash, optionally
    * @return the block data
    */
  def getBlock[F[_] : MonadThrow](
    hash: BlockHash,
    offset: Option[Offset] = None
  )(implicit
    blockDataFetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]]
  ): F[Block] = getBlockWithAccounts(hash, offset).map(_._1)

  /** Gets the block head.
    * @return Block head
    */
  def getBlockHead[F[_] : MonadThrow] (implicit
    blockDataFetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]]
  ): F[Block] = getBlock(blockHeadHash)

  /** Gets all blocks from the head down to the oldest block not already in the database.
   *  @param fetchLocalMaxLevel should read the current top-level available for the chain, as stored in conseil
    * @return Blocks and Account hashes involved
    */
  def getBlocksNotInDatabase[F[_] : MonadThrow](
    fetchLocalMaxLevel: () => F[Int]
  )(implicit
    blockDataFetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]],
    resultMonoid: Monoid[BlockFetchingResults[F]]
  ): F[BlockFetchingResults[F]] =
    for {
      maxLevel <- fetchLocalMaxLevel()
      blockHead <- getBlockHead
    } yield {
      val headLevel = blockHead.data.header.level
      val headHash = blockHead.data.hash
      val bootstrapping = maxLevel == -1

      if (maxLevel < headLevel) {
        //got something to load
        if (bootstrapping) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
        else logger.info("I found the new block head at level {}, the currently stored max is {}. I'll fetch the missing {} blocks.", headLevel, maxLevel, headLevel - maxLevel)
        getBlocks((headHash, headLevel), maxLevel + 1 to headLevel)
      } else {
        logger.info("No new blocks to fetch from the network")
        resultMonoid.empty
      }
    }

  /** Gets last `depth` blocks.
    * @param depth      Number of latest block to fetch, `None` to get all
    * @param headHash   Hash of a block from which to start, None to start from a real head
    * @return           Blocks and Account hashes involved
    */
  def getLatestBlocks[F[_] : MonadThrow](
    depth: Option[Int] = None,
    headHash: Option[BlockHash] = None
  )(implicit
    blockDataFetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]],
  ): F[BlockFetchingResults[F]] =
    headHash.fold(getBlockHead)(getBlock(_))
      .map {
        maxHead =>
          val headLevel = maxHead.data.header.level
          val headHash = maxHead.data.hash
          val minLevel = depth.fold(1)(d => max(1, headLevel - d + 1))
          getBlocks((headHash, headLevel), minLevel to headLevel)
      }

  /** Gets block from Tezos Blockchains, as well as their associated operation, from minLevel to maxLevel.
    * @param reference Hash and level of a known block
    * @param levelRange a range of levels to load
    * @return the list of blocks with relative account ids touched in the operations
    */
  private def getBlocks[F[_] : MonadThrow](
    reference: (BlockHash, Int),
    levelRange: Range.Inclusive
  )(implicit
    blockDataFetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]]
  ): BlockFetchingResults[F] = {

    val (hashRef, levelRef) = reference
    require(levelRange.start >= 0 && levelRange.end <= levelRef)

    //build offsets in descending order, using level bounds, to load oldest blocks first
    val offsets = Stream.range(levelRange.start, levelRange.end + 1).map(lvl => levelRef - lvl)

    logger.info(s"Request to fetch blocks in levels $levelRange, with reference block at level $levelRef and hash: ${hashRef.value}")

    //Gets blocks data for the requested offsets and associates the operations and account hashes available involved in said operations
    //Special care is taken for the genesis block (level = 0) that doesn't have operations defined, we use empty data for it
    offsets.evalMap(offset => getBlockWithAccounts(hashRef, Some(offset)))

  }

  private[this] def toMichelsonScript[T <: MichelsonElement : Parser](json: String)(implicit tag: ClassTag[T]): String = {

    def unparsableResult(json: Any, exception: Option[Throwable] = None): String = {
      exception match {
        case Some(t) => logger.error(s"${tag.runtimeClass}: Error during conversion of $json", t)
        case None => logger.error(s"${tag.runtimeClass}: Error during conversion of $json")
      }

      s"Unparsable code: $json"
    }

    def parse(json: String): String = convert[T](json) match {
      case Right(convertedResult) => convertedResult
      case Left(exception) => unparsableResult(json, Some(exception))
    }

    Try(parse(json)).getOrElse(unparsableResult(json))
  }

}