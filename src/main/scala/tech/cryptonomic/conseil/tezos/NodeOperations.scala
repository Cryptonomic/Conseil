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
import scala.{Stream => _} //hide from scope
import scala.math.max
import scala.util.Try
import scala.reflect.ClassTag
import cats._
import cats.data.Kleisli
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.functor._
import cats.syntax.flatMap._
import cats.syntax.option._
import cats.instances.string._
import cats.effect.Concurrent
import fs2.Stream

/**
  * Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database.
  *
  * @param network            Which Tezos network to go against
  * @param batchConf          configuration for batched download of node data
  * @param fetchFutureContext thread context for async operations
  */
class NodeOperations(val network: String, batchConf: BatchFetchConfiguration) extends LazyLogging {
  import batchConf.{accountConcurrencyLevel, blockConcurrencyLevel, delegateConcurrencyLevel}
  //use this alias to make signatures easier to be read and kept in-sync

  /** describes capability to run a single remote call returning strings */
  type GetHandler[Eff[_]] = RpcHandler[Eff, String, String]

  /** a monad that can raise and handle Throwables */
  type MonadThrow[Eff[_]] = MonadError[Eff, Throwable]

  /** an applicative that can raise and handle Throwables */
  type ApplicativeThrow[Eff[_]] = ApplicativeError[Eff, Throwable]

  /** generic pair of multiple Ts with their associated block reference */
  type BlockWithMany[T] = (Block, List[T])

  type BlockCoordinates = (BlockHash, Offset)

  type BlockFetchingResults[Eff[_]] = Stream[Eff, (Block, List[AccountId])]

  /* Generic loader of derived entities, associated with some block operation.
   * Can be used to load accounts, delegates and similar entities.
   *
   * @param keyBlockIndexing   the mapping of all keys requested to the block they're referenced by
   * @param entitiesStreamLoad a function that actually loads data for keys related to a single block
   */
  private def getBlockRelatedEntities[Eff[_]: Concurrent, Key, Entity](
    keyBlockIndexing: Map[Key, BlockReference],
    entitiesStreamLoad: Stream[Eff, (BlockHash, Key)] => Stream[Eff, (Key, Entity)]
  ): Stream[Eff, BlockTagged[Map[Key, Entity]]] = {
    import TezosTypes.Syntax._
    import mouse.any._

    //for each hash in the map, get the stream of results and concat them all, keeping the order
    val keyStream: Stream[Eff, (BlockHash, Key)] =
      keyBlockIndexing.groupBy {
        case (key, (blockHash, level)) => blockHash
      }.mapValues(_.keySet.iterator |> Stream.fromIterator[Eff, Key])
        .map {
          case (hash, keys) =>
            Stream.constant[Eff, BlockHash](hash).zip(keys)
        }
        .reduce(_ ++ _)

    entitiesStreamLoad(keyStream).groupAdjacentBy {
      case (key, entity) =>
        keyBlockIndexing(key) //create chunks having the same block reference, relying on how the stream is ordered
    }.map {
      case ((hash, level), keyedEntitiesChunk) =>
        val entitiesMap = keyedEntitiesChunk.foldLeft(Map.empty[Key, Entity]) { _ + _ } //collect to a map each chunk
        entitiesMap.taggedWithBlock(hash, level) //tag with the block reference
    }

  }

  /**
    * Fetches a single block along with associated data from the chain
    *
    * @param hash   hash of the block
    * @param offset pass it to get a relative predecessor of the reference hash
    * @return       the block data wrapped in a `Future`
    */
  def getBlock[Eff[_]: MonadThrow](
    hash: BlockHash,
    offset: Option[Offset] = None
  )(
    implicit blockFetch: DataFetcher.Std[Eff, BlockCoordinates, BlockData],
    opsAndAccountsFetch: DataFetcher.Std[Eff, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[Int]],
    proposalFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[ProtocolId]]
  ): Eff[(Block, List[AccountId])] = {
    import TezosTypes.Lenses._

    val parseMichelsonScripts: Block => Block =
      withLoggingContext[Block, String](makeContext = block => s"block with hash ${block.data.hash.value}") {
        implicit ctx =>
          val codeAlter = codeLens.modify(toMichelsonScript[MichelsonSchema, String])
          val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction, String])
          val parametersAlter = parametersLens.modify(toMichelsonScript[MichelsonInstruction, String])

          codeAlter compose storageAlter compose parametersAlter
      }

    getBareBlock(hash, offset).flatMap(
      data =>
        (getAllOperationsAndAccountsForBlock(data), getCurrentVotesForBlock(data)).mapN {
          case ((operations, accountIds), votes) =>
            parseMichelsonScripts(Block(data, operations, votes)) -> accountIds
        }
    )
  }

  /**
    * Fetches a single block from the chain without associated data
    *
    * @param hash   hash of the block
    * @param offset pass it to get a relative predecessor of the reference hash
    * @tparam Eff   the result effect
    * @return       the block data
    */
  def getBareBlock[Eff[_]: MonadThrow: DataFetcher.Std[?[_], BlockCoordinates, BlockData]](
    hash: BlockHash,
    offset: Option[Offset] = None
  ): Eff[BlockData] =
    fetcher.run(hash -> offset.getOrElse(0))

  /**
    * Gets the block head along with associated data.
    *
    * @tparam Eff the result effect
    * @return     Block head
    */
  def getBlockHead[Eff[_]: MonadThrow](
    implicit blockFetcher: DataFetcher.Std[Eff, BlockCoordinates, BlockData],
    opsAndAccountsFetch: DataFetcher.Std[Eff, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[Int]],
    proposalFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[ProtocolId]]
  ): Eff[Block] =
    getBlock(blockHeadHash).map(_._1)

  /**
    * Gets just the block head without associated data.
    *
    * @tparam Eff the result effect
    * @return     Block head
    */
  def getBareBlockHead[Eff[_]: MonadThrow: DataFetcher.Std[?[_], BlockCoordinates, BlockData]]: Eff[BlockData] =
    getBareBlock(blockHeadHash)

  /**
    * Fetches both operations for a block and accounts referenced by those operations
    * @param block core data of the block
    * @tparam Eff  the result effect
    * @return      The list of operations and account ids
    */
  def getAllOperationsAndAccountsForBlock[Eff[_]: MonadThrow: DataFetcher.Std[
    ?[_],
    BlockHash,
    (List[OperationsGroup], List[AccountId])
  ]](
    block: BlockData
  ): Eff[(List[OperationsGroup], List[AccountId])] =
    //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
    if (isGenesis(block))
      (List.empty[OperationsGroup], List.empty[AccountId]).pure[Eff]
    else
      fetcher.run(block.hash)

  /**
    * Fetches current votes information at the specific block
    *
    * @param block  the core block data
    * @param offset pass it to get a relative predecessor of the reference hash
    * @tparam Eff   the result effect
    * @return Voting for the block
    */
  def getCurrentVotesForBlock[Eff[_]: MonadThrow](
    block: BlockData,
    offset: Option[Offset] = None
  )(
    implicit
    quorumFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[Int]],
    proposalFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[ProtocolId]]
  ): Eff[CurrentVotes] =
    if (isGenesis(block))
      CurrentVotes.empty.pure[Eff]
    else {
      val fetchCurrentQuorum: Kleisli[Eff, BlockCoordinates, Option[Int]] = fetcher
      val fetchCurrentProposal: Kleisli[Eff, BlockCoordinates, Option[ProtocolId]] = fetcher
      (fetchCurrentQuorum, fetchCurrentProposal).mapN(CurrentVotes(_, _)).run(block.hash -> offset.getOrElse(0))
    }

  /**
    * Fetches a specific account for a given block.
    * @param blockHash  Hash of given block
    * @param accountId  Account ID
    * @tparam Eff       the result effect
    * @return           The account
    */
  def getAccountForBlock[Eff[_]: GetHandler: Functor](blockHash: BlockHash, accountId: AccountId): Eff[Account] =
    RpcHandler
      .runGet(s"blocks/${blockHash.value}/context/contracts/${accountId.id}")
      .map(fromJson[Account])

  /**
    * Fetches the manager of a specific account for a given block.
    * @param blockHash  Hash of given block
    * @param accountId  Account ID
    * @tparam effect    the result effect
    * @return           The account
    */
  def getAccountManagerForBlock[Eff[_]: GetHandler: Functor](
    blockHash: BlockHash,
    accountId: AccountId
  ): Eff[ManagerKey] =
    RpcHandler
      .runGet(s"blocks/${blockHash.value}/context/contracts/${accountId.id}/manager_key")
      .map(fromJson[ManagerKey])

  /**
    * Fetches all accounts for a given block.
    * @param blockHash  Hash of given block.
    * @tparam Eff       the result effect
    * @return           Accounts
    */
  def getAllAccountsForBlock[Eff[_]: GetHandler: Concurrent](
    blockHash: BlockHash
  )(
    implicit stdFetch: DataFetcher.Std[Eff, (BlockHash, AccountId), Option[Account]]
  ): Stream[Eff, (AccountId, Account)] =
    for {
      jsonEncodedAccounts <- Stream.eval(RpcHandler.runGet(s"blocks/${blockHash.value}/context/contracts"))
      idList = fromJson[List[String]](jsonEncodedAccounts).map(AccountId)
      continuallyHash = Stream.constant(blockHash, idList.size)
      accountIds = Stream(idList: _*)
      accounts <- getAccountsForBlock[Eff](continuallyHash zip accountIds)
    } yield accounts

  /**
    * Fetches the accounts identified by id
    *
    * @param accountIds the ids
    * @param blockHash  the block storing the accounts, the head block if not specified
    * @tparam Eff       the result effect
    * @return           the list of accounts, indexed by AccountId
    */
  def getAccountsForBlock[Eff[_]: Concurrent: DataFetcher.Std[?[_], (BlockHash, AccountId), Option[Account]]](
    accountIds: Stream[Eff, (BlockHash, AccountId)]
  ): Stream[Eff, (AccountId, Account)] = {
    import TezosOptics.Accounts.{scriptLens, storageLens}

    /* Shows part of the hashes that failed to parse */
    val logError: PartialFunction[Throwable, Stream[Eff, Unit]] = {
      case err: Throwable =>
        val sampleSize = 30
        val logAction = accountIds
          .take(sampleSize + 1)
          .map { case (_, AccountId(id)) => id }
          .compile
          .toVector
          .flatMap { ids =>
            val sample = if (ids.size <= sampleSize) ids else ids.dropRight(1) :+ "..."
            logger.error(s"Could not get accounts' data for ids ${sample.mkString(",")}", err).pure[Eff]
          }
        Stream.eval(logAction)
    }

    def parseMichelsonScripts(id: AccountId): Account => Account =
      withLoggingContext[Account, String](makeContext = _ => s"account with keyhash ${id.id}") { implicit ctx =>
        val scriptAlter = scriptLens.modify(toMichelsonScript[MichelsonSchema, String])
        val storageAlter = storageLens.modify(toMichelsonScript[MichelsonInstruction, String])

        scriptAlter compose storageAlter
      }

    /* Concurrently gets data, then logs any error, issue warns for missing data, eventually
     * parses the account object, if all went well
     */
    accountIds
      .parEvalMap(accountConcurrencyLevel) {
        fetcher.tapWith((_, _)).run
      }
      .onError(logError)
      .evalTap {
        case ((blockHash, accountId), maybeAccount) =>
          Applicative[Eff].whenA(maybeAccount.isEmpty) {
            logger
              .warn(
                "No account was found for key {} and block hash {}, when querying the {} node",
                accountId.id,
                blockHash.value,
                network
              )
              .pure[Eff]
          }
      }
      .collect {
        case ((_, accountId), Some(account)) => accountId -> parseMichelsonScripts(accountId)(account)
      }
  }

  /**
    * Get accounts for all the identifiers passed-in with the corresponding block
    * @param accountsBlocksIndex a map from unique id to the [latest] block reference
    * @return         Accounts with their corresponding block data
    */
  def getAccounts[Eff[_]: Concurrent: DataFetcher.Std[?[_], (BlockHash, AccountId), Option[Account]]](
    accountsBlocksIndex: Map[AccountId, BlockReference]
  ): Stream[Eff, BlockTagged[Map[AccountId, Account]]] =
    getBlockRelatedEntities(
      keyBlockIndexing = accountsBlocksIndex,
      entitiesStreamLoad = getAccountsForBlock[Eff]
    )

  /**
    * Fetches the delegates identified by public key hash, using a fetcher that should already
    * take into account the block referencing the delegate.
    *
    * @param delegateKeys     the pkhs for requested delegates
    * @param fetcherForBlock  data fetcher for delegates, built for given a reference block hash
    * @return                 the stream of delegates, indexed by PublicKeyHash
    */
  def getDelegatesForBlock[Eff[_]: Concurrent: DataFetcher.Std[?[_], (BlockHash, PublicKeyHash), Option[Delegate]]](
    delegateKeys: Stream[Eff, (BlockHash, PublicKeyHash)]
  ): Stream[Eff, (PublicKeyHash, Delegate)] = {

    val logError: PartialFunction[Throwable, Stream[Eff, Unit]] = {
      case err: Throwable =>
        val sampleSize = 30
        val logAction = delegateKeys
          .take(sampleSize + 1)
          .map { case (_, PublicKeyHash(value)) => value }
          .compile
          .toVector
          .flatMap { keys =>
            val sample = if (keys.size <= sampleSize) keys else keys.dropRight(1) :+ "..."
            logger.error(s"Could not get accounts' data for key hashes ${sample.mkString(",")}", err).pure[Eff]
          }
        Stream.eval(logAction)
    }

    /* Concurrently gets data, then logs any error, issue warns for missing data, eventually
     * parses the delegate object, if all went well
     */
    delegateKeys
      .parEvalMap(delegateConcurrencyLevel) {
        fetcher.tapWith((_, _)).run
      }
      .onError(logError)
      .evalTap {
        case ((blockHash, pkh), maybeDelegate) =>
          Applicative[Eff].whenA(maybeDelegate.isEmpty) {
            logger
              .warn(
                "No delegate was found for PKH {} and block hash {}, when querying the {} node",
                pkh.value,
                blockHash.value,
                network
              )
              .pure[Eff]
          }
      }
      .collect {
        case ((_, key), Some(delegate)) => key -> delegate
      }

  }

  //move it to the node operator
  def getDelegates[Eff[_]: Concurrent: DataFetcher.Std[?[_], (BlockHash, PublicKeyHash), Option[Delegate]]](
    keysBlocksIndex: Map[PublicKeyHash, BlockReference]
  ): Stream[Eff, BlockTagged[Map[PublicKeyHash, Delegate]]] =
    getBlockRelatedEntities(
      keyBlockIndexing = keysBlocksIndex,
      entitiesStreamLoad = getDelegatesForBlock[Eff]
    )

  /** Fetches detailed data for voting associated to the passed-in blocks */
  def getVotingDetails[Eff[_]: MonadThrow](block: Block)(
    implicit
    proposalFetcher: DataFetcher.Std[Eff, Block, List[(ProtocolId, ProposalSupporters)]],
    bakersFetch: DataFetcher.Std[Eff, Block, List[Voting.BakerRolls]],
    ballotsFetcher: DataFetcher.Std[Eff, Block, List[Voting.Ballot]]
  ): Eff[(Voting.Proposal, BlockWithMany[Voting.BakerRolls], BlockWithMany[Voting.Ballot])] = {

    //adapt the proposal protocols result to include the block
    val fetchProposals =
      fetcher[Eff, Block, List[(ProtocolId, ProposalSupporters)], Throwable].tapWith {
        case (block, protocols) => Voting.Proposal(protocols, block)
      }

    val fetchBakers =
      fetcher[Eff, Block, List[Voting.BakerRolls], Throwable].tapWith(_ -> _)

    val fetchBallots =
      fetcher[Eff, Block, List[Voting.Ballot], Throwable].tapWith(_ -> _)

    /* combine the three kleisli operations to return a tuple of the results
     * and then run the composition on the input block
     */
    (fetchProposals, fetchBakers, fetchBallots).tupled.run(block)
  }

  /** Gets all blocks from the head down to the oldest block not already in the database.
    * @param fetchLocalMaxLevel should read the current top-level available for the chain, as stored in conseil
    * @return Blocks and Account hashes involved, with a total of the chain level to expect
    */
  def getBlocksNotInDatabase[Eff[_]: MonadThrow: Concurrent](
    fetchLocalMaxLevel: => Eff[Int]
  )(
    implicit blockFetcher: DataFetcher.Std[Eff, BlockCoordinates, BlockData],
    opsAndAccountsFetch: DataFetcher.Std[Eff, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[Int]],
    proposalFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[ProtocolId]],
    m: Monoid[BlockFetchingResults[Eff]]
  ): Eff[(BlockFetchingResults[Eff], Int)] =
    for {
      localLevel <- fetchLocalMaxLevel
      blockHead <- getBlockHead
    } yield {
      val headLevel = blockHead.data.header.level
      val headHash = blockHead.data.hash
      val bootstrapping = localLevel == -1

      if (localLevel < headLevel) {
        //got something to load
        if (bootstrapping) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
        else
          logger.info(
            "I found the new block head at level {}, the currently stored max is {}. I'll fetch the missing {} blocks.",
            headLevel,
            localLevel,
            headLevel - localLevel
          )
        val minLevel = if (bootstrapping) 1 else localLevel
        getBlocksInLevelRamge((headHash, headLevel), localLevel + 1 to headLevel) -> (headLevel - minLevel)
      } else {
        logger.info("No new blocks to fetch from the network")
        m.empty -> 0
      }
    }

  /** Gets last `depth` blocks.
    * @param depth Number of latest block to fetch, `None` to get all
    * @param headHash Hash of a block from which to start, None to start from a real head
    * @return Blocks and Account hashes involved, paired with the computed result size, based on a level range
    */
  def getLatestBlocks[Eff[_]: MonadThrow: Concurrent](
    depth: Option[Int] = None,
    headHash: Option[BlockHash] = None
  )(
    implicit blockFetcher: DataFetcher.Std[Eff, BlockCoordinates, BlockData],
    opsAndAccountsFetch: DataFetcher.Std[Eff, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[Int]],
    proposalFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[ProtocolId]]
  ): Eff[(BlockFetchingResults[Eff], Int)] =
    headHash.fold(getBareBlockHead)(getBareBlock(_)).map { maxHead =>
      val headLevel = maxHead.header.level
      val headHash = maxHead.hash
      val minLevel = depth.fold(0)(d => max(0, headLevel - d + 1))
      getBlocksInLevelRamge((headHash, headLevel), minLevel to headLevel) -> (headLevel - minLevel + 1)
    }

  /** Gets block from Tezos Blockchains, as well as their associated operation, from minLevel to maxLevel.
    * @param reference Hash and level of a known block
    * @param levelRange a range of levels to load
    * @return the list of blocks with relative account ids touched in the operations
    */
  private def getBlocksInLevelRamge[Eff[_]: MonadThrow: Concurrent](
    reference: (BlockHash, Int),
    levelRange: Range.Inclusive
  )(
    implicit blockFetcher: DataFetcher.Std[Eff, BlockCoordinates, BlockData],
    opsAndAccountsFetch: DataFetcher.Std[Eff, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[Int]],
    proposalFetch: DataFetcher.Std[Eff, BlockCoordinates, Option[ProtocolId]]
  ): BlockFetchingResults[Eff] = {
    logger.info("Fetching block data in range: " + levelRange)
    val (hashRef, levelRef) = reference
    require(levelRange.start >= 0 && levelRange.end <= levelRef)

    //build offsets in descending order, using level bounds, to load oldest blocks first
    val offsets = Stream.range(levelRange.start, levelRange.end + 1).map(lvl => levelRef - lvl)
    //Gets blocks data for the requested offsets and associates the operations and account hashes available involved in said operations
    //Special care is taken for the genesis block (level = 0) that doesn't have operations defined, we use empty data for it

    offsets
      .lift[Eff]
      .prefetchN(blockConcurrencyLevel)
      .parEvalMap(blockConcurrencyLevel)(
        offset => getBlock(hashRef, offset.some)
      )

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
  private[this] def toMichelsonScript[T <: MichelsonElement: Parser, CTX: Show](
    json: String
  )(implicit tag: ClassTag[T], ctx: CTX): String = {
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

  /* Used to provide an implicit logging context (of type CTX) to the michelson script conversion method
   * It essentially defines an extraction mechanism to define a generic logging context from the conversion input (i.e. `makeContext`)
   * and then provides both the context and the original input to another function that will use both.
   *
   * We require that the `CTX` type can be printed to be logged, so a Show instance must be available to convert it to a String.
   */
  private[this] def withLoggingContext[T, CTX: Show](makeContext: T => CTX)(function: CTX => T => T): T => T =
    (t: T) => {
      function(makeContext(t))(t)
    }

}
