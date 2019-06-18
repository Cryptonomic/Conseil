package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.generic.rpc.{DataFetcher, RpcHandler}
import tech.cryptonomic.conseil.generic.rpc.DataFetcher.fetcher
import tech.cryptonomic.conseil.config.BatchFetchConfiguration
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.michelson.JsonToMichelson.convert
import tech.cryptonomic.conseil.tezos.michelson.dto.{MichelsonElement, MichelsonInstruction, MichelsonSchema}
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser.Parser
import tech.cryptonomic.conseil.util.JsonUtil.fromJson
import com.typesafe.scalalogging.LazyLogging
import cats.{Applicative, ApplicativeError, FlatMap, Functor, MonadError, Monoid, Show}
import cats.data.Reader
import cats.syntax.all._
import cats.instances.string._
import cats.instances.tuple._
import cats.effect.Concurrent
import scala.{Stream => _}
import fs2.Stream
import scala.math.max
import scala.reflect.ClassTag
import scala.util.Try

/** Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database. */
class NodeOperator(val network: String, batchConf: BatchFetchConfiguration)
  extends LazyLogging {

  //use this aliases to make signatures easier to read and kept in-sync

  /** describes capability to run a single remote call returning strings*/
  type GetHandler[Eff[_]] = RpcHandler[Eff, String, String]
  /** alias for a fetcher with String json encoding and Throwable failures */
  type NodeFetcherThrow[Eff[_], In, Out] = DataFetcher.Aux[Eff, Throwable, In, Out, String]
  /** a monad that can raise and handle Throwables */
  type MonadThrow[Eff[_]] = MonadError[Eff, Throwable]
  /** an applicative that can raise and handle Throwables */
  type ApplicativeThrow[Eff[_]] = ApplicativeError[Eff, Throwable]
  /** generic pair of multiple Ts with their associated block reference */
  type BlockWithMany[T] = (Block, List[T])
  /** the whole results of reading latest info from the chain */
  type BlockFetchingResults[Eff[_]] = Stream[Eff, (Block, List[AccountId])]

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
    * @param blockHash  the block storing the accounts
    * @return           the list of accounts, indexed by AccountId
    */
  def getAccountsForBlock[F[_] : MonadThrow](accountIds: List[AccountId], blockHash: BlockHash)
    (implicit fetchProvider: Reader[BlockHash, NodeFetcherThrow[F, AccountId, Option[Account]]]): F[Map[AccountId, Account]] = {
      import TezosOptics.Accounts.{scriptLens, storageLens}
      import cats.instances.list._

      implicit val accountFetcher: DataFetcher.Aux[F, Throwable, AccountId, Option[Account], String] = fetchProvider(blockHash)

      def parseMichelsonScripts(id: AccountId): Account => Account = withLoggingContext[Account, String](makeContext = _ => s"account with keyhash ${id.id}") {
        implicit ctx =>
          val scriptAlter = scriptLens.modify(toMichelsonScript[MichelsonSchema, String])
          val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction, String])

          scriptAlter compose storageAlter
      }

      val fetchedAccounts: F[List[(AccountId, Option[Account])]] =
        fetcher.tapWith((_, _))
          .traverse(accountIds)

      fetchedAccounts.map{
        indexedAccounts =>
          indexedAccounts.collect {
            case (accountId, Some(account)) => accountId -> parseMichelsonScripts(accountId)(account)
          }.toMap
      }

  }

  /* Generic loader of derived entities, associated with some block operation.
   * Can be used to load accounts, delegates and similar entities.
   * It requires :
   * @param loadEntitiesByBlock will provide a function that actually loads data for keys
   *        related to a single block, once given the reference block hash. It's a "function factory".
   * @param keyIndex the mapping of all keys requested to the block they're referenced by
   */
  private def getBlockRelatedEntities[F[_] : ApplicativeThrow : Concurrent, Key, Entity](
    loadEntitiesByBlock: Reader[BlockHash, Stream[F, Key] => Stream[F, (Key, Entity)]],
    keyIndex: Map[Key, BlockReference]
  ): Stream[F, BlockTagged[Map[Key, Entity]]] = {
    import TezosTypes.Syntax._

    val reverseIndex =
      keyIndex.groupBy {
        case (key, (blockHash, level)) => blockHash
      }
      .mapValues(_.keySet)
      .toMap

    val keyStreamsByBlock = reverseIndex.mapValues(keys => Stream.fromIterator(keys.iterator))

   //for each hash in the map, get the stream of results and concat them all, keeping the order
    keyStreamsByBlock.map {
      case (hash, keys) =>
        loadEntitiesByBlock(hash)(keys)
    }
    .fold(Stream.empty)(_ ++ _)
    .groupAdjacentBy {
      case (key, entity) => keyIndex(key) //create chunks having the same block reference, relying on how the stream is ordered
    }
    .map {
      case ((hash, level), keyedEntitiesChunk) =>
        val entitiesMap = keyedEntitiesChunk.foldLeft(Map.empty[Key, Entity]){_ + _} //collect to a map each chunk
        entitiesMap.taggedWithBlock(hash, level) //tag with the block reference
    }

  }

  /** Fetches the accounts identified by id, using a fetcher that should already
    * take into account the block referencing the accounts.
    *
    * @param accountIds the ids for requested accounts
    * @param fetcherForBlock data fetcher for accounts, built for given a reference block hash
    * @return the stream of accounts, indexed by AccountId
    */
  def getAccountsForBlock[F[_] : ApplicativeThrow : Concurrent](
    accountIds: Stream[F, AccountId]
  )(
    implicit fetcherForBlock: NodeFetcherThrow[F, AccountId, Option[Account]]
  ): Stream[F, (AccountId, Account)] = {
    import TezosOptics.Accounts.{scriptLens, storageLens}

    def parseMichelsonScripts(id: AccountId): Account => Account = withLoggingContext[Account, String](makeContext = _ => s"account with key hash ${id.id}") {
      implicit ctx =>
        val scriptAlter = scriptLens.modify(toMichelsonScript[MichelsonSchema, String])
        val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction, String])

        scriptAlter compose storageAlter
    }

    val logError: PartialFunction[Throwable, Stream[F, Unit]] = {
      case err: Throwable =>
        val sampleSize = 30
        val logAction = accountIds
          .take(sampleSize + 1)
          .map(_.id)
          .compile.toVector
          .flatMap {
            ids =>
              val sample = if (ids.size <= sampleSize) ids else ids.dropRight(1) :+ "..."
              logger.error(s"Could not get accounts' data for ids ${sample.mkString(",")}", err).pure
          }
        Stream.eval(logAction)
    }

    /* Concurrently gets data, then logs any error, issue warns for missing data, eventually
     * parses the account object, if all went well
     */
    accountIds.parEvalMap(batchConf.accountFetchConcurrencyLevel)(
      fetcher.tapWith((_, _)).run
    )
    .onError(logError)
    .evalTap {
      case (accountId, maybeAccount) =>
        Applicative[F].whenA(maybeAccount.isEmpty) {
          logger.warn("The following account key was not found querying the {} node: {}", network, accountId).pure
        }
    }
    .collect {
      case (accountId, Some(account)) => accountId -> parseMichelsonScripts(accountId)(account)
    }
  }

  /**
    * Get accounts for all the identifiers passed-in with the corresponding block
    *
    * @param accountsBlocksIndex a map from unique id to the referring block [reference]
    * @return Accounts with their corresponding block data
    */
  def getAccounts[F[_] : ApplicativeThrow : Concurrent](
    accountsBlocksIndex: Map[AccountId, BlockReference]
  )(
    implicit fetchProvider: Reader[BlockHash, NodeFetcherThrow[F, AccountId, Option[Account]]]
  ): Stream[F, BlockTagged[Map[AccountId, Account]]] =
    getBlockRelatedEntities[F, AccountId, Account](
      //maps the Reader, so that, applying it to a hash, it returns the block-specific fetch function
      loadEntitiesByBlock = fetchProvider.map {
        implicit fetcher => //pass this implicitly from the reader
          (ids: Stream[F, AccountId]) => getAccountsForBlock(ids)
      },
      keyIndex = accountsBlocksIndex
    )

  /**
    * Fetches the delegates identified by public key hash, using a fetcher that should already
    * take into account the block referencing the delegate.
    *
    * @param delegateKeys the pkhs for requested delegates
    * @param fetcherForBlock data fetcher for delegates, built for given a reference block hash
    * @return the stream of delegates, indexed by PublicKeyHash
    */
  def getDelegatesForBlock[F[_] : ApplicativeThrow : Concurrent](
    delegateKeys: Stream[F, PublicKeyHash]
  )(
    implicit fetcherForBlock: NodeFetcherThrow[F, PublicKeyHash, Option[Delegate]]
  ): Stream[F, (PublicKeyHash, Delegate)] = {

    val logError: PartialFunction[Throwable, Stream[F, Unit]] = {
      case err: Throwable =>
        val sampleSize = 30
        val logAction = delegateKeys
          .take(sampleSize + 1)
          .map(_.value)
          .compile.toVector
          .flatMap {
            keys =>
              val sample = if (keys.size <= sampleSize) keys else keys.dropRight(1) :+ "..."
              logger.error(s"Could not get accounts' data for key hashes ${sample.mkString(",")}", err).pure
          }
        Stream.eval(logAction)
    }

    /* Concurrently gets data, then logs any error, issue warns for missing data, eventually
     * parses the delegate object, if all went well
     */
    delegateKeys.parEvalMap(batchConf.delegateFetchConcurrencyLevel)(
      fetcher.tapWith((_, _)).run
    )
    .onError(logError)
    .evalTap {
      case (pkh, maybeDelegate) =>
        Applicative[F].whenA(maybeDelegate.isEmpty){
          logger.warn("The following delegate key was not found querying the {} node: {}", network, pkh).pure
        }
    }
    .collect {
      case (pkh, Some(delegate)) => pkh -> delegate
    }
  }

  /**
    * Get delegates for all the identifiers passed-in with the corresponding block
    *
    * @param keysBlocksIndex a map from unique key hash to the referring block [reference]
    * @return Delegates with their corresponding block data
    */
  def getDelegates[F[_] : MonadThrow : Concurrent](
    keysBlocksIndex: Map[PublicKeyHash, BlockReference]
  )(
    implicit fetchProvider: Reader[BlockHash, NodeFetcherThrow[F, PublicKeyHash, Option[Delegate]]]
  ): Stream[F, BlockTagged[Map[PublicKeyHash, Delegate]]] =
    getBlockRelatedEntities[F, PublicKeyHash, Delegate](
      //maps the Reader, so that, applying it to a hash, it returns the block-specific fetch function
      loadEntitiesByBlock = fetchProvider.map {
        implicit fetcher => //pass this implicitly from the reader
          (keys: Stream[F, PublicKeyHash]) => getDelegatesForBlock(keys)
      },
      keyIndex = keysBlocksIndex
    )

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
    ): F[(Voting.Proposal, BlockWithMany[Voting.BakerRolls], BlockWithMany[Voting.Ballot])] = {

    //adapt the proposal protocols result to include the block
    val fetchProposals =
      fetcher[F, Block, List[ProtocolId], Throwable]
        .tapWith {
            case (block, protocols) => Voting.Proposal(protocols, block)
          }

    val fetchBakers =
      fetcher[F, Block, List[Voting.BakerRolls], Throwable].tapWith(_ -> _)

    val fetchBallots =
      fetcher[F, Block, List[Voting.Ballot], Throwable].tapWith(_ -> _)


    /* combine the three kleisli operations to return a tuple of the results
     * and then run the composition on the input block
     */
    (fetchProposals, fetchBakers, fetchBallots).tupled.run(block)
  }

  /** Fetches a single block from the chain, without waiting for the result.
    * The block is identified by it's level offset with respect to a reference
    * block which is already configured in the implicit `blockDataFetcher` argument.
    * @param offset an offset level to use from the reference block, optionally
    * @return the block data
    */
  private def getBlockWithAccounts[F[_] : MonadThrow](
    offset: Option[Offset] = None
  )(implicit
    blockDataFetcher: NodeFetcherThrow[F, Offset, BlockData],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]]
  ): F[(Block, List[AccountId])] = {
    import TezosTypes.Lenses._

    val fetchBlockData = fetcher[F, Offset, BlockData, Throwable].run(offset.getOrElse(0))

    val parseMichelsonScripts: Block => Block = withLoggingContext[Block, String](makeContext = block => s"block with hash ${block.data.hash.value}") {
      implicit ctx =>
        val codeAlter = codeLens.modify(toMichelsonScript[MichelsonSchema, String])
        val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction, String])
        val parametersAlter = parametersLens.modify(toMichelsonScript[MichelsonInstruction, String])

        codeAlter compose storageAlter compose parametersAlter
      }

    fetchBlockData.flatMap( data =>
      (getAllOperationsAndAccountsForBlock(data), getCurrentVotesForBlock(data)).mapN {
        case ((operations, accountIds), votes) =>
          parseMichelsonScripts(Block(data, operations, votes)) -> accountIds
      }
    )

  }

  /** Fetches a single block along with associated data from the chain, without waiting for the result
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
  ): F[Block] = {
    //bring the block fetcher for the specific reference hash into scope, so that each getBlockWithAccount can re-use it
    implicit val blockFetcher = blockDataFetchProvider(hash)

    getBlockWithAccounts(offset).map(_._1)
  }

  /** Fetches a single block from the chain without associated data, without waiting for the result
    * @param hash Hash of the block
    * @param offset an offset level to use from the passed hash, optionally
    * @return the block data
    */
  def getBareBlock[F[_] : MonadThrow](
    hash: BlockHash,
    offset: Option[Offset] = None
  )(implicit
    fetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]]
  ): F[BlockData] = {
    implicit val blockFetcher = fetchProvider(hash)
    fetcher.run(offset.getOrElse(0))
  }

  /** Gets the block head.
    * @return Block head
    */
  def getBlockHead[F[_] : MonadThrow] (implicit
    blockDataFetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]]
  ): F[Block] = getBlock(blockHeadHash)

  /** Gets just the block head without associated data.
    * @return Block head
    */
  def getBareBlockHead[F[_] : MonadThrow](
    implicit fetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]]
  ): F[BlockData] =
    getBareBlock(blockHeadHash)


  /** Gets all blocks from the head down to the oldest block not already in the database.
   *  @param fetchLocalMaxLevel should read the current top-level available for the chain, as stored in conseil
    * @return Blocks and Account hashes involved
    */
  def getBlocksNotInDatabase[F[_] : MonadThrow : Concurrent](
    fetchLocalMaxLevel: => F[Int]
  )(implicit
    blockDataFetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]],
    resultMonoid: Monoid[BlockFetchingResults[F]]
  ): F[(BlockFetchingResults[F], Int)] =
    for {
      maxLevel <- fetchLocalMaxLevel
      blockHead <- getBlockHead
    } yield {
      val headLevel = blockHead.data.header.level
      val headHash = blockHead.data.hash
      val bootstrapping = maxLevel == -1

      if (maxLevel < headLevel) {
        //got something to load
        if (bootstrapping) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
        else logger.info("I found the new block head at level {}, the currently stored max is {}. I'll fetch the missing {} blocks.", headLevel, maxLevel, headLevel - maxLevel)
        val minLevel = if (bootstrapping) 1 else maxLevel
        getBlocks((headHash, headLevel), maxLevel + 1 to headLevel) -> (headLevel - minLevel)
      } else {
        logger.info("No new blocks to fetch from the network")
        resultMonoid.empty -> 0
      }
    }

  /** Gets last `depth` blocks.
    * @param depth Number of latest block to fetch, `None` to get all
    * @param headHash Hash of a block from which to start, None to start from a real head
    * @return Blocks and Account hashes involved, paired with the computed result size, based on a level range
    */
  def getLatestBlocks[F[_] : MonadThrow : Concurrent](
    depth: Option[Int] = None,
    headHash: Option[BlockHash] = None
  )(implicit
    blockDataFetchProvider: Reader[BlockHash, NodeFetcherThrow[F, Offset, BlockData]],
    additionalDataFetcher: NodeFetcherThrow[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[Int]],
    proposalFetcher: NodeFetcherThrow[F, (BlockHash, Option[Offset]), Option[ProtocolId]],
  ): F[(BlockFetchingResults[F], Int)] =
    headHash.fold(getBlockHead)(getBlock(_))
      .map {
        maxHead =>
          val headLevel = maxHead.data.header.level
          val headHash = maxHead.data.hash
          val minLevel = depth.fold(0)(d => max(0, headLevel - d + 1))
          getBlocks((headHash, headLevel), minLevel to headLevel) -> (headLevel - minLevel + 1)
      }

  /** Gets block from Tezos Blockchains, as well as their associated operation, from minLevel to maxLevel.
    * @param reference Hash and level of a known block
    * @param levelRange a range of levels to load
    * @return the list of blocks with relative account ids touched in the operations
    */
  private def getBlocks[F[_] : MonadThrow: Concurrent](
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

    logger.debug(s"Request to fetch blocks in levels $levelRange, with reference block at level $levelRef and hash: ${hashRef.value}")

    //bring the block fetcher for the specific reference hash into scope, so that each getBlockWithAccount can re-use it
    implicit val blockFetcher = blockDataFetchProvider(hashRef)

    //Gets blocks data for the requested offsets and associates the operations and account hashes available involved in said operations
    //Special care is taken for the genesis block (level = 0) that doesn't have operations defined, we use empty data for it
    offsets.lift[F]
      .prefetchN(batchConf.blockFetchConcurrencyLevel)
      .parEvalMap(batchConf.blockFetchConcurrencyLevel)(offset => getBlockWithAccounts(Some(offset)))

  }

  /* Used to provide an implicit logging context (of type CTX) to the michelson script conversion method
   * It essentially defines an extraction mechanism to define a generic logging context from the conversion input (i.e. `makeContext`)
   * and then provides both the context and the original input to another function that will use both.
   *
   * We require that the `CTX` type can be printed to be logged, so a Show instance must be available to convert it to a String.
   */
  private[this] def withLoggingContext[T, CTX: Show](makeContext: T => CTX)(function: CTX => T => T): T => T = (t: T) => {
    function(makeContext(t))(t)
  }

  /* Takes a json string and tries parsing it as a Micheline data structure.
   * If the parsing succeeds, the returned value is the Michelson equivalent of the original json,
   * otherwise an error is logged explaining what failed and an error string referring the input script is returned.
   *
   * The method requires an implicit "logging context" to be provided, as additional information over what kind of operation
   * was occurring when the parsing failed.
   * Example: we use the context when converting inner script fields of blocks or accounts. The context is a string identifying the
   *   specific block or account.
   */
  private[this] def toMichelsonScript[T <: MichelsonElement : Parser, CTX : Show](json: String)(implicit tag: ClassTag[T], ctx: CTX): String = {
    import cats.syntax.show._

    def unparsableResult(json: Any, exception: Option[Throwable] = None): String = {
      exception match {
        case Some(t) => logger.error(s"${tag.runtimeClass}: Error during conversion of $json in ${ctx.show}", t)
        case None => logger.error(s"${tag.runtimeClass}: Error during conversion of $json in ${ctx.show}")
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