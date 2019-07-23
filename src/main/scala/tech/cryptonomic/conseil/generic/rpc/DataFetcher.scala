package tech.cryptonomic.conseil.generic.rpc

import cats._
import cats.arrow._
import cats.data.Kleisli

/** Contract for a generic combination of operations that get encoded data (usually json) from
  * some resource (e.g. the tezos node), then converts that to a value.
  * The interface assumes some kind of higher-order typed effect `Eff[_]` is part
  * of the operation execution, and represents everything parametrically, to leave
  * room for future evolutions of the code protocol.
  * Thus the required implementation is in the form of a `cats.data.Kleisli` type,
  * parametric on effect `Eff`, input `In`, output `Out`.
  * The implementation should take into account some form of (side-effecting) error handling procedure,
  * where the error type is once again parametrically defined as `Err`.
  *
  * @tparam Eff the higher-order effect type (e.g. `Try`, `Future`, `IO`, `Either`)
  * @tparam In the input type (e.g. ids of the accounts, offset from the head for blocks)
  * @tparam Out the output type (e.g. decoded block data)
  */
trait DataFetcher[Eff[_], In, Out] {

  /** the encoded representation type used e.g. some Json representation */
  type Encoded

  /** the expected type of possible errors that the Eff-wrapped operations might raise */
  type Error

  /** an effectful function from `In` to the `Encoded` value */
  def fetchData: Kleisli[Eff, In, Encoded]

  /** an effectful function that decodes the json value to an output `Out` */
  def decodeData: Kleisli[Eff, Encoded, Out]

  /** override this function to run a side-effect if the data fetching step fails */
  def onDataFetchError: Error => Unit = Function.const(())

  /** override this function to run a side-effect if the decoding step fails */
  def onDecodingError: Error => Unit = Function.const(())

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
  import cats.syntax.applicativeError._
  import cats.syntax.semigroupal._

  /** using the combination of the provided fetcher functions we fetch all data with this general outline:
    * 1. get encoded (e.g. json) values from the node, given a traversable input collection
    * 2. keep the correlation with the input and decode each value (e.g. from json) to a corresponding output
    * 3. return the traversable collection containing paired input and output values, all wrapped in the effect F
    * The resulting effect will also encode a possible failure in virtue of the implicit MonadError instance that is provided
    */
  def fetcher[Eff[_], In, Out, Err](
    implicit fetcher: Aux[Eff, In, Out, _, Err],
    flatMapper: FlatMap[Eff],
    errorHandling: ApplicativeError[Eff, Err]
  ): Kleisli[Eff, In, Out] =
    fetcher.fetchData.onError { case err => Kleisli.pure(fetcher.onDataFetchError(err)) }
      .andThen(
        fetcher.decodeData.onError { case err => Kleisli.pure(fetcher.onDecodingError(err)) }
      )

  /* An alias used to define function constraints on the internal dependent types (i.e. `I`, `O`, `E`)
   * which would be otherwise opaque in any function signature
   *
   * Example: the fetcher for tezos blocks might have type `Aux[Future, List, Throwable, Int, BlockData, String]
   * where the Int is for the offset from a given block, and String is a representation of a json value.
   */
  type Aux[Eff[_], In, Out, Encoding, Err] = DataFetcher[Eff, In, Out] {
    type Error = Err
    type Encoded = Encoding
  }

  /** An alias that describes fetchers using
    * - String as internal Encoding of the data
    * - Throwable as the error that might occur during the operation
    * This is the most common situation that Lorre's gonna handle
    */
  type Std[Eff[_], In, Out] = Aux[Eff, In, Out, String, Throwable]

  /** Adds an extra decoding operation for every intermediate encoded result, returning both the
    * original output `O` and the new decoded output `O2` as a tuple
    *
    * Example: we can combine the operation data read for a block hash from the "blocks/hash/operations" endpoint
    * and decode both a list of Operations, and a list of AccountIds, extracted from the same json string
    *
    * Thus we use addDecoding[Future, List, BlockHash, List[Operations], List[AccountId], String](operationsFetcher, accountDecoder)
    * The fetcher contains both the fetch operation and one decoding function, while the `additionalDecode` is the second decoding function.
    *
    * Combining those we have a single Kleisli function that
    * - fetches the json for the operations on the block hash
    * - decodes the json string both as a list of operations and a list of accounts, extracted from the operations data
    * - returns the decodings in a tuple (Output1, Ouput2), e.g. the pair of operations and accounts
    */
  def multiDecodeFetcher[Eff[_]: Apply, In, Out, Out2, Encoding, Err](
    implicit
    fetcher: Aux[Eff, In, Out, Encoding, Err],
    additionalDecode: Kleisli[Eff, Encoding, Out2]
  ) = new DataFetcher[Eff, In, (Out, Out2)] {
    type Error = Err
    type Encoded = Encoding

    def fetchData = fetcher.fetchData

    def decodeData = fetcher.decodeData.product(additionalDecode)
  }

  /** Provides a generic way to convert the effect-type of a data fetcher, if there's
    * a "natural transformation" `F ~> G` in scope
    *
    * Please refer to `tezos.cryptonomic.conseil.generic.rpc.RpcHandler.functionK` for
    * an explanatory example.
    */
  def functionK[F[_], G[_], Err, Input, Encoding](implicit nat: F ~> G) =
    new FunctionK[Aux[F, Input, ?, Encoding, Err], Aux[G, Input, ?, Encoding, Err]] {

      def apply[A](fa: Aux[F, Input, A, Encoding, Err]): Aux[G, Input, A, Encoding, Err] =
        new DataFetcher[G, Input, A] {
          type Error = Err
          type Encoded = fa.Encoded

          def fetchData: Kleisli[G, Input, Encoding] = Kleisli.liftFunctionK(nat)(fa.fetchData)

          def decodeData: Kleisli[G, Encoding, A] = Kleisli.liftFunctionK(nat)(fa.decodeData)
        }

    }

  /** Import this object in scope, i.e.
    * `import t.c.c.g.c.DataFetcher.Instances._`
    * To get implicit instances in scope for additioal and generic operations on fetchers
    */
  object Instances {

    /** A Profunctor allows to "map" on both sides of a function-like type, that is,
      * considering a DataFetcher that reads a `String` type and returns an `Int` type
      * - change the output from an Int to a Double, using fetcher.rmap(f: Int => Double) (convert the result)
      * - change the input from String to an Boolean, using fetcher.lmap(f: Boolean => String) (adapt the input)
      * - change both input from String to Boolean and output from Int to Double at the same time,
      *   using fetcher.dimap(f1: Boolean => String)(f2: Int => Double)
      *
      * The use case is to create the simplest possible fetchers and compose extra conversions after the definition.
      * To enable the extension methods, you need to also `import cats.syntax.profunctor._`
      */
    implicit def profunctorInstances[Eff[_]: Functor, Encoding, Err] =
      new Profunctor[Aux[Eff, ?, ?, Encoding, Err]] {
        override def dimap[A, B, C, D](
          fab: Aux[Eff, A, B, Encoding, Err]
        )(f: C => A)(g: B => D): Aux[Eff, C, D, Encoding, Err] =
          new DataFetcher[Eff, C, D] {
            type Error = Err
            type Encoded = Encoding

            def fetchData: Kleisli[Eff, C, Encoding] = fab.fetchData.local(f)

            def decodeData: Kleisli[Eff, Encoding, D] = fab.decodeData.map(g)

          }
      }

  }

}
