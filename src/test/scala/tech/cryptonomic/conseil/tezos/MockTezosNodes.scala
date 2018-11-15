package tech.cryptonomic.conseil.tezos
import tech.cryptonomic.conseil.util.JsonUtil

import scala.concurrent.Future
import scala.util.Try
import cats._
import cats.implicits._

class MockTezosNodes {

  //RENAME ALL TEST FILES IN TERMS OF TEST OFFSETS

  /**
    * Helper function that returns the json block data stored in the forking_tests files.
    * @param hashWithOffset <hash>~<offset>, where hash is of the current head block, and offset is how many levels away
    * @param chain which test chain we're working off of
    * @param lorre which iteration of lorre we're working off of
    * @return
    */
  private def getStoredBlock(hashWithOffset: String, chain: String, lorre: String): String =
    scala.io.Source.fromFile(s"src/test/resources/forking_tests/$chain/$lorre/$hashWithOffset.json").mkString

  /**
    * Represents an unforked chain with one iteration.
    */
  object NormalChain extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      commands.traverse(command => runAsyncGetQuery(network, command))

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents first iteration of Lorre for an unforked chain.
    */
  object NormalChainLorreOne extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0", "normal_chain", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1", "normal_chain", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2", "normal_chain", "lorre_one")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      commands.traverse(command => runAsyncGetQuery(network, command))

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents a chain with one fork, with the second branch winning.
    */
  object OneBranchFork extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      commands.traverse(command => runAsyncGetQuery(network, command))

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the first iteration of Lorre on a chain with one fork, with the second branch winning.
    */
  object OneBranchForkLorreOne extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0", "normal_chain", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1", "normal_chain", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2", "normal_chain", "lorre_one")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      commands.traverse(command => runAsyncGetQuery(network, command))

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the second iteration of Lorre on a chain with one fork, with the second branch winning.
    */
  object OneBranchForkLorreTwo extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0", "one_branch_fork", "lorre_two")
        case "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1", "one_branch_fork", "lorre_two")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      commands.traverse(command => runAsyncGetQuery(network, command))

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the third iteration of Lorre on a chain with one fork, with the second branch winning.
    */
  object OneBranchForkLorreThree extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0", "one_branch_fork", "lorre_two")
        case "BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1", "one_branch_fork", "lorre_two")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      commands.traverse(command => runAsyncGetQuery(network, command))

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  object OneBranchForkAlternating extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      commands.traverse(command => runAsyncGetQuery(network, command))

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  object TwoBranchFork extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      commands.traverse(command => runAsyncGetQuery(network, command))

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  object TwoBranchForkAlternating extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery(network: String, commands: List[String], concurrencyLevel: Int): Future[List[String]] =
      commands.traverse(command => runAsyncGetQuery(network, command))

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

}
