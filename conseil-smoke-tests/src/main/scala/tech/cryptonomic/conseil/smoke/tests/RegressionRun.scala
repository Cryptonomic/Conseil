package tech.cryptonomic.conseil.smoke.tests

import cats.effect.{ExitCode, IO, IOApp}
import tech.cryptonomic.conseil.smoke.tests.suites.RegressionSuite

/** Run this main class to test the basic regression suite */
object RegressionRun extends IOApp {

  /** The main entrypoint expects at most two parameters:
    *  - the configuration file path [will run with a default if missing]
    *  - the network to sync to [will not download data before the test if missing]
    *
    * Called with a network value, it will try to first run Lorre on the given
    * network node, which should be one of those available on the configuration file.
    * This will currently fetch a fixed number of blocks from the tezos network, and then
    * run the tests hitting endpoints of a locally run conseil process.
    *
    * A local database must be available, whose details will be defined once more in
    * the local configuration file.
    * If you need to first run the lorre fetch process, the database is expected to
    * be empty.
    * Otherwise a previously filled instance from this same application can be re-used to
    * dramatically speed up the process.
    * This last optimization makes sense only if you're testing regressions against the Api changes.
    * Changing anything in the Lorre indexer would imply you need to re-run the indexer to check nothing
    * got broken!
    */
  override def run(args: List[String]): IO[ExitCode] = {
    val defaultConfigFileName = "conseil-regression-tests.conf"

    val (conf, platform, network) = args match {
      case platform :: network :: configfile :: _ => (configfile, platform, Some(network))
      case platform :: configfile :: Nil => (configfile, platform, None)
      case platform :: Nil => (defaultConfigFileName, platform, None)
    }

    val configPrint = {
      val syncReport = network.fold(s"no syncing to $platform")(net => s"syncing to $platform " + net)
      IO(println(s"Running with configuration from $conf and $syncReport"))
    }

    for {
      _ <- configPrint
      probe <- RegressionSuite(conf, platform, network)
      _ <- probe.runRegressionSuite
    } yield ExitCode.Success

  }
}
