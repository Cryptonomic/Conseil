package tech.cryptonomic.conseil.tezos

import cats._
import cats.data.Kleisli
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import tech.cryptonomic.conseil.generic.chain.{DataFetcher, RemoteRpc}
import tech.cryptonomic.conseil.tezos.TezosRemoteInstances.Akka.RemoteContext
import tech.cryptonomic.conseil.util.JsonUtil
import tech.cryptonomic.conseil.util.JsonUtil.{JsonString, adaptManagerPubkeyField}
import TezosTypes._

object BlocksDataFetchers {
  import TezosRemoteInstances.Akka.Streams.ConcurrencyLevel

  def apply(streamConcurrency: ConcurrencyLevel)(implicit actorSystem: ActorSystem, rpc: RemoteContext, ec: ExecutionContext) =
    new BlocksDataFetchers with TezosRemoteInstances.Akka.Streams with LazyLogging {
      override implicit val system = actorSystem
      override implicit val tezosContext = rpc
      override implicit val fetchFutureContext = ec
      override val fetchConcurrency: Int = streamConcurrency
    }
}

/** Defines intances of `DataFetcher` for block-related data */
trait BlocksDataFetchers {
  //we require the cabability to log
  self: LazyLogging with TezosRemoteInstances.Akka.Streams =>
  import cats.data.Reader
  import cats.instances.future._
  import cats.syntax.applicativeError._
  import cats.syntax.applicative._
  import JsonDecoders.Circe.decodeLiftingTo
  import TezosRemoteInstances.Akka.Streams.StreamSource

  implicit def system: ActorSystem
  implicit def tezosContext: RemoteContext
  implicit def fetchFutureContext: ExecutionContext
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

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

  /** a fetcher factory for blocks, based on a reference hash */
  implicit val blocksFetcherProvider: Reader[BlockHash, DataFetcher.Aux[Future, List, Throwable, Offset, BlockData, String]] = Reader( (hashRef: BlockHash) =>
    new FutureFetcher {
      import JsonDecoders.Circe.Blocks._

      type Encoded = String
      type In = Offset
      type Out = BlockData

      def makeUrl = (offset: Offset) => s"blocks/${hashRef.value}~${String.valueOf(offset)}"

      // lazy val failureHandler = (offset: Offset, error: Throwable) =>
      //   logger.error("I encountered problems while fetching block data from {}, for offset reference {} . The error says {}",
      //     network,
      //     s"${hashRef.value}~${String.valueOf(offset)}",
      //     error.getMessage
      //   )

      //fetch a future stream of values
      override val fetchData = Kleisli {
        offsets =>
          val inStream: StreamSource[In] = Source(offsets)
          RemoteRpc.runGet(fetchConcurrency, inStream, makeUrl)
            .runFold(List.empty[(In, String)])(_ :+ _)
      }

      // decode with `JsonDecoders`
      override val decodeData = Kleisli {
        json =>
          decodeLiftingTo[Future, Out](json)
            .onError(logErrorOnJsonDecoding(s"I fetched a block definition from tezos node that I'm unable to decode: $json"))
      }

    }
  )

  /** decode account ids from operation json results with the `cats.Id` effect, i.e. a total function with no effect */
  val accountIdsJsonDecode: Kleisli[Id, String, List[AccountId]] =
    Kleisli[Id, String, List[AccountId]] {
      case JsonUtil.AccountIds(id, ids @ _*) =>
        (id :: ids.toList).distinct.map(AccountId)
      case _ =>
        List.empty
    }

  /** a fetcher of operation groups from block hashes */
  implicit val operationGroupFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Operations._

    type Encoded = String
    type In = BlockHash
    type Out = List[OperationsGroup]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/operations"

    // lazy val failureHandler = (hash: BlockHash, error: Throwable) =>
    //   logger.error("I encountered problems while fetching baker operations from {}, for block {}. The error says {}",
    //     network,
    //     hash.value,
    //     error.getMessage
    //   )

    override val fetchData = Kleisli {
      hashes =>
        val inStream: StreamSource[In] = Source(hashes)
        RemoteRpc.runGet(fetchConcurrency, inStream, makeUrl)
          .runFold(List.empty[(In, String)])(_ :+ _)
    }

    override val decodeData = Kleisli(
      json =>
        decodeLiftingTo[Future, List[Out]](adaptManagerPubkeyField(JsonString.sanitize(json)))
          .map(_.flatten)
          .onError(logErrorOnJsonDecoding(s"I fetched some operations json from tezos node that I'm unable to decode into operation groups: $json"))
    )

  }

  // the account decoder has no effect, so we need to "lift" it to a `Future` effect to make it compatible with the original fetcher

  /** A derived fetcher that reads block hashes to get both the operation groups and the account ids from the same returned json */
  implicit val operationsWithAccountsFetcher = DataFetcher.decodeBoth(operationGroupFetcher, accountIdsJsonDecode.lift[Future])

  /** a fetcher for the current quorum of blocks */
  implicit val currentQuorumFetcher = new FutureFetcher {

    type Encoded = String
    type In = BlockHash
    type Out = Option[Int]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_quorum"

    // lazy val failureHandler =  (hash: BlockHash, error: Throwable) =>
    //   logger.error("I encountered problems while fetching quorums from {}, for block {}. The error says {}",
    //     network,
    //     hash.value,
    //     error.getMessage
    //   )

    override val fetchData = Kleisli {
      hashes =>
        val inStream: StreamSource[In] = Source(hashes)
        RemoteRpc.runGet(fetchConcurrency, inStream, makeUrl)
          .runFold(List.empty[(In, String)])(_ :+ _)
    }

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
  implicit val currentProposalFetcher = new FutureFetcher {
    import JsonDecoders.Circe._

    type Encoded = String
    type In = BlockHash
    type Out = Option[ProtocolId]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_proposal"

    // lazy val failureHandler = (hash: BlockHash, error: Throwable) =>
    //   logger.error("I encountered problems while fetching current proposals from {}, for block {}. The error says {}",
    //     network,
    //     hash.value,
    //     error.getMessage
    //   )

    override val fetchData = Kleisli {
      hashes =>
        val inStream: StreamSource[In] = Source(hashes)
        RemoteRpc.runGet(fetchConcurrency, inStream, makeUrl)
          .runFold(List.empty[(In, String)])(_ :+ _)
    }

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
  implicit val proposalsFetcher = new FutureFetcher {
    import JsonDecoders.Circe._
    import cats.instances.future._

    type Encoded = String
    type In = Block
    type Out = List[ProtocolId]

    val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/proposals"

    // lazy val failureHandler = (block: Block, error: Throwable) =>
    //   logger.error("I encountered problems while fetching proposals details from {}, for block {} at level {}. The error says {}",
    //     network,
    //     block.data.hash.value,
    //     block.data.header.level,
    //     error.getMessage
    //   )

    override val fetchData = Kleisli {
      blocks =>
        val inStream: StreamSource[In] = Source(blocks)
        RemoteRpc.runGet(fetchConcurrency, inStream, makeUrl)
          .runFold(List.empty[(In, String)])(_ :+ _)
    }

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
  implicit val bakersFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Votes._
    import cats.instances.future._


    type Encoded = String
    type In = Block
    type Out = List[Voting.BakerRolls]

    val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/listings"

    // lazy val failureHandler = (block: Block, error: Throwable) =>
    //   logger.error("I encountered problems while fetching baker rolls from {}, for block {} at level {}. The error says {}",
    //     network,
    //     block.data.hash.value,
    //     block.data.header.level,
    //     error.getMessage
    //   )

    override val fetchData = Kleisli {
      blocks =>
        val inStream: StreamSource[In] = Source(blocks)
        RemoteRpc.runGet(fetchConcurrency, inStream, makeUrl)
          .runFold(List.empty[(In, String)])(_ :+ _)
    }

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
 implicit val ballotsFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Votes._
    import cats.instances.future._


    type Encoded = String
    type In = Block
    type Out = List[Voting.Ballot]

    val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/ballot_list"

    // lazy val failureHandler = (block: Block, error: Throwable) =>
    //   logger.error("I encountered problems while fetching ballot votes from {}, for block {} at level {}. The error says {}",
    //     network,
    //     block.data.hash.value,
    //     block.data.header.level,
    //     error.getMessage
    //   )


    override val fetchData = Kleisli {
      blocks =>
        val inStream: StreamSource[In] = Source(blocks)
        RemoteRpc.runGet(fetchConcurrency, inStream, makeUrl)
          .runFold(List.empty[(In, String)])(_ :+ _)
    }

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

object AccountsDataFetchers {

  import TezosRemoteInstances.Akka.Streams.ConcurrencyLevel

  def apply(streamConcurrency: ConcurrencyLevel)(implicit actorSystem: ActorSystem, rpc: RemoteContext, ec: ExecutionContext) =
    new AccountsDataFetchers with TezosRemoteInstances.Akka.Streams with LazyLogging {
      override implicit val system = actorSystem
      override implicit val tezosContext = rpc
      override implicit val fetchFutureContext = ec
      override val accountsFetchConcurrency: Int = streamConcurrency
    }
}

/** Defines intances of `DataFetcher` for accounts-related data */
trait AccountsDataFetchers {
  //we require the cabability to log
  self: LazyLogging with TezosRemoteInstances.Akka.Streams =>
  import cats.data.Reader
  import cats.instances.future._
  import cats.syntax.applicativeError._
  import cats.syntax.applicative._
  import JsonDecoders.Circe.decodeLiftingTo
  import TezosRemoteInstances.Akka.Streams.StreamSource

  implicit def system: ActorSystem
  implicit def tezosContext: RemoteContext
  implicit def fetchFutureContext: ExecutionContext
  implicit val actorMaterializer: ActorMaterializer = ActorMaterializer()

  /* reduces repetion in error handling */
  private def logWarnOnJsonDecoding[Encoded](message: String): PartialFunction[Throwable, Future[Unit]] = {
    case decodingError: io.circe.Error =>
      logger.warn(message, decodingError).pure[Future]
    case t =>
      logger.error("Something unexpected failed while decoding json", t).pure[Future]
  }

  /** parallelism in the multiple requests decoding on the RPC interface */
  def accountsFetchConcurrency: Int

  //common type alias to simplify signatures
  private type FutureFetcher = DataFetcher[Future, List, Throwable]

  /** a fetcher for accounts, dependent on a specific block hash reference */
  implicit val accountsFetcherProvider: Reader[BlockHash, DataFetcher.Aux[Future, List, Throwable, AccountId, Option[Account], String]] = Reader( (referenceBlock: BlockHash) =>
    new FutureFetcher {
      import JsonDecoders.Circe.Accounts._

      type Encoded = String
      type In = AccountId
      type Out = Option[Account]

      val makeUrl = (id: AccountId) => s"blocks/${referenceBlock.value}/context/contracts/${id.id}"

      // lazy val failureHandler = (id: AccountId, error: Throwable) =>
      //   logger.error("I encountered problems while fetching account data from {}, for id {}. The error says {}",
      //     network,
      //     id.id,
      //     error.getMessage
      //   )

      override def fetchData = Kleisli {
        ids =>
          val inStream: StreamSource[In] = Source(ids)
          RemoteRpc.runGet(accountsFetchConcurrency, inStream, makeUrl)
            .runFold(List.empty[(In, String)])(_ :+ _)
        }

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
  )

}