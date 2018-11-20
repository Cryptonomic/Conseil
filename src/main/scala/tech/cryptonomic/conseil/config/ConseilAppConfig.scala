package tech.cryptonomic.conseil.config

import tech.cryptonomic.conseil.config.ConseilConfig._

/** wraps all configuration needed to run Conseil */
trait ConseilAppConfig {

  /** Lazily reads all configuration upstart, will print all errors encountered during loading */
  protected lazy val applicationConfiguration = {
    val loadedConf = for {
      server <- pureconfig.loadConfig[ConseilConfig.ServerConfiguration](namespace = "conseil")
      securityApi <- SecurityConfig()
    } yield (server, securityApi)

    //something went wrong
    loadedConf.left.foreach {
      failures =>
        printConfigurationError(context = "Conseil application", failures.toList.mkString("\n\n"))
    }
    loadedConf

  }

}