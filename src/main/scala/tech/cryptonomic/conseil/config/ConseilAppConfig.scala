package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.config.Platforms._
import pureconfig.generic.auto._


/** wraps all configuration needed to run Conseil */
trait ConseilAppConfig {
  /** Lazily reads all configuration upstart, will print all errors encountered during loading */
  protected lazy val applicationConfiguration = {
    import tech.cryptonomic.conseil.util.ConfigUtil.Pureconfig._

    val loadedConf = for {
      server <- pureconfig.loadConfig[ServerConfiguration](namespace = "conseil")
      platforms <- pureconfig.loadConfig[PlatformsConfiguration](namespace = "platforms")
      securityApi <- Security()
    } yield (server, platforms, securityApi)

    //something went wrong
    loadedConf.left.foreach {
      failures =>
        printConfigurationError(context = "Conseil application", failures.toList.mkString("\n\n"))
    }
    loadedConf

  }

}