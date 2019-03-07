package tech.cryptonomic.conseil.generic.chain

import cats._
import cats.data.Kleisli
import cats.syntax.semigroupal._

/** Contract for a generic combination of operations that get encoded data (usually json) from
  * some resource (e.g. the tezos node), then converts that to a value, batching multiple requests together.
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
trait MultiFetchDecoding[F[_], T[_]] {
  /** the input type, e.g. ids of data */
  type In
  /** the output type, e.g. the decoded block data */
  type Out
  /** the encoded representation type used e.g. some Json representation */
  type Encoded

  /** an effectful function from a collection of inputs `T[In]`
    * to the collection of encoded values, tupled with the corresponding input `T[(In, Encoded)]`
    */
  def fetchBatch: Kleisli[F, T[In], T[(In, Encoded)]]

  /** an effectful function that decodes the json value to an output `Out` */
  def decodeData: Kleisli[F, Encoded, Out]

  /** using the combination of the provided functions we fetch all data with this general outline:
    * 1. get encoded (e.g. json) values from the node, given a traversable input collection
    * 2. keep the correlation with the input and decode each value (e.g. from json) to a corresponding output
    * 3. return the traversable collection containing paired input and output values, all wrapped in the effect F
    */
  def fetch(implicit app: Monad[F], tr: Traverse[T]): Kleisli[F, T[In], T[(In, Out)]] =
    fetchBatch.andThen(json => decodeData.second[In].traverse(json))

}

/** How to use, based on tezos examples
  *
  * The current flow to obtain data from tezos is roughly the same, the following flow describes the operations, the expected content
  * of each step, and an example from tezos
  *
  * [Inputs]               -> [Fetch a Data Batch]                                                        -> [Decode Data]
  * [A list of input data] -> [A list of json data paired with corresponding input, wrapped in an effect] -> [A list of output data, paired with inputs, wrapped in the effect]
  * [List of offsets]      -> [Future of pairs (offset, json) that describes block data]                  -> [Future of pairs (offset, BlockData) with the block now decoded]
  * [List of block hashes] -> [Future of pairs (hash, json) that describes operations data]               -> [Future of pairs (hash, List[Operations]) with the ops now decoded]
  *
  * which suggest that we may encode this in a trait that separates the steps with a
  * - fetchBatch function, that, given a collection of inputs, returns the effectful result
      of encoded data (e.g. json), paired with its input value (to preserve correlation)
  * - decodeData function that will decode every single endoded value to a full-blown output scala data type
  *
  * The effectul function is defined in term of a "Kleisli arrow", which is nothing but a fancy-named wrapper for A => F[B], where F[_] is the so-called effect
  * Using Kleisli enables a plethora of useful methods to call, that will more easily convert a Kleisli arrow to something that accepts different input, or that
  * adds further operations on input or output, or that adds other effects, like traversing or folding a collection of results if the B result type allows that.
  *
  * This enables us to easily compose the `fetchBatch` function and `decodeData` function to a general `fetch` function, that will use the former to retrieve a list of
  * encoded values, paired with input, and apply the decoding to each, all the while preserving the correlation with the original input value.
  *
  * In addition to this, we might want to apply multiple decoding steps in parallel, and pair the decoded results with the same inputs
  * Or still we might want to apply more than one fetcher to the same set of inputs, and pair all the results, in a single pass
  * This is where the `addDecoding` and `mergeResults`/`combineResults` combinators comes useful.
  */
object MultiFetchDecoding {
  import cats.syntax.foldable._
  import cats.syntax.functorFilter._

  /* An alias used to define function constraints on the internal dependent types (i.e. `I`, `O`, `E`)
   * which would be otherwise opaque in any function signature
   *
   * Example: the fetcher for tezos blocks might have type `Aux[Future, List, Int, BlockData, String]
   * where the Int is for the offset from a given block, and String is a representation of a json value.
   */
  private type Aux[F[_], T[_], I, O, E] = MultiFetchDecoding[F, T] {
      type In = I
      type Out = O
      type Encoded = E
    }

  /** Adds an extra decoding operation for every intermediate encoded result, returning both the
    * original output `O` and the new decoded output `O2` as a tuple
    *
    * Example: we can combine the operation data read for a block hash from the "blocks/hash/operations" endpoint
    * and decode both a list of Operations, and a list of AccountIds, extracted from the same json string
    *
    * Thus we use addDecoding[Future, List, BlockHash, List[Operations], List[AccountId], String](operationsFetcher, accountDecoder)
    * The fetcher contains both the fetch operation and one decoding function, while the `additionalDecode` is the second decoding function.
    *
    * Combining those we have a single Kleisli that
    * - fetches the json for the operations on a list of block hashes
    * - decodes the json string both as a list of operations and a list of accounts
    * - returns the decodings in a tuple, where each pair of outputs is also paired to the corresponding input hash, i.e. (I -> (O, O2))
    */
  def addDecoding[F[_]: Apply, T[_], I, O, O2, E](
    multiFetch: Aux[F,T,I,O,E],
    additionalDecode: Kleisli[F, E, O2]
  ) = new MultiFetchDecoding[F, T] {
    type In = I
    type Out = (O, O2)
    type Encoded = E

    def fetchBatch = multiFetch.fetchBatch

    def decodeData = multiFetch.decodeData.product(additionalDecode)
  }

  /** Combines two multifetch acting on the same inputs to get a tuple of the outputs
    *
    * For an example refer to `mergeResults` taking three arguments, and
    * consider the equivalent case for two.
    */
  def mergeResults[F[_]: Monad, T[_] : Traverse: FunctorFilter, In, O1, O2, Out, E1, E2](
    mf1: Aux[F, T, In, O1, E1],
    mf2: Aux[F, T, In, O2, E2]
  )(merge: (O1, O2) => Out): Kleisli[F, T[In], T[(In, Out)]] =
    combineResults(mf1, mf2) {
      (outs1, outs2) =>
        outs1.mapFilter {
          case (in, out1) =>
            outs2.collectFirst{
              case (`in`, out2) => (`in`, merge(out1, out2))
            }
        }
    }

  /** Combines three multifetch acting on the same inputs to get a 3-tuple of the outputs and merge them all
    * in a single output value, using a `merge` function of the three
    *
    * Example:
    * we might have some input hashes for blocks and want to load three separate data related to those
    * blocks: current voting period, current quorum, current proposal.
    *
    * Each of those needs to call a separate endpoint, and will have a different json string returned and
    * decoded as different types.
    *
    * Yet the inputs are the same, so we can create separate multi-fetchers that read from the endpoints and decodes
    * each his own json, but tuple them to execute them on the same input hashes and collect a tuple of the
    * `(period, quorum, proposal)` for each input `BlockHash` in the list.
    *
    * By default the tuple is left as is, but we can merge the values passing a custom `merge` function
    *  e.g. we can build a `CurrentVotes` object using its constructor method as merge.
    *
    * We tuple the fetchers as in:
    *
    *      MultiFetchDecoding.mergeResults(currentPeriodMultiFetch, currentQuorumMultiFetch, currentProposalMultiFetch)(CurrentVotes.apply)
    *
    * and if we run the "fetch" Kleisli on the merged fetchers, passing a `List[BlockHash]` we get a `Future[List[(BlockHash, CurrentVotes)]]
    */
  def mergeResults[F[_]: Monad, T[_] : Traverse: FunctorFilter, In, O1, O2, O3, Out, E1, E2, E3](
    mf1: Aux[F, T, In, O1, E1],
    mf2: Aux[F, T, In, O2, E2],
    mf3: Aux[F, T, In, O3, E3]
  )(merge: (O1, O2, O3) => Out = (_: O1, _: O2, _: O3)): Kleisli[F, T[In], T[(In, Out)]] =
    combineResults(mf1, mf2, mf3) {
      (outs1, outs2, outs3) =>
        /* here we only traverse a single output collection, assuming that the others will share the same "keys" (i.e. first element in the tuple)
         * and using them as maps for faster match, instead of doing a linear search each time.
         * We take advantage of the generic conversion from Traverse to list and then to a scala Map
         */
        val (lookup2, lookup3) = (outs2.toList.toMap, outs3.toList.toMap)
        outs1.mapFilter {
          case (in, out1) =>
            for {
              out2 <- lookup2.get(in)
              out3 <- lookup3.get(in)
            } yield (in, merge(out1, out2, out3))
        }
    }

  /** Combines two multifetch acting on the same inputs to get an arbitrary combination of the outputs,
    * without requiring the intermediate encoding to be the same, as opposed to `addDecoding`.
    *
    * For an example refer to `combineResults` with three arguments, and consider the equivalent for two.
    */
  def combineResults[F[_]: Monad, T[_] : Traverse, In, E1, E2, O1, O2, Out](
    mf1: Aux[F, T, In, O1, E1],
    mf2: Aux[F, T, In, O2, E2]
  )(combine: (T[(In, O1)], T[(In, O2)]) => T[(In, Out)]): Kleisli[F, T[In], T[(In, Out)]] =
    (mf1.fetch).product(mf2.fetch).map(combine.tupled)

  /** Combines three multifetch acting on the same inputs to get am arbitrary combination of the outputs,
    * without requiring the intermediate encoding to be the same.
    *
    * Example:
    * Considering the same case for `mergeResults` that fetches votes data: period, quorum and proposal for a list
    * of block hashes, we might want to directly take the three corresponding traversables and combine them in a resulting value.
    *
    * The difference with merge is that we combine three whole traversables (e.g. Lists), containing the "hash to value" pairs, instead
    * of merging each element with matching input `I` into a single traversable of outputs
    */
  def combineResults[F[_]: Monad , T[_] : Traverse: FunctorFilter, In, E1, E2, E3, O1, O2, O3, Out](
    mf1: Aux[F, T, In, O1, E1],
    mf2: Aux[F, T, In, O2, E2],
    mf3: Aux[F, T, In, O3, E3]
  )(combine: (T[(In, O1)], T[(In, O2)], T[(In, O3)]) => T[(In, Out)]): Kleisli[F, T[In], T[(In, Out)]] =
    Apply[({type L[a] = Kleisli[F, T[In], a]})#L].tuple3(mf1.fetch, mf2.fetch, mf3.fetch).map(combine.tupled)

  /** Allows to add an additional decoder to a "multi-fetch" instance, by providing an extension method `decodeAlso(additionalDecode)`,
    * subject to `F` having a `cats.Apply` instance.
    * The result is only syntactic sugar over calling `MultiFetchDecoding.addDecoding`
    */
  object Syntax {

    implicit class MultiFetchOps[F[_], T[_], I, O, E](m: Aux[F, T, I, O, E]) {
      def decodeAlso[O2](additionalDecode: Kleisli[F, E, O2])(implicit app: Apply[F]): Aux[F, T, I, (O, O2), E] = addDecoding(m, additionalDecode)
    }
  }

}
