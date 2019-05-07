package tech.cryptonomic.conseil.generic.chain

import cats._
import cats.data.Kleisli
import cats.syntax.applicativeError._
import cats.syntax.semigroupal._

/** Contract for a generic combination of operations that get encoded data (usually json) from
  * some resource (e.g. the tezos node), then converts that to a value, batching multiple requests together.
  * The returned values are tupled with the corresponding input
  * The interface assumes some kind of higher-order typed effect `Eff[_]` is part
  * of the operation execution, and represents everything parametrically, to leave
  * room for future evolutions of the code protocol.
  * Thus the required implementation is in the form of a `cats.data.Kleisli` type,
  * parametric on effect `Eff`, input `In`, output `Out`. As needed, the in/out types are
  * wrapped in a `Coll[_]` type constructor that represents multiple values (e.g. a collection type).
  *
  * @tparam Eff the higher-order effect type (e.g. `Try`, `Future`, `IO`, `Either`)
  * @tparam Coll a traversable type (used to wrap mutliple requests and corresponding return values)
  * @tparam Err the expected type of possible errors that the Eff-wrapped operations might raise
  */
trait DataFetcher[Eff[_], Coll[_], Err] {
  /** the input type, e.g. ids of data */
  type In
  /** the output type, e.g. the decoded block data */
  type Out
  /** the encoded representation type used e.g. some Json representation */
  type Encoded

  /** an effectful function from a collection of inputs `T[In]`
    * to the collection of encoded values, tupled with the corresponding input `T[(In, Encoded)]`
    */
  def fetchData: Kleisli[Eff, Coll[In], Coll[(In, Encoded)]]

  /** an effectful function that decodes the json value to an output `Out` */
  def decodeData: Kleisli[Eff, Encoded, Out]

  /** override this function to run a side-effect if the data fetching step fails */
  def onDataFetchError: Err => Unit = Function.const(())

  /** override this function to run a side-effect if the decoding step fails */
  def onDecodingError: Err => Unit = Function.const(())

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
  * - fetchData function, that, given a collection of inputs, returns the effectful result
      of encoded data (e.g. json), paired with its input value (to preserve correlation)
  * - decodeData function that will decode every single endoded value to a full-blown output scala data type
  *
  * The effectul function is defined in term of a "Kleisli arrow", which is nothing but a fancy-named wrapper for A => F[B], where F[_] is the so-called effect
  * Using Kleisli enables a plethora of useful methods to call, that will more easily convert a Kleisli arrow to something that accepts different input, or that
  * adds further operations on input or output, or that adds other effects, like traversing or folding a collection of results if the B result type allows that.
  *
  * This enables us to easily compose the `fetchData` function and `decodeData` function to a general `fetch` function, that will use the former to retrieve a list of
  * encoded values, paired with input, and apply the decoding to each, all the while preserving the correlation with the original input value.
  *
  * In addition to this, we might want to apply multiple decoding steps in parallel, and pair the decoded results with the same inputs
  * Or still we might want to apply more than one fetcher to the same set of inputs, and pair all the results, in a single pass
  * This is where the `addDecoding` and `mergeResults`/`fetchCombine` combinators comes useful.
  */
object DataFetcher {
  import cats.syntax.foldable._
  import cats.syntax.functorFilter._

  /** using the combination of the provided fetcher functions we fetch all data with this general outline:
    * 1. get encoded (e.g. json) values from the node, given a traversable input collection
    * 2. keep the correlation with the input and decode each value (e.g. from json) to a corresponding output
    * 3. return the traversable collection containing paired input and output values, all wrapped in the effect F
    * The resulting effect will also encode a possible failure in virtue of the implicit MonadError instance that is provided
    */
  def fetch[In, Out, Eff[_], Coll[_]: Traverse, Err]
    (implicit app: MonadError[Eff, Err], fetcher: DataFetcher.Aux[Eff, Coll, Err, In, Out, _]): Kleisli[Eff, Coll[In], Coll[(In, Out)]] =
    fetcher.fetchData.onError { case err => Kleisli.pure(fetcher.onDataFetchError(err)) }
      .andThen( encoded =>
        fetcher.decodeData
          .onError { case err => Kleisli.pure(fetcher.onDecodingError(err)) }
          .second[In]         //only decodes the second element of the tuple (the encoded value) and pass the first on (the input)
          .traverse(encoded)  //operates on a whole traverable Coll and wraps the resulting collection in a single Effect wrapper
      )

  /* An alias used to define function constraints on the internal dependent types (i.e. `I`, `O`, `E`)
   * which would be otherwise opaque in any function signature
   *
   * Example: the fetcher for tezos blocks might have type `Aux[Future, List, Throwable, Int, BlockData, String]
   * where the Int is for the offset from a given block, and String is a representation of a json value.
   */
  private type Aux[Eff[_], Coll[_], Err, Input, Output, Encoding] = DataFetcher[Eff, Coll, Err] {
      type In = Input
      type Out = Output
      type Encoded = Encoding
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
  implicit def decodeBoth[Eff[_]: Apply, Coll[_], Err, Input, Output, Output2, Encoding](
    fetcher: Aux[Eff, Coll, Err, Input, Output, Encoding],
    additionalDecode: Kleisli[Eff, Encoding, Output2]
  ) = new DataFetcher[Eff, Coll, Err] {
    type In = Input
    type Out = (Output, Output2)
    type Encoded = Encoding

    def fetchData = fetcher.fetchData

    def decodeData = fetcher.decodeData.product(additionalDecode)
  }

  /** Combines two DataFetchers acting on the same inputs to get a tuple of the outputs
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
    *      DataFetcher.fetchMerge(currentQuorumFetcher, currentProposalFetcher)(CurrentVotes(_, _))
    *
    * and once we run the resulting Kleisli on the merged fetchers, passing a `List[BlockHash]` we get a `Future[List[(BlockHash, CurrentVotes)]]
    *
    */
  def fetchMerge[Eff[_]: Monad, Coll[_]: Traverse : FunctorFilter, Err, Input, Output1, Output2, Output, Encoding1, Encoding2](
    mf1: Aux[Eff, Coll, Err, Input, Output1, Encoding1],
    mf2: Aux[Eff, Coll, Err, Input, Output2, Encoding2]
  )(merge: (Output1, Output2) => Output = (o1: Output1, o2: Output2) => (o1, o2)
  )(implicit appErr: MonadError[Eff, Err]): Kleisli[Eff, Coll[Input], Coll[(Input, Output)]] =
    fetchCombine(mf1, mf2) {
      (outs1: Coll[(Input, Output1)], outs2: Coll[(Input, Output2)]) =>
        outs1.mapFilter {
          case (in, out1) =>
            outs2.collectFirst{
              case (`in`, out2) => (`in`, merge(out1, out2))
            }
        }
    }

  /** Combines two DataFetchers acting on the same inputs to get an arbitrary combination of the outputs,
    * without requiring the intermediate encoding to be the same, as opposed to `addDecoding`.
    *
    * Example:
    * Considering the same case for `fetchMerge` that fetches votes data: quorum and proposal for a list
    * of block hashes, we might want to directly take the corresponding traversables and combine them in a resulting value.
    *
    * The difference with merge is that we combine the whole traversables (e.g. Lists), containing the "hash to value" pairs, instead
    * of merging each element with matching input `In` into a single traversable of outputs
    */
  def fetchCombine[Eff[_]: Monad, Coll[_], Err, Input, Encoding1, Encoding2, Output1, Output2, Output](
    fetcher1: Aux[Eff, Coll, Err, Input, Output1, Encoding1],
    fetcher2: Aux[Eff, Coll, Err, Input, Output2, Encoding2]
  )(combine: (Coll[(Input, Output1)], Coll[(Input, Output2)]) => Coll[(Input, Output)]
  )(implicit
    traverse: Traverse[Coll],
    appErr: MonadError[Eff, Err]
  ): Kleisli[Eff, Coll[Input], Coll[(Input, Output)]] =
    fetch(traverse, appErr, fetcher1)
      .product(fetch(traverse, appErr, fetcher2))
      .map(combine.tupled)

}
