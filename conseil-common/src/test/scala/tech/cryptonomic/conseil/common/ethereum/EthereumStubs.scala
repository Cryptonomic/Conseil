package tech.cryptonomic.conseil.common.ethereum

import cats.effect._
import org.http4s._
import org.http4s.client.Client
import fs2.Stream

import tech.cryptonomic.conseil.common.ethereum.rpc.EthereumClient
import tech.cryptonomic.conseil.common.rpc.RpcClient

/**
  * Stubs for the Ethereum-related tests.
  */
trait EthereumStubs {

  implicit val contextShift: ContextShift[IO]

  /**
    * Stubs that can help to provide tests for the [[EthereumClient]].
    *
    * Usage example:
    *
    * {{{
    *   "test name" in new EthereumClientStubs {
    *     // ethereumClientStub is available in the current scope
    *   }
    * }}}
    */
  trait EthereumClientStubs {

    def ethereumClientStub(jsonResponse: String): EthereumClient[IO] = {
      val response = Response[IO](
        Status.Ok,
        body = Stream(jsonResponse).through(fs2.text.utf8Encode)
      )
      val rpcClient =
        new RpcClient[IO]("https://api-endpoint.com", 1, Client.fromHttpApp(HttpApp.liftF(IO.pure(response))))
      new EthereumClient(rpcClient)
    }
  }

}
