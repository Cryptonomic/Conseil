package tech.cryptonomic.conseil.tezos

import cats._
import cats.data.Kleisli
import com.typesafe.scalalogging.LazyLogging
import scala.concurrent.ExecutionContext
import tech.cryptonomic.conseil.generic.chain.DataFetcher
import tech.cryptonomic.conseil.util.JsonUtil
import tech.cryptonomic.conseil.util.JsonUtil.{JsonString, adaptManagerPubkeyField}
import TezosTypes._

/** Defines intances of `DataFetcher` for block-related data */
trait BlocksDataFetchers {
  //we require the cabability to log
  self: LazyLogging =>
  import scala.concurrent.Future
  import io.circe.parser.decode
  import JsonDecoders.Circe.decodeLiftingTo

  def fetchFutureContext: ExecutionContext

  /** the tezos network to connect to */
  def network: String
  /** the tezos interface to query */
  def node: TezosRPCInterface
  /** parallelism in the multiple requests decoding on the RPC interface */
  def fetchConcurrency: Int

  type FutureFetcher = DataFetcher[Future, List, Throwable]

  /** a fetcher of blocks */
  def blocksFetcher(hashRef: BlockHash) = new FutureFetcher {
    import JsonDecoders.Circe.Blocks._

    type Encoded = String
    type In = Int //offset from head
    type Out = BlockData

    def makeUrl = (offset: In) => s"blocks/${hashRef.value}~${String.valueOf(offset)}"

    //fetch a future stream of values
    override val fetchData =
      Kleisli(offsets => node.runBatchedGetQuery(network, offsets, makeUrl, fetchConcurrency))

    // decode with `JsonDecoders`
    override val decodeData = Kleisli {
      json =>
        decode[BlockData](JsonString.sanitize(json)) match {
          case Left(error) =>
            logger.error("I fetched a block definition from tezos node that I'm unable to decode: {}", json)
            Future.failed(error)
          case Right(value) =>
            Future.successful(value)
        }
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
      Kleisli(hashes => node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency))

    override val decodeData = Kleisli(
      json =>
        decode[List[List[OperationsGroup]]](adaptManagerPubkeyField(JsonString.sanitize(json)))
          .map(_.flatten) match {
            case Left(error) =>
              logger.error("I fetched some operations json from tezos node that I'm unable to decode into operation groups: {}", json)
              Future.failed(error)
            case Right(value) =>
              Future.successful(value)
          }
    )

  }

  val currentPeriodFetcher = new FutureFetcher {
    import JsonDecoders.Circe._

    type Encoded = String
    type In = BlockHash
    type Out = ProposalPeriod.Kind

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_period_kind"

    override val fetchData =
      Kleisli(hashes => node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency))

    override val decodeData = Kleisli(
      json => Future.fromTry(decode[ProposalPeriod.Kind](json).toTry)
    )

  }

  val currentQuorumFetcher = new FutureFetcher {

    type Encoded = String
    type In = BlockHash
    type Out = Option[Int]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_quorum"

    override val fetchData =
      Kleisli(hashes => node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency))

    override val decodeData = Kleisli(
      json => Future.successful(decode[Int](json).toOption)
    )

  }

  val currentProposalFetcher = new FutureFetcher {
    import JsonDecoders.Circe._

    type Encoded = String
    type In = BlockHash
    type Out = Option[ProtocolId]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_proposal"

    override val fetchData =
      Kleisli(hashes => node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency))

    override val decodeData = Kleisli(
      json => Future.successful(decode[ProtocolId](json).toOption)
    )

  }

  val proposalsMultiFetch = new FutureFetcher {
    import JsonDecoders.Circe._
    import cats.instances.future._


    type Encoded = String
    type In = Block
    type Out = List[ProtocolId]

    val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/proposals"

    override val fetchData =
      Kleisli(blocks => node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency))

    override val decodeData = Kleisli{
      json =>
        implicit val ec: ExecutionContext = fetchFutureContext
        decodeLiftingTo[Future, List[ProtocolId]](json)
    }
  }

  val bakersMultiFetch = new FutureFetcher {
    import JsonDecoders.Circe.Votes._
    import cats.instances.future._


    type Encoded = String
    type In = Block
    type Out = List[Voting.BakerRolls]

    val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/listings"

    override val fetchData =
      Kleisli(blocks => node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency))

    override val decodeData = Kleisli{
      json =>
        implicit val ec: ExecutionContext = fetchFutureContext
        decodeLiftingTo[Future, List[Voting.BakerRolls]](json)
    }
  }

  val ballotsMultiFetch = new FutureFetcher {
    import JsonDecoders.Circe.Votes._
    import cats.instances.future._


    type Encoded = String
    type In = Block
    type Out = List[Voting.Ballot]

    val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/ballot_list"

    override val fetchData =
      Kleisli(blocks => node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency))

    override val decodeData = Kleisli{
      json =>
        implicit val ec: ExecutionContext = fetchFutureContext
        decodeLiftingTo[Future, List[Voting.Ballot]](json)
    }
  }

}