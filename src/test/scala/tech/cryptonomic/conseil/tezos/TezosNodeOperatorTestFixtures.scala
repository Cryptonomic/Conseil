package tech.cryptonomic.conseil.tezos

import cats.{Id, MonadError}
import cats.data.Kleisli
import tech.cryptonomic.conseil.generic.chain.DataFetcher
import TezosTypes._

/** provides implicit instances useful to call methods for testing the `NodeOperator`*/
trait TezosNodeOperatorTestImplicits {

  //we need this to run the node's methods with an Id effect, though this instance is not lawful
  implicit val idErrorInstance = new MonadError[Id, Throwable] {
    override def raiseError[A](e: Throwable): cats.Id[A] = throw e
    override def flatMap[A, B](fa: cats.Id[A])(f: A => cats.Id[B]): cats.Id[B] = f(fa)
    override def tailRecM[A, B](a: A)(f: A => cats.Id[Either[A,B]]): cats.Id[B] = f(a) match {
      case Left(value) => raiseError(new RuntimeException("didn't expect"))
      case Right(value) => value
    }
    override def pure[A](x: A): cats.Id[A] = x
    override def handleErrorWith[A](fa: cats.Id[A])(f: Throwable => cats.Id[A]): cats.Id[A] = fa
  }

  //type alias, makes things more readable
  type TestFetcher[In, Out] = DataFetcher.Aux[Id, Throwable, In, Out, String]

  /** Use it to get dummy instances of fetchers needed by complex node operations
    * and make them available implicitly
    */
  protected def withInstances (
    testCode:
      TestFetcher[BlockHash, (List[OperationsGroup], List[AccountId])] =>
      TestFetcher[(BlockHash, Option[Offset]), Option[Int]] =>
      TestFetcher[(BlockHash, Option[Offset]), Option[TezosTypes.ProtocolId]] =>
      Any
  ) = {
    val extraBlockFetcher = dummyFetcher[BlockHash, (List[OperationsGroup], List[AccountId])](out = (Nil, Nil))
    val quorumFetcher = dummyFetcher[(BlockHash, Option[Offset]), Option[Int]](out = None)
    val proposalFetcher = dummyFetcher[(BlockHash, Option[Offset]), Option[ProtocolId]](out = None)
    testCode(extraBlockFetcher)(quorumFetcher)(proposalFetcher)
  }

  /** A semi-realistic fetcher of blocks that will return a single json response, passed-in
    * as an argument, and will try to decode it as json to a `BlockData` instance
    */
  protected def testBlockFetcher(jsonResponse: String) = new DataFetcher[Id, Throwable] {
    type Encoded = String
    type In = Offset
    type Out = BlockData

    import JsonDecoders.Circe.Blocks._
    import JsonDecoders.Circe.decodeLiftingTo

    override def fetchData = Kleisli[Id, In, Encoded](
      Function.const(jsonResponse)
    )

    override def decodeData = Kleisli(
      json => decodeLiftingTo[Id, BlockData](json)
    )
  }

  /* Provides a fetcher that
   * - encodes internally to an empty string
   * - returns always `out` as the final decoded result for any input
   */
  private def dummyFetcher[I, O](out: O) = new DataFetcher[Id, Throwable] {
    type Encoded = String
    type In = I
    type Out = O

    override def fetchData = Kleisli[Id, In, Encoded](
      Function.const("")
    )

    override def decodeData = Kleisli[Id, String, Out]{
      Function.const(out)
    }
  }

}