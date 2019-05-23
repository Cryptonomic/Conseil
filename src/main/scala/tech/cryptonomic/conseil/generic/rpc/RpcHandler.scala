package tech.cryptonomic.conseil.generic.rpc

import cats._
import cats.arrow._
import cats.data.Kleisli

/** Defines operations to execute calls against a remote node
 *
 * For the sake of comprehension, we can compare a Kleisli[F, A, B] data-type
 * as a "special" function that wraps the result in some "effect" F, i.e. A => F[B]
 *
 * @tparam Eff the effect associated with execution (Future, IO, Either, Id...)
 * @tparam Command represents the input to identify the endpoint (e.g. a path string)
 * @tparam Reponse represents the output from the node (e.g. a json string)
 */
trait RpcHandler[Eff[_], Command, Response] {
  import cats.data.Kleisli

  /** the type of a Post payload, only used for Post calls */
  type PostPayload

  /** describes the effectful execution of a GET http call */
  def getQuery: Kleisli[Eff, Command, Response]

  /** describes the effectful execution of a POST http call */
  def postQuery: Kleisli[Eff, (Command, Option[PostPayload]), Response]

}

/** Provides useful functions to interact with Rpc Nodes*/
object RpcHandler {

  /** An alias to express explicit type requirements including the internal Payload type of the RpcHandler */
  type Aux[Eff[_], Command, Response, Payload] = RpcHandler[Eff, Command, Response] { type PostPayload = Payload }

  /** Factor method based on an implicit instance being available in scope */
  def apply[Eff[_], Command, Response,  PostPayload](implicit rpc: Aux[Eff, Command, Response, PostPayload]) = rpc

  /** A static call that uses the implicit `RpcHandler` instance available for the expected parameter types */
  def runGet[Eff[_], Command, Response](
    command: Command
  )(implicit
    rpc: RpcHandler[Eff, Command, Response]
  ): Eff[Response] =
    rpc.getQuery.run(command)

  /** A static call that uses the implicit `RpcHandler` instance available for the expected parameter types */
  def runPost[Eff[_], Command, Response, PostPayload](
    command: Command,
    payload: Option[PostPayload] = None
  )(implicit
    rpc: Aux[Eff, Command, Response, PostPayload]
  ): Eff[Response] =
    rpc.postQuery.run((command, payload))

  /** Provides a generic way to convert the effect-type of an rpc handler, if there's
    * a "natural transformation" `F ~> G` in scope
    * Use case
    * - suppose we have a RpcHandler using `Future`s as the effect, e.g. RpcHandler[Future, String, String]
    * - suppose also that we can convert every Future[A] to a IO[A], independently of its content type `A`.
    *   This is done defining a "Higher-Kinded Function" `FunctionK`, also commonly referred to as
    *   a "natural transformation" between functors `Future` and `IO`, and denoted as `Future ~> IO`.
    *
    * We have here defined an additional transformation, based on `Future ~> IO` that can be used to convert
    * our RpcHandler[Future, String, String] to a RpcHandler[IO, String, String], in a natural and
    * purely mechanical way, with no need to manually implement the latter.
    *
    * For the sake of completeness, this specific example can be misleading, as using this transformation
    * for the Future ~> IO case is inefficient and technically wrong, because of the "strict" nature of Future's
    * evaluation strategy, as opposed to IO's "lazy" evaluation.
    */
  def functionK[F[_], G[_], Command, Payload](implicit nat: F ~> G) =
    new FunctionK[Aux[F, Command, ?, Payload], Aux[G, Command, ?, Payload]] {

      override def apply[T](fa: Aux[F, Command, T, Payload]): Aux[G, Command, T, Payload] =
        new RpcHandler[G, Command, T] {
          type PostPayload = Payload

          def getQuery: Kleisli[G, Command, T] =
            Kleisli.liftFunctionK(nat)(fa.getQuery)

          def postQuery: Kleisli[G, (Command, Option[Payload]), T] =
            Kleisli.liftFunctionK(nat)(fa.postQuery)

        }
    }

}
