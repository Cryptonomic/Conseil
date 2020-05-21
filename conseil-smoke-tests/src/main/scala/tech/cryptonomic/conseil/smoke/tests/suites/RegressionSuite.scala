package tech.cryptonomic.conseil.smoke.tests.suites

import cats.effect.IO

trait RegressionSuite {
  def runRegressionSuite: IO[Unit]
}

object RegressionSuite {

  /** Creates a valid client probe or fails trying
    *
    * @param configfile should point to a valid configuration for Lorre/Conseil
    * @param syncPlatform should be a valid entry for one of supported platforms
    * @param syncNetwork can be a valid configuration entry for network to sync to
    * @return a client to run regression tests
    */
  def apply(configfile: String, syncPlatform: String, syncNetwork: Option[String] = None): IO[RegressionSuite] =
    syncPlatform.toLowerCase match {
      case "tezos" => IO(new TezosRegressionSuite(configfile, syncNetwork))
      case other => IO.raiseError(new IllegalArgumentException(s"Platform: $other is not supported!"))
    }
}
