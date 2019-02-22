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
  def addDecoding[F[_]: Apply, T[_], I, O, O2, J](multiFetch: Aux[F,T,I,O,J], additionalDecode: Kleisli[F, J, O2]) = new TezosNodeMultiFetch[F, T] {
    type In = I
    type Out = (O, O2)
    type JsonType = J

    def fetchJsonBatch = multiFetch.fetchJsonBatch

    def decodeJson = multiFetch.decodeJson.product(additionalDecode)
  }

  /** Combines two multifetch acting on the same inputs to get a pair of the outputs,
   * considering that the intermediate json should not be the same, as required for `addDecoding`
   * TODO consider composing with a different function that (,) as to combine more than one with different out shapes
   */
  def tupleResults[F[_]: Monad, T[_] : Traverse: FunctorFilter, I, O1, O2, J1, J2](mf1: Aux[F, T, I, O1, J1], mf2: Aux[F, T, I, O2, J2]): Kleisli[F, T[I], T[(I, (O1, O2))]] =
    (mf1.fetch).product(mf2.fetch).map {
      case (outs1, outs2) =>
        outs1.mapFilter {
          case (in, out1) =>
            outs2.collectFirst{
              case (`in`, out2) => (`in`, (out1, out2))
            }
        }
    }

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

    def makeBlocksUrl = (offset: In) => s"blocks/${hashRef.value}~${String.valueOf(offset)}"

    //fetch a future stream of values
    override val fetchJsonBatch =
      Kleisli(offsets => node.runBatchedGetQuery(network, offsets, makeBlocksUrl, concurrency))

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

    val makeOperationsUrl = (hash: BlockHash) => s"blocks/${hash.value}/operations"

    override val fetchJsonBatch =
      Kleisli(hashes => node.runBatchedGetQuery(network, hashes, makeOperationsUrl, concurrency))

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

}