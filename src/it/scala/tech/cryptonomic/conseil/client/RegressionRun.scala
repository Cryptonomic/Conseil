package tech.cryptonomic.conseil.client
import cats.effect.IOApp
import cats.effect.{ExitCode, IO}

/** Run this main class to test the basic regression suite */
object RegressionRun extends IOApp {

  /** The main entrypoint expects at most one parameter
    * Called with a string value, it will try to first run Lorre on the given
    * network, which should be one of those available on the configuration file.
    * This will currently fetch a fixed number of blocks from the tezos network, and then
    * run the test calls hitting endpoints of a locally run conseil process.
    *
    * A local database must be available, whose details will be defined once more in
    * the local configuration file.
    * If you need to first run the lorre fetch process, the database is expected to
    * be empty.
    * Otherwise a previously filled instance from this same application can be re-used to
    * dramatically speed up the process.
    */
  override def run(args: List[String]): IO[ExitCode] = {
    val network = args.headOption

    DataEndpointsClientProbe
      .runRegressionSuite(syncToNetwork = network)
      .map(_ => ExitCode.Success)
  }

}
