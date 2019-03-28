package tech.cryptonomic.conseil.tezos

import cats._
import cats.data.Kleisli
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import tech.cryptonomic.conseil.generic.chain.DataFetcher
import tech.cryptonomic.conseil.util.JsonUtil
import tech.cryptonomic.conseil.util.JsonUtil.{JsonString, adaptManagerPubkeyField}
import tech.cryptonomic.conseil.util.CollectionOps._
import TezosTypes._

/** Defines intances of `DataFetcher` for block-related data */
trait BlocksDataFetchers {
  //we require the cabability to log
  self: LazyLogging =>
  import cats.instances.future._
  import cats.syntax.applicativeError._
  import cats.syntax.applicative._
  import JsonDecoders.Circe.decodeLiftingTo

  implicit def fetchFutureContext: ExecutionContext

  /** the tezos network to connect to */
  def network: String
  /** the tezos interface to query */
  def node: TezosRPCInterface
  /** parallelism in the multiple requests decoding on the RPC interface */
  def fetchConcurrency: Int

  /* reduces repetion in error handling */
  private def logErrorOnJsonDecoding[Encoded](message: String): PartialFunction[Throwable, Future[Unit]] = {
    case decodingError: io.circe.Error =>
      logger.error(message, decodingError).pure[Future]
    case t =>
      logger.error("Something unexpected failed while decoding json", t).pure[Future]
  }

  /* reduces repetion in error handling, used when the failure is expected to be recovered */
  private def logWarnOnJsonDecoding[Encoded](message: String): PartialFunction[Throwable, Future[Unit]] = {
    case decodingError: io.circe.Error =>
      logger.warn(message, decodingError).pure[Future]
    case t =>
      logger.error("Something unexpected failed while decoding json", t).pure[Future]
  }

  //common type alias to simplify signatures
  private type FutureFetcher = DataFetcher[Future, List, Throwable]

  /** a fetcher of blocks */
  def blocksFetcher(hashRef: BlockHash) = new FutureFetcher {
    import JsonDecoders.Circe.Blocks._

    type Encoded = String
    type In = Int //offset from head
    type Out = BlockData

    def makeUrl = (offset: In) => s"blocks/${hashRef.value}~${String.valueOf(offset)}"

    //fetch a future stream of values
    override val fetchData =
      Kleisli(offsets =>
        node.runBatchedGetQuery(network, offsets, makeUrl, fetchConcurrency)
          .onError { case err =>
            logger.error("I encountered problems while fetching blocks data from {}, for offsets {} from the {}. The error says {}",
              network,
              offsets.onBounds((first, last) => s"$first to $last").getOrElse("unspecified"),
              hashRef,
              err.getMessage
            ).pure[Future]
          }
      )

    // decode with `JsonDecoders`
    override val decodeData = Kleisli {
      json =>
        decodeLiftingTo[Future, Out](json)
          .onError(logErrorOnJsonDecoding(s"I fetched a block definition from tezos node that I'm unable to decode: $json"))
    }

  }

  /** decode account ids from operation json results with the `cats.Id` effect, i.e. a total function with no effect */
  val accountIdsJsonDecode: Kleisli[Id, String, List[AccountId]] =
    Kleisli[Id, String, List[AccountId]] {
      case JsonUtil.AccountIds(id, ids @ _*) =>
        (id :: ids.toList).distinct.map(AccountId)
      case _ =>
        List.empty
    }

  /** a fetcher of operation groups from block hashes */
  val operationGroupFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Operations._

    type Encoded = String
    type In = BlockHash
    type Out = List[OperationsGroup]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/operations"

    override val fetchData =
      Kleisli(hashes =>
        node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency)
          .onError { case err =>
            logger.error("I encountered problems while fetching operations from {}, for blocks {}. The error says {}",
              network,
              hashes.map(_.value).mkString(", "),
              err.getMessage
            ).pure[Future]
          }
      )

    override val decodeData = Kleisli(
      json =>
        decodeLiftingTo[Future, List[Out]](adaptManagerPubkeyField(JsonString.sanitize(json)))
          .map(_.flatten)
          .onError(logErrorOnJsonDecoding(s"I fetched some operations json from tezos node that I'm unable to decode into operation groups: $json"))
    )

  }

  /** a fetcher for the current quorum of blocks */
  val currentQuorumFetcher = new FutureFetcher {

    type Encoded = String
    type In = BlockHash
    type Out = Option[Int]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_quorum"

    override val fetchData =
      Kleisli(hashes =>
        node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency)
          .onError { case err =>
            logger.error("I encountered problems while fetching quorums from {}, for blocks {}. The error says {}",
              network,
              hashes.map(_.value).mkString(", "),
              err.getMessage
            ).pure[Future]
          }
      )

    override val decodeData = Kleisli(
      json =>
        decodeLiftingTo[Future, Out](json)
          .onError(logWarnOnJsonDecoding(s"I fetched current quorum json from tezos node that I'm unable to decode: $json"))
          .recover {
            case NonFatal(_) => Option.empty
          }
    )

  }

  /** a fetcher for the current proposals of blocks */
  val currentProposalFetcher = new FutureFetcher {
    import JsonDecoders.Circe._

    type Encoded = String
    type In = BlockHash
    type Out = Option[ProtocolId]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_proposal"

    override val fetchData =
      Kleisli(hashes =>
        node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency)
          .onError { case err =>
            logger.error("I encountered problems while fetching current proposals from {}, for blocks {}. The error says {}",
              network,
              hashes.map(_.value).mkString(", "),
              err.getMessage
            ).pure[Future]
          }
      )

    override val decodeData = Kleisli(
      json =>
        decodeLiftingTo[Future, Out](json)
          .onError(logWarnOnJsonDecoding(s"I fetched a proposal protocol json from tezos node that I'm unable to decode: $json"))
          .recover {
            case NonFatal(_) => Option.empty
          }
    )

  }

  /** a fetcher for all proposals for blocks */
  val proposalsMultiFetch = new FutureFetcher {
    import JsonDecoders.Circe._
    import cats.instances.future._


    type Encoded = String
    type In = Block
    type Out = List[ProtocolId]

    val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/proposals"

    override val fetchData =
      Kleisli(blocks =>
        node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency)
          .onError { case err =>
            logger.error("I encountered problems while fetching proposals details from {}, for blocks {}. The error says {}",
              network,
              blocks.map(_.data.hash.value).mkString(", "),
              err.getMessage
            ).pure[Future]
          }
      )

    override val decodeData = Kleisli{
      json =>
        decodeLiftingTo[Future, Out](json)
          .onError(logWarnOnJsonDecoding(s"I fetched voting proposal protocols json from tezos node that I'm unable to decode: $json"))
          .recover{
            //we recover parsing failures with an empty result, as we have no optionality here to lean on
            case NonFatal(_) => List.empty
          }
  }
  }

  /** a fetcher of baker rolls for blocks */
  val bakersMultiFetch = new FutureFetcher {
    import JsonDecoders.Circe.Votes._
    import cats.instances.future._


    type Encoded = String
    type In = Block
    type Out = List[Voting.BakerRolls]

    val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/listings"

    override val fetchData =
      Kleisli(blocks =>
        node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency)
          .onError { case err =>
            logger.error("I encountered problems while fetching baker rolls from {}, for blocks {}. The error says {}",
              network,
              blocks.map(_.data.hash.value).mkString(", "),
              err.getMessage
            ).pure[Future]
          }
      )

    override val decodeData = Kleisli{
      json =>
        decodeLiftingTo[Future, Out](json)
          .onError(logWarnOnJsonDecoding(s"I fetched baker rolls json from tezos node that I'm unable to decode: $json"))
          .recover{
            //we recover parsing failures with an empty result, as we have no optionality here to lean on
            case NonFatal(_) => List.empty
          }
    }
  }

 /** a fetcher of ballot votes for blocks */
 val ballotsMultiFetch = new FutureFetcher {
    import JsonDecoders.Circe.Votes._
    import cats.instances.future._


    type Encoded = String
    type In = Block
    type Out = List[Voting.Ballot]

    val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/ballot_list"

    override val fetchData =
      Kleisli(blocks =>
        node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency)
          .onError { case err =>
            logger.error("I encountered problems while fetching ballot votes from {}, for blocks {}. The error says {}",
              network,
              blocks.map(_.data.hash.value).mkString(", "),
              err.getMessage
            ).pure[Future]
          }
    )

    override val decodeData = Kleisli{
      json =>
        decodeLiftingTo[Future, Out](json)
          .onError(logWarnOnJsonDecoding(s"I fetched ballot votes json from tezos node that I'm unable to decode: $json"))
          .recover{
            //we recover parsing failures with an empty result, as we have no optionality here to lean on
            case NonFatal(_) => List.empty
          }
    }
  }

}

/** Defines intances of `DataFetcher` for accounts-related data */
trait AccountsDataFetchers {
  //we require the cabability to log
  self: LazyLogging =>
  import cats.instances.future._
  import cats.syntax.applicativeError._
  import cats.syntax.applicative._
  import JsonDecoders.Circe.decodeLiftingTo

  implicit def fetchFutureContext: ExecutionContext

  /* reduces repetion in error handling */
  private def logWarnOnJsonDecoding[Encoded](message: String): PartialFunction[Throwable, Future[Unit]] = {
    case decodingError: io.circe.Error =>
      logger.warn(message, decodingError).pure[Future]
    case t =>
      logger.error("Something unexpected failed while decoding json", t).pure[Future]
  }

  /** the tezos network to connect to */
  def network: String
  /** the tezos interface to query */
  def node: TezosRPCInterface
  /** parallelism in the multiple requests decoding on the RPC interface */
  def accountsFetchConcurrency: Int

  //common type alias to simplify signatures
  private type FutureFetcher = DataFetcher[Future, List, Throwable]

  def accountFetcher(referenceBlock: BlockHash) = new FutureFetcher {
    import JsonDecoders.Circe.Accounts._

    type Encoded = String
    type In = AccountId
    type Out = Option[Account]

    val makeUrl = (id: AccountId) => s"blocks/${referenceBlock.value}/context/contracts/${id.id}"

    override val fetchData = Kleisli(
      ids =>
        node.runBatchedGetQuery(network, ids, makeUrl, accountsFetchConcurrency)
          .onError {
            case err =>
              logger.error("I encountered problems while fetching account data from {}, for ids {}. The error says {}",
                network,
                ids.map(_.id).mkString(", "),
                err.getMessage
              ).pure[Future]
          }
      )

    override def decodeData = Kleisli {
      json =>
        decodeLiftingTo[Future, Account](json)
          .map(Some(_))
          .onError(logWarnOnJsonDecoding(s"I fetched an account json from tezos node that I'm unable to decode: $json"))
          .recover{
            //we need to consider that some accounts failed to be written in the chain, though we have ids in the block
            case NonFatal(_) => Option.empty
          }
    }
  }

}