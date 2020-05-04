package tech.cryptonomic.conseil.client
import cats.effect.IOApp
import cats.effect.{ExitCode, IO}

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

    val (conf, network) = args match {
      case configfile :: network :: _ => (configfile, Some(network))
      case configfile :: Nil => (configfile, None)
      case _ => (defaultConfigFileName, None)
    }

    val configPrint = IO(
      println(
        s"Running with configuration from $conf and ${network.fold("no syncing to tezos")(net => "syncing to tezos " + net)}"
      )
    )

    for {
      _ <- configPrint
      probe <- DataEndpointsClientProbe(conf, network)
      _ <- probe.runRegressionSuite
    } yield ExitCode.Success

  }
}
