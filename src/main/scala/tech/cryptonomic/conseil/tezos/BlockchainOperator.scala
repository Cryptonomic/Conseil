package tech.cryptonomic.conseil.tezos

import tech.cryptonomic.conseil.generic.chain.{DataFetcher, RemoteRpc}
import tech.cryptonomic.conseil.generic.chain.DataFetcher.{fetch, fetchMerge}
import tech.cryptonomic.conseil.tezos.TezosTypes._
import tech.cryptonomic.conseil.tezos.michelson.JsonToMichelson
import tech.cryptonomic.conseil.tezos.michelson.dto.{MichelsonCode, MichelsonElement, MichelsonExpression, MichelsonSchema}
import tech.cryptonomic.conseil.tezos.michelson.parser.JsonParser
import tech.cryptonomic.conseil.util.JsonUtil.{fromJson, JsonString => JS}
import com.typesafe.scalalogging.Logger
import cats.{Functor, MonadError, Monoid}
import cats.data.Reader
import cats.syntax.applicative._
import cats.syntax.applicativeError._
import cats.syntax.apply._
import cats.syntax.flatMap._
import cats.syntax.functor._
import scala.math.max
import scala.reflect.ClassTag
import scala.util.Try

/** Operations run against Tezos nodes, mainly used for collecting chain data for later entry into a database. */
trait BlockchainOperator {

  /** which tezos network to go against */
  def network: String
  /** assumes some capability to log */
  def logger: Logger

  //use this aliases to make signatures easier to read and kept in-sync

  /** the whole results of reading latest info from the chain */
  type BlockFetchingResults = List[(Block, List[AccountId])]
  /** describes capability to run a single remote call returning strings*/
  type RpcGet[F[_]] = RemoteRpc.Basic[F, String, _]
  /** describes capability to run a single remote call returning strings, passing a payload*/
  type RpcPost[F[_], Payload] = RemoteRpc.Basic[F, String, Payload]
  /** complex fetching of multiple data, collected in Lists (both input and corresponding outputs), with internal string encoding */
  type ListFetcher[F[_], In, Out] = DataFetcher.Aux[F, List, Throwable, In, Out, String]
  /** a monad that can raise and handle Throwables */
  type MonadThrow[F[_]] = MonadError[F, Throwable]
  /** generic pair of multiple Ts with their associated block reference */
  type BlockRelated[T] = (Block, List[T])

  /**
    * Fetches a specific account for a given block.
    * @param blockHash  Hash of given block
    * @param accountId  Account ID
    * @return           The account
    */
  def getAccountForBlock[F[_] : RpcGet : Functor](blockHash: BlockHash, accountId: AccountId): F[Account] =
    RemoteRpc.runGet(s"blocks/${blockHash.value}/context/contracts/${accountId.id}")
      .map(fromJson[Account])

  /**
    * Fetches the manager of a specific account for a given block.
    * @param blockHash  Hash of given block
    * @param accountId  Account ID
    * @return           The account's manager key
    */
  def getAccountManagerForBlock[F[_] : RpcGet : Functor](blockHash: BlockHash, accountId: AccountId): F[ManagerKey] =
    RemoteRpc.runGet(s"blocks/${blockHash.value}/context/contracts/${accountId.id}/manager_key")
      .map(fromJson[ManagerKey])

  /**
    * Fetches all accounts for a given block.
    * @param blockHash  Hash of given block.
    * @return           Accounts
    */
  def getAllAccountsForBlock[F[_] : MonadThrow : RpcGet](blockHash: BlockHash)
    (implicit fetchProvider: Reader[BlockHash, ListFetcher[F, AccountId, Option[Account]]]): F[Map[AccountId, Account]] = {

    for {
      jsonEncodedAccounts <- RemoteRpc.runGet(s"blocks/${blockHash.value}/context/contracts")
      accountIds = fromJson[List[String]](jsonEncodedAccounts).map(AccountId)
      accounts <- getAccountsForBlock(accountIds, blockHash)
    } yield accounts
  }

  /**
    * Fetches the accounts identified by id
    *
    * @param accountIds the ids
    * @param blockHash  the block storing the accounts, the head block if not specified
    * @return           the list of accounts, indexed by AccountId
    */
  def getAccountsForBlock[F[_] : MonadThrow](accountIds: List[AccountId], blockHash: BlockHash = blockHeadHash)
    (implicit fetchProvider: Reader[BlockHash, ListFetcher[F, AccountId, Option[Account]]]): F[Map[AccountId, Account]] = {
      import TezosOptics.Accounts.optionalScriptCode
      import cats.instances.list._

      implicit val fetcher = fetchProvider(blockHash)

      /*tries decoding but simply returns the input unchanged on failure*/
      def parseScript(code: String): String = Try(toMichelsonScript[MichelsonCode](code)).getOrElse(code)

      val fetchedAccounts: F[List[(AccountId, Option[Account])]] =
        fetch[AccountId, Option[Account], F, List, Throwable].run(accountIds)

      fetchedAccounts.map(
        indexedAccounts =>
          indexedAccounts.collect {
            case (accountId, Some(account)) => accountId -> optionalScriptCode.modify(parseScript)(account)
          }.toMap
      )
    }

  /**
    * Get accounts for all the identifiers passed-in with the corresponding block
    * @param accountsBlocksIndex a map from unique id to the [latest] block reference
    * @return         Accounts with their corresponding block data
    *
    */
  @deprecated(message = "This might not be needed anymore", since = "April 2019")
  def getAccountsForBlocks[F[_] : MonadThrow](accountsBlocksIndex: Map[AccountId, BlockReference])
    (implicit fetchProvider: Reader[BlockHash, ListFetcher[F, AccountId, Option[Account]]]): F[List[BlockAccounts]] = {

    def notifyAnyLostIds(missing: Set[AccountId]) =
      if (missing.nonEmpty) {
        logger.warn(
          "The following account keys were not found querying the {} node: {}",
          network,
          missing.map(_.id).mkString("\n", ",", "\n")
        )
      }

    //uses the index to collect together BlockAccounts matching the same block
    def groupByLatestBlock(data: Map[AccountId, Account]): List[BlockAccounts] =
      data.groupBy {
        case (id, _) => accountsBlocksIndex(id)
      }.map {
        case ((hash, level), accounts) => BlockAccounts(hash, level, accounts)
      }.toList

    //fetch accounts by requested ids and group them together with corresponding blocks
    getAccountsForBlock(accountsBlocksIndex.keys.toList)
      .onError{
        case err =>
          val showSomeIds = accountsBlocksIndex.keys.take(30).map(_.id).mkString("", ",", if (accountsBlocksIndex.size > 30) "..." else "")
          logger.error(s"Could not get accounts' data for ids ${showSomeIds}", err).pure[F]
      }
      .map {
        accountsMap =>
          notifyAnyLostIds(accountsBlocksIndex.keySet -- accountsMap.keySet)
          groupByLatestBlock(accountsMap)
      }

  }

  /**
    * Fetches operations for a block, without waiting for the result
    * @param blockHash Hash of the block
    * @return          The list of operations
    */
  def getAllOperationsForBlock[F[_] : RpcGet : MonadThrow](block: BlockData): F[List[OperationsGroup]] = {
    import JsonDecoders.Circe.decodeLiftingTo
    import JsonDecoders.Circe.Operations._
    import tech.cryptonomic.conseil.util.JsonUtil.adaptManagerPubkeyField

    //parse json, and try to convert to objects, converting failures to a failed F monad
    //we could later improve by "accumulating" all errors in a single failed monad, with `decodeAccumulating`
    def decodeOperations(json: String) =
      decodeLiftingTo[F, List[List[OperationsGroup]]](adaptManagerPubkeyField(JS.sanitize(json)))
        .map(_.flatten)

    if (isGenesis(block))
      List.empty.pure //This is a workaround for the Tezos node returning a 404 error when asked for the operations or accounts of the genesis blog, which seems like a bug.
    else
      RemoteRpc.runGet(s"blocks/${block.hash.value}/operations")
        .flatMap(decodeOperations)
  }

  /** Fetches current votes information at the specific block */
  def getCurrentVotesForBlock[F[_] : RpcGet : MonadThrow](block: BlockData, offset: Option[Offset] = None): F[CurrentVotes] =
    if (isGenesis(block))
      CurrentVotes.empty.pure
    else {
      import JsonDecoders.Circe._

      val offsetString = offset.map(_.toString).getOrElse("")
      val hashString = block.hash.value

      val fetchCurrentQuorum =
        RemoteRpc.runGet(s"blocks/$hashString~$offsetString/votes/current_quorum") flatMap { json =>
          decodeLiftingTo[F, Option[Int]](json)
        }

      val fetchCurrentProposal =
        RemoteRpc.runGet(s"blocks/$hashString~$offsetString/votes/current_proposal") flatMap { json =>
          decodeLiftingTo[F, Option[ProtocolId]](json)
        }

      (fetchCurrentQuorum, fetchCurrentProposal).mapN(CurrentVotes.apply)
    }

  /** Fetches detailed data for voting associated to the passed-in blocks */
  def getVotingDetails[F[_] : MonadThrow](blocks: List[Block])
    (implicit
      proposalFetcher: ListFetcher[F, Block, List[ProtocolId]],
      bakersFetch: ListFetcher[F, Block, List[Voting.BakerRolls]],
      ballotsFetcher: ListFetcher[F, Block, List[Voting.Ballot]]
    ): F[(List[Voting.Proposal], List[BlockRelated[Voting.BakerRolls]], List[BlockRelated[Voting.Ballot]])] = {
    import cats.instances.list._

    //adapt the proposal protocols result to include the block
    val fetchProposals =
      fetch[Block, List[ProtocolId], F, List, Throwable]
        .map {
          proposalsList => proposalsList.map {
            case (block, protocols) => Voting.Proposal(protocols, block)
          }
        }

    val fetchBakers =
      fetch[Block, List[Voting.BakerRolls], F, List, Throwable]

    val fetchBallots =
      fetch[Block, List[Voting.Ballot], F, List, Throwable]


    /* combine the three kleisli operations to return a tuple of the results
     * and then run the composition on the input blocks
     */
    (fetchProposals, fetchBakers, fetchBallots).tupled.run(blocks.filterNot(b => isGenesis(b.data)))
  }

  /** Fetches a single block from the chain, without waiting for the result
    * @param hash Hash of the block
    * @param offset an offset level to use from the passed hash, optionally
    * @return the block data
    */
  def getBlock[F[_] : RpcGet : MonadThrow](hash: BlockHash, offset: Option[Offset] = None): F[Block] = {
    import JsonDecoders.Circe.decodeLiftingTo
    import JsonDecoders.Circe.Blocks._

    val offsetString = offset.fold("")(_.toString)

    val fetchBlock =
      RemoteRpc.runGet(s"blocks/${hash.value}~$offsetString") flatMap { json =>
        decodeLiftingTo[F, BlockData](JS.sanitize(json))
      }

    for {
      block <- fetchBlock
      ops <- getAllOperationsForBlock(block)
      votes <- getCurrentVotesForBlock(block)
    } yield Block(block, ops, votes)

  }

  /** Gets the block head.
    * @return Block head
    */
  def getBlockHead[F[_] : RpcGet : MonadThrow](): F[Block] =
    getBlock(blockHeadHash)

  /** Gets all blocks from the head down to the oldest block not already in the database.
   *  @param fetchLocalMaxLevel should read the current top-level available for the chain, as stored in conseil
    * @return Blocks and Account hashes involved
    */
  def getBlocksNotInDatabase[F[_] : RpcGet : MonadThrow](fetchLocalMaxLevel: () => F[Int])
  (implicit
    blockDataFetchProvider: Reader[BlockHash, ListFetcher[F, Offset, BlockData]],
    additionalDataFetcher: ListFetcher[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: ListFetcher[F, BlockHash, Option[Int]],
    proposalFetcher: ListFetcher[F, BlockHash, Option[ProtocolId]],
    resultMonoid: Monoid[BlockFetchingResults]
  ): F[BlockFetchingResults] =
    for {
      maxLevel <- fetchLocalMaxLevel()
      blockHead <- getBlockHead()
      headLevel = blockHead.data.header.level
      headHash = blockHead.data.hash
      bootstrapping = maxLevel == -1
      results <-
        if (maxLevel < headLevel) {
          //got something to load
          if (bootstrapping) logger.warn("There were apparently no blocks in the database. Downloading the whole chain..")
          else logger.info("I found the new block head at level {}, the currently stored max is {}. I'll fetch the missing {} blocks.", headLevel, maxLevel, headLevel - maxLevel)
          getBlocks((headHash, headLevel), maxLevel + 1 to headLevel)
        } else {
          logger.info("No new blocks to fetch from the network")
          resultMonoid.empty.pure
        }
    } yield results

  /** Gets last `depth` blocks.
    * @param depth      Number of latest block to fetch, `None` to get all
    * @param headHash   Hash of a block from which to start, None to start from a real head
    * @return           Blocks and Account hashes involved
    */
  def getLatestBlocks[F[_] : RpcGet : MonadThrow](depth: Option[Int] = None, headHash: Option[BlockHash] = None)
  (implicit
    blockDataFetchProvider: Reader[BlockHash, ListFetcher[F, Offset, BlockData]],
    additionalDataFetcher: ListFetcher[F, BlockHash, (List[OperationsGroup], List[AccountId])],
    quorumFetcher: ListFetcher[F, BlockHash, Option[Int]],
    proposalFetcher: ListFetcher[F, BlockHash, Option[ProtocolId]]
  ): F[BlockFetchingResults] =
    headHash.fold(getBlockHead())(getBlock(_))
      .flatMap {
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
  private def getBlocks[F[_] : MonadThrow](reference: (BlockHash, Int), levelRange: Range.Inclusive)
    (implicit
      blockDataFetchProvider: Reader[BlockHash, ListFetcher[F, Offset, BlockData]],
      additionalDataFetcher: ListFetcher[F, BlockHash, (List[OperationsGroup], List[AccountId])],
      quorumFetcher: ListFetcher[F, BlockHash, Option[Int]],
      proposalFetcher: ListFetcher[F, BlockHash, Option[ProtocolId]]
    ): F[BlockFetchingResults] = {
      import cats.instances.list._
      import TezosTypes.Lenses._

      val (hashRef, levelRef) = reference
      require(levelRange.start >= 0 && levelRange.end <= levelRef)
      val offsets = levelRange.map(lvl => levelRef - lvl).toList

      implicit val blockDataFetcher = blockDataFetchProvider(hashRef)
      val proposalsStateFetch = fetchMerge(quorumFetcher, proposalFetcher)(CurrentVotes.apply)

      val parseMichelsonScripts: Block => Block = {
        val codeAlter = codeLens.modify(toMichelsonScript[MichelsonSchema])
        val storageAlter = storageLens.modify(toMichelsonScript[MichelsonExpression])
        val parametersAlter = parametersLens.modify(toMichelsonScript[MichelsonExpression])

        codeAlter compose storageAlter compose parametersAlter
      }

      //Gets blocks data for the requested offsets and associates the operations and account hashes available involved in said operations
      //Special care is taken for the genesis block (level = 0) that doesn't have operations defined, we use empty data for it
      for {
        fetchedBlocksData <- fetch[Offset, BlockData, F, List, Throwable].run(offsets)
        blockHashes = fetchedBlocksData.collect{ case (offset, block) if !isGenesis(block) => block.hash }
        fetchedOperationsWithAccounts <- fetch[BlockHash, (List[OperationsGroup], List[AccountId]), F, List, Throwable].run(blockHashes)
        proposalsState <- proposalsStateFetch.run(blockHashes)
      } yield {
        val operationalDataMap = fetchedOperationsWithAccounts.map{ case (hash, (ops, accounts)) => (hash, (ops, accounts))}.toMap
        val proposalsMap = proposalsState.toMap
        fetchedBlocksData.map {
          case (offset, md) =>
            val (ops, accs) = if (isGenesis(md)) (List.empty, List.empty) else operationalDataMap(md.hash)
            val votes = proposalsMap.getOrElse(md.hash, CurrentVotes.empty)
            (parseMichelsonScripts(Block(md, ops, votes)), accs)
        }
      }

    }

  private def toMichelsonScript[T <: MichelsonElement : JsonParser.Parser](json: Any)(implicit tag: ClassTag[T]): String = {
    val UNPARSABLE_CODE_PLACEMENT = "Unparsable code: "
    import JsonToMichelson._
    import tech.cryptonomic.conseil.util.Conversion.Syntax._

    Some(json).collect {
      case t: String => t.convertToA[Either[Throwable, ?], String]
      case t: Micheline => t.expression.convertToA[Either[Throwable, ?], String]
    } match {
      case Some(Right(value)) => value
      case Some(Left(t)) =>
        logger.error(s"${tag.runtimeClass}: Error during conversion of $json", t)
        UNPARSABLE_CODE_PLACEMENT + json
      case _ =>
        logger.error(s"${tag.runtimeClass}: Error during conversion of $json")
        UNPARSABLE_CODE_PLACEMENT + json
    }
  }

}