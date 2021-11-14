package tech.cryptonomic.conseil.common.bitcoin

import cats.effect._
import cats.arrow.FunctionK
import slick.jdbc.PostgresProfile.api._
import slickeffect.Transactor
import org.http4s._
import org.http4s.client.Client
import fs2.Stream

import tech.cryptonomic.conseil.common.bitcoin.rpc.BitcoinClient
import tech.cryptonomic.conseil.common.rpc.RpcClient

/**
  * Stubs for the Bitcoin-related tests.
  */
trait BitcoinStubs {

  // implicit val contextShift: ContextShift[IO]

  /**
    * Stubs that can help to provide tests for the [[BitcoinClient]].
    *
    * Usage example:
    *
    * {{{
    *   "test name" in new BitcoinClientStubs {
    *     // bitcoinClientStub is available in the current scope
    *   }
    * }}}
    */
  trait BitcoinClientStubs {

    def bitcoinClientStub(jsonResponse: String): BitcoinClient[IO] = {
      val response = Response[IO](
        Status.Ok,
        body = Stream(jsonResponse).through(fs2.text.utf8.encode)
      )
      val rpcClient =
        new RpcClient[IO]("https://api-endpoint.com", 1, Client.fromHttpApp(HttpApp.liftF(IO.pure(response))))
      new BitcoinClient(rpcClient)
    }
  }

  /**
    * Stubs that can help to provide tests for the [[BitcoinPersistence]].
    *
    * Usage example:
    *
    * {{{
    *   "test name" in new BitcoinPersistenceStubs {
    *     // bitcoinPersistenceStub is available in the current scope
    *   }
    * }}}
    */
  class BitcoinPersistenceStubs(dbHandler: Database) {

    /**
      * This transactor object will actually execute any slick database action (DBIO) and convert
      * the result into a lazy IO value. When the IO effect is run to obtain the value, the transactor
      * automatically guarantees to properly release the underlying database resources.
      *
      * The default implementation of [[slickeffect.Transactor]] wraps Slick db into the resource,
      * to handle proper shutdown at the end of the execution. In the test mode we want to encapsulate
      * every single test, so we have to prevent `Transactor` from shutdown with providing own method
      * to run the Slick query. The [[slickeffect.Transactor]] uses `FunctionK` to do the execution, so we
      * need to `liftK` own effectful function with the `dbHandler.run`.
      * More info about [[cats.arrow.FunctionK]]: https://github.com/typelevel/cats/blob/master/docs/src/main/tut/datatypes/functionk.md
      */
    val tx = Transactor.liftK(new FunctionK[DBIO, IO] {
      def apply[A](dbio: DBIO[A]): IO[A] = IO.fromFuture(IO(dbHandler.run(dbio)))
    })

    val bitcoinPersistenceStub = new BitcoinPersistence[IO]
  }

}
