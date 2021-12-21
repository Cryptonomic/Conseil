package tech.cryptonomic.conseil.smoke.tests.suites

import cats.effect.{IO, Resource}
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import scala.sys.process.Process
import scala.util.Try

abstract class RegressionSuite {
  def runRegressionSuite: IO[Unit]

  val configfile: String
  val syncPlatform: String
  val syncNetwork: Option[String]

  val referenceBlockHash: String
  val depth: String

  val apiKey: String = {
    val key = Try(
      ConfigFactory.load().getStringList("conseil.security.api-keys.keys").get(0)
    )
    key.failed.foreach(
      e => println(s" ${e.getMessage} No apiKey found in configuration, I can't test conseil api without")
    )
    key.get
  }

  object Setup {
    /* We might wanna start with only 10k blocks */
    private def runLorre(platform: String, network: String) =
      Process(
        command = Seq("sbt", s"runLorre -v -d $depth -h $referenceBlockHash $platform $network"),
        cwd = None,
        extraEnv = "SBT_OPTS" -> s"-Dconfig.file=$configfile"
      )
    private def runApi =
      Process(
        command = Seq("sbt", "runApi -v"),
        cwd = None,
        extraEnv = "SBT_OPTS" -> s"-Dconfig.file=$configfile"
      )

    private def syncData(platform: String, network: String) =
      IO(runLorre(platform, network).!.ensuring(_ == 0, "lorre failed to load correctly"))

    private def startConseil: IO[Process] =
      for {
        _ <- if (syncNetwork.nonEmpty) syncData(syncPlatform, syncNetwork.get) else IO(0)
        proc <- IO(runApi.run())
        _ <- IO(println("waiting for conseil to start"))
        _ <- IO.sleep(30.seconds)
      } yield proc

    val conseilProcess = Resource.make(startConseil) { conseil =>
      IO(println(s"Stopping conseil process $conseil")) *>
        IO(conseil.destroy())
    }

  }
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
      case "ethereum" => IO(new EthereumRegressionSuite(configfile, syncNetwork))
      case "bitcoin" => IO(new BitcoinRegressionSuite(configfile, syncNetwork))
      case other => IO.raiseError(new IllegalArgumentException(s"Platform: $other is not supported!"))
    }
}
