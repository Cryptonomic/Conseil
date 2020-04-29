package tech.cryptonomic.conseil.indexer.tezos

import cats._
import cats.data.Kleisli
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import tech.cryptonomic.conseil.common.generic.chain.DataFetcher
import tech.cryptonomic.conseil.common.util.JsonUtil
import tech.cryptonomic.conseil.common.util.JsonUtil.{adaptManagerPubkeyField, JsonString}
import tech.cryptonomic.conseil.common.util.CollectionOps._
import org.slf4j.LoggerFactory
import tech.cryptonomic.conseil.common.tezos.TezosTypes._
import tech.cryptonomic.conseil.common.tezos.TezosTypes.Voting.BallotCounts

/** Defines intances of `DataFetcher` for block-related data */
private[tezos] trait TezosBlocksDataFetchers {
  //we require the capability to log
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
  private def logWarnOnJsonDecoding[Encoded](
      message: String,
      ignore: Boolean = false
  ): PartialFunction[Throwable, Future[Unit]] = {
    case decodingError: io.circe.Error if ignore =>
      ().pure[Future]
    case decodingError: io.circe.Error =>
      logger.warn(message, decodingError).pure[Future]
    case t =>
      logger.error("Something unexpected failed while decoding json", t).pure[Future]
  }

  //common type alias to simplify signatures
  private type FutureFetcher = DataFetcher[Future, List, Throwable]

  /** untyped alias to clarify intent */
  type Offset = Int

  /** a fetcher of blocks */
  implicit def blocksFetcher(hashRef: BlockHash) = new FutureFetcher {
    import JsonDecoders.Circe.Blocks._

    type Encoded = String
    type In = Offset
    type Out = BlockData

    private def makeUrl = (offset: Offset) => s"blocks/${hashRef.value}~${String.valueOf(offset)}"

    //fetch a future stream of values
    override val fetchData = {
      Kleisli(
        offsets => {
          logger.info(s"""Fetching blocks for offsets ${offsets.min} to ${offsets.max}""")
          node.runBatchedGetQuery(network, offsets, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching blocks data from {}, for offsets {} from the {}. The error says {}",
                  network,
                  offsets.onBounds((first, last) => s"$first to $last").getOrElse("unspecified"),
                  hashRef,
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )
    }

    // decode with `JsonDecoders`
    override val decodeData = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logErrorOnJsonDecoding(s"I fetched a block definition from tezos node that I'm unable to decode: $json")
        )
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
  implicit val operationGroupFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Operations._

    type Encoded = String
    type In = BlockHash
    type Out = List[OperationsGroup]

    private val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/operations"

    override val fetchData = {
      Kleisli(
        hashes => {
          logger.info("Fetching operations")
          node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching operations from {}, for blocks {}. The error says {}",
                  network,
                  hashes.map(_.value).mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )
    }

    override val decodeData = Kleisli(
      json =>
        decodeLiftingTo[Future, List[Out]](adaptManagerPubkeyField(JsonString.sanitize(json)))
          .map(_.flatten)
          .onError(
            logErrorOnJsonDecoding(
              s"I fetched some operations json from tezos node that I'm unable to decode into operation groups: $json"
            )
          )
    )

  }

  val berLogger = LoggerFactory.getLogger("RightsFetcher")

  implicit val futureBakingRightsFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Rights._

    /** the input type, e.g. ids of data */
    override type In = Int

    /** the output type, e.g. the decoded block data */
    override type Out = List[BakingRights]

    /** the encoded representation type used e.g. some Json representation */
    override type Encoded = String

    private val makeUrl = (level: Int) => s"blocks/head/helpers/baking_rights?level=$level"

    /** an effectful function from a collection of inputs `T[In]`
      * to the collection of encoded values, tupled with the corresponding input `T[(In, Encoded)]`
      */
    override val fetchData: Kleisli[Future, List[Int], List[(Int, String)]] =
      Kleisli(
        levels => {
          berLogger.info("Fetching future baking rights")
          node.runBatchedGetQuery(network, levels, makeUrl, fetchConcurrency).onError {
            case err =>
              berLogger
                .error(
                  "I encountered problems while fetching future baking rights from {}, for levels {}. The error says {}",
                  network,
                  levels.mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    /** an effectful function that decodes the json value to an output `Out` */
    override val decodeData: Kleisli[Future, String, List[BakingRights]] = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched future baking rights json from tezos node that I'm unable to decode: $json",
            ignore = Option(json).forall(_.trim.isEmpty)
          )
        )
        .recover {
          //we recover parsing failures with an empty result, as we have no optionality here to lean on
          case NonFatal(_) => List.empty
        }
    }
  }

  implicit val futureEndorsingRightsFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Rights._

    /** the input type, e.g. ids of data */
    override type In = Int

    /** the output type, e.g. the decoded block data */
    override type Out = List[EndorsingRights]

    /** the encoded representation type used e.g. some Json representation */
    override type Encoded = String

    private val makeUrl = (level: Int) => s"blocks/head/helpers/endorsing_rights?level=$level"

    /** an effectful function from a collection of inputs `T[In]`
      * to the collection of encoded values, tupled with the corresponding input `T[(In, Encoded)]`
      */
    override val fetchData: Kleisli[Future, List[Int], List[(Int, String)]] =
      Kleisli(
        levels => {
          berLogger.info("Fetching future endorsing rights")
          node.runBatchedGetQuery(network, levels, makeUrl, fetchConcurrency).onError {
            case err =>
              berLogger
                .error(
                  "I encountered problems while fetching future endorsing rights from {}, for levels {}. The error says {}",
                  network,
                  levels.mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    /** an effectful function that decodes the json value to an output `Out` */
    override val decodeData: Kleisli[Future, String, List[EndorsingRights]] = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched future endorsing rights json from tezos node that I'm unable to decode: $json",
            ignore = Option(json).forall(_.trim.isEmpty)
          )
        )
        .recover {
          //we recover parsing failures with an empty result, as we have no optionality here to lean on
          case NonFatal(_) => List.empty
        }
    }
  }

  implicit val bakingRightsFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Rights._

    /** the input type, e.g. ids of data */
    override type In = FetchRights

    /** the output type, e.g. the decoded block data */
    override type Out = List[BakingRights]

    /** the encoded representation type used e.g. some Json representation */
    override type Encoded = String

    private val makeUrl = (blockData: In) => s"blocks/${blockData.blockHash.get.value}/helpers/baking_rights"

    /** an effectful function from a collection of inputs `T[In]`
      * to the collection of encoded values, tupled with the corresponding input `T[(In, Encoded)]`
      */
    override val fetchData: Kleisli[Future, List[In], List[
      (FetchRights, String)
    ]] =
      Kleisli(
        hashesWithCycleAndGovernancePeriod => {
          val hashes = hashesWithCycleAndGovernancePeriod.map(_.blockHash)
          logger.info("Fetching baking rights")
          node.runBatchedGetQuery(network, hashesWithCycleAndGovernancePeriod, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching baking rights from {}, for blocks {}. The error says {}",
                  network,
                  hashes.map(_.get.value).mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    /** an effectful function that decodes the json value to an output `Out` */
    override val decodeData: Kleisli[Future, String, List[BakingRights]] = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched baking rights json from tezos node that I'm unable to decode: $json",
            ignore = Option(json).forall(_.trim.isEmpty)
          )
        )
        .recover {
          //we recover parsing failures with an empty result, as we have no optionality here to lean on
          case NonFatal(_) => List.empty
        }
    }
  }

  implicit val endorsingRightsFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Rights._

    /** the input type, e.g. ids of data */
    override type In = FetchRights

    /** the output type, e.g. the decoded block data */
    override type Out = List[EndorsingRights]

    /** the encoded representation type used e.g. some Json representation */
    override type Encoded = String

    private val makeUrl = (blockData: In) => s"blocks/${blockData.blockHash.get.value}/helpers/endorsing_rights"

    /** an effectful function from a collection of inputs `T[In]`
      * to the collection of encoded values, tupled with the corresponding input `T[(In, Encoded)]`
      */
    override val fetchData: Kleisli[Future, List[In], List[
      (FetchRights, String)
    ]] =
      Kleisli(
        hashesWithCycleAndGovernancePeriod => {
          val hashes = hashesWithCycleAndGovernancePeriod.map(_.blockHash)
          logger.info("Fetching endorsing rights")
          node.runBatchedGetQuery(network, hashesWithCycleAndGovernancePeriod, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching endorsing rights from {}, for blocks {}. The error says {}",
                  network,
                  hashes.map(_.get.value).mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    /** an effectful function that decodes the json value to an output `Out` */
    override val decodeData: Kleisli[Future, String, List[EndorsingRights]] = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched endorsing rights json from tezos node that I'm unable to decode: $json",
            ignore = Option(json).forall(_.trim.isEmpty)
          )
        )
        .recover {
          //we recover parsing failures with an empty result, as we have no optionality here to lean on
          case NonFatal(_) => List.empty
        }
    }
  }

  // the account decoder has no effect, so we need to "lift" it to a `Future` effect to make it compatible with the original fetcher

  /** A derived fetcher that reads block hashes to get both the operation groups and the account ids from the same returned json */
  implicit val operationsWithAccountsFetcher =
    DataFetcher.decodeBoth(operationGroupFetcher, accountIdsJsonDecode.lift[Future])

  /** a fetcher for the current quorum of blocks */
  implicit val currentQuorumFetcher = new FutureFetcher {

    type Encoded = String
    type In = BlockHash
    type Out = Option[Int]

    private val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_quorum"

    override val fetchData =
      Kleisli(
        hashes => {
          logger.info("Fetching current quorum")
          node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching quorums from {}, for blocks {}. The error says {}",
                  network,
                  hashes.map(_.value).mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    override val decodeData = Kleisli(
      json =>
        decodeLiftingTo[Future, Out](json)
          .onError(
            logWarnOnJsonDecoding(s"I fetched current quorum json from tezos node that I'm unable to decode: $json")
          )
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

    private val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_proposal"

    override val fetchData =
      Kleisli(
        hashes => {
          logger.info("Fetching current proposal")
          node.runBatchedGetQuery(network, hashes, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching current proposals from {}, for blocks {}. The error says {}",
                  network,
                  hashes.map(_.value).mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    override val decodeData = Kleisli(
      json =>
        decodeLiftingTo[Future, Out](json)
          .onError(
            logWarnOnJsonDecoding(
              s"I fetched a proposal protocol json from tezos node that I'm unable to decode: $json"
            )
          )
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
    type Out = List[(ProtocolId, ProposalSupporters)]

    private val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/proposals"

    override val fetchData =
      Kleisli(
        blocks => {
          logger.info(
            "Fetching all proposals protocols in levels {}",
            blocks.head.data.header.level to blocks.last.data.header.level
          )
          node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching proposals details from {}, for blocks {}. The error says {}",
                  network,
                  blocks.map(_.data.hash.value).mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    override val decodeData = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched voting proposal protocols json from tezos node that I'm unable to decode: $json",
            ignore = Option(json).forall(_.trim.isEmpty)
          )
        )
        .recover {
          //we recover parsing failures with an empty result, as we have no optionality here to lean on
          case NonFatal(_) => List.empty
        }
    }
  }

  /** a fetcher of baker rolls for blocks */
  implicit val bakersFetcherByHash = new FutureFetcher {
    import JsonDecoders.Circe.Votes._
    import cats.instances.future._

    type Encoded = String
    type In = String
    type Out = List[Voting.BakerRolls]

    private val makeUrl = (blockHash: String) => s"blocks/$blockHash/votes/listings"

    override val fetchData =
      Kleisli(
        blocks => {
          logger.info("Fetching bakers for {} blocks", blocks.size)
          node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching baker rolls from {}, for blocks {}. The error says {}",
                  network,
                  blocks.mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    override val decodeData = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched baker rolls json from tezos node that I'm unable to decode: $json",
            ignore = Option(json).forall(_.trim.isEmpty)
          )
        )
        .recover {
          //we recover parsing failures with an empty result, as we have no optionality here to lean on
          case NonFatal(_) => List.empty
        }
    }
  }

  /** a fetcher of baker rolls for blocks */
  implicit val bakersFetcher = new FutureFetcher {
    import cats.instances.future._

    type Encoded = String
    type In = Block
    type Out = List[Voting.BakerRolls]

    private val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/listings"

    override val fetchData =
      Kleisli(
        blocks => {
          logger.info("Fetching bakers in levels {}", blocks.head.data.header.level to blocks.last.data.header.level)
          node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching baker rolls from {}, for blocks {}. The error says {}",
                  network,
                  blocks.map(_.data.hash.value).mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    override val decodeData = bakersFetcherByHash.decodeData
  }

  /** a fetcher of ballot votes for blocks */
  implicit val ballotsFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Votes._
    import cats.instances.future._

    type Encoded = String
    type In = Block
    type Out = List[Voting.Ballot]

    private val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/ballot_list"

    override val fetchData =
      Kleisli(
        blocks => {
          logger.info("Fetching ballots")
          node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching ballot votes from {}, for blocks {}. The error says {}",
                  network,
                  blocks.map(_.data.hash.value).mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    override val decodeData = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched ballot votes json from tezos node that I'm unable to decode: $json",
            ignore = Option(json).forall(_.trim.isEmpty)
          )
        )
        .recover {
          //we recover parsing failures with an empty result, as we have no optionality here to lean on
          case NonFatal(_) => List.empty
        }
    }
  }

  /** a fetcher of ballot votes for blocks */
  implicit val ballotCountFetcher = new FutureFetcher {
    import JsonDecoders.Circe.Votes._
    import cats.instances.future._

    type Encoded = String
    type In = Block
    type Out = Option[BallotCounts]

    private val makeUrl = (block: Block) => s"blocks/${block.data.hash.value}/votes/ballots"

    override val fetchData =
      Kleisli(
        blocks => {
          logger
            .info("Fetching ballot counts in levels {}", blocks.head.data.header.level to blocks.last.data.header.level)
          node.runBatchedGetQuery(network, blocks, makeUrl, fetchConcurrency).onError {
            case err =>
              logger
                .error(
                  "I encountered problems while fetching ballot counts from {}, for blocks {}. The error says {}",
                  network,
                  blocks.map(_.data.hash.value).mkString(", "),
                  err.getMessage
                )
                .pure[Future]
          }
        }
      )

    override val decodeData = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched ballot counts json from tezos node that I'm unable to decode: $json",
            ignore = Option(json).forall(_.trim.isEmpty)
          )
        )
        .recover {
          //we recover parsing failures with an empty result, as we have no optionality here to lean on
          case NonFatal(_) => None
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

  /* reduces repetion in error handling */
  private def logErrorOnJsonDecoding[Encoded](message: String): PartialFunction[Throwable, Future[Unit]] = {
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

  implicit def accountFetcher(referenceBlock: BlockHash) = new FutureFetcher {
    import JsonDecoders.Circe.Accounts._

    type Encoded = String
    type In = AccountId
    type Out = Option[Account]

    private val makeUrl = (id: AccountId) => s"blocks/${referenceBlock.value}/context/contracts/${id.id}"

    override val fetchData = Kleisli(
      ids => {
        logger.info("Fetching accounts for block {}", referenceBlock.value)
        node.runBatchedGetQuery(network, ids, makeUrl, accountsFetchConcurrency).onError {
          case err =>
            logger
              .error(
                "I encountered problems while fetching account data from {}, for ids {}. The error says {}",
                network,
                ids.map(_.id).mkString(", "),
                err.getMessage
              )
              .pure[Future]
        }
      }
    )

    override def decodeData = Kleisli { json =>
      decodeLiftingTo[Future, Account](json)
        .map(Some(_))
        .onError(logWarnOnJsonDecoding(s"I fetched an account json from tezos node that I'm unable to decode: $json"))
        .recover {
          //we need to consider that some accounts failed to be written in the chain, though we have ids in the block
          case NonFatal(_) => Option.empty
        }
    }
  }

  implicit def delegateFetcher(referenceBlock: BlockHash) = new FutureFetcher {
    import JsonDecoders.Circe.Delegates._

    type Encoded = String
    type In = PublicKeyHash
    type Out = Option[Delegate]

    private val makeUrl = (pkh: PublicKeyHash) => s"blocks/${referenceBlock.value}/context/delegates/${pkh.value}"

    override val fetchData = Kleisli(
      keyHashes => {
        logger.info("Fetching delegated contracts for block {}", referenceBlock.value)
        node.runBatchedGetQuery(network, keyHashes, makeUrl, accountsFetchConcurrency).onError {
          case err =>
            logger
              .error(
                "I encountered problems while fetching delegates data from {}, for pkhs {}. The error says {}",
                network,
                keyHashes.map(_.value).mkString(", "),
                err.getMessage
              )
              .pure[Future]
        }
      }
    )

    override def decodeData = Kleisli { json =>
      decodeLiftingTo[Future, Delegate](json)
        .map(Some(_))
        .onError(
          logErrorOnJsonDecoding(s"I fetched an account delegate json from tezos node that I'm unable to decode: $json")
        )
        .recover {
          //we need to consider that some accounts failed to be written in the chain, though we have ids in the block
          case NonFatal(_) => Option.empty
        }
    }
  }

  implicit val activeDelegateFetcher = new FutureFetcher {
    type Encoded = String
    type In = BlockHash
    type Out = List[String]

    private val makeUrl = (blockHash: BlockHash) => s"blocks/${blockHash.value}/context/delegates?active"

    override val fetchData = Kleisli(
      blockHashes => {
        logger.info("Fetching active delegates")
        node.runBatchedGetQuery(network, blockHashes, makeUrl, accountsFetchConcurrency).onError {
          case err =>
            logger
              .error(
                "I encountered problems while fetching active delegates data from {}, for pkhs {}. The error says {}",
                network,
                blockHashes.map(_.value).mkString(", "),
                err.getMessage
              )
              .pure[Future]
        }
      }
    )

    override def decodeData = Kleisli { json =>
      decodeLiftingTo[Future, Out](json)
        .onError(
          logWarnOnJsonDecoding(
            s"I fetched active delegates json from tezos node that I'm unable to decode: $json"
          )
        )
        .recover {
          //we recover parsing failures with an empty result, as we have no optionality here to lean on
          case NonFatal(_) => List.empty
        }
    }
  }

}
