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
    * 1. get json values from the node, given a traversable input collection
    * 2. keep the correlation with the input and convert every json to a corresponding output
    * 3. return the traversable collection containing paired input and output values, all wrapped in the effect F
    */
  def fetch(implicit app: Monad[F], tr: Traverse[T]): Kleisli[F, T[In], T[(In, Out)]] =
    fetchBatch.andThen(json => decodeData.second[In].traverse(json))

}

object MultiFetchDecoding {
  import cats.syntax.foldable._
  import cats.syntax.functorFilter._

  /* An alias used to define function constraints on the internal dependent types (i.e. `I`, `O`, `E`)
   * which would be otherwise opaque in any function signature
   */
  private type Aux[F[_], T[_], I, O, E] = MultiFetchDecoding[F, T] {
      type In = I
      type Out = O
      type Encoded = E
    }

  /** Adds an extra decoding operation for every intermediate encoded result, returning both the
    * original output `O` and the new decoded output `O2` as a tuple
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

  /** Combines two multifetch acting on the same inputs to get a tuple of the outputs */
  def tupledResults[F[_]: Monad, T[_] : Traverse: FunctorFilter, In, O1, O2, E1, E2](
    mf1: Aux[F, T, In, O1, E1],
    mf2: Aux[F, T, In, O2, E2]
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
  def tupledResults[F[_]: Monad, T[_] : Traverse: FunctorFilter, In, O1, O2, O3, E1, E2, E3](
    mf1: Aux[F, T, In, O1, E1],
    mf2: Aux[F, T, In, O2, E2],
    mf3: Aux[F, T, In, O3, E3]
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
    * without requiring the intermediate encoding to be the same, as for `addDecoding`.
    */
  def mergeResults[F[_]: Monad, T[_] : Traverse, In, E1, E2, O1, O2, Out](
    mf1: Aux[F, T, In, O1, E1],
    mf2: Aux[F, T, In, O2, E2]
  )(merge: (T[(In, O1)], T[(In, O2)]) => T[(In, Out)]): Kleisli[F, T[In], T[(In, Out)]] =
    (mf1.fetch).product(mf2.fetch).map(merge.tupled)

  /** Combines three multifetch acting on the same inputs to get am arbitrary combination of the outputs,
    * without requiring the intermediate encoding to be the same.
    */
  def tripleMergeResults[F[_]: Monad , T[_] : Traverse: FunctorFilter, In, E1, E2, E3, O1, O2, O3, Out](
    mf1: Aux[F, T, In, O1, E1],
    mf2: Aux[F, T, In, O2, E2],
    mf3: Aux[F, T, In, O3, E3]
  )(merge: (T[(In, O1)], T[(In, O2)], T[(In, O3)]) => T[(In, Out)]): Kleisli[F, T[In], T[(In, Out)]] =
    Apply[({type L[a] = Kleisli[F, T[In], a]})#L].tuple3(mf1.fetch, mf2.fetch, mf3.fetch).map(merge.tupled)

  /** Allows to add an additional decoder to a "multi-fetch" instance, by providing an extension method `decodeAlso(additionalDecode)`,
    * subject to `F` having a `cats.Apply` instance
    */
  object Syntax {
    implicit class MultiFetchOps[F[_], T[_], I, O, E](m: Aux[F, T, I, O, E]) {
      def decodeAlso[O2](additionalDecode: Kleisli[F, E, O2])(implicit app: Apply[F]): Aux[F, T, I, (O, O2), E] = addDecoding(m, additionalDecode)
    }
  }

}
