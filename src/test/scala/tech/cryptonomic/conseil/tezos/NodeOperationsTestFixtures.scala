package tech.cryptonomic.conseil.tezos

import cats.{~>, Id, MonadError}
import cats.data.Kleisli
import tech.cryptonomic.conseil.generic.rpc.DataFetcher
import TezosTypes._

/** provides implicit instances useful to call methods for testing the `NodeOperator`*/
trait NodeOperationsTestFixtures {

  //we need this to run the node's methods with an Id effect, though this instance is not lawful
  implicit val idErrorInstance = new MonadError[Id, Throwable] {
    override def raiseError[A](e: Throwable): cats.Id[A] = throw e
    override def flatMap[A, B](fa: cats.Id[A])(f: A => cats.Id[B]): cats.Id[B] = f(fa)
    override def tailRecM[A, B](a: A)(f: A => cats.Id[Either[A, B]]): cats.Id[B] = f(a) match {
      case Left(value) => raiseError(new RuntimeException("didn't expect"))
      case Right(value) => value
    }
    override def pure[A](x: A): cats.Id[A] = x
    override def handleErrorWith[A](fa: cats.Id[A])(f: Throwable => cats.Id[A]): cats.Id[A] = fa
  }

  /** Use it to get dummy instances of fetchers needed by complex node operations
    * and make them available implicitly
    */
  protected def withDummyFetchers[Eff[_]](
    testCode: DataFetcher.Std[Eff, BlockHash, (List[OperationsGroup], List[AccountId])] => DataFetcher.Std[
      Eff,
      (BlockHash, Offset),
      Option[Int]
    ] => DataFetcher.Std[Eff, (BlockHash, Offset), Option[TezosTypes.ProtocolId]] => Any
  )(implicit fk: Id ~> Eff) = {
    val extraBlockFetcher = dummyFetcher[Eff, BlockHash, (List[OperationsGroup], List[AccountId])](out = (Nil, Nil))
    val quorumFetcher =
      dummyFetcher[Eff, (BlockHash, Offset), Option[Int]](out = Some(TezosResponseBuilder.votesQuorum))
    val proposalFetcher = dummyFetcher[Eff, (BlockHash, Offset), Option[ProtocolId]](out = None)
    testCode(extraBlockFetcher)(quorumFetcher)(proposalFetcher)
  }

  /* A partly-stubbed fetcher of blocks that will return a json response, computed
   * from the passed-in function argument, and will try to decode it
   * as json to a `BlockData` instance, all within an arbitrary Eff, given the proper transformation funK: Id ~> Eff
   */
  protected def testBlockFetcher[Eff[_]](jsonFetch: (BlockHash, Offset) => String)(implicit funK: Id ~> Eff) =
    new DataFetcher[Eff, (BlockHash, Offset), BlockData] {
      import JsonDecoders.Circe.Blocks._
      import JsonDecoders.Circe.decodeLiftingTo

      type Encoded = String
      type Error = Throwable

      override def fetchData =
        Kleisli[Id, (BlockHash, Offset), Encoded] {
          jsonFetch.tupled
        }.mapK(funK)

      override def decodeData =
        Kleisli(
          json => decodeLiftingTo[Id, BlockData](json)
        ).mapK(funK)
    }

  /* Provides a fetcher that
   * - encodes internally to an empty string
   * - returns always `out` as the final decoded result for any input
   * - wraps the results into an arbitrary Eff, given the proper transformation funK: Id ~> Eff
   */
  private def dummyFetcher[Eff[_], I, O](out: O)(implicit funK: Id ~> Eff) =
    new DataFetcher[Eff, I, O] {

      type Encoded = String
      type Error = Throwable

      override def fetchData =
        Kleisli[Id, I, Encoded](
          Function.const("")
        ).mapK(funK)

      override def decodeData =
        Kleisli[Id, Encoded, O] {
          Function.const(out)
        }.mapK(funK)
    }

}
