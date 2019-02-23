package tech.cryptonomic.conseil.tezos

import cats._
import cats.data.Kleisli
import cats.syntax.semigroupal._
import com.typesafe.scalalogging.LazyLogging
import tech.cryptonomic.conseil.util.JsonUtil
import tech.cryptonomic.conseil.util.JsonUtil.{JsonString, adaptManagerPubkeyField}
import TezosTypes._

/** Contract for a generic combination of operations that get json from the node,
  * then converts said json to a value, all doing multiple requests together.
  * The returned values are tupled with the corresponding input
  * The interface assumes some kind of higher-order typed effect `F[_]` is part
  * of the operation execution, and represents everything parametrically, to leave
  * room for future evolutions of the code protocol.
  * Thus the required implementation is in the form of a `cats.data.Kleisli` type,
  * parametric on effect `F`, input `In`, output `Out`. As needed, the in/out types are
  * wrapped in a `T[_]` type constructor that represents multiple values (e.g. a collection type).
  *
  * @tparam F the higher-order effect type (e.g. `Try`, `Future`, `IO`, `Either`)
  * @tparam T a traversable type (used to wrap mutliple requests and corresponding return values)
  */
trait TezosNodeMultiFetch[F[_], T[_]] {
  /** the input type*/
  type In
  /** the output type*/
  type Out
  /** the json representation type used*/
  type JsonType

  /** an effectful function from a collection of inputs `T[In]`
    * to the collection of json, tupled with the corresponding input `T[(In, JsonType)]`
    */
  def fetchJsonBatch: Kleisli[F, T[In], T[(In, JsonType)]]

  /** an effectful function that decodes the json value to an output `Out` */
  def decodeJson: Kleisli[F, JsonType, Out]

  /** using the combination of the provided functions we fetch all data with this general outline:
    * 1. get json values from the node, given a traversable input collection
    * 2. keep the correlation with the input and convert every json to a corresponding output
    * 3. return the traversable collection containing paired input and output values, all wrapped in the effect F
    */
  def fetch(implicit app: Monad[F], tr: Traverse[T]): Kleisli[F, T[In], T[(In, Out)]] =
    fetchJsonBatch.andThen(json => decodeJson.second[In].traverse(json))

}

object TezosNodeMultiFetch {
  import cats.syntax.foldable._
  import cats.syntax.functorFilter._

  /* An alias used to define function constraints on the internal dependent types (i.e. `I`, `O`, `J`)
   * which would be otherwise transparent in any function signature
   */
  private type Aux[F[_], T[_], I, O, J] = TezosNodeMultiFetch[F, T] {
      type In = I
      type Out = O
      type JsonType = J
    }

  /** Adds an extra json-decoding operation for every json intermediate result, returning both the
    * original output `O` and the new decoded output `O2` as a tuple
    */
  def addDecoding[F[_]: Apply, T[_], I, O, O2, J](
    multiFetch: Aux[F,T,I,O,J],
    additionalDecode: Kleisli[F, J, O2]
  ) = new TezosNodeMultiFetch[F, T] {
    type In = I
    type Out = (O, O2)
    type JsonType = J

    def fetchJsonBatch = multiFetch.fetchJsonBatch

    def decodeJson = multiFetch.decodeJson.product(additionalDecode)
  }

  /** Combines two multifetch acting on the same inputs to get a tuple of the outputs */
  def tupledResults[F[_]: Monad, T[_] : Traverse: FunctorFilter, In, O1, O2, J1, J2](
    mf1: Aux[F, T, In, O1, J1],
    mf2: Aux[F, T, In, O2, J2]
  ): Kleisli[F, T[In], T[(In, (O1, O2))]] =
    mergeResults(mf1, mf2) {
      (outs1, outs2) =>
        outs1.mapFilter {
          case (in, out1) =>
            outs2.collectFirst{
              case (`in`, out2) => (`in`, (out1, out2))
            }
        }
    }

  /** Combines two multifetch acting on the same inputs to get a 3-tuple of the outputs */
  def tupledResults[F[_]: Monad, T[_] : Traverse: FunctorFilter, In, O1, O2, O3, J1, J2, J3](
    mf1: Aux[F, T, In, O1, J1],
    mf2: Aux[F, T, In, O2, J2],
    mf3: Aux[F, T, In, O3, J3]
  ): Kleisli[F, T[In], T[(In, (O1, O2, O3))]] =
    tripleMergeResults(mf1, mf2, mf3) {
      (outs1, outs2, outs3) =>
        val (lookup2, lookup3) = (outs2.toList.toMap, outs3.toList.toMap)
        outs1.mapFilter {
          case (in, out1) =>
            for {
              out2 <- lookup2.get(in)
              out3 <- lookup3.get(in)
            } yield (in, (out1, out2, out3))
        }
    }

  /** Combines two multifetch acting on the same inputs to get an arbitrary combination of the outputs,
    * without requiring the intermediate json to be the same, as for `addDecoding`.
    */
  def mergeResults[F[_]: Monad, T[_] : Traverse, In, J1, J2, O1, O2, Out](
    mf1: Aux[F, T, In, O1, J1],
    mf2: Aux[F, T, In, O2, J2]
  )(merge: (T[(In, O1)], T[(In, O2)]) => T[(In, Out)]): Kleisli[F, T[In], T[(In, Out)]] =
    (mf1.fetch).product(mf2.fetch).map(merge.tupled)

  /** Combines three multifetch acting on the same inputs to get am arbitrary combination of the outputs,
    * without requiring the intermediate json to be the same.
    */
  def tripleMergeResults[F[_]: Monad , T[_] : Traverse: FunctorFilter, In, J1, J2, J3, O1, O2, O3, Out](
    mf1: Aux[F, T, In, O1, J1],
    mf2: Aux[F, T, In, O2, J2],
    mf3: Aux[F, T, In, O3, J3]
  )(merge: (T[(In, O1)], T[(In, O2)], T[(In, O3)]) => T[(In, Out)]): Kleisli[F, T[In], T[(In, Out)]] =
    Apply[({type L[a] = Kleisli[F, T[In], a]})#L].tuple3(mf1.fetch, mf2.fetch, mf3.fetch).map(merge.tupled)

  /** Allows to add an additional decoder to a "multi-fetch" instance, by providing an extension method `decodeAlso(additionalDecode)`,
    * subject to `F` having a `cats.Apply` instance
    */
  object Syntax {
    implicit class MultiFetchOps[F[_], T[_], I, O, J](m: Aux[F, T, I, O, J]) {
      def decodeAlso[O2](additionalDecode: Kleisli[F, J, O2])(implicit app: Apply[F]): Aux[F, T, I, (O, O2), J] = addDecoding(m, additionalDecode)
    }
  }

}

/** Defines intances of `TezosNodeMultiFetch` for block-related data */
object BlocksMultiFetchingInstances extends LazyLogging {
  import scala.concurrent.Future
  import io.circe.parser.decode

  /** a fetcher of blocks */
  def blocksMultiFetch(
    network: String,
    node: TezosRPCInterface,
    hashRef: BlockHash,
    concurrency: Int
  ) = new TezosNodeMultiFetch[Future, List] {
    import JsonDecoders.Circe.Blocks._

    type JsonType = String
    type In = Int //offset from head
    type Out = BlockData

    def makeUrl = (offset: In) => s"blocks/${hashRef.value}~${String.valueOf(offset)}"

    //fetch a future stream of values
    override val fetchJsonBatch =
      Kleisli(offsets => node.runBatchedGetQuery(network, offsets, makeUrl, concurrency))

    // decode with `JsonDecoders`
    override val decodeJson = Kleisli {
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
  def operationGroupMultiFetch(
    network: String,
    node: TezosRPCInterface,
    concurrency: Int
  ) = new TezosNodeMultiFetch[Future, List] {
    import JsonDecoders.Circe.Operations._

    type JsonType = String
    type In = BlockHash
    type Out = List[OperationsGroup]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/operations"

    override val fetchJsonBatch =
      Kleisli(hashes => node.runBatchedGetQuery(network, hashes, makeUrl, concurrency))

    override val decodeJson = Kleisli(
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

  def currentPeriodMultiFetch(
    network: String,
    node: TezosRPCInterface,
    concurrency: Int
  ) = new TezosNodeMultiFetch[Future, List] {

    type JsonType = String
    type In = BlockHash
    type Out = Option[String]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_period_kind"

    override val fetchJsonBatch =
      Kleisli(hashes => node.runBatchedGetQuery(network, hashes, makeUrl, concurrency))

    override val decodeJson = Kleisli(
      json => Future.successful(decode[String](json).toOption)
    )

  }

  def currentQuorumMultiFetch(
    network: String,
    node: TezosRPCInterface,
    concurrency: Int
  ) = new TezosNodeMultiFetch[Future, List] {

    type JsonType = String
    type In = BlockHash
    type Out = Option[Int]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_quorum"

    override val fetchJsonBatch =
      Kleisli(hashes => node.runBatchedGetQuery(network, hashes, makeUrl, concurrency))

    override val decodeJson = Kleisli(
      json => Future.successful(decode[Int](json).toOption)
    )

  }

  def currentProposalMultiFetch(
    network: String,
    node: TezosRPCInterface,
    concurrency: Int
  ) = new TezosNodeMultiFetch[Future, List] {

    type JsonType = String
    type In = BlockHash
    type Out = Option[BlockHash]

    val makeUrl = (hash: BlockHash) => s"blocks/${hash.value}/votes/current_proposal"

    override val fetchJsonBatch =
      Kleisli(hashes => node.runBatchedGetQuery(network, hashes, makeUrl, concurrency))

    override val decodeJson = Kleisli(
      json => Future.successful(decode[String](json).map(BlockHash).toOption)
    )

  }

}