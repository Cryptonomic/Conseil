package tech.cryptonomic.conseil.tezos

import cats._
import cats.data.Kleisli
import com.typesafe.scalalogging.LazyLogging
import scala.util.control.NonFatal
import tech.cryptonomic.conseil.generic.rpc.{DataFetcher, RpcHandler}
import tech.cryptonomic.conseil.tezos.TezosRpc.Akka.TezosNodeContext
import tech.cryptonomic.conseil.util.JsonUtil
import tech.cryptonomic.conseil.util.JsonUtil.{adaptManagerPubkeyField, JsonString}
import TezosTypes._
import cats._
import cats.arrow._
import cats.syntax.flatMap._
import io.circe.Decoder

trait TezosNodeFetchersLogging extends LazyLogging {
  import cats.syntax.applicative._

  /* error logging for the fetch operation */
  protected def logErrorOnJsonFetching[Eff[_]: Applicative](message: String): PartialFunction[Throwable, Eff[Unit]] = {
    case t => logger.error(message, t).pure[Eff]
  }

  /* error logging for the decode operation, used when the failure not recoverable */
  protected def logErrorOnJsonDecoding[Eff[_]: Applicative](message: String): PartialFunction[Throwable, Eff[Unit]] = {
    case decodingError: io.circe.Error =>
      logger.error(message, decodingError).pure[Eff]
    case t =>
      logger.error("Something unexpected failed while decoding json", t).pure[Eff]
  }

  /* error logging for the decode operation, used when we expect the failure to be recovered */
  protected def logWarnOnJsonDecoding[Eff[_]: Applicative](
    message: String,
    ignore: Boolean = false
  ): PartialFunction[Throwable, Eff[Unit]] = {
    case decodingError: io.circe.Error if ignore =>
      ().pure[Eff]
    case decodingError: io.circe.Error =>
      logger.warn(message, decodingError).pure[Eff]
    case t =>
      logger.error("Something unexpected failed while decoding json", t).pure[Eff]
  }

}

object BlocksDataFetchers {
  import com.typesafe.scalalogging.Logger

  def apply(rpc: TezosNodeContext) =
    new BlocksDataFetchers with TezosRpc.Cats.IOEff with TezosNodeFetchersLogging {
      implicit override lazy val tezosContext = rpc
      override lazy val logger = Logger[BlocksDataFetchers.type]
    }

}

/** Defines intances of `DataFetcher` for block-related data */
trait BlocksDataFetchers {
  //we require the cabability to log and the rpc handlers
  self: TezosNodeFetchersLogging with TezosRpc.Cats.IOEff =>
  import cats.syntax.applicativeError._
  import cats.effect.IO
  import DataFetcher.Instances._
  import JsonDecoders.Circe.decodeLiftingTo

  implicit def tezosContext: TezosNodeContext

  //common type for the fetchers
  type IOFetcher[In, Out] = DataFetcher.Std[IO, In, Out]

  /* verifies if the string is empty, guaranteeing null-safety and removing margins */
  private def stringSafelyEmpty(s: String) = Option(s).forall(_.trim.isEmpty)

  /* standard pattern to create a fetcher
   * - based on rpc-handlers,
   * - wrapping the output into IO
   * - preparsing the input to define a command, i.e. a path fragment for the tezos endpoint
   * @param makeCommand should read the input type and produce a string identifying the path to the tezos rpc api
   * @param decodeJson how to actually convert the json string into the output Decoded type
   * @tparam In the input to the fetcher
   * @tparam Decoded the output, decoded from tezos json response
   */
  private def makeIOFetcherFromRpc[In, Decoded](
    makeCommand: In => String,
    decodeJson: String => IO[Decoded]
  ): IOFetcher[In, Decoded] = {
    val genericCommandFetcher = new DataFetcher[IO, String, Decoded] {

      type Encoded = String
      type Error = Throwable

      //fetch json from the passed-in URL fragment command
      override val fetchData = Kleisli { command =>
        IO(logger.info("Fetching data from tezos node for path: {}", command)) >>
          RpcHandler
            .runGet[IO, String, Encoded](command)
            .onError(logErrorOnJsonFetching[IO](s"I failed to fetch the json from tezos node for path: $command"))
      }

      // decode with `JsonDecoders`
      override val decodeData = Kleisli(decodeJson)

    }

    //adapt the fetcher to accept the actual input, by pre-parsing it with `lmap` over the command factory
    Profunctor[IOFetcher].lmap(genericCommandFetcher)(makeCommand)
  }

  /** a fetcher for blocks, based on a reference hash and offset */
  implicit val blocksFetcher: IOFetcher[(BlockHash, Offset), BlockData] = {
    import JsonDecoders.Circe.Blocks._

    makeIOFetcherFromRpc[(BlockHash, Offset), BlockData](
      makeCommand = { case (hashRef, offset) => s"blocks/${hashRef.value}~${String.valueOf(offset)}" },
      decodeJson = json =>
        decodeLiftingTo[IO, BlockData](json)
          .onError(
            logErrorOnJsonDecoding[IO](
              s"I fetched a block definition from tezos node that I'm unable to decode: $json"
            )
          )
    )
  }

  /** a fetcher of operation groups from block hashes */
  implicit val operationGroupFetcher: IOFetcher[BlockHash, List[OperationsGroup]] = {
    import JsonDecoders.Circe.Operations._

    val makeCommand = (hash: BlockHash) => s"blocks/${hash.value}/operations"

    val fetcher = new DataFetcher[IO, String, List[List[OperationsGroup]]] {

      type Encoded = String
      type Error = Throwable

      override val fetchData = Kleisli { command =>
        IO(logger.info("Fetching data from tezos node for path: {}", command)) >>
          RpcHandler
            .runGet[IO, String, Encoded](command)
            .onError(logErrorOnJsonFetching[IO](s"I failed to fetch the json from tezos node for path: $command"))
      }

      override val decodeData = Kleisli { json =>
        decodeLiftingTo[IO, List[List[OperationsGroup]]](adaptManagerPubkeyField(JsonString.sanitize(json)))
          .onError(
            logErrorOnJsonDecoding[IO](
              s"I fetched some operations json from tezos node that I'm unable to decode into operation groups: $json"
            )
          )
      }
    }

    //adapt the fetcher to accept the real input and return the flattened output, by pre-parsing the input and transforming the output with `dimap`
    Profunctor[IOFetcher].dimap(fetcher)(makeCommand)(_.flatten)
  }

  /** decode account ids from operation json results with the `cats.Id` effect, i.e. a total function with no effect */
  implicit private val accountIdsJsonDecode: Kleisli[IO, String, List[AccountId]] =
    Kleisli[Id, String, List[AccountId]] {
      case JsonUtil.AccountIds(id, ids @ _*) =>
        (id :: ids.toList).distinct.map(AccountId)
      case _ =>
        List.empty
    }.lift[IO]

  /** An implicitly derived fetcher that reads block hashes to get both the operation groups and the account ids from the same returned json */
  implicit val operationsWithAccountsFetcher =
    DataFetcher.addMultiDecoding[IO, BlockHash, List[OperationsGroup], List[AccountId], String, Throwable]

  /** a fetcher for the current quorum of blocks */
  implicit val currentQuorumFetcher = {
    val defaultQuorum = "0"

    makeIOFetcherFromRpc[(BlockHash, Offset), Option[Int]](
      makeCommand = { case (hashRef, offset) => s"blocks/${hashRef.value}~$offset/votes/current_quorum" },
      decodeJson = json =>
        decodeLiftingTo[IO, Option[Int]](if (stringSafelyEmpty(json)) defaultQuorum else json)
          .onError(
            logWarnOnJsonDecoding[IO](s"I fetched current quorum json from tezos node that I'm unable to decode: $json")
          )
          .recover {
            case NonFatal(_) => Option.empty
          }
    )
  }

  /** a fetcher for the current proposals of blocks */
  implicit val currentProposalFetcher = {
    import JsonDecoders.Circe._

    val defaultProposalId = """ "3M" """.trim

    makeIOFetcherFromRpc[(BlockHash, Offset), Option[ProtocolId]](
      makeCommand = { case (hashRef, offset) => s"blocks/${hashRef.value}~$offset/votes/current_proposal" },
      decodeJson = json =>
        decodeLiftingTo[IO, Option[ProtocolId]](if (stringSafelyEmpty(json)) defaultProposalId else json)
          .onError(
            logWarnOnJsonDecoding[IO](
              s"I fetched a proposal protocol json from tezos node that I'm unable to decode: $json"
            )
          )
          .recover {
            case NonFatal(_) => Option.empty
          }
    )
  }

  /** a fetcher for all proposals for blocks */
  implicit val proposalsFetcher = {
    import JsonDecoders.Circe._

    makeIOFetcherFromRpc[Block, List[(ProtocolId, ProposalSupporters)]](
      makeCommand = (block: Block) => s"blocks/${block.data.hash.value}/votes/proposals",
      decodeJson = json =>
        decodeLiftingTo[IO, List[(ProtocolId, ProposalSupporters)]](json)
          .onError(
            logWarnOnJsonDecoding[IO](
              s"I fetched voting proposal protocols json from tezos node that I'm unable to decode: $json",
              ignore = stringSafelyEmpty(json)
            )
          )
          .recover {
            //we recover parsing failures with an empty result, as we have no optionality here to lean on
            case NonFatal(_) => List.empty
          }
    )
  }

  /** a fetcher of baker rolls for blocks */
  implicit val bakersFetcher = {
    import JsonDecoders.Circe.Votes._

    makeIOFetcherFromRpc[Block, List[Voting.BakerRolls]](
      makeCommand = (block: Block) => s"blocks/${block.data.hash.value}/votes/listings",
      decodeJson = json =>
        decodeLiftingTo[IO, List[Voting.BakerRolls]](json)
          .onError(
            logWarnOnJsonDecoding[IO](
              s"I fetched baker rolls json from tezos node that I'm unable to decode: $json",
              ignore = stringSafelyEmpty(json)
            )
          )
          .recover {
            //we recover parsing failures with an empty result, as we have no optionality here to lean on
            case NonFatal(_) => List.empty
          }
    )
  }

  /** a fetcher of ballot votes for blocks */
  implicit val ballotsFetcher = {
    import JsonDecoders.Circe.Votes._

    makeIOFetcherFromRpc[Block, List[Voting.Ballot]](
      makeCommand = (block: Block) => s"blocks/${block.data.hash.value}/votes/ballot_list",
      decodeJson = json =>
        decodeLiftingTo[IO, List[Voting.Ballot]](json)
          .onError(
            logWarnOnJsonDecoding[IO](
              s"I fetched ballot votes json from tezos node that I'm unable to decode: $json",
              ignore = stringSafelyEmpty(json)
            )
          )
          .recover {
            //we recover parsing failures with an empty result, as we have no optionality here to lean on
            case NonFatal(_) => List.empty
          }
    )
  }

}

object AccountsDataFetchers {
  import com.typesafe.scalalogging.Logger

  def apply(rpc: TezosNodeContext) =
    new AccountsDataFetchers with TezosRpc.Cats.IOEff with TezosNodeFetchersLogging {
      implicit override lazy val tezosContext = rpc
      override lazy val logger = Logger[AccountsDataFetchers.type]
    }
}

/** Defines intances of `DataFetcher` for accounts-related data */
trait AccountsDataFetchers {
  //we require the cabability to log and the rpc handlers
  self: TezosNodeFetchersLogging with TezosRpc.Cats.IOEff =>
  import cats.syntax.applicativeError._
  import cats.effect.IO
  import DataFetcher.Instances._
  import JsonDecoders.Circe.decodeLiftingTo

  implicit def tezosContext: TezosNodeContext

  //common type for the fetchers
  type IOFetcher[In, Out] = DataFetcher.Std[IO, In, Out]

  def makeIOFetcherFromRpc[In, Decoded: Decoder](
    makeCommand: In => String,
    decodeJson: String => IO[Decoded]
  ): IOFetcher[In, Decoded] = {
    val genericCommandFetcher = new DataFetcher[IO, String, Decoded] {

      type Encoded = String
      type Error = Throwable

      //fetch json from the passed-in URL fragment command
      override val fetchData = Kleisli { command =>
        IO(logger.info("Fetching data from tezos node for path: {}", command)) >>
          RpcHandler
            .runGet[IO, String, Encoded](command)
            .onError(logErrorOnJsonFetching[IO](s"I failed to fetch the json from tezos node for path: $command"))
      }

      // decode with `JsonDecoders`
      override val decodeData = Kleisli(decodeJson)

    }

    //adapt the fetcher to accept the actual input, by pre-parsing it with `lmap` over the command factory
    Profunctor[IOFetcher].lmap(genericCommandFetcher)(makeCommand)
  }

  /** a fetcher for accounts, dependent on a specific block hash reference */
  implicit val accountsFetcher: IOFetcher[(BlockHash, AccountId), Option[Account]] = {
    import JsonDecoders.Circe.Accounts._
    import cats.syntax.option._

    makeIOFetcherFromRpc[(BlockHash, AccountId), Option[Account]](
      makeCommand = { case (blockRef, id) => s"blocks/${blockRef.value}/context/contracts/${id.id}" },
      decodeJson = json =>
        decodeLiftingTo[IO, Account](json)
          .onError(
            logWarnOnJsonDecoding[IO](s"I fetched an account json from tezos node that I'm unable to decode: $json")
          )
          .map(_.some)
          .recover {
            //we need to consider that some accounts failed to be written in the chain, though we have ids in the block
            case NonFatal(_) => Option.empty
          }
    )
  }

  /** a fetcher for delegates, dependent on a specific block hash reference */
  implicit val delegateFetcher: IOFetcher[(BlockHash, PublicKeyHash), Option[Delegate]] = {
    import JsonDecoders.Circe.Delegates._
    import cats.syntax.option._

    makeIOFetcherFromRpc[(BlockHash, PublicKeyHash), Option[Delegate]](
      makeCommand = { case (blockRef, pkh) => s"blocks/${blockRef.value}/context/delegates/${pkh.value}" },
      decodeJson = json =>
        decodeLiftingTo[IO, Delegate](json)
          .onError(
            logErrorOnJsonDecoding[IO](
              s"I fetched an account delegate json from tezos node that I'm unable to decode: $json"
            )
          )
          .map(_.some)
          .recover {
            //we need to consider that a delegate failed to be written in the chain, though we have its reference in some account
            case NonFatal(_) => Option.empty
          }
    )
  }

}
