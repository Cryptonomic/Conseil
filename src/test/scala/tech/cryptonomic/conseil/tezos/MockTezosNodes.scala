package tech.cryptonomic.conseil.tezos
import tech.cryptonomic.conseil.util.JsonUtil

import scala.concurrent.Future
import scala.util.Try
import cats._
import cats.implicits._

class MockTezosNodes {
  import scala.concurrent.ExecutionContext.Implicits.global

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

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

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

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

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

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

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
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0", "one_branch_fork", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1", "one_branch_fork", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2", "one_branch_fork", "lorre_one")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

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
        case "blocks/BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0", "one_branch_fork", "lorre_two")
        case "blocks/BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1", "one_branch_fork", "lorre_two")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

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
        case "blocks/BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~0" =>
          getStoredBlock("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~0", "one_branch_fork", "lorre_three")
        case "blocks/BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~1" =>
          getStoredBlock("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~1", "one_branch_fork", "lorre_three")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  /**
    * Represents a chain with one fork, with two branches competing for dominance as Lorre iterates.
    */
  object OneBranchForkAlternating extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the first iteration of Lorre on a chain with one fork, with two branches competing for dominance as Lorre iterates.
    */
  object OneBranchForkAlternatingLorreOne extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0", "one_branch_fork_alternating", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1", "one_branch_fork_alternating", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2", "one_branch_fork_alternating", "lorre_one")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the second iteration of Lorre on a chain with one fork, with two branches competing for dominance as Lorre iterates.
    */
  object OneBranchForkAlternatingLorreTwo extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0", "one_branch_fork_alternating", "lorre_two")
        case "blocks/BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1", "one_branch_fork_alternating", "lorre_two")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the third iteration of Lorre on a chain with one fork, with two branches competing for dominance as Lorre iterates.
    */
  object OneBranchForkAlternatingLorreThree extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~0" =>
          getStoredBlock("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~0", "one_branch_fork_alternating", "lorre_three")
        case "blocks/BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~1" =>
          getStoredBlock("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~1", "one_branch_fork_alternating", "lorre_three")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  /**
    * Represents the fourth iteration of Lorre on a chain with one fork, with two branches competing for dominance as Lorre iterates.
    */
  object OneBranchForkAlternatingLorreFour extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BMSw4mdnPTRpNtNoRBGpnYDGGH9pcxB2FHBxQeonyUunaV5bAz1~0" =>
          getStoredBlock("BMSw4mdnPTRpNtNoRBGpnYDGGH9pcxB2FHBxQeonyUunaV5bAz1~0", "one_branch_fork_alternating", "lorre_four")
        case "blocks/BMSw4mdnPTRpNtNoRBGpnYDGGH9pcxB2FHBxQeonyUunaV5bAz1~1" =>
          getStoredBlock("BMSw4mdnPTRpNtNoRBGpnYDGGH9pcxB2FHBxQeonyUunaV5bAz1~1", "one_branch_fork_alternating", "lorre_four")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  /**
    * Represents the fifth iteration of Lorre on a chain with one fork, with two branches competing for dominance as Lorre iterates.
    */
  object OneBranchForkAlternatingLorreFive extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr~0" =>
          getStoredBlock("BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr~0", "one_branch_fork_alternating", "lorre_five")
        case "blocks/BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr~1" =>
          getStoredBlock("BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr~1", "one_branch_fork_alternating", "lorre_five")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  /**
    * Represents a chain with two forks, with the third branch becoming the main branch.
    */
  object TwoBranchFork extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents first iteration of Lorre for a chain with two forks.
    */
  object TwoBranchForkLorreOne extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0", "two_branch_fork", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1", "two_branch_fork", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2", "two_branch_fork", "lorre_one")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the second iteration of Lorre on a chain with two forks.
    */
  object TwoBranchForkLorreTwo extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0", "two_branch_fork", "lorre_two")
        case "blocks/BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1", "two_branch_fork", "lorre_two")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the third iteration of Lorre on a chain with two forks.
    */
  object TwoBranchForkLorreThree extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~0" =>
          getStoredBlock("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~0", "two_branch_fork", "lorre_three")
        case "blocks/BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~1" =>
          getStoredBlock("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~1", "two_branch_fork", "lorre_three")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  /**
    * Represents the fourth iteration of Lorre on a chain with one fork, with two branches competing for dominance as Lorre iterates.
    */
  object TwoBranchForkLorreFour extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~0" =>
          getStoredBlock("BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~0", "two_branch_fork", "lorre_four")
        case "blocks/BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~1" =>
          getStoredBlock("BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~1", "two_branch_fork", "lorre_four")
        case "blocks/BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~2" =>
          getStoredBlock("BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~2", "two_branch_fork", "lorre_four")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  /**
    * Represents a chain with two forks, and three branches competing for dominance on multiple iterations of Lorre.
    */
  object TwoBranchForkAlternating extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = ???

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the first iteration of Lorre on a chain with two forks, and three branches competing for dominance on multiple iterations of Lorre.
    */
  object TwoBranchForkAlternatingLorreOne extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~0", "two_branch_fork_alternating", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~1", "two_branch_fork_alternating", "lorre_one")
        case "blocks/BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2" =>
          getStoredBlock("BKy8NcuerruFgeCGAoUG3RfjhHf1diYjrgD2qAJ5rNwp2nRJ9H4~2", "two_branch_fork_alternating", "lorre_one")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the second iteration of Lorre on a chain with two forks, and three branches competing for dominance on multiple iterations of Lorre.
    */
  object TwoBranchForkAlternatingLorreTwo extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~0", "two_branch_fork_alternating", "lorre_two")
        case "blocks/BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1" =>
          getStoredBlock("BKpFANTnUBqVe8Hm4rUkNuYtJkg7PjLrHjkQaNQj7ph5Bi6qXVi~1", "two_branch_fork_alternating", "lorre_two")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */

  }

  /**
    * Represents the third iteration of Lorre on a chain with two forks, and three branches competing for dominance on multiple iterations of Lorre.
    */
  object TwoBranchForkAlternatingLorreThree extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~0" =>
          getStoredBlock("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~0", "two_branch_fork_alternating", "lorre_three")
        case "blocks/BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~1" =>
          getStoredBlock("BLFaY9jHrkuxnQAQv3wJif6V7S6ekGHoxbCBmeuFyLixGAYm3Bp~1", "two_branch_fork_alternating", "lorre_three")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  /**
    * Represents the fourth iteration of Lorre on a chain with two forks, and three branches competing for dominance on multiple iterations of Lorre.
    */
  object TwoBranchForkAlternatingLorreFour extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~0" =>
          getStoredBlock("BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~0", "two_branch_fork_alternating", "lorre_four")
        case "blocks/BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~1" =>
          getStoredBlock("BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~1", "two_branch_fork_alternating", "lorre_four")
        case "blocks/BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~2" =>
          getStoredBlock("BMKRY5YvFhbwcLPsV3vfvYZ97ktSfu2eJTx2V21PfUxUEYXzTsp~2", "two_branch_fork_alternating", "lorre_four")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  /**
    * Represents the fifth iteration of Lorre on a chain with one fork, with three branches competing for dominance as Lorre iterates.
    */
  object TwoBranchForkAlternatingLorreFive extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr~0" =>
          getStoredBlock("BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr~0", "two_branch_fork_alternating", "lorre_five")
        case "blocks/BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr~1" =>
          getStoredBlock("BM7bFA88UaPfBEW2XPZGCaB1rH38tjx21J571JxvkFp3JvSuBpr~1", "two_branch_fork_alternating", "lorre_five")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }

  /**
    * Represents the sixth iteration of Lorre on a chain with one fork, with three branches competing for dominance as Lorre iterates.
    */
  object TwoBranchForkAlternatingLorreSix extends TezosRPCInterface {

    override def runGetQuery(network: String, command: String): Try[String] = ???

    override def runPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Try[String] = ???

    override def runAsyncGetQuery(network: String, command: String): Future[String] = Future.successful{
      command match {
        case "blocks/BMeiBtFrXuVN7kcVaC4mt1dbncX2n8tb76qUeM4JCr97Cb7U84u~0" =>
          getStoredBlock("BMeiBtFrXuVN7kcVaC4mt1dbncX2n8tb76qUeM4JCr97Cb7U84u~0", "two_branch_fork_alternating", "lorre_six")
        case "blocks/BMeiBtFrXuVN7kcVaC4mt1dbncX2n8tb76qUeM4JCr97Cb7U84u~1" =>
          getStoredBlock("BMeiBtFrXuVN7kcVaC4mt1dbncX2n8tb76qUeM4JCr97Cb7U84u~1", "two_branch_fork_alternating", "lorre_six")
        case "blocks/BMeiBtFrXuVN7kcVaC4mt1dbncX2n8tb76qUeM4JCr97Cb7U84u~2" =>
          getStoredBlock("BMeiBtFrXuVN7kcVaC4mt1dbncX2n8tb76qUeM4JCr97Cb7U84u~2", "two_branch_fork_alternating", "lorre_six")
        case "blocks/BMeiBtFrXuVN7kcVaC4mt1dbncX2n8tb76qUeM4JCr97Cb7U84u~3" =>
          getStoredBlock("BMeiBtFrXuVN7kcVaC4mt1dbncX2n8tb76qUeM4JCr97Cb7U84u~3", "two_branch_fork_alternating", "lorre_six")
      }
    }

    override def runAsyncPostQuery(network: String, command: String, payload: Option[JsonUtil.JsonString]): Future[String] = ???

    override def runBatchedGetQuery[ID](network: String, ids: List[ID], mapToCommand: ID => String, concurrencyLevel: Int): Future[List[(ID, String)]] =
      ids.traverse(id =>
        runAsyncGetQuery(network, mapToCommand(id)).map((id, _))
      )

    /*
    Add a switching function here to switch between iterations of Lorre. Each chain uses a list of TezosRPCInterfaces
     */
  }



}
