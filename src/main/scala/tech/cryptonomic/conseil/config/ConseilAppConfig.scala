package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.config.Platforms._
import pureconfig.generic.auto._
import pureconfig.loadConfig

/** wraps all configuration needed to run Conseil */
trait ConseilAppConfig {
  /** Lazily reads all configuration upstart, will print all errors encountered during loading */
  protected lazy val applicationConfiguration = {
    import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

    //this extra configuration might be needed as we add send operations to the conseil API
    // crypto <- loadConfig[SodiumConfiguration](namespace = "sodium.libraryPath")

    val loadedConf = for {
      server <- loadConfig[ServerConfiguration](namespace = "conseil")
      platforms <- loadConfig[PlatformsConfiguration](namespace = "platforms")
      securityApi <- Security()
      caching <- loadAkkaCacheConfig("akka.http.caching.lfu-cache")
    } yield (server, platforms, securityApi, caching)

    //something went wrong
    loadedConf.left.foreach {
      failures =>
        printConfigurationError(context = "Conseil application", failures.toList.mkString("\n\n"))
    }
    loadedConf

  }

}